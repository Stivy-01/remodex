package com.remodex.mobile.ui.turn

import com.remodex.mobile.core.model.ExecutionStatus
import com.remodex.mobile.data.TurnCommandExecutionPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolExecutionUiBuilderTest {
    private fun title(st: ExecutionStatus): String =
        when (st) {
            ExecutionStatus.Running -> "RUN"
            ExecutionStatus.Completed -> "OK"
            ExecutionStatus.Failed -> "FAIL"
            ExecutionStatus.Cancelled -> "STOP"
        }

    @Test
    fun mapsStoppedToCancelled() {
        val preview =
            TurnCommandExecutionPresentation(
                phase = "stopped",
                command = "git status",
                outputText = null,
                rawText = "",
            )
        assertEquals(ExecutionStatus.Cancelled, mapExecutionStatusFromPreview(preview))
        val ui = buildToolExecutionUiPreview(preview, null, preview.command, ::title)
        assertEquals("STOP", ui.title)
    }

    @Test
    fun mapsRunningFailedCompleted() {
        val running =
            TurnCommandExecutionPresentation("running", "x", null, "").let {
                assertEquals(ExecutionStatus.Running, mapExecutionStatusFromPreview(it))
                buildToolExecutionUiPreview(it, null, it.command, ::title)
            }
        assertEquals("RUN", running.title)

        val failed =
            TurnCommandExecutionPresentation("failed", "x", null, "").let {
                assertEquals(ExecutionStatus.Failed, mapExecutionStatusFromPreview(it))
                buildToolExecutionUiPreview(it, null, it.command, ::title)
            }
        assertEquals("FAIL", failed.title)

        val completed =
            TurnCommandExecutionPresentation("completed", "x", null, "").let {
                assertEquals(ExecutionStatus.Completed, mapExecutionStatusFromPreview(it))
                buildToolExecutionUiPreview(it, null, it.command, ::title)
            }
        assertEquals("OK", completed.title)
    }

    @Test
    fun subtitleGradleTestFromPowerShellWrappedGradlew() {
        val raw =
            """ran "C:\WINDOWS\System32\WindowsPowerShell\v1.0\powershell.exe" -Command 'cmd /c gradlew.bat test'"""
        val preview =
            TurnCommandExecutionPresentation(
                phase = "completed",
                command = raw,
                outputText = null,
                rawText = "",
            )
        val ui = buildToolExecutionUiPreview(preview, null, raw, ::title)
        assertEquals("Gradle tests", ui.subtitle)
    }

    @Test
    fun subtitleGitStatus() {
        val raw = "git status"
        val preview =
            TurnCommandExecutionPresentation(
                phase = "completed",
                command = raw,
                outputText = null,
                rawText = "",
            )
        val ui = buildToolExecutionUiPreview(preview, null, raw, ::title)
        assertEquals("Git status", ui.subtitle)
    }

    @Test
    fun subtitleGetContentQuotedWindowsPathLeavesBasename() {
        val raw =
            """Get-Content -Path 'C:\Users\VeryLong\AppData\Local\Somewhere\ImportantFile.ps1' -Raw"""
        val preview =
            TurnCommandExecutionPresentation(
                phase = "completed",
                command = raw,
                outputText = null,
                rawText = "",
            )
        val sub = buildToolExecutionUiPreview(preview, null, raw, ::title).subtitle!!
        assertTrue(sub.contains("ImportantFile.ps1"), "expected basename hint: $sub")
        assertFalse(sub.contains("VeryLong"), "should not expose long path prefix: $sub")
    }

    @Test
    fun subtitleFallbackShortensLongPath() {
        val raw = """C:\VeryLong\Windows\Path\To\node.exe run build"""
        val preview =
            TurnCommandExecutionPresentation(
                phase = "completed",
                command = raw,
                outputText = null,
                rawText = "",
            )
        val sub = buildToolExecutionUiPreview(preview, null, raw, ::title).subtitle!!
        assertTrue(sub.length <= 80, "got len=${sub.length}: $sub")
        assertFalse(sub.contains("VeryLong"), "should not expose long path segments: $sub")
    }

    @Test
    fun isExpandableFalseWhenNoMetadataOrOutput() {
        val preview =
            TurnCommandExecutionPresentation(
                phase = "completed",
                command = "echo hi",
                outputText = null,
                rawText = "",
            )
        val ui = buildToolExecutionUiPreview(preview, null, preview.command, ::title)
        assertFalse(ui.isExpandable)
    }

    @Test
    fun isExpandableTrueWhenOutputPresent() {
        val preview =
            TurnCommandExecutionPresentation(
                phase = "completed",
                command = "echo hi",
                outputText = "out",
                rawText = "",
            )
        val ui = buildToolExecutionUiPreview(preview, null, preview.command, ::title)
        assertTrue(ui.isExpandable)
    }

    @Test
    fun samePreviewProducesEqualToolExecutionUiSnapshots() {
        val preview =
            TurnCommandExecutionPresentation(
                phase = "completed",
                command = "npm run build",
                outputText = null,
                rawText = "",
            )
        val a = buildToolExecutionUiPreview(preview, null, preview.command, ::title)
        val b = buildToolExecutionUiPreview(preview, null, preview.command, ::title)
        assertEquals(a, b)
    }
}
