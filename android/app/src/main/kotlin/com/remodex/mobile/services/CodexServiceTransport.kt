package com.remodex.mobile.services

import android.util.Log
import com.remodex.mobile.core.error.CodexServiceError
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

internal const val MAX_WS_PAYLOAD_BYTES = 16 * 1024 * 1024

/**
 * Relay `x-role` for this app.
 *
 * The open-source relay accepts `android`, but hosted relay deployments may only accept the original
 * `iphone` mobile role. The secure transport already uses the `iphone` sender literal for phone->Mac
 * envelopes, so keep the WebSocket role on that compatibility path.
 */
internal const val RELAY_WS_ROLE_ANDROID = "iphone"
private const val REMODEX_WS_LOG_TAG = "RemodexWs"

/**
 * Mirrors [CodexService+Transport.swift](../../../../../../../../CodexMobile/CodexMobile/Services/CodexService+Transport.swift).
 */
internal suspend fun CodexService.openWebSocketAwaitOpen(
    httpUrl: okhttp3.HttpUrl,
    token: String,
    role: String?,
) {
    suspendCancellableCoroutine { cont ->
        val reqBuilder = Request.Builder().url(httpUrl)
        val resolvedRole = role?.trim().orEmpty().ifEmpty { RELAY_WS_ROLE_ANDROID }
        reqBuilder.header("x-role", resolvedRole)
        val t = token.trim()
        if (t.isNotEmpty()) {
            reqBuilder.header("Authorization", "Bearer $t")
        }
        val request = reqBuilder.build()
        Log.i(
            REMODEX_WS_LOG_TAG,
            "opening relay websocket scheme=${httpUrl.scheme} host=${httpUrl.host} role=$resolvedRole hasToken=${t.isNotEmpty()}",
        )
        val listener = newRelayWebSocketListener(handshakeCont = cont)
        val ws = httpClient.newWebSocket(request, listener)
        cont.invokeOnCancellation { ws.cancel() }
    }
}

internal fun CodexService.newRelayWebSocketListener(
    handshakeCont: CancellableContinuation<Unit>?,
): WebSocketListener {
    val svc = this
        return object : WebSocketListener() {
        override fun onOpen(
            webSocket: WebSocket,
            response: Response,
        ) {
            Log.i(
                REMODEX_WS_LOG_TAG,
                "websocket open code=${response.code} sessionReady=${svc.sessionReady} queueSize=${webSocket.queueSize()}",
            )
            svc.webSocket = webSocket
            handshakeCont?.takeIf { it.isActive }?.resumeWith(Result.success(Unit))
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            Log.w(
                REMODEX_WS_LOG_TAG,
                "websocket failure code=${response?.code} sessionReady=${svc.sessionReady} currentSocket=${svc.webSocket === webSocket}: ${t.javaClass.simpleName}: ${t.message}",
            )
            handshakeCont?.takeIf { it.isActive }?.resumeWith(Result.failure(t))
            if (svc.closingByClient) return
            if (svc.webSocket === webSocket) {
                val msg = t.message?.trim()?.takeIf { it.isNotEmpty() } ?: t.toString()
                svc.scheduleWireDrop(msg)
            }
        }

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            Log.d(REMODEX_WS_LOG_TAG, "websocket message bytes=${text.toByteArray(Charsets.UTF_8).size}")
            svc.wireInbound?.trySend(text)
        }

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            Log.i(
                REMODEX_WS_LOG_TAG,
                "websocket closing code=$code reason=$reason sessionReady=${svc.sessionReady} currentSocket=${svc.webSocket === webSocket}",
            )
            webSocket.close(code, reason)
            if (!svc.closingByClient && svc.webSocket === webSocket) {
                svc.scheduleWireDrop("WebSocket closed ($code): $reason")
            }
        }
    }
}

internal fun CodexService.sendRawText(text: String) {
    val ws = webSocket ?: throw CodexServiceError.Disconnected
    val bytes = text.toByteArray(Charsets.UTF_8)
    if (bytes.size > MAX_WS_PAYLOAD_BYTES) {
        throw CodexServiceError.InvalidInput(
            "This payload is too large for the relay connection. Try fewer or smaller images and retry.",
        )
    }
    if (!ws.send(text)) {
        Log.w(
            REMODEX_WS_LOG_TAG,
            "websocket send returned false bytes=${bytes.size} sessionReady=$sessionReady queueSize=${ws.queueSize()}",
        )
        throw CodexServiceError.InvalidInput("WebSocket send failed")
    }
    Log.d(REMODEX_WS_LOG_TAG, "websocket send queued bytes=${bytes.size} queueSize=${ws.queueSize()}")
}

/** OkHttp HttpUrl parses only http/https; relay URLs use ws/wss. */
internal fun parseRelayHttpUrl(serverUrl: String): okhttp3.HttpUrl {
    val t = serverUrl.trim()
    val forParse =
        when {
            t.startsWith("ws://", ignoreCase = true) -> "http://${t.substring(5)}"
            t.startsWith("wss://", ignoreCase = true) -> "https://${t.substring(6)}"
            else -> t
        }
    return forParse.toHttpUrlOrNull() ?: throw CodexServiceError.InvalidServerURL(t)
}
