package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.core.model.CodexSubagentAction
import com.remodex.mobile.core.model.CodexSubagentRef
import com.remodex.mobile.core.model.CodexSubagentState
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

class TurnTimelineRichContentParserTest {
    @Test
    fun parseMermaidMarkdown_acceptsTildeFenceAndMixedCaseLanguage() {
        val source =
            """
            Intro

            ~~~MeRmAiD
            flowchart TD
              A --> B
            ~~~

            Middle

            ```mermaid
            sequenceDiagram
              Alice->>Bob: hi
            ```

            Outro
            """.trimIndent()

        val segments = TurnTimelineRichContentParser.parseMermaidMarkdown(source)

        assertNotNull(segments)
        assertEquals(5, segments.size)
        assertEquals(TurnMarkdownSegmentKind.markdown, segments[0].kind)
        assertEquals(TurnMarkdownSegmentKind.mermaid, segments[1].kind)
        assertEquals(TurnMarkdownSegmentKind.markdown, segments[2].kind)
        assertEquals(TurnMarkdownSegmentKind.mermaid, segments[3].kind)
        assertEquals(TurnMarkdownSegmentKind.markdown, segments[4].kind)
        assertTrue(segments[1].text.contains("flowchart TD"))
        assertTrue(segments[3].text.contains("sequenceDiagram"))
    }

    @Test
    fun parseMermaidMarkdown_ignoresMermaidFenceInsideLongerCodeFence() {
        val source =
            """
            ````markdown
            Demo

            ```mermaid
            flowchart TD
              A --> B
            ```
            ````
            """.trimIndent()

        val segments = TurnTimelineRichContentParser.parseMermaidMarkdown(source)

        assertEquals(null, segments)
    }

    @Test
    fun parseMermaidMarkdown_respectsLongerMermaidFenceClose() {
        val source =
            """
            Intro

            ````mermaid
            flowchart TD
              A[``` nested text] --> B
            ````

            Outro
            """.trimIndent()

        val segments = TurnTimelineRichContentParser.parseMermaidMarkdown(source)

        assertNotNull(segments)
        assertEquals(3, segments.size)
        assertEquals(TurnMarkdownSegmentKind.markdown, segments[0].kind)
        assertEquals(TurnMarkdownSegmentKind.mermaid, segments[1].kind)
        assertEquals(TurnMarkdownSegmentKind.markdown, segments[2].kind)
        assertTrue(segments[1].text.contains("nested text"))
    }

    @Test
    fun parseFileChange_extractsFencedPatchFromProse() {
        val message =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.fileChange,
                text =
                    """
                    Here is the patch.

                    ```diff
                    diff --git a/app/src/main/kotlin/A.kt b/app/src/main/kotlin/A.kt
                    index 123..456 100644
                    --- a/app/src/main/kotlin/A.kt
                    +++ b/app/src/main/kotlin/A.kt
                    @@ -1,2 +1,2 @@
                    -old
                    +new
                    ```

                    Done.
                    """.trimIndent(),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val preview = TurnTimelineRichContentParser.parseFileChange(message)

        assertEquals("1 file changed", preview.headline)
        assertEquals("Here is the patch.", preview.summaryText)
        assertEquals(1, preview.fileCount)
        assertEquals(1, preview.totalAdditions)
        assertEquals(1, preview.totalDeletions)
        assertTrue(preview.rawPatchText?.contains("diff --git") == true)
        assertEquals("Edited", preview.entries.single().label)
        assertTrue(preview.entries.single().patchChunkText?.contains("diff --git") == true)
    }

    @Test
    fun parseFileChange_proseBasenameMismatch_fillsLeadingCountsAfterChunkAttach() {
        val unified =
            """
            diff --git a/long/nested/repo/CodexThread.kt b/long/nested/repo/CodexThread.kt
            --- a/long/nested/repo/CodexThread.kt
            +++ b/long/nested/repo/CodexThread.kt
            @@ -1 +1 @@
            -legacy
            +modern
            """.trimIndent()
        val message =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.fileChange,
                text =
                    """
                    Path: C:/Users/me/Desktop/apps/CodexThread.kt
                    Kind: update

                    ```diff
                    $unified
                    ```
                    """.trimIndent(),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val preview = TurnTimelineRichContentParser.parseFileChange(message)
        val only = preview.entries.single()

        assertEquals(1, preview.fileCount)
        assertNotNull(only.patchChunkText)
        assertEquals(1, only.additions)
        assertEquals(1, only.deletions)
    }

    @Test
    fun parseFileChange_multiFile_splitsPatchChunkPerEntry() {
        val unified =
            """
            diff --git a/src/A.kt b/src/A.kt
            --- a/src/A.kt
            +++ b/src/A.kt
            @@ -1 +1 @@
            -uniqueMarkerAlpha
            +afterAlpha

            diff --git a/src/B.kt b/src/B.kt
            --- a/src/B.kt
            +++ b/src/B.kt
            @@ -1 +1 @@
            -uniqueMarkerBravo
            +afterBravo
            """.trimIndent()
        val message =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.fileChange,
                text =
                    """
                    Path: D:\workspace\repo\android\src\A.kt

                    Totals: +1 -1

                    Path: D:\workspace\repo\android\src\B.kt

                    Totals: +9 -99

                    ```diff
                    $unified
                    ```
                    """.trimIndent(),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val preview = TurnTimelineRichContentParser.parseFileChange(message)

        assertEquals(2, preview.fileCount)
        fun leaf(p: String) = p.replace('\\', '/').substringAfterLast('/')
        val a = preview.entries.first { leaf(it.path) == "A.kt" }
        val b = preview.entries.first { leaf(it.path) == "B.kt" }
        assertNotNull(a.patchChunkText)
        assertNotNull(b.patchChunkText)
        assertTrue(a.patchChunkText!!.contains("uniqueMarkerAlpha"), a.patchChunkText)
        assertFalse(a.patchChunkText.contains("uniqueMarkerBravo"), a.patchChunkText)
        assertTrue(b.patchChunkText!!.contains("uniqueMarkerBravo"), b.patchChunkText)
        assertFalse(b.patchChunkText.contains("uniqueMarkerAlpha"), b.patchChunkText)
    }

    @Test
    fun parseFileChange_diffListedOrderDiffersFromProse_chunksMatchSummarizedPaths() {
        val unified =
            """
            diff --git a/src/CodexThreadTitleManagementTest.kt b/src/CodexThreadTitleManagementTest.kt
            --- a/src/CodexThreadTitleManagementTest.kt
            +++ b/src/CodexThreadTitleManagementTest.kt
            @@ -1 +1 @@
            -markerCodexRemoved
            +markerCodexAdded

            diff --git a/src/IncomingEventRouterServerRequestTest.kt b/src/IncomingEventRouterServerRequestTest.kt
            --- a/src/IncomingEventRouterServerRequestTest.kt
            +++ b/src/IncomingEventRouterServerRequestTest.kt
            @@ -1 +1 @@
            -markerIncomingRemoved
            +markerIncomingAdded
            """.trimIndent()
        val message =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.fileChange,
                text =
                    """
                    Path: src/IncomingEventRouterServerRequestTest.kt
                    Kind: update

                    Path: src/CodexThreadTitleManagementTest.kt
                    Kind: update

                    ```diff
                    $unified
                    ```
                    """.trimIndent(),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val preview = TurnTimelineRichContentParser.parseFileChange(message)

        assertEquals(2, preview.fileCount)
        val incoming =
            preview.entries.first { it.path.contains("IncomingEventRouterServerRequestTest") }
        val codex = preview.entries.first { it.path.contains("CodexThreadTitleManagement") }
        assertNotNull(incoming.patchChunkText)
        assertNotNull(codex.patchChunkText)
        assertTrue(incoming.patchChunkText!!.contains("markerIncomingAdded"), incoming.patchChunkText)
        assertTrue(codex.patchChunkText!!.contains("markerCodexAdded"), codex.patchChunkText)
        assertFalse(incoming.patchChunkText.contains("markerCodexAdded"), incoming.patchChunkText)
        assertFalse(codex.patchChunkText.contains("markerIncomingAdded"), codex.patchChunkText)
        assertEquals(1, incoming.additions)
        assertEquals(1, incoming.deletions)
        assertEquals(1, codex.additions)
        assertEquals(1, codex.deletions)
    }

    @Test
    fun parseFileChange_summaryRows_supportPathKindAndTotals() {
        val message =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.fileChange,
                text =
                    """
                    Path: app/src/main/kotlin/Main.kt
                    kind: renamed
                    totals: +4 -1

                    file path: app/src/main/kotlin/Other.kt
                    kind: added
                    changes: +10 -0
                    """.trimIndent(),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val preview = TurnTimelineRichContentParser.parseFileChange(message)

        assertEquals("2 files changed", preview.headline)
        assertEquals(2, preview.fileCount)
        assertEquals(14, preview.totalAdditions)
        assertEquals(1, preview.totalDeletions)
        assertEquals("Renamed", preview.entries[0].label)
        assertEquals("Added", preview.entries[1].label)
        assertEquals("app/src/main/kotlin/Main.kt", preview.entries[0].path)
        assertEquals("app/src/main/kotlin/Other.kt", preview.entries[1].path)
    }

    @Test
    fun parseFileChange_skipsNonDiffFence_thenExtractsDiffPatch() {
        val message =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.fileChange,
                text =
                    """
                    Snippet

                    ```kotlin
                    val x = 1
                    ```

                    ```diff
                    diff --git a/app/Foo.kt b/app/Foo.kt
                    --- a/app/Foo.kt
                    +++ b/app/Foo.kt
                    @@ -1,2 +1,2 @@
                    -a
                    +b
                    c
                    ```
                    """.trimIndent(),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val preview = TurnTimelineRichContentParser.parseFileChange(message)

        assertTrue(preview.rawPatchText?.contains("diff --git") == true)
        assertEquals(1, preview.fileCount)
        assertEquals(1, preview.totalAdditions)
        assertEquals(1, preview.totalDeletions)
    }

    @Test
    fun parseFileChange_hunkOnlySinglePath_fillsCountsFromRawHunkBody() {
        val message =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.fileChange,
                text =
                    """
                    Path: app/src/Main.kt

                    ```diff
                    @@ -1,3 +1,4 @@
                     line1
                    -old
                    +new
                    +new2
                    ```
                    """.trimIndent(),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val preview = TurnTimelineRichContentParser.parseFileChange(message)

        assertEquals(1, preview.fileCount)
        assertEquals(2, preview.totalAdditions)
        assertEquals(1, preview.totalDeletions)
        assertEquals("Main.kt", preview.entries.single().path.substringAfterLast('/'))
    }

    @Test
    fun turnFileChangePresentation_likelyHasDiff() {
        val fromFence =
            TurnFileChangePresentation(
                headline = "h",
                summaryText = "s",
                entries = emptyList(),
                rawText = "body",
                rawPatchText = "diff --git a/x b/x",
            )
        assertTrue(fromFence.likelyHasDiff)

        val fromMarkers =
            TurnFileChangePresentation(
                headline = "h",
                summaryText = "s",
                entries = emptyList(),
                rawText = "+++ b/foo.kt\n",
                rawPatchText = null,
            )
        assertTrue(fromMarkers.likelyHasDiff)

        val fromPathOnly =
            TurnFileChangePresentation(
                headline = "h",
                summaryText = "s",
                entries = emptyList(),
                rawText = "Path: C:\\repo\\Foo.kt\n",
                rawPatchText = null,
            )
        assertFalse(fromPathOnly.likelyHasDiff)
    }

    @Test
    fun parseSubagent_withoutStructuredAction_usesFirstNonBlankLine() {
        val message =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.subagentAction,
                text =
                    """
                    
                    Agent finished

                    Reviewed 3 files
                    """.trimIndent(),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val preview = TurnTimelineRichContentParser.parseSubagent(message)

        assertEquals("Agent finished", preview.headline)
        assertEquals("Agent finished\n\nReviewed 3 files", preview.summaryText)
        assertEquals(null, preview.promptText)
        assertTrue(preview.agents.isEmpty())
    }

    @Test
    fun parseSubagent_usesStructuredMetadataAndFallbackText() {
        val message =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.subagentAction,
                text = "Subagent activity",
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                subagentAction =
                    CodexSubagentAction(
                        tool = "send_input",
                        status = "   ",
                        prompt = "  Review the diff  ",
                        receiverThreadIds = listOf("sub-1"),
                        receiverAgents =
                            listOf(
                                CodexSubagentRef(
                                    threadId = "sub-1",
                                    nickname = " Worker ",
                                    role = " reviewer ",
                                    model = " gpt-5 ",
                                ),
                            ),
                        agentStates =
                            mapOf(
                                "sub-1" to CodexSubagentState(
                                    threadId = "sub-1",
                                    status = " running ",
                                    message = " Booting ",
                                ),
                            ),
                    ),
            )

        val preview = TurnTimelineRichContentParser.parseSubagent(message)

        assertEquals("Updating agent", preview.headline)
        assertEquals("Updating agent", preview.summaryText)
        assertEquals("Review the diff", preview.promptText)
        assertEquals(1, preview.agents.size)
        assertEquals("Worker [reviewer]", preview.agents.single().label)
        assertEquals("gpt-5", preview.agents.single().model)
        assertEquals("running", preview.agents.single().status)
        assertEquals("Booting", preview.agents.single().message)
    }

    @Test
    fun parseCommandExecution_extractsPhaseCommandAndOutput() {
        val message =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.commandExecution,
                text =
                    """
                    failed ./gradlew testDebugUnitTest
                    Task :app:testDebugUnitTest FAILED
                    """.trimIndent(),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val preview = TurnTimelineRichContentParser.parseCommandExecution(message)

        assertEquals("failed", preview.phase)
        assertEquals("./gradlew testDebugUnitTest", preview.command)
        assertEquals("Task :app:testDebugUnitTest FAILED", preview.outputText)
        assertTrue(preview.isFailure)
    }

    @Test
    fun parseCommandExecution_extractsMetadataLines() {
        val message =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.commandExecution,
                text =
                    """
                    completed bash -lc "cd /repo && ./gradlew test"
                    cwd: /repo/android
                    exitCode: 0
                    duration: 2.5s
                    BUILD SUCCESSFUL
                    """.trimIndent(),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val preview = TurnTimelineRichContentParser.parseCommandExecution(message)

        assertEquals("completed", preview.phase)
        assertEquals("""bash -lc "cd /repo && ./gradlew test"""", preview.command)
        assertEquals("/repo/android", preview.cwd)
        assertEquals(0, preview.exitCode)
        assertEquals(2500, preview.durationMs)
        assertEquals("BUILD SUCCESSFUL", preview.outputText)
    }

    @Test
    fun parseFileChange_fillsDiffCountsWhenSummaryPathDiffersFromPatchPaths() {
        val message =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.fileChange,
                text =
                    """
                    Path: C:\Users\dev\apps\remodex\TurnCommandHumanizer.kt
                    Kind: update
                    Totals: +4 -2

                    ```diff
                    diff --git a/android/app/src/main/kotlin/turn/TurnCommandHumanizer.kt b/android/app/src/main/kotlin/turn/TurnCommandHumanizer.kt
                    --- a/android/app/src/main/kotlin/turn/TurnCommandHumanizer.kt
                    +++ b/android/app/src/main/kotlin/turn/TurnCommandHumanizer.kt
                    @@ -1,1 +1,1 @@
                    -x
                    +y
                    ```
                    """.trimIndent(),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val preview = TurnTimelineRichContentParser.parseFileChange(message)

        assertEquals(1, preview.fileCount)
        assertEquals(1, preview.entries.single().additions)
        assertEquals(1, preview.entries.single().deletions)
    }

    @Test
    fun parseFileChange_prefersSummaryTotalsWhenPatchCountsAreAllZero() {
        val message =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.fileChange,
                text =
                    """
                    Path: app/src/Feature.kt
                    Kind: update
                    Totals: +7 -3

                    ```diff
                    diff --git a/app/src/Feature.kt b/app/src/Feature.kt
                    new file mode 100644
                    --- /dev/null
                    +++ b/app/src/Feature.kt
                    ```
                    """.trimIndent(),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val preview = TurnTimelineRichContentParser.parseFileChange(message)

        assertEquals(1, preview.fileCount)
        assertEquals(7, preview.entries.single().additions)
        assertEquals(3, preview.entries.single().deletions)
    }

    @Test
    fun parseCommandExecution_inlinedPhaseChevron_sameAsSpaceSeparatedPhase() {
        val chevron =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.commandExecution,
                text = """completed> bash -lc "cd /repo && ./gradlew test"""",
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )
        val spaced =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.commandExecution,
                text = """completed bash -lc "cd /repo && ./gradlew test"""",
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val a = TurnTimelineRichContentParser.parseCommandExecution(chevron)
        val b = TurnTimelineRichContentParser.parseCommandExecution(spaced)

        assertEquals(b.command, a.command)
        assertEquals("completed", a.phase)
        assertEquals("completed", b.phase)
    }

    @Test
    fun parseCommandExecution_usesStreamingStateWhenPhaseIsMissing() {
        val message =
            CodexMessage(
                threadId = "thread-1",
                role = CodexMessageRole.system,
                kind = CodexMessageKind.commandExecution,
                text = "npm start",
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                isStreaming = true,
            )

        val preview = TurnTimelineRichContentParser.parseCommandExecution(message)

        assertEquals("running", preview.phase)
        assertEquals("npm start", preview.command)
        assertEquals(null, preview.outputText)
        assertTrue(preview.isRunning)
    }
}
