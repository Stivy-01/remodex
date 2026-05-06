package com.remodex.mobile.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.serializer

typealias RPCObject = Map<String, JSONValue>

fun JsonObject.toRpcObject(): RPCObject = mapValues { (_, v) -> JSONValue.fromJsonElement(v) }

@Serializable(JSONValueSerializer::class)
sealed class JSONValue {
    data class Str(val value: String) : JSONValue()

    /** Whole JSON numbers that fit in a long (matches typical RPC payloads). */
    data class NumLong(val value: Long) : JSONValue()

    data class NumDouble(val value: Double) : JSONValue()

    data class Bool(val value: Boolean) : JSONValue()

    data class Obj(val map: Map<String, JSONValue>) : JSONValue()

    data class Arr(val elements: List<JSONValue>) : JSONValue()

    data object Null : JSONValue()

    val stringValue: String?
        get() = (this as? Str)?.value

    val intValue: Int?
        get() =
            when (this) {
                is NumLong -> value.toInt()
                else -> null
            }

    val longValue: Long?
        get() = (this as? NumLong)?.value

    val doubleValue: Double?
        get() =
            when (this) {
                is NumLong -> value.toDouble()
                is NumDouble -> value
                else -> null
            }

    val boolValue: Boolean?
        get() = (this as? Bool)?.value

    val objectValue: Map<String, JSONValue>?
        get() = (this as? Obj)?.map

    val arrayValue: List<JSONValue>?
        get() = (this as? Arr)?.elements

    companion object {
        fun fromJsonElement(element: JsonElement): JSONValue =
            when (element) {
                JsonNull -> Null
                is JsonObject ->
                    Obj(element.mapValues { (_, v) -> fromJsonElement(v) })
                is JsonArray -> Arr(element.map { fromJsonElement(it) })
                else -> {
                    val p = element.jsonPrimitive
                    when {
                        p.isString -> Str(p.content)
                        p.booleanOrNull != null -> Bool(checkNotNull(p.booleanOrNull))
                        p.longOrNull != null -> NumLong(checkNotNull(p.longOrNull))
                        p.doubleOrNull != null -> NumDouble(checkNotNull(p.doubleOrNull))
                        else -> Str(p.content)
                    }
                }
            }

        fun toJsonElement(value: JSONValue): JsonElement =
            when (value) {
                is Str -> JsonPrimitive(value.value)
                is NumLong -> JsonPrimitive(value.value)
                is NumDouble -> JsonPrimitive(value.value)
                is Bool -> JsonPrimitive(value.value)
                is Obj ->
                    buildJsonObject {
                        value.map.forEach { (k, v) ->
                            put(k, toJsonElement(v))
                        }
                    }
                is Arr ->
                    buildJsonArray {
                        value.elements.forEach { add(toJsonElement(it)) }
                    }
                Null -> JsonNull
            }
    }
}

object JSONValueSerializer : KSerializer<JSONValue> {
    private val delegate = serializer<JsonElement>()

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(
        encoder: Encoder,
        value: JSONValue,
    ) {
        val json = encoder as JsonEncoder
        json.encodeJsonElement(JSONValue.toJsonElement(value))
    }

    override fun deserialize(decoder: Decoder): JSONValue {
        val json = decoder as JsonDecoder
        return JSONValue.fromJsonElement(json.decodeJsonElement())
    }
}
