package com.remodex.mobile.services

import com.remodex.mobile.core.model.CodexRateLimitBucket
import com.remodex.mobile.core.model.CodexRateLimitWindow
import com.remodex.mobile.core.model.JSONValue
import java.time.Instant
import kotlin.math.roundToLong

/**
 * Decodes `account/rateLimits/read` and `account/rateLimits/updated` payloads.
 * Parity with [CodexService+Status.swift](../../../../../../../../CodexMobile/CodexMobile/Services/CodexService+Status.swift).
 */
internal object RateLimitPayloadCodec {
    fun decodeRateLimitBuckets(payloadObject: Map<String, JSONValue>): List<CodexRateLimitBucket> {
        val byLimitId =
            payloadObject["rateLimitsByLimitId"]?.objectValue
                ?: payloadObject["rate_limits_by_limit_id"]?.objectValue
        if (byLimitId != null) {
            return byLimitId.mapNotNull { (limitId, value) -> decodeRateLimitBucket(limitId, value) }
        }

        val nested =
            payloadObject["rateLimits"]?.objectValue
                ?: payloadObject["rate_limits"]?.objectValue
        if (nested != null) {
            if (containsDirectRateLimitWindows(nested)) {
                return decodeDirectRateLimitBuckets(nested)
            }
            val single = decodeRateLimitBucket(null, JSONValue.Obj(nested))
            if (single != null) return listOf(single)
        }

        val nestedResult = payloadObject["result"]?.objectValue
        if (nestedResult != null) {
            return decodeRateLimitBuckets(nestedResult)
        }

        if (containsDirectRateLimitWindows(payloadObject)) {
            return decodeDirectRateLimitBuckets(payloadObject)
        }
        return emptyList()
    }

    fun mergeRateLimitBuckets(
        existing: List<CodexRateLimitBucket>,
        incoming: List<CodexRateLimitBucket>,
    ): List<CodexRateLimitBucket> {
        if (existing.isEmpty()) return incoming
        if (incoming.isEmpty()) return existing
        val merged = linkedMapOf<String, CodexRateLimitBucket>()
        existing.forEach { merged[it.limitId] = it }
        for (bucket in incoming) {
            val current = merged[bucket.limitId]
            if (current != null) {
                merged[bucket.limitId] =
                    CodexRateLimitBucket(
                        limitId = bucket.limitId,
                        limitName = bucket.limitName ?: current.limitName,
                        primary = bucket.primary ?: current.primary,
                        secondary = bucket.secondary ?: current.secondary,
                    )
            } else {
                merged[bucket.limitId] = bucket
            }
        }
        return merged.values.toList()
    }

    private fun decodeRateLimitBucket(
        explicitLimitId: String?,
        value: JSONValue,
    ): CodexRateLimitBucket? {
        val objectMap = value.objectValue ?: return null
        val primary = decodeRateLimitWindow(objectMap["primary"] ?: objectMap["primary_window"])
        val secondary = decodeRateLimitWindow(objectMap["secondary"] ?: objectMap["secondary_window"])
        if (primary == null && secondary == null) return null
        val limitId =
            firstNonEmptyString(
                listOf(
                    explicitLimitId,
                    firstStringIn(objectMap, listOf("limitId", "limit_id", "id")),
                ),
            ) ?: stableFallbackLimitId(objectMap, primary, secondary)
        return CodexRateLimitBucket(
            limitId = limitId,
            limitName = firstStringIn(objectMap, listOf("limitName", "limit_name", "name")),
            primary = primary,
            secondary = secondary,
        )
    }

    private fun decodeDirectRateLimitBuckets(`object`: Map<String, JSONValue>): List<CodexRateLimitBucket> {
        val out = mutableListOf<CodexRateLimitBucket>()
        decodeRateLimitWindow(`object`["primary"] ?: `object`["primary_window"])?.let { primary ->
            out.add(
                CodexRateLimitBucket(
                    limitId = "primary",
                    limitName = firstStringIn(`object`, listOf("limitName", "limit_name", "name")),
                    primary = primary,
                    secondary = null,
                ),
            )
        }
        decodeRateLimitWindow(`object`["secondary"] ?: `object`["secondary_window"])?.let { secondary ->
            out.add(
                CodexRateLimitBucket(
                    limitId = "secondary",
                    limitName = firstStringIn(`object`, listOf("secondaryName", "secondary_name")),
                    primary = secondary,
                    secondary = null,
                ),
            )
        }
        return out
    }

    private fun decodeRateLimitWindow(value: JSONValue?): CodexRateLimitWindow? {
        val o = value?.objectValue ?: return null
        val usedPercent = firstIntIn(o, listOf("usedPercent", "used_percent")) ?: 0
        val windowDurationMins =
            firstIntIn(
                o,
                listOf("windowDurationMins", "window_duration_mins", "windowMinutes", "window_minutes"),
            )
        val resetAt = instantFromResetsAt(o)
        return CodexRateLimitWindow(
            usedPercent = usedPercent,
            windowDurationMins = windowDurationMins,
            resetsAt = resetAt,
        )
    }

    private fun instantFromResetsAt(o: Map<String, JSONValue>): Instant? {
        val raw =
            o["resetsAt"]?.doubleValue
                ?: o["resets_at"]?.doubleValue
                ?: o["resetAt"]?.doubleValue
                ?: o["reset_at"]?.doubleValue
        if (raw != null) {
            val seconds = if (raw > 10_000_000_000.0) raw / 1000.0 else raw
            return Instant.ofEpochSecond(seconds.roundToLong())
        }
        val s =
            firstStringIn(o, listOf("resetsAt", "resets_at", "resetAt", "reset_at"))
                ?: return null
        return runCatching { Instant.parse(s) }.getOrNull()
    }

    private fun containsDirectRateLimitWindows(o: Map<String, JSONValue>): Boolean =
        o["primary"] != null ||
            o["secondary"] != null ||
            o["primary_window"] != null ||
            o["secondary_window"] != null

    private fun firstNonEmptyString(candidates: List<String?>): String? =
        candidates.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun stableFallbackLimitId(
        o: Map<String, JSONValue>,
        primary: CodexRateLimitWindow?,
        secondary: CodexRateLimitWindow?,
    ): String {
        firstStringIn(o, listOf("limitName", "limit_name", "name"))?.let { name ->
            return "unnamed-${name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}"
        }
        val primaryWindow = primary?.windowDurationMins?.toString() ?: "none"
        val secondaryWindow = secondary?.windowDurationMins?.toString() ?: "none"
        return "unnamed-$primaryWindow-$secondaryWindow"
    }

    private fun firstStringIn(
        o: Map<String, JSONValue>,
        keys: List<String>,
    ): String? {
        for (k in keys) {
            when (val v = o[k]) {
                is JSONValue.Str -> if (v.value.isNotBlank()) return v.value
                is JSONValue.NumLong -> return v.value.toString()
                is JSONValue.NumDouble -> return v.value.toString()
                else -> continue
            }
        }
        return null
    }

    private fun firstIntIn(
        o: Map<String, JSONValue>,
        keys: List<String>,
    ): Int? {
        for (k in keys) {
            when (val v = o[k]) {
                is JSONValue.NumLong -> return v.value.toInt()
                is JSONValue.NumDouble -> return v.value.toInt()
                is JSONValue.Str -> v.value.toIntOrNull()?.let { return it }
                else -> continue
            }
        }
        return null
    }
}
