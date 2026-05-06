package com.remodex.mobile.core.model

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** ISO-8601 and unix epoch (seconds or millis) for wire interop with the iOS client. */
object Iso8601InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) {
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value))
    }

    override fun deserialize(decoder: Decoder): Instant {
        if (decoder is JsonDecoder) {
            val el = decoder.decodeJsonElement()
            val p = el as? JsonPrimitive ?: error("Expected primitive for Instant")
            p.longOrNull?.let { raw ->
                val seconds = if (raw > 10_000_000_000L) raw / 1000 else raw
                return Instant.ofEpochSecond(seconds)
            }
            p.doubleOrNull?.let { raw ->
                val seconds = if (raw > 10_000_000_000.0) raw / 1000.0 else raw
                return Instant.ofEpochMilli((seconds * 1000).toLong())
            }
            if (p.isString) {
                return parseIso(p.content)
            }
            error("Unsupported JSON primitive for Instant")
        }
        return parseIso(decoder.decodeString())
    }

    private fun parseIso(s: String): Instant {
        val t = s.trim()
        if (t.isEmpty()) error("Empty instant")
        return try {
            Instant.parse(t)
        } catch (_: DateTimeParseException) {
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(t, Instant::from)
        }
    }
}
