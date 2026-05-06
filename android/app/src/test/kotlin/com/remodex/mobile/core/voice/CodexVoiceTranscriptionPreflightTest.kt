package com.remodex.mobile.core.voice

import com.remodex.mobile.core.error.CodexServiceError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CodexVoiceTranscriptionPreflightTest {
    @Test
    fun rejectsOversizedClip() {
        val preflight =
            CodexVoiceTranscriptionPreflight(
                byteCount = CodexVoiceTranscriptionPreflight.MAX_BYTE_COUNT + 1,
                durationSeconds = 30.0,
            )
        val err = assertFailsWith<CodexServiceError.InvalidInput> { preflight.validate() }
        assertEquals("Voice clips must be smaller than 10 MB.", err.message)
    }

    @Test
    fun rejectsClipLongerThanTwoMinutes() {
        val preflight =
            CodexVoiceTranscriptionPreflight(
                byteCount = 2048,
                durationSeconds = 120.5,
            )
        val err = assertFailsWith<CodexServiceError.InvalidInput> { preflight.validate() }
        assertEquals("Voice clips must be 120 seconds or less.", err.message)
    }

    @Test
    fun acceptsBoundaryDurationAndSize() {
        val p =
            CodexVoiceTranscriptionPreflight(
                byteCount = CodexVoiceTranscriptionPreflight.MAX_BYTE_COUNT,
                durationSeconds = 120.0,
            )
        assertNull(p.failureMessage)
        p.validate()
    }
}
