package com.remodex.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
data class RPCMessage(
    val jsonrpc: String? = null,
    @Serializable(JSONValueSerializer::class)
    val id: JSONValue? = null,
    val method: String? = null,
    @Serializable(JSONValueSerializer::class)
    val params: JSONValue? = null,
    @Serializable(JSONValueSerializer::class)
    val result: JSONValue? = null,
    val error: RPCError? = null,
) {
    val isRequest: Boolean get() = method != null

    val isResponse: Boolean get() = result != null || error != null

    val isErrorResponse: Boolean get() = error != null

    companion object {
        fun request(
            id: JSONValue? = null,
            method: String,
            params: JSONValue? = null,
            includeJsonRpc: Boolean = true,
        ): RPCMessage =
            RPCMessage(
                jsonrpc = if (includeJsonRpc) "2.0" else null,
                id = id,
                method = method,
                params = params,
                result = null,
                error = null,
            )

        fun success(
            id: JSONValue?,
            result: JSONValue,
            includeJsonRpc: Boolean = true,
        ): RPCMessage =
            RPCMessage(
                jsonrpc = if (includeJsonRpc) "2.0" else null,
                id = id,
                method = null,
                params = null,
                result = result,
                error = null,
            )

        fun failure(
            id: JSONValue?,
            error: RPCError,
            includeJsonRpc: Boolean = true,
        ): RPCMessage =
            RPCMessage(
                jsonrpc = if (includeJsonRpc) "2.0" else null,
                id = id,
                method = null,
                params = null,
                result = null,
                error = error,
            )

        fun notification(
            method: String,
            params: JSONValue? = null,
            includeJsonRpc: Boolean = false,
        ): RPCMessage =
            RPCMessage(
                jsonrpc = if (includeJsonRpc) "2.0" else null,
                id = null,
                method = method,
                params = params,
                result = null,
                error = null,
            )
    }
}

@Serializable
data class RPCError(
    val code: Int,
    val message: String,
    @Serializable(JSONValueSerializer::class)
    val data: JSONValue? = null,
)

/**
 * True when the bridge reports the thread is gone (parity iOS `shouldTreatAsThreadNotFound`),
 * excluding "not materialized yet" transient errors that share [-32600].
 */
fun RPCError.isExplicitServerThreadMissing(): Boolean {
    val m = message.lowercase()
    if (m.contains("not materialized") || m.contains("not yet materialized")) return false
    return m.contains("thread not found") || m.contains("unknown thread")
}
