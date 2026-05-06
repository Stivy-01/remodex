package com.remodex.mobile.core.protocol

import com.remodex.mobile.core.model.RPCMessage
import kotlinx.serialization.json.Json

/** Encodes/decodes wire JSON-RPC payloads (F3 transport codec boundary). */
class JsonRpcCodec(
    private val json: Json,
) {
    fun decodeMessage(text: String): RPCMessage? =
        runCatching { json.decodeFromString(RPCMessage.serializer(), text) }.getOrNull()

    fun encodeMessage(message: RPCMessage): String =
        json.encodeToString(RPCMessage.serializer(), message)
}
