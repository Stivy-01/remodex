package com.remodex.mobile.core.model

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val CODEX_SECURE_PROTOCOL_VERSION = 1
const val CODEX_PAIRING_QR_VERSION = 2
const val CODEX_SECURE_HANDSHAKE_TAG = "remodex-e2ee-v1"
const val CODEX_SECURE_HANDSHAKE_LABEL = "client-auth"
const val CODEX_SECURE_CLOCK_SKEW_TOLERANCE_SECONDS = 60.0
const val CODEX_TRUSTED_SESSION_RESOLVE_TAG = "remodex-trusted-session-resolve-v1"
const val CODEX_TRUSTED_SESSION_RESOLVE_RESPONSE_TAG = "remodex-trusted-session-resolve-response-v1"
const val CODEX_TRUSTED_SESSION_RESOLVE_CLOCK_SKEW_TOLERANCE_SECONDS = 90.0

@Serializable
enum class CodexSecureHandshakeMode {
    @SerialName("qr_bootstrap")
    qrBootstrap,

    @SerialName("trusted_reconnect")
    trustedReconnect,
}

enum class CodexSecureConnectionState {
    notPaired,
    trustedMac,
    liveSessionUnresolved,
    handshaking,
    encrypted,
    reconnecting,
    rePairRequired,
    updateRequired;

    val statusLabel: String
        get() =
            when (this) {
                notPaired -> "Not paired"
                trustedMac -> "Trusted Mac"
                liveSessionUnresolved -> "Trusted Mac ready"
                handshaking -> "Secure handshake in progress"
                encrypted -> "End-to-end encrypted"
                reconnecting -> "Reconnecting securely"
                rePairRequired -> "Re-pair required"
                updateRequired -> "Update required"
            }
}

@Serializable
data class CodexPairingQRPayload(
    val v: Int,
    val relay: String,
    val sessionId: String,
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val expiresAt: Long,
)

@Serializable
data class CodexPhoneIdentityState(
    val phoneDeviceId: String,
    val phoneIdentityPrivateKey: String,
    val phoneIdentityPublicKey: String,
)

@Serializable
data class CodexTrustedMacRecord(
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    @Serializable(Iso8601InstantSerializer::class)
    val lastPairedAt: Instant,
    val relayURL: String? = null,
    val displayName: String? = null,
    val lastResolvedSessionId: String? = null,
    @Serializable(Iso8601InstantSerializer::class)
    val lastResolvedAt: Instant? = null,
    @Serializable(Iso8601InstantSerializer::class)
    val lastUsedAt: Instant? = null,
)

@Serializable
data class CodexTrustedMacRegistry(
    val records: Map<String, CodexTrustedMacRecord> = emptyMap(),
) {
    companion object {
        val empty = CodexTrustedMacRegistry()
    }
}

@Serializable
data class SecureClientHello(
    val kind: String = "clientHello",
    val protocolVersion: Int,
    val sessionId: String,
    val handshakeMode: CodexSecureHandshakeMode,
    val phoneDeviceId: String,
    val phoneIdentityPublicKey: String,
    val phoneEphemeralPublicKey: String,
    val clientNonce: String,
)

@Serializable
data class SecureServerHello(
    val kind: String,
    val protocolVersion: Int,
    val sessionId: String,
    val handshakeMode: CodexSecureHandshakeMode,
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val macEphemeralPublicKey: String,
    val serverNonce: String,
    val keyEpoch: Int,
    val expiresAtForTranscript: Long,
    val macSignature: String,
    val clientNonce: String? = null,
)

@Serializable
data class SecureClientAuth(
    val kind: String = "clientAuth",
    val sessionId: String,
    val phoneDeviceId: String,
    val keyEpoch: Int,
    val phoneSignature: String,
)

@Serializable
data class SecureReadyMessage(
    val kind: String,
    val sessionId: String,
    val keyEpoch: Int,
    val macDeviceId: String,
)

@Serializable
data class SecureResumeState(
    val kind: String = "resumeState",
    val sessionId: String,
    val keyEpoch: Int,
    val lastAppliedBridgeOutboundSeq: Int,
)

@Serializable
data class SecureErrorMessage(
    val kind: String,
    val code: String,
    val message: String,
)

@Serializable
data class SecureEnvelope(
    val kind: String,
    val v: Int,
    val sessionId: String,
    val keyEpoch: Int,
    val sender: String,
    val counter: Int,
    val ciphertext: String,
    val tag: String,
)

@Serializable
data class SecureApplicationPayload(
    val bridgeOutboundSeq: Int? = null,
    val payloadText: String,
)

/** Established session keys and sequencing (in-memory; not serialized to disk as a single blob on iOS). */
data class CodexSecureSession(
    val sessionId: String,
    val keyEpoch: Int,
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val phoneToMacKey: ByteArray,
    val macToPhoneKey: ByteArray,
    val lastInboundBridgeOutboundSeq: Int,
    val lastInboundCounter: Int,
    val nextOutboundCounter: Int,
)

/** Handshake state between hello and ready (ephemeral key material as raw bytes until crypto layer lands). */
data class CodexPendingHandshake(
    val mode: CodexSecureHandshakeMode,
    val transcriptBytes: ByteArray,
    val phoneEphemeralPrivateKey: ByteArray,
    val phoneDeviceId: String,
)

@Serializable
data class CodexTrustedSessionResolveRequest(
    val macDeviceId: String,
    val phoneDeviceId: String,
    val phoneIdentityPublicKey: String,
    val nonce: String,
    val timestamp: Long,
    val signature: String,
)

@Serializable
data class CodexTrustedSessionResolveResponse(
    val ok: Boolean,
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val displayName: String? = null,
    val sessionId: String,
    val responseTimestamp: Long,
    val signature: String,
)

@Serializable
data class CodexRelayErrorResponse(
    val ok: Boolean? = null,
    val error: String? = null,
    val code: String? = null,
)

sealed class CodexSecureTransportError(
    message: String,
) : Exception(message) {
    class InvalidQr(
        message: String,
    ) : CodexSecureTransportError(message)

    class SecureError(
        message: String,
    ) : CodexSecureTransportError(message)

    class IncompatibleVersion(
        message: String,
    ) : CodexSecureTransportError(message)

    class InvalidHandshake(
        message: String,
    ) : CodexSecureTransportError(message)

    data object DecryptFailed : CodexSecureTransportError("Unable to decrypt the secure Remodex payload.")

    class TimedOut(
        message: String,
    ) : CodexSecureTransportError(message)
}

sealed class CodexTrustedSessionResolveError(
    message: String,
) : Exception(message) {
    data object NoTrustedMac : CodexTrustedSessionResolveError("No trusted Mac is available to reconnect.")

    data object UnsupportedRelay : CodexTrustedSessionResolveError("This relay does not support trusted reconnect yet.")

    class MacOffline(
        message: String,
    ) : CodexTrustedSessionResolveError(message)

    class RePairRequired(
        message: String,
    ) : CodexTrustedSessionResolveError(message)

    class InvalidResponse(
        message: String,
    ) : CodexTrustedSessionResolveError(message)

    class Network(
        message: String,
    ) : CodexTrustedSessionResolveError(message)
}

fun codexTrustedSessionResolveTranscriptBytes(
    macDeviceId: String,
    phoneDeviceId: String,
    phoneIdentityPublicKey: String,
    nonce: String,
    timestamp: Long,
): ByteArray {
    val out = ByteArrayOutputStream()
    out.appendLengthPrefixedUtf8(CODEX_TRUSTED_SESSION_RESOLVE_TAG)
    out.appendLengthPrefixedUtf8(macDeviceId)
    out.appendLengthPrefixedUtf8(phoneDeviceId)
    out.appendLengthPrefixedData(base64DecodeOrEmpty(phoneIdentityPublicKey))
    out.appendLengthPrefixedUtf8(nonce)
    out.appendLengthPrefixedUtf8(timestamp.toString())
    return out.toByteArray()
}

fun codexTrustedSessionResolveResponseTranscriptBytes(
    macDeviceId: String,
    macIdentityPublicKey: String,
    displayName: String?,
    sessionId: String,
    phoneDeviceId: String,
    phoneIdentityPublicKey: String,
    nonce: String,
    timestamp: Long,
    responseTimestamp: Long,
): ByteArray {
    val out = ByteArrayOutputStream()
    out.appendLengthPrefixedUtf8(CODEX_TRUSTED_SESSION_RESOLVE_RESPONSE_TAG)
    out.appendLengthPrefixedUtf8(macDeviceId)
    out.appendLengthPrefixedData(base64DecodeOrEmpty(macIdentityPublicKey))
    out.appendLengthPrefixedUtf8(displayName.orEmpty())
    out.appendLengthPrefixedUtf8(sessionId)
    out.appendLengthPrefixedUtf8(phoneDeviceId)
    out.appendLengthPrefixedData(base64DecodeOrEmpty(phoneIdentityPublicKey))
    out.appendLengthPrefixedUtf8(nonce)
    out.appendLengthPrefixedUtf8(timestamp.toString())
    out.appendLengthPrefixedUtf8(responseTimestamp.toString())
    return out.toByteArray()
}

fun codexSecureTranscriptBytes(
    sessionId: String,
    protocolVersion: Int,
    handshakeMode: CodexSecureHandshakeMode,
    keyEpoch: Int,
    macDeviceId: String,
    phoneDeviceId: String,
    macIdentityPublicKey: String,
    phoneIdentityPublicKey: String,
    macEphemeralPublicKey: String,
    phoneEphemeralPublicKey: String,
    clientNonce: ByteArray,
    serverNonce: ByteArray,
    expiresAtForTranscript: Long,
): ByteArray {
    val out = ByteArrayOutputStream()
    out.appendLengthPrefixedUtf8(CODEX_SECURE_HANDSHAKE_TAG)
    out.appendLengthPrefixedUtf8(sessionId)
    out.appendLengthPrefixedUtf8(protocolVersion.toString())
    out.appendLengthPrefixedUtf8(handshakeMode.nameWire())
    out.appendLengthPrefixedUtf8(keyEpoch.toString())
    out.appendLengthPrefixedUtf8(macDeviceId)
    out.appendLengthPrefixedUtf8(phoneDeviceId)
    out.appendLengthPrefixedData(base64DecodeOrEmpty(macIdentityPublicKey))
    out.appendLengthPrefixedData(base64DecodeOrEmpty(phoneIdentityPublicKey))
    out.appendLengthPrefixedData(base64DecodeOrEmpty(macEphemeralPublicKey))
    out.appendLengthPrefixedData(base64DecodeOrEmpty(phoneEphemeralPublicKey))
    out.appendLengthPrefixedData(clientNonce)
    out.appendLengthPrefixedData(serverNonce)
    out.appendLengthPrefixedUtf8(expiresAtForTranscript.toString())
    return out.toByteArray()
}

private fun CodexSecureHandshakeMode.nameWire(): String =
    when (this) {
        CodexSecureHandshakeMode.qrBootstrap -> "qr_bootstrap"
        CodexSecureHandshakeMode.trustedReconnect -> "trusted_reconnect"
    }

fun codexClientAuthTranscript(transcriptBytes: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(transcriptBytes)
    out.appendLengthPrefixedUtf8(CODEX_SECURE_HANDSHAKE_LABEL)
    return out.toByteArray()
}

fun codexSecureNonce(
    sender: String,
    counter: Int,
): ByteArray {
    val nonce = ByteArray(12) { 0 }
    nonce[0] = if (sender == "mac") 1 else 2
    var remaining = counter.toLong()
    for (index in 11 downTo 1) {
        nonce[index] = (remaining and 0xff).toByte()
        remaining = remaining ushr 8
    }
    return nonce
}

fun codexSecureFingerprint(publicKeyBase64: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(base64DecodeOrEmpty(publicKeyBase64))
    return digest.joinToString("") { "%02x".format(it) }.take(12).uppercase()
}

fun base64DecodeOrEmpty(value: String): ByteArray =
    base64DecodeOrNull(value) ?: ByteArray(0)

fun base64DecodeOrNull(value: String): ByteArray? =
    try {
        Base64.getDecoder().decode(value)
    } catch (_: IllegalArgumentException) {
        null
    }

private fun ByteArrayOutputStream.appendLengthPrefixedUtf8(value: String) {
    appendLengthPrefixedData(value.toByteArray(StandardCharsets.UTF_8))
}

private fun ByteArrayOutputStream.appendLengthPrefixedData(value: ByteArray) {
    val len = value.size
    require(len >= 0)
    write((len ushr 24) and 0xFF)
    write((len ushr 16) and 0xFF)
    write((len ushr 8) and 0xFF)
    write(len and 0xFF)
    write(value)
}
