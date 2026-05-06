package com.remodex.mobile.services

import com.remodex.mobile.core.model.CodexRateLimitBucket
import com.remodex.mobile.core.model.CodexRateLimitWindow
import com.remodex.mobile.core.model.JSONValue
import kotlin.test.assertEquals
import org.junit.Test

class RateLimitPayloadCodecTest {
    @Test
    fun `decodes read response with rateLimitsByLimitId`() {
        val payload =
            mapOf(
                "rateLimitsByLimitId" to
                    JSONValue.Obj(
                        mapOf(
                            "codex_5h" to
                                JSONValue.Obj(
                                    mapOf(
                                        "limitId" to JSONValue.Str("codex_5h"),
                                        "limitName" to JSONValue.Str("5h limit"),
                                        "primary" to
                                            JSONValue.Obj(
                                                mapOf(
                                                    "usedPercent" to JSONValue.NumLong(25L),
                                                    "windowDurationMins" to JSONValue.NumLong(300L),
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )
        val buckets = RateLimitPayloadCodec.decodeRateLimitBuckets(payload)
        assertEquals(1, buckets.size)
        assertEquals("codex_5h", buckets[0].limitId)
        assertEquals(25, buckets[0].primary?.clampedUsedPercent)
    }

    @Test
    fun `merges into visible rows like iOS updated notification`() {
        val obj =
            JSONValue.Obj(
                mapOf(
                    "rateLimits" to
                        JSONValue.Obj(
                            mapOf(
                                "limitId" to JSONValue.Str("codex"),
                                "limitName" to JSONValue.Str("Codex"),
                                "primary" to
                                    JSONValue.Obj(
                                        mapOf(
                                            "usedPercent" to JSONValue.NumLong(42L),
                                            "windowDurationMins" to JSONValue.NumLong(60L),
                                        ),
                                    ),
                            ),
                        ),
                ),
            )
        val m = obj.objectValue!!
        val buckets = RateLimitPayloadCodec.decodeRateLimitBuckets(m)
        assertEquals(1, buckets.size)
        val rows = CodexRateLimitBucket.visibleDisplayRows(buckets)
        assertEquals(1, rows.size)
        assertEquals(58, rows[0].window.remainingPercent)
    }

    @Test
    fun `merge updates existing limit by id`() {
        val existing =
            listOf(
                CodexRateLimitBucket(
                    limitId = "a",
                    limitName = "Old",
                    primary = CodexRateLimitWindow(usedPercent = 10, windowDurationMins = 60, resetsAt = null),
                    secondary = null,
                ),
            )
        val incoming =
            listOf(
                CodexRateLimitBucket(
                    limitId = "a",
                    limitName = null,
                    primary = CodexRateLimitWindow(usedPercent = 50, windowDurationMins = 60, resetsAt = null),
                    secondary = null,
                ),
            )
        val merged = RateLimitPayloadCodec.mergeRateLimitBuckets(existing, incoming)
        assertEquals(1, merged.size)
        assertEquals(50, merged[0].primary?.usedPercent)
        assertEquals("Old", merged[0].limitName)
    }

    @Test
    fun `decode uses stable fallback id when bridge omits limit id`() {
        val payload =
            mapOf(
                "rateLimitsByLimitId" to
                    JSONValue.Obj(
                        mapOf(
                            "" to
                                JSONValue.Obj(
                                    mapOf(
                                        "primary" to
                                            JSONValue.Obj(
                                                mapOf(
                                                    "usedPercent" to JSONValue.NumLong(12L),
                                                    "windowDurationMins" to JSONValue.NumLong(300L),
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )

        val first = RateLimitPayloadCodec.decodeRateLimitBuckets(payload).single()
        val second = RateLimitPayloadCodec.decodeRateLimitBuckets(payload).single()

        assertEquals("unnamed-300-none", first.limitId)
        assertEquals(first.limitId, second.limitId)
    }
}
