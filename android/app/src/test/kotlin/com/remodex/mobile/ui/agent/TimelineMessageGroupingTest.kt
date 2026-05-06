package com.remodex.mobile.ui.agent

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TimelineMessageGroupingTest {
    private val t0 = Instant.parse("2024-01-01T00:00:00Z")

    private fun cmd(id: String): CodexMessage =
        CodexMessage(
            id = id,
            threadId = "t1",
            role = CodexMessageRole.system,
            kind = CodexMessageKind.commandExecution,
            text = "completed > $id",
            createdAt = t0,
        )

    private fun file(id: String): CodexMessage =
        CodexMessage(
            id = id,
            threadId = "t1",
            role = CodexMessageRole.system,
            kind = CodexMessageKind.fileChange,
            text = "path $id",
            createdAt = t0,
        )

    private fun thinking(id: String): CodexMessage =
        CodexMessage(
            id = id,
            threadId = "t1",
            role = CodexMessageRole.system,
            kind = CodexMessageKind.thinking,
            text = "Thinking...",
            createdAt = t0,
        )

    @Test
    fun upToThreeCommands_remainSingles() {
        val items = listOf(cmd("a"), cmd("b"), cmd("c")).toTimelineListItems()
        assertEquals(3, items.size)
        assertTrue(items.all { it is TimelineListItem.Single })
    }

    @Test
    fun fourCommands_becomeOneGroup() {
        val items = listOf(cmd("a"), cmd("b"), cmd("c"), cmd("d")).toTimelineListItems()
        assertEquals(1, items.size)
        val group = assertIs<TimelineListItem.CommandExecutionGroup>(items.single())
        assertEquals(4, group.messages.size)
        assertEquals("a", group.messages[0].id)
        assertEquals("d", group.messages[3].id)
    }

    @Test
    fun fourFileChanges_becomeOneGroup() {
        val items = listOf(file("1"), file("2"), file("3"), file("4")).toTimelineListItems()
        assertEquals(1, items.size)
        assertIs<TimelineListItem.FileChangeGroup>(items.single())
    }

    @Test
    fun assistantBetweenCommands_breaksRuns() {
        val assistant =
            CodexMessage(
                id = "as",
                threadId = "t1",
                role = CodexMessageRole.assistant,
                kind = CodexMessageKind.chat,
                text = "ok",
                createdAt = t0,
            )
        val items =
            listOf(
                cmd("1"),
                cmd("2"),
                cmd("3"),
                cmd("4"),
                assistant,
                cmd("5"),
                cmd("6"),
                cmd("7"),
                cmd("8"),
            ).toTimelineListItems()
        assertEquals(3, items.size)
        assertIs<TimelineListItem.CommandExecutionGroup>(items[0]).also {
            assertEquals(listOf("1", "2", "3", "4"), it.messages.map { m -> m.id })
        }
        assertIs<TimelineListItem.Single>(items[1]).also { assertEquals("as", it.message.id) }
        assertIs<TimelineListItem.CommandExecutionGroup>(items[2]).also { assertEquals(4, it.messages.size) }
    }

    @Test
    fun commandThenFile_doesNotMergeKinds() {
        val items = listOf(cmd("c1"), file("f1"), file("f2"), file("f3"), file("f4")).toTimelineListItems()
        assertEquals(2, items.size)
        assertIs<TimelineListItem.Single>(items[0])
        assertIs<TimelineListItem.FileChangeGroup>(items[1]).also { assertEquals(4, it.messages.size) }
    }

    @Test
    fun longAssistantMessage_splitsIntoStableChunks() {
        val assistant =
            CodexMessage(
                id = "long",
                threadId = "t1",
                role = CodexMessageRole.assistant,
                kind = CodexMessageKind.chat,
                text = (1..140).joinToString("\n\n") { "Paragraph $it with enough text to make this response large." },
                createdAt = t0,
            )

        val items = listOf(assistant).toTimelineListItems()

        assertTrue(items.size > 1)
        assertIs<TimelineListItem.MessageChunk>(items.first()).also {
            assertEquals("long-chunk-0", it.stableKey)
            assertTrue(it.isFirstChunk)
        }
    }

    @Test
    fun longAssistantSplitter_preservesFencedCodeBlock() {
        val code = (1..260).joinToString("\n") { "println($it)" }
        val text = "Intro\n\n```kotlin\n$code\n```\n\nOutro " + "x".repeat(4000)

        val chunks = splitAssistantMarkdownForTimeline(text)

        assertTrue(chunks.any { it.contains("```kotlin") && it.contains("\n```") })
        assertEquals(1, chunks.count { it.contains("```kotlin") })
    }

    @Test
    fun assistantWorkGroup_collapsesEarlierAssistantMessagesInTurn() {
        fun assistant(id: String, seconds: Long): CodexMessage =
            CodexMessage(
                id = id,
                threadId = "t1",
                role = CodexMessageRole.assistant,
                kind = CodexMessageKind.chat,
                text = id,
                createdAt = t0.plusSeconds(seconds),
                turnId = "turn-1",
            )

        val items = listOf(assistant("step-1", 0), assistant("step-2", 30), assistant("summary", 70)).toTimelineListItems()

        assertEquals(2, items.size)
        assertIs<TimelineListItem.AssistantWorkGroup>(items[0]).also {
            assertEquals(listOf("step-1", "step-2"), it.messages.map { message -> message.id })
        }
        assertIs<TimelineListItem.Single>(items[1]).also {
            assertEquals("summary", it.message.id)
        }
    }

    @Test
    fun thinkingRows_areHiddenFromTimelineRendering() {
        val items = listOf(cmd("c1"), thinking("r1"), file("f1")).toTimelineListItems()
        assertEquals(2, items.size)
        assertIs<TimelineListItem.Single>(items[0]).also { assertEquals("c1", it.message.id) }
        assertIs<TimelineListItem.Single>(items[1]).also { assertEquals("f1", it.message.id) }
    }
}
