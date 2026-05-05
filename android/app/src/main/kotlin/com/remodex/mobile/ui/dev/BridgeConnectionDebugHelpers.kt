package com.remodex.mobile.ui.dev

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.RPCMessage
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val LOG_TRUNCATE_CHARS = 12_000

internal fun bridgeDebugParseOptionalRpcParams(
    json: Json,
    raw: String,
): JSONValue? {
    val t = raw.trim()
    if (t.isEmpty() || t == "null") return null
    return JSONValue.fromJsonElement(json.parseToJsonElement(t))
}

internal fun bridgeDebugFormatRpcMessageForLog(
    logJson: Json,
    msg: RPCMessage,
): String {
    val encoded =
        runCatching { logJson.encodeToString(RPCMessage.serializer(), msg) }
            .getOrElse { msg.toString() }
    return if (encoded.length > LOG_TRUNCATE_CHARS) {
        encoded.take(LOG_TRUNCATE_CHARS) + "\n… (truncated)"
    } else {
        encoded
    }
}

internal fun bridgeDebugFormatRpcDebugError(e: Throwable): String =
    when (e) {
        is CodexServiceError.RpcFailure ->
            "RPC error code=${e.rpcError.code} message=${e.rpcError.message}"
        else -> bridgeDebugFormatConnectError(e)
    }

internal fun bridgeDebugIsLoopbackRelayHost(relayUrl: String): Boolean {
    val trimmed = relayUrl.trim()
    val forParse =
        when {
            trimmed.startsWith("ws://", ignoreCase = true) -> "http://${trimmed.substring(5)}"
            trimmed.startsWith("wss://", ignoreCase = true) -> "https://${trimmed.substring(6)}"
            else -> trimmed
        }
    val host =
        forParse.toHttpUrlOrNull()?.host?.lowercase()
            ?: return false
    return host == "127.0.0.1" || host == "localhost" || host == "::1"
}

/** Replace host in relay base URL when user connects from emulator/phone (QR often uses 127.0.0.1). */
internal fun bridgeDebugApplyRelayHostOverride(
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
    val httpish = parsed.newBuilder().host(host).build().toString()
    return when {
        wss -> httpish.replaceFirst("https://", "wss://", ignoreCase = true)
        ws -> httpish.replaceFirst("http://", "ws://", ignoreCase = true)
        else -> httpish
    }
}

internal fun bridgeDebugFormatConnectError(e: Throwable): String {
    val lines = ArrayList<String>(4)
    var cur: Throwable? = e
    var depth = 0
    while (cur != null && depth < 5) {
        val head =
            if (depth == 0) {
                cur.message?.trim().orEmpty().ifEmpty { cur.javaClass.simpleName }
            } else {
                val msg = cur.message?.trim().orEmpty().ifEmpty { cur.javaClass.simpleName }
                "Caused by: $msg"
            }
        lines.add(head)
        cur = cur.cause
        depth++
    }
    return lines.joinToString("\n")
}
