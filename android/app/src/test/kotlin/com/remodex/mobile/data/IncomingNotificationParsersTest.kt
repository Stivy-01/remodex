package com.remodex.mobile.data

import com.remodex.mobile.core.model.JSONValue
import kotlin.test.Test
import kotlin.test.assertEquals

class IncomingNotificationParsersTest {
    @Test
    fun extractAssistantDelta_preservesLeadingSpaceTokens() {
        val params = mapOf("delta" to JSONValue.Str(" world"))

        assertEquals(" world", IncomingNotificationParsers.extractAssistantDelta(params))
    }

    @Test
    fun extractAssistantDelta_preservesWhitespaceOnlyTokens() {
        val params = mapOf("delta" to JSONValue.Str(" "))

        assertEquals(" ", IncomingNotificationParsers.extractAssistantDelta(params))
    }

    @Test
    fun extractTextDelta_preservesNestedRawDelta() {
        val params =
            mapOf(
                "event" to
                    JSONValue.Obj(
                        mapOf("delta" to JSONValue.Str("\nnext")),
                    ),
            )

        assertEquals("\nnext", IncomingNotificationParsers.extractTextDelta(params))
    }
}
