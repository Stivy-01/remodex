package com.remodex.mobile.data

import com.remodex.mobile.core.model.CommandExecutionDetails
import kotlin.test.Test
import kotlin.test.assertEquals

class TurnCommandExecutionPreviewMergeTest {
    @Test
    fun merge_detailsOverridesParsedMetadata() {
        val parsed =
            TurnCommandExecutionPresentation(
                phase = "completed",
                command = "command",
                outputText = "parsed output",
                rawText = "completed command",
                cwd = "/parsed",
                exitCode = 0,
                durationMs = 100,
            )
        val details =
            CommandExecutionDetails(
                fullCommand = "bash -lc ./gradlew test",
                cwd = "/structured",
                exitCode = 1,
                durationMs = 250,
                outputTail = "structured output",
            )

        val merged = TurnCommandExecutionPreviewMerge.merge(parsed, details)

        assertEquals("completed", merged.phase)
        assertEquals("bash -lc ./gradlew test", merged.command)
        assertEquals("/structured", merged.cwd)
        assertEquals(1, merged.exitCode)
        assertEquals(250, merged.durationMs)
        assertEquals("structured output", merged.outputText)
    }

    @Test
    fun merge_keepsParsedFallbackWhenDetailsIsNull() {
        val parsed =
            TurnCommandExecutionPresentation(
                phase = "running",
                command = "npm test",
                outputText = "parsed output",
                rawText = "npm test",
            )

        assertEquals(parsed, TurnCommandExecutionPreviewMerge.merge(parsed, null))
    }

    @Test
    fun merge_usesParsedOutputWhenStructuredOutputIsBlank() {
        val parsed =
            TurnCommandExecutionPresentation(
                phase = "completed",
                command = "npm test",
                outputText = "parsed output",
                rawText = "completed npm test",
            )
        val details =
            CommandExecutionDetails(
                fullCommand = "npm test -- --runInBand",
                outputTail = "   ",
            )

        val merged = TurnCommandExecutionPreviewMerge.merge(parsed, details)

        assertEquals("npm test -- --runInBand", merged.command)
        assertEquals("parsed output", merged.outputText)
    }
}
