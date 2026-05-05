package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class TurnTimelineRichContentCacheTest {
    @Test
    fun fileChangeCacheReusesRenderModelForSameMessageSnapshot() {
        TurnTimelineRichContentCache.clear()
        val message = fileChangeMessage("msg-1", "```diff\n+hello\n```")

        val first = TurnTimelineRichContentCache.parseFileChange(message)
        val second = TurnTimelineRichContentCache.parseFileChange(message)

        assertSame(first, second)
    }

    @Test
    fun fileChangeCacheInvalidatesWhenTextChanges() {
        TurnTimelineRichContentCache.clear()
        val first = TurnTimelineRichContentCache.parseFileChange(fileChangeMessage("msg-1", "```diff\n+hello\n```"))
        val second = TurnTimelineRichContentCache.parseFileChange(fileChangeMessage("msg-1", "```diff\n+bye\n```"))

        assertNotSame(first, second)
    }

    @Test
    fun commandCacheSeparatesStreamingFallback() {
        TurnTimelineRichContentCache.clear()
        val running =
            TurnTimelineRichContentCache.parseCommandExecution(
                commandMessage(id = "cmd-1", text = "", streaming = true),
            )
        val completed =
            TurnTimelineRichContentCache.parseCommandExecution(
                commandMessage(id = "cmd-1", text = "", streaming = false),
            )

        assertEquals("running", running.phase)
        assertEquals("completed", completed.phase)
    }

    @Test
    fun mermaidCacheReusesSegmentsForSameText() {
        TurnTimelineRichContentCache.clear()
        val markdown = "Intro\n```mermaid\nflowchart TD\nA-->B\n```"

        val first = TurnTimelineRichContentCache.parseMermaidMarkdown(markdown)
        val second = TurnTimelineRichContentCache.parseMermaidMarkdown(markdown)

        assertSame(first, second)
    }

    private fun fileChangeMessage(
        id: String,
        text: String,
    ) = CodexMessage(
        id = id,
        threadId = "thread",
        role = CodexMessageRole.system,
        kind = CodexMessageKind.fileChange,
        text = text,
        createdAt = Instant.EPOCH,
    )

    private fun commandMessage(
        id: String,
        text: String,
        streaming: Boolean,
    ) = CodexMessage(
        id = id,
        threadId = "thread",
        role = CodexMessageRole.system,
        kind = CodexMessageKind.commandExecution,
        text = text,
        createdAt = Instant.EPOCH,
        isStreaming = streaming,
    )
}
