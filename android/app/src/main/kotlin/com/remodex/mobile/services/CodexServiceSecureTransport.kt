package com.remodex.mobile.services

import com.remodex.mobile.core.crypto.RemodexNativeCrypto
import com.remodex.mobile.core.crypto.SecureEnvelopeCipher
import com.remodex.mobile.core.model.CODEX_SECURE_HANDSHAKE_TAG
import com.remodex.mobile.core.model.CODEX_SECURE_PROTOCOL_VERSION
import com.remodex.mobile.core.model.CodexSecureHandshakeMode
import com.remodex.mobile.core.model.CodexSecureSession
import com.remodex.mobile.core.model.CodexSecureTransportError
import com.remodex.mobile.core.model.CodexTrustedMacRecord
import com.remodex.mobile.core.model.CodexTrustedMacRegistry
import com.remodex.mobile.core.model.SecureApplicationPayload
import com.remodex.mobile.core.model.SecureClientAuth
import com.remodex.mobile.core.model.SecureClientHello
import com.remodex.mobile.core.model.SecureEnvelope
import com.remodex.mobile.core.model.SecureReadyMessage
import com.remodex.mobile.core.model.SecureResumeState
import com.remodex.mobile.core.model.SecureServerHello
import com.remodex.mobile.core.model.base64DecodeOrEmpty
import com.remodex.mobile.core.model.base64DecodeOrNull
import com.remodex.mobile.core.model.codexClientAuthTranscript
import com.remodex.mobile.core.model.codexSecureNonce
import com.remodex.mobile.core.model.codexSecureTranscriptBytes
import com.remodex.mobile.core.security.CodexSecureKeys
import com.remodex.mobile.core.security.PhoneIdentityStore
import com.remodex.mobile.core.transport.CodexSecureTransportErrorEvent
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.channels.ClosedReceiveChannelException

/**
 * Protocol label for phone→Mac encrypted envelopes (nonce + SecureEnvelope.sender).
 * Must stay `iphone` until phodex-bridge `secure-transport.js` accepts another literal for this direction.
 */
private const val SECURE_ENVELOPE_MOBILE_SENDER = "iphone"
private const val MAX_CIPHERTEXT_BASE64_LENGTH = 2 * 1024 * 1024
private const val MAX_TAG_BASE64_LENGTH = 64
private const val MAX_SECURE_PLAINTEXT_BYTES = 1 * 1024 * 1024

/**
 * Mirrors [CodexService+SecureTransport.swift](../../../../../../../../CodexMobile/CodexMobile/Services/CodexService+SecureTransport.swift).
 */
internal fun CodexService.secureWireText(plaintext: String): String {
    val sess =
        synchronized(secureSessionLock) {
            val current =
                secureSession
                    ?: throw CodexSecureTransportError.InvalidHandshake("The secure Remodex session is not ready yet. Try reconnecting.")
            if (current.nextOutboundCounter == Int.MAX_VALUE) {
                throw CodexSecureTransportError.InvalidHandshake("The secure Remodex session reached its message limit. Reconnect and try again.")
            }
            secureSession = current.copy(nextOutboundCounter = current.nextOutboundCounter + 1)
            current
        }
    val inner = SecureApplicationPayload(bridgeOutboundSeq = null, payloadText = plaintext)
    val payloadBytes = json.encodeToString(SecureApplicationPayload.serializer(), inner).encodeToByteArray()
    val counter = sess.nextOutboundCounter
    val nonce = codexSecureNonce(SECURE_ENVELOPE_MOBILE_SENDER, counter)
    val (ct, tag) = SecureEnvelopeCipher.seal(sess.phoneToMacKey, nonce, payloadBytes)
    val env =
        SecureEnvelope(
            kind = "encryptedEnvelope",
            v = CODEX_SECURE_PROTOCOL_VERSION,
            sessionId = sess.sessionId,
            keyEpoch = sess.keyEpoch,
            sender = SECURE_ENVELOPE_MOBILE_SENDER,
            counter = counter,
            ciphertext = Base64.getEncoder().encodeToString(ct),
            tag = Base64.getEncoder().encodeToString(tag),
        )
    return json.encodeToString(SecureEnvelope.serializer(), env)
}

internal fun CodexService.handleEncryptedEnvelope(raw: String) {
    val sess = secureSession ?: return
    val envelope = runCatching { json.decodeFromString<SecureEnvelope>(raw) }.getOrNull() ?: return
    if (envelope.sessionId != sess.sessionId || envelope.keyEpoch != sess.keyEpoch) return
    if (envelope.sender != "mac") return
    if (envelope.counter < 0) return
    if (envelope.counter <= sess.lastInboundCounter) return
    if (
        envelope.ciphertext.length > MAX_CIPHERTEXT_BASE64_LENGTH ||
        envelope.tag.length > MAX_TAG_BASE64_LENGTH
    ) {
        return
    }

    val nonce = codexSecureNonce("mac", envelope.counter)
    val plaintext =
        SecureEnvelopeCipher.open(
            sess.macToPhoneKey,
            nonce,
            base64DecodeOrEmpty(envelope.ciphertext),
            base64DecodeOrEmpty(envelope.tag),
        ) ?: return
    if (plaintext.size > MAX_SECURE_PLAINTEXT_BYTES) return

    val payload =
        runCatching {
            json.decodeFromString<SecureApplicationPayload>(plaintext.decodeToString())
        }.getOrNull() ?: return

    synchronized(secureSessionLock) {
        val current = secureSession ?: return
        if (current.sessionId != envelope.sessionId || current.keyEpoch != envelope.keyEpoch) return
        if (envelope.counter <= current.lastInboundCounter) return
        val nextBridgeSeq =
            payload.bridgeOutboundSeq?.let { seq ->
                if (seq <= current.lastInboundBridgeOutboundSeq) {
                    return
                }
                seq
            } ?: current.lastInboundBridgeOutboundSeq
        secureSession =
            current.copy(
                lastInboundCounter = envelope.counter,
                lastInboundBridgeOutboundSeq = nextBridgeSeq,
            )
        payload.bridgeOutboundSeq?.let { seq ->
            secureStore.writeString(CodexSecureKeys.relayLastAppliedBridgeOutboundSeq, seq.toString())
        }
    }

    val innerRpc = jsonRpc.decodeMessage(payload.payloadText) ?: return
    dispatchIncomingRpc(innerRpc)
}

internal suspend fun CodexService.performSecureHandshake() {
    val snapshot = sessionPersistence.loadRelaySnapshot()
    val sessionId =
        snapshot.relaySessionId?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw CodexSecureTransportError.InvalidHandshake(
                "The saved relay pairing is incomplete. Scan a fresh QR code to reconnect.",
            )
    val macDeviceId =
        snapshot.relayMacDeviceId?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw CodexSecureTransportError.InvalidHandshake(
                "The saved relay pairing is incomplete. Scan a fresh QR code to reconnect.",
            )

    val phone = PhoneIdentityStore.loadOrCreate(secureStore)
    val registry =
        secureStore.readCodable<CodexTrustedMacRegistry>(CodexSecureKeys.trustedMacRegistry)
            ?: CodexTrustedMacRegistry.empty
    val trustedMac = registry.records[macDeviceId]
    val forceQr = sessionPersistence.shouldForceQrBootstrapOnNextHandshake()
    val handshakeMode =
        if (!forceQr && trustedMac != null) {
            CodexSecureHandshakeMode.trustedReconnect
        } else {
            CodexSecureHandshakeMode.qrBootstrap
        }

    val expectedMacIdentityPublicKey =
        when (handshakeMode) {
            CodexSecureHandshakeMode.trustedReconnect ->
                trustedMac!!.macIdentityPublicKey
            CodexSecureHandshakeMode.qrBootstrap ->
                snapshot.relayMacIdentityPublicKey?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw CodexSecureTransportError.InvalidHandshake(
                        "The initial pairing metadata is missing the Mac identity key. Scan a new QR code to reconnect.",
                    )
        }

    val relayProtocolVersion =
        snapshot.relayProtocolVersion?.toIntOrNull() ?: CODEX_SECURE_PROTOCOL_VERSION
    val lastSeq = snapshot.relayLastAppliedBridgeOutboundSeq?.toIntOrNull() ?: 0

    val clientNonceBytes = RemodexNativeCrypto.randomBytes(32)
    val clientNonceB64 = Base64.getEncoder().encodeToString(clientNonceBytes)
    val (ephPriv, ephPub) = RemodexNativeCrypto.generateX25519KeyPair()
    val phoneEphemeralPublicKeyB64 = Base64.getEncoder().encodeToString(ephPub)

    val clientHello =
        SecureClientHello(
            protocolVersion = relayProtocolVersion,
            sessionId = sessionId,
            handshakeMode = handshakeMode,
            phoneDeviceId = phone.phoneDeviceId,
            phoneIdentityPublicKey = phone.phoneIdentityPublicKey,
            phoneEphemeralPublicKey = phoneEphemeralPublicKeyB64,
            clientNonce = clientNonceB64,
        )
    sendRawText(json.encodeToString(SecureClientHello.serializer(), clientHello))

    val serverHello =
        waitMatchingServerHello(
            expectedSessionId = sessionId,
            expectedMacDeviceId = macDeviceId,
            expectedMacIdentityPublicKey = expectedMacIdentityPublicKey,
            expectedClientNonce = clientNonceB64,
            clientNonceBytes = clientNonceBytes,
            phoneDeviceId = phone.phoneDeviceId,
            phoneIdentityPublicKey = phone.phoneIdentityPublicKey,
            phoneEphemeralPublicKey = phoneEphemeralPublicKeyB64,
        )

    if (serverHello.protocolVersion != CODEX_SECURE_PROTOCOL_VERSION) {
        throw CodexSecureTransportError.IncompatibleVersion(
            "This bridge is using a different secure transport version. Update Remodex on the phone or Mac and try again.",
        )
    }
    if (serverHello.sessionId != sessionId) {
        throw CodexSecureTransportError.InvalidHandshake("The secure bridge session ID did not match the saved pairing.")
    }
    if (serverHello.macDeviceId != macDeviceId) {
        throw CodexSecureTransportError.InvalidHandshake(
            "The bridge reported a different Mac identity for this relay session.",
        )
    }
    if (serverHello.macIdentityPublicKey != expectedMacIdentityPublicKey) {
        throw CodexSecureTransportError.InvalidHandshake("The secure Mac identity key did not match the paired device.")
    }

    val serverNonceBytes = decodeHandshakeBase64(serverHello.serverNonce, "serverNonce")
    val transcriptBytes =
        codexSecureTranscriptBytes(
            sessionId = sessionId,
            protocolVersion = serverHello.protocolVersion,
            handshakeMode = serverHello.handshakeMode,
            keyEpoch = serverHello.keyEpoch,
            macDeviceId = serverHello.macDeviceId,
            phoneDeviceId = phone.phoneDeviceId,
            macIdentityPublicKey = serverHello.macIdentityPublicKey,
            phoneIdentityPublicKey = phone.phoneIdentityPublicKey,
            macEphemeralPublicKey = serverHello.macEphemeralPublicKey,
            phoneEphemeralPublicKey = phoneEphemeralPublicKeyB64,
            clientNonce = clientNonceBytes,
            serverNonce = serverNonceBytes,
            expiresAtForTranscript = serverHello.expiresAtForTranscript,
        )

    val macPubBytes = decodeHandshakeBase64(serverHello.macIdentityPublicKey, "macIdentityPublicKey")
    val macSigBytes = decodeHandshakeBase64(serverHello.macSignature, "macSignature")
    if (!RemodexNativeCrypto.ed25519Verify(transcriptBytes, macSigBytes, macPubBytes)) {
        throw CodexSecureTransportError.InvalidHandshake("The secure Mac signature could not be verified.")
    }

    val phonePriv = decodeHandshakeBase64(phone.phoneIdentityPrivateKey, "phoneIdentityPrivateKey")
    val clientAuthTranscript = codexClientAuthTranscript(transcriptBytes)
    val phoneSig = RemodexNativeCrypto.ed25519Sign(clientAuthTranscript, phonePriv)
    val clientAuth =
        SecureClientAuth(
            sessionId = sessionId,
            phoneDeviceId = phone.phoneDeviceId,
            keyEpoch = serverHello.keyEpoch,
            phoneSignature = Base64.getEncoder().encodeToString(phoneSig),
        )
    sendRawText(json.encodeToString(SecureClientAuth.serializer(), clientAuth))

    waitMatchingSecureReady(
        expectedSessionId = sessionId,
        expectedKeyEpoch = serverHello.keyEpoch,
        expectedMacDeviceId = macDeviceId,
    )

    val shared =
        RemodexNativeCrypto.x25519SharedSecret(
            ephPriv,
            decodeHandshakeBase64(serverHello.macEphemeralPublicKey, "macEphemeralPublicKey"),
        )
    val salt = RemodexNativeCrypto.sha256(transcriptBytes)
    val infoPrefix =
        "$CODEX_SECURE_HANDSHAKE_TAG|$sessionId|$macDeviceId|${phone.phoneDeviceId}|${serverHello.keyEpoch}"
    val phoneToMac =
        RemodexNativeCrypto.hkdfSha256(
            shared,
            salt,
            "$infoPrefix|phoneToMac".encodeToByteArray(),
            32,
        )
    val macToPhone =
        RemodexNativeCrypto.hkdfSha256(
            shared,
            salt,
            "$infoPrefix|macToPhone".encodeToByteArray(),
            32,
        )

    secureSession =
        CodexSecureSession(
            sessionId = sessionId,
            keyEpoch = serverHello.keyEpoch,
            macDeviceId = macDeviceId,
            macIdentityPublicKey = serverHello.macIdentityPublicKey,
            phoneToMacKey = phoneToMac,
            macToPhoneKey = macToPhone,
            lastInboundBridgeOutboundSeq = lastSeq,
            lastInboundCounter = -1,
            nextOutboundCounter = 0,
        )

    if (handshakeMode == CodexSecureHandshakeMode.qrBootstrap) {
        val nextRegistry =
            trustMacRecord(
                registry,
                macDeviceId,
                serverHello.macIdentityPublicKey,
                snapshot.relayUrl,
            )
        secureStore.writeCodable(CodexSecureKeys.trustedMacRegistry, nextRegistry)
        secureStore.writeString(CodexSecureKeys.lastTrustedMacDeviceId, macDeviceId)
    }

    sessionPersistence.setForceQrBootstrapOnNextHandshake(false)

    val resume =
        SecureResumeState(
            sessionId = sessionId,
            keyEpoch = serverHello.keyEpoch,
            lastAppliedBridgeOutboundSeq = lastSeq,
        )
    sendRawText(json.encodeToString(SecureResumeState.serializer(), resume))
}

private fun CodexService.trustMacRecord(
    existing: CodexTrustedMacRegistry,
    deviceId: String,
    publicKey: String,
    relayURL: String?,
): CodexTrustedMacRegistry {
    val prev = existing.records[deviceId]
    val record =
        CodexTrustedMacRecord(
            macDeviceId = deviceId,
            macIdentityPublicKey = publicKey,
            lastPairedAt = Instant.now(),
            relayURL = relayURL ?: prev?.relayURL,
            displayName = prev?.displayName,
            lastResolvedSessionId = prev?.lastResolvedSessionId,
            lastResolvedAt = prev?.lastResolvedAt,
            lastUsedAt = Instant.now(),
        )
    return CodexTrustedMacRegistry(existing.records + (deviceId to record))
}

private suspend fun CodexService.waitMatchingServerHello(
    expectedSessionId: String,
    expectedMacDeviceId: String,
    expectedMacIdentityPublicKey: String,
    expectedClientNonce: String,
    clientNonceBytes: ByteArray,
    phoneDeviceId: String,
    phoneIdentityPublicKey: String,
    phoneEphemeralPublicKey: String,
): SecureServerHello {
    while (true) {
        val raw = receiveSecureControl("serverHello")
        val hello = runCatching { json.decodeFromString<SecureServerHello>(raw) }.getOrNull() ?: continue
        if (hello.clientNonce != null && hello.clientNonce != expectedClientNonce) {
            continue
        }
        if (hello.clientNonce == null) {
            if (
                !isMatchingLegacyServerHello(
                    hello,
                    expectedSessionId = expectedSessionId,
                    expectedMacDeviceId = expectedMacDeviceId,
                    expectedMacIdentityPublicKey = expectedMacIdentityPublicKey,
                    clientNonce = clientNonceBytes,
                    phoneDeviceId = phoneDeviceId,
                    phoneIdentityPublicKey = phoneIdentityPublicKey,
                    phoneEphemeralPublicKey = phoneEphemeralPublicKey,
                )
            ) {
                continue
            }
        }
        return hello
    }
}

private fun CodexService.isMatchingLegacyServerHello(
    hello: SecureServerHello,
    expectedSessionId: String,
    expectedMacDeviceId: String,
    expectedMacIdentityPublicKey: String,
    clientNonce: ByteArray,
    phoneDeviceId: String,
    phoneIdentityPublicKey: String,
    phoneEphemeralPublicKey: String,
): Boolean {
    if (hello.protocolVersion != CODEX_SECURE_PROTOCOL_VERSION) return false
    if (hello.sessionId != expectedSessionId) return false
    if (hello.macDeviceId != expectedMacDeviceId) return false
    if (hello.macIdentityPublicKey != expectedMacIdentityPublicKey) return false
    val macPub = base64DecodeOrNull(hello.macIdentityPublicKey) ?: return false
    val serverNonce = base64DecodeOrNull(hello.serverNonce) ?: return false
    val macSignature = base64DecodeOrNull(hello.macSignature) ?: return false
    val transcriptBytes =
        codexSecureTranscriptBytes(
            sessionId = expectedSessionId,
            protocolVersion = hello.protocolVersion,
            handshakeMode = hello.handshakeMode,
            keyEpoch = hello.keyEpoch,
            macDeviceId = hello.macDeviceId,
            phoneDeviceId = phoneDeviceId,
            macIdentityPublicKey = hello.macIdentityPublicKey,
            phoneIdentityPublicKey = phoneIdentityPublicKey,
            macEphemeralPublicKey = hello.macEphemeralPublicKey,
            phoneEphemeralPublicKey = phoneEphemeralPublicKey,
            clientNonce = clientNonce,
            serverNonce = serverNonce,
            expiresAtForTranscript = hello.expiresAtForTranscript,
        )
    return RemodexNativeCrypto.ed25519Verify(
        transcriptBytes,
        macSignature,
        macPub,
    )
}

private fun decodeHandshakeBase64(
    value: String,
    fieldName: String,
): ByteArray =
    base64DecodeOrNull(value)
        ?: throw CodexSecureTransportError.InvalidHandshake("The secure bridge sent invalid $fieldName metadata.")

private suspend fun CodexService.waitMatchingSecureReady(
    expectedSessionId: String,
    expectedKeyEpoch: Int,
    expectedMacDeviceId: String,
) {
    while (true) {
        val raw = receiveSecureControl("secureReady")
        val ready = runCatching { json.decodeFromString<SecureReadyMessage>(raw) }.getOrNull() ?: continue
        if (ready.sessionId == expectedSessionId &&
            ready.keyEpoch == expectedKeyEpoch &&
            ready.macDeviceId == expectedMacDeviceId
        ) {
            return
        }
    }
}

private suspend fun CodexService.receiveSecureControl(kind: String): String {
    val mux = controlMux ?: throw CodexSecureTransportError.InvalidHandshake("Secure transport is not initialized.")
    try {
        return mux.receive(kind)
    } catch (e: ClosedReceiveChannelException) {
        val c = e.cause
        if (c is CodexSecureTransportErrorEvent) {
            throw CodexSecureTransportError.SecureError(c.message)
        }
        throw e
    }
}
