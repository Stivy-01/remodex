package com.remodex.mobile.core.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceDraftAppendTest {
    @Test
    fun appendToEmpty() {
        assertEquals("hello", VoiceDraftAppend.append("", "  hello  "))
    }

    @Test
    fun appendWithSpace() {
        assertEquals("hi there", VoiceDraftAppend.append("hi", "there"))
    }

    @Test
    fun appendPreservesTrailingSpaceInExistingIgnored() {
        assertEquals("a b", VoiceDraftAppend.append("a  ", "b"))
    }

    @Test
    fun emptyTranscriptNoOp() {
        assertEquals("x", VoiceDraftAppend.append("x", "   "))
    }
}
