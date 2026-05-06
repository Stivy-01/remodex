package com.remodex.mobile.core.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodexThreadDisplayTitleTest {
    @Test
    fun displayTitle_literalNullTitleString_fallsThroughToPreview() {
        val t =
            CodexThread(
                id = "t1",
                title = "null",
                preview = "first line seed",
            )
        assertEquals("First line seed", t.displayTitle)
    }

    @Test
    fun displayTitle_placeholderTitleUsesPreview_whenPreviewShorter() {
        val t =
            CodexThread(
                id = "t1",
                title = "this is an extremely long first user prompt that should not dominate the headline",
                preview = "short headline",
            )
        assertEquals("Short headline", t.displayTitle)
    }

    @Test
    fun displayTitle_placeholderTitle_fallsThroughToDefaultDisplayTitle() {
        val t = CodexThread(id = "t1", title = "Conversation")
        assertEquals(CodexThread.DEFAULT_DISPLAY_TITLE, t.displayTitle)
    }

    @Test
    fun displayTitle_newThreadPlaceholder_fallsThroughToDefaultDisplayTitle() {
        val t = CodexThread(id = "t1", title = "new thread")
        assertEquals(CodexThread.DEFAULT_DISPLAY_TITLE, t.displayTitle)
    }

    @Test
    fun displayTitle_prefersNameOverTitle() {
        val t =
            CodexThread(
                id = "t1",
                name = "Pinned",
                title = "anything",
            )
        assertEquals("Pinned", t.displayTitle)
    }

    @Test
    fun fromJsonObject_dropsMalformedTitleString() {
        val obj =
            buildJsonObject {
                put("id", JsonPrimitive("tid"))
                put("title", JsonPrimitive("null"))
                put("preview", JsonPrimitive("hello"))
            }
        val decoded = CodexThread.fromJsonObject(obj)
        assertNull(decoded.title)
        assertEquals("hello", decoded.preview)
        assertEquals("Hello", decoded.displayTitle)
    }

    @Test
    fun normalizeIdentifier_treatsNullLiteralAsAbsent() {
        assertNull(CodexThread.normalizeIdentifier("null"))
        assertNull(CodexThread.normalizeIdentifier("  undefined  "))
        assertEquals("real", CodexThread.normalizeIdentifier("real"))
    }
}
