package com.remodex.mobile.pairing

import com.remodex.mobile.core.persistence.RelaySessionSnapshot
import com.remodex.mobile.core.persistence.SessionPersistence
import com.remodex.mobile.core.model.CodexPairingQRPayload
import com.remodex.mobile.core.security.SecureStore
import com.remodex.mobile.core.transport.isLocalRelayHost
import com.remodex.mobile.core.transport.validateRelayUrl
import com.remodex.mobile.data.CodexRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Relay URL uses loopback — phone cannot reach the PC bridge without a LAN IP override. */
class LoopbackRelayException(
    message: String,
) : Exception(message)

/**
 * Build WebSocket URL + bearer token from saved snapshot (parity with debug “Connect (saved pairing)” flow).
 */
fun buildWebSocketConnectParams(
    snap: RelaySessionSnapshot,
    relayHostOverride: String = "",
): Pair<String, String> {
    val relayRaw =
        snap.relayUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("No relay URL — scan a pairing QR first.")
    if (relayHostOverride.trim().isEmpty() && isLoopbackRelayHost(relayRaw)) {
        throw LoopbackRelayException(
            "Relay uses 127.0.0.1 / localhost — on the phone that points to the device itself, not your PC. " +
                "Enter your computer's Wi‑Fi IPv4 (e.g. from ipconfig). Android emulator: try 10.0.2.2.",
        )
    }
    val relay = applyRelayHostOverride(relayRaw, relayHostOverride)
    val sessionId =
        snap.relaySessionId?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("No session id — scan a pairing QR first.")
    val url = "${relay.trimEnd('/')}/$sessionId"
    return url to sessionId
}

/**
 * Disconnects and reconnects using [snap] when relay fields are present (parity iOS `toggleConnection`
 * after bridge update, including service-tier capability prompts).
 */
suspend fun reconnectUsingSavedRelaySnapshot(
    repository: CodexRepository,
    snap: RelaySessionSnapshot,
    relayHostOverride: String = "",
) {
    if (snap.relayUrl.isNullOrBlank() || snap.relaySessionId.isNullOrBlank()) return
    repository.disconnect()
    val (url, token) = buildWebSocketConnectParams(snap, relayHostOverride)
    repository.connect(serverUrl = url, token = token, role = null)
}

suspend fun applyQrPayloadAndConnect(
    repository: CodexRepository,
    sessionPersistence: SessionPersistence,
    secureStore: SecureStore,
    payload: CodexPairingQRPayload,
    relayHostOverride: String,
    tokenOverride: String,
) {
    withContext(Dispatchers.IO) {
        sessionPersistence.applyPairingPayload(payload, secureStore)
        sessionPersistence.saveLocalRelayHostOverride(relayHostOverride)
    }
    val snap = sessionPersistence.loadRelaySnapshot()
    val (url, defaultToken) = buildWebSocketConnectParams(snap, relayHostOverride)
    val token = tokenOverride.trim().ifEmpty { defaultToken }
    repository.connect(serverUrl = url, token = token, role = null)
}

fun isLoopbackRelayHost(relayUrl: String): Boolean {
    val host = validateRelayUrl(relayUrl)?.httpUrl?.host?.lowercase() ?: return false
    return host == "127.0.0.1" || host == "localhost" || host == "::1"
}

fun applyRelayHostOverride(
    relayUrl: String,
    overrideHost: String,
): String {
    val host = overrideHost.trim()
    if (host.isEmpty()) return relayUrl
    val trimmed = relayUrl.trim()
    val ws = trimmed.startsWith("ws://", ignoreCase = true)
    val wss = trimmed.startsWith("wss://", ignoreCase = true)
    val forParse =
        when {
            ws -> "http://${trimmed.substring(5)}"
            wss -> "https://${trimmed.substring(6)}"
            else -> trimmed
        }
    val parsed = forParse.toHttpUrlOrNull() ?: return relayUrl
    if (parsed.isHttps.not() && !isLocalRelayHost(host)) return relayUrl
    val httpish = parsed.newBuilder().host(host).build().toString()
    return when {
        wss -> httpish.replaceFirst("https://", "wss://", ignoreCase = true)
        ws -> httpish.replaceFirst("http://", "ws://", ignoreCase = true)
        else -> httpish
    }
}
