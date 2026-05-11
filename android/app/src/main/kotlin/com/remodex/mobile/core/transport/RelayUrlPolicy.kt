package com.remodex.mobile.core.transport

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val supportedRelaySchemes = setOf("ws", "wss", "http", "https")

data class RelayUrlValidation(
    val httpUrl: HttpUrl,
    val websocketUrl: String,
    val cleartext: Boolean,
)

fun validateRelayUrl(
    rawRelayUrl: String,
    requireWebSocketScheme: Boolean = false,
): RelayUrlValidation? {
    val trimmed = rawRelayUrl.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val scheme = trimmed.substringBefore("://", missingDelimiterValue = "").lowercase()
    if (scheme !in supportedRelaySchemes) return null
    if (requireWebSocketScheme && scheme !in setOf("ws", "wss")) return null

    val httpish =
        when (scheme) {
            "ws" -> "http://${trimmed.substring(5)}"
            "wss" -> "https://${trimmed.substring(6)}"
            else -> trimmed
        }
    val httpUrl = httpish.toHttpUrlOrNull() ?: return null
    if (httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()) return null

    val cleartext = scheme == "ws" || scheme == "http"
    if (cleartext && !isLocalRelayHost(httpUrl.host)) return null

    val websocketUrl =
        when (scheme) {
            "https" -> httpUrl.toString().replaceFirst("https://", "wss://", ignoreCase = true)
            "http" -> httpUrl.toString().replaceFirst("http://", "ws://", ignoreCase = true)
            else -> trimmed
        }
    return RelayUrlValidation(httpUrl = httpUrl, websocketUrl = websocketUrl, cleartext = cleartext)
}

fun isLocalRelayHost(host: String): Boolean {
    val h = host.trim().trim('[', ']').lowercase()
    if (h.isEmpty()) return false
    if (h == "localhost" || h == "::1" || h.endsWith(".local")) return true
    if (!h.contains('.') && !h.contains(':')) return true
    if (isPrivateIpv4(h)) return true
    if (!h.contains(':')) return false
    return h.startsWith("fc") ||
        h.startsWith("fd") ||
        h.startsWith("fe8") ||
        h.startsWith("fe9") ||
        h.startsWith("fea") ||
        h.startsWith("feb")
}

private fun isPrivateIpv4(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    return octets[0] == 10 ||
        octets[0] == 127 ||
        (octets[0] == 172 && octets[1] in 16..31) ||
        (octets[0] == 192 && octets[1] == 168) ||
        (octets[0] == 169 && octets[1] == 254)
}
