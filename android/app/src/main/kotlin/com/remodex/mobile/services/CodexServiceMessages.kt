package com.remodex.mobile.services

import android.content.pm.PackageManager
import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.RPCMessage
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Mirrors [CodexService+Messages.swift](../../../../../../../../CodexMobile/CodexMobile/Services/CodexService+Messages.swift).
 * Low-level JSON-RPC framing lives in [com.remodex.mobile.core.protocol.JsonRpcCodec].
 */
internal suspend fun CodexService.sendRequestImpl(
    method: String,
    params: JSONValue?,
): RPCMessage {
    testRpcRequestHandler?.let { handler ->
        return handler(method, params)
    }
    if (!sessionReady) throw CodexServiceError.Disconnected
    val id = JSONValue.Str(UUID.randomUUID().toString())
    val req = RPCMessage.request(id = id, method = method, params = params, includeJsonRpc = false)
    val deferred = CompletableDeferred<RPCMessage>()
    pendingRpc[idKey(id)] = deferred
    try {
        sendMessage(req)
        return deferred.await()
    } finally {
        pendingRpc.remove(idKey(id))
    }
}

internal suspend fun CodexService.sendNotificationImpl(
    method: String,
    params: JSONValue?,
) {
    if (!sessionReady) throw CodexServiceError.Disconnected
    sendMessage(RPCMessage.notification(method = method, params = params, includeJsonRpc = false))
}

internal fun CodexService.sendMessage(message: RPCMessage) {
    val payload = jsonRpc.encodeMessage(message)
    val wire = secureWireText(payload)
    sendRawText(wire)
}

internal fun CodexService.processWireText(raw: String) {
    if (isSecureWirePreClassification(raw)) {
        val kind = wireMessageKind(raw) ?: return
        if (kind == "encryptedEnvelope") {
            handleEncryptedEnvelope(raw)
        } else {
            controlMux?.offer(kind, raw)
        }
        return
    }
    val message = jsonRpc.decodeMessage(raw) ?: return
    dispatchIncomingRpc(message)
}

private fun CodexService.isSecureWirePreClassification(text: String): Boolean {
    if (!text.contains("\"kind\":")) return false
    val markers =
        listOf(
            "\"serverHello\"",
            "\"secureReady\"",
            "\"secureError\"",
            "\"encryptedEnvelope\"",
        )
    return markers.any { text.contains(it) }
}

private fun CodexService.wireMessageKind(text: String): String? =
    try {
        val el = json.parseToJsonElement(text)
        (el as? JsonObject)?.get("kind")?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }

internal fun CodexService.dispatchIncomingRpc(message: RPCMessage) {
    val method = message.method?.trim()
    if (method != null && message.id != null) {
        incomingRouter.dispatchServerRequest(
            method = method,
            requestId = message.id!!,
            params = message.params,
        ) { response ->
            runCatching { sendMessage(response) }
        }
        return
    }
    if (method != null) {
        incomingRouter.dispatchNotification(method, message.params)
        return
    }
    val id = message.id ?: return
    completePendingRpc(id, message)
}

internal fun CodexService.completePendingRpc(
    id: JSONValue,
    message: RPCMessage,
) {
    val key = idKey(id)
    val def = pendingRpc.remove(key) ?: return
    val err = message.error
    if (err != null) {
        def.completeExceptionally(CodexServiceError.RpcFailure(err))
    } else {
        def.complete(message)
    }
}

internal fun idKey(id: JSONValue): String =
    when (id) {
        is JSONValue.Str -> "s:${id.value}"
        is JSONValue.NumLong -> "i:${id.value}"
        is JSONValue.NumDouble -> "d:${id.value}"
        is JSONValue.Bool -> "b:${id.value}"
        is JSONValue.Null -> "null"
        else -> "complex:$id"
    }

internal suspend fun CodexService.initializeSession() {
    val appVersion = readAppVersion()
    val clientInfo =
        JSONValue.Obj(
            mapOf(
                "name" to JSONValue.Str("codexmobile_android"),
                "title" to JSONValue.Str("CodexMobile Android"),
                "version" to JSONValue.Str(appVersion),
            ),
        )
    val modern =
        JSONValue.Obj(
            mapOf(
                "clientInfo" to clientInfo,
                "capabilities" to
                    JSONValue.Obj(
                        mapOf("experimentalApi" to JSONValue.Bool(true)),
                    ),
            ),
        )
    try {
        rpcRequestWhileHandshaking("initialize", modern)
    } catch (e: Exception) {
        if (!shouldRetryInitializeWithoutCapabilities(e)) {
            throw e
        }
        val legacy = JSONValue.Obj(mapOf("clientInfo" to clientInfo))
        rpcRequestWhileHandshaking("initialize", legacy)
    }
    sendMessage(RPCMessage.notification(method = "initialized", params = null, includeJsonRpc = false))
}

private fun shouldRetryInitializeWithoutCapabilities(e: Throwable): Boolean {
    val rpc = (e as? CodexServiceError.RpcFailure)?.rpcError ?: return false
    if (rpc.code != -32600 && rpc.code != -32602) return false
    val msg = rpc.message.lowercase()
    if (!msg.contains("capabilities") && !msg.contains("experimentalapi")) return false
    return msg.contains("unknown") ||
        msg.contains("unexpected") ||
        msg.contains("unrecognized") ||
        msg.contains("invalid") ||
        msg.contains("unsupported") ||
        msg.contains("field")
}

internal suspend fun CodexService.rpcRequestWhileHandshaking(
    method: String,
    params: JSONValue?,
): RPCMessage {
    val id = JSONValue.Str(UUID.randomUUID().toString())
    val req = RPCMessage.request(id = id, method = method, params = params, includeJsonRpc = false)
    val deferred = CompletableDeferred<RPCMessage>()
    pendingRpc[idKey(id)] = deferred
    try {
        sendMessage(req)
        return deferred.await()
    } finally {
        pendingRpc.remove(idKey(id))
    }
}

internal fun CodexService.readAppVersion(): String =
    try {
        val pm = appContext.packageManager
        val pkg = appContext.packageName
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName ?: "0.1.2"
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0).versionName ?: "0.1.2"
        }
    } catch (_: Exception) {
        "0.1.2"
    }
