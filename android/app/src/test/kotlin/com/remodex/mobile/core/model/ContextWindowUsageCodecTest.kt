package com.remodex.mobile.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContextWindowUsageCodecTest {
    @Test
    fun decodeCamelCaseTokensUsedAndLimit() {
        val u =
            ContextWindowUsageCodec.decodeObject(
                mapOf(
                    "tokensUsed" to JSONValue.NumLong(173_033),
                    "tokenLimit" to JSONValue.NumLong(258_400),
                ),
            )
        assertEquals(173_033, u?.tokensUsed)
        assertEquals(258_400, u?.tokenLimit)
    }

    @Test
    fun decodeSnakeCaseAliases() {
        val u =
            ContextWindowUsageCodec.decodeObject(
                mapOf(
                    "tokens_used" to JSONValue.NumLong(100),
                    "max_tokens" to JSONValue.NumLong(200),
                ),
            )
        assertEquals(100, u?.tokensUsed)
        assertEquals(200, u?.tokenLimit)
    }

    @Test
    fun decodeDerivesLimitFromRemaining() {
        val u =
            ContextWindowUsageCodec.decodeObject(
                mapOf(
                    "total_tokens" to JSONValue.NumLong(50),
                    "tokens_remaining" to JSONValue.NumLong(150),
                ),
            )
        assertEquals(50, u?.tokensUsed)
        assertEquals(200, u?.tokenLimit)
    }

    @Test
    fun decodeNullUsageValue() {
        assertNull(ContextWindowUsageCodec.decode(JSONValue.Null))
        assertNull(ContextWindowUsageCodec.decode(null))
    }

    @Test
    fun decodeNonObjectUsageReturnsNull() {
        assertNull(ContextWindowUsageCodec.decode(JSONValue.Str("x")))
    }

    @Test
    fun decodeJsonValueObjWrapper() {
        val u =
            ContextWindowUsageCodec.decode(
                JSONValue.Obj(
                    mapOf(
                        "input_tokens" to JSONValue.NumLong(10),
                        "context_window" to JSONValue.NumLong(100),
                    ),
                ),
            )
        assertEquals(10, u?.tokensUsed)
        assertEquals(100, u?.tokenLimit)
    }

    @Test
    fun decodeFromIncomingUsageParams_prefersExplicitUsageObject() {
        val params =
            mapOf(
                "threadId" to JSONValue.Str("t-ignore"),
                "usage" to
                    JSONValue.Obj(
                        mapOf(
                            "tokensUsed" to JSONValue.NumLong(5),
                            "tokenLimit" to JSONValue.NumLong(50),
                        ),
                    ),
            )
        val u = ContextWindowUsageCodec.decodeFromIncomingUsageParams(params)
        assertEquals(5, u?.tokensUsed)
        assertEquals(50, u?.tokenLimit)
    }

    @Test
    fun decodeFromLegacyTokenCountPayload_prefersLastUsageOverTotal() {
        val payload =
            mapOf(
                "info" to
                    JSONValue.Obj(
                        mapOf(
                            "total_token_usage" to
                                JSONValue.Obj(
                                    mapOf("total_tokens" to JSONValue.NumLong(123_884_753)),
                                ),
                            "last_token_usage" to
                                JSONValue.Obj(
                                    mapOf("total_tokens" to JSONValue.NumLong(200_930)),
                                ),
                            "model_context_window" to JSONValue.NumLong(258_400),
                        ),
                    ),
            )
        val u = ContextWindowUsageCodec.decodeFromLegacyTokenCountPayload(payload)
        assertEquals(200_930, u?.tokensUsed)
        assertEquals(258_400, u?.tokenLimit)
    }

    @Test
    fun decodeFromThreadReadThreadObject_readsRootUsageFields() {
        val threadObj =
            mapOf(
                "id" to JSONValue.Str("th"),
                "tokens_used" to JSONValue.NumLong(9),
                "max_tokens" to JSONValue.NumLong(99),
            )
        val u = ContextWindowUsageCodec.decodeFromThreadReadThreadObject(threadObj)
        assertEquals(9, u?.tokensUsed)
        assertEquals(99, u?.tokenLimit)
    }

    @Test
    fun decodeDefaultsMissingUsageToZeroWhenLimitExists() {
        val u =
            ContextWindowUsageCodec.decodeObject(
                mapOf(
                    "context_window" to JSONValue.NumLong(258_400),
                ),
            )
        assertEquals(0, u?.tokensUsed)
        assertEquals(258_400, u?.tokenLimit)
    }

    @Test
    fun decodeDoesNotInventUsageWithoutLimit() {
        assertNull(ContextWindowUsageCodec.decodeObject(emptyMap()))
    }

    @Test
    fun zeroSnapshotMarksSuccessfulEmptyRefreshResolved() {
        assertEquals(0, ContextWindowUsage.Zero.tokensUsed)
        assertEquals(0, ContextWindowUsage.Zero.tokenLimit)
        assertEquals(0, ContextWindowUsage.Zero.percentUsed)
    }
}
