package com.remodex.mobile.ui.turn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TurnTimelineRenderCachesTest {
    @Test
    fun markdownFenceCacheReusesSegmentsForSameInput() {
        TurnMarkdownRenderCache.clear()
        val markdown = "Text\n```kotlin\nprintln(\"hi\")\n```"

        val first = TurnMarkdownRenderCache.fenceSegments(markdown)
        val second = TurnMarkdownRenderCache.fenceSegments(markdown)

        assertSame(first, second)
    }

    @Test
    fun commandHumanizerCacheSeparatesRunningState() {
        TurnMarkdownRenderCache.clear()

        val running = TurnMarkdownRenderCache.humanizeCommand("git status", isRunning = true)
        val completed = TurnMarkdownRenderCache.humanizeCommand("git status", isRunning = false)

        assertEquals("Checking", running.verb)
        assertEquals("Checked", completed.verb)
    }

    @Test
    fun commandHumanizerSummarizesWindowsPowerShellCommandPayload() {
        TurnMarkdownRenderCache.clear()
        val raw = """"C:\WINDOWS\System32\WindowsPowerShell\v1.0\powershell.exe" -Command 'cmd /c gradlew.bat app:compileDebugKotlin'"""

        val running = TurnMarkdownRenderCache.humanizeCommand(raw, isRunning = true)
        val completed = TurnMarkdownRenderCache.humanizeCommand(raw, isRunning = false)

        assertEquals("Running", running.verb)
        assertEquals("'cmd /c gradlew.bat app:compileDebugKotlin'", running.target)
        assertEquals("Completed", completed.verb)
        assertEquals("'cmd /c gradlew.bat app:compileDebugKotlin'", completed.target)
    }

    @Test
    fun commandHumanizerSummarizesCompletedWindowsPowerShellHistoryPrefix() {
        TurnMarkdownRenderCache.clear()
        val raw = """ran "C:\WINDOWS\System32\WindowsPowerShell\v1.0\powershell.exe" -Command 'cmd /c gradlew.bat app:compileDebugKotlin'"""

        val completed = TurnMarkdownRenderCache.humanizeCommand(raw, isRunning = false)

        assertEquals("Completed", completed.verb)
        assertEquals("'cmd /c gradlew.bat app:compileDebugKotlin'", completed.target)
    }

    @Test
    fun commandHumanizerSummarizesWindowsPowerShellAfterCompletedChevronRanPrefix() {
        TurnMarkdownRenderCache.clear()
        val raw =
            """completed>ran "C:\WINDOWS\System32\WindowsPowerShell\v1.0\powershell.exe" -Command 'cmd /c gradlew.bat app:compileDebugKotlin'"""

        val completed = TurnMarkdownRenderCache.humanizeCommand(raw, isRunning = false)

        assertEquals("Completed", completed.verb)
        assertEquals("'cmd /c gradlew.bat app:compileDebugKotlin'", completed.target)
    }

    @Test
    fun commandHumanizerSummarizesWindowsPowerShellWithForwardSlashPath() {
        TurnMarkdownRenderCache.clear()
        val raw =
            """ran "C:/WINDOWS/System32/WindowsPowerShell/v1.0/powershell.exe" -Command 'cmd /c gradlew.bat app:compileDebugKotlin'"""

        val completed = TurnMarkdownRenderCache.humanizeCommand(raw, isRunning = false)

        assertEquals("Completed", completed.verb)
        assertEquals("'cmd /c gradlew.bat app:compileDebugKotlin'", completed.target)
    }

    @Test
    fun directiveCacheReusesOutcomeForSameMessageSnapshot() {
        TurnDirectiveRenderCache.clear()
        val text = """::code-comment{title="Issue" body="Body" file="/tmp/A.kt"}"""

        val first = TurnDirectiveRenderCache.parseCodeCommentDirectives("msg-1", text)
        val second = TurnDirectiveRenderCache.parseCodeCommentDirectives("msg-1", text)

        assertSame(first, second)
    }
}
