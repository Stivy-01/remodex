package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessageDeliveryState
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class MessageTimelineStoreTest {
    @Test
    fun completeAssistantMessage_mergesStreamingRowWhenCompletionAddsItemId() =
        runTest {
            val store = MessageTimelineStore()

            store.appendAssistantDelta(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = null,
                delta = "prova",
            )
            store.completeAssistantMessage(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = "item-1",
                text = "prova ok",
            )

            val messages = store.messagesByThread.value["thread-1"].orEmpty()
            assertEquals(1, messages.size)
            assertEquals(CodexMessageRole.assistant, messages.single().role)
            assertEquals("prova ok", messages.single().text)
            assertEquals("item-1", messages.single().itemId)
        }

    @Test
    fun appendAssistantDelta_preservesDeltaSpacing() =
        runTest {
            val store = MessageTimelineStore()

            store.appendAssistantDelta(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = "item-1",
                delta = "hello",
            )
            store.appendAssistantDelta(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = "item-1",
                delta = " world",
            )

            val messages = store.messagesByThread.value["thread-1"].orEmpty()
            assertEquals("hello world", messages.single().text)
            assertEquals(true, messages.single().isStreaming)
        }

    @Test
    fun completeAssistantMessage_keepsRepeatedNoIdCompletions() =
        runTest {
            val store = MessageTimelineStore()

            store.completeAssistantMessage(
                threadId = "thread-1",
                turnId = null,
                itemId = null,
                text = "same answer",
            )
            store.completeAssistantMessage(
                threadId = "thread-1",
                turnId = null,
                itemId = null,
                text = "same answer",
            )

            val messages = store.messagesByThread.value["thread-1"].orEmpty()
            assertEquals(2, messages.size)
            assertEquals(listOf("same answer", "same answer"), messages.map { it.text })
        }

    @Test
    fun completeSystemItem_mergesStreamingReasoningRowWhenCompletionAddsItemId() =
        runTest {
            val store = MessageTimelineStore()

            store.appendStreamingSystemItemDelta(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = null,
                kind = CodexMessageKind.thinking,
                delta = "Thinking...",
            )
            store.completeSystemItem(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = "reasoning-1",
                kind = CodexMessageKind.thinking,
                text = "Thinking final",
            )

            val messages = store.messagesByThread.value["thread-1"].orEmpty()
            assertEquals(1, messages.size)
            assertEquals(CodexMessageKind.thinking, messages.single().kind)
            assertEquals("Thinking final", messages.single().text)
            assertEquals("reasoning-1", messages.single().itemId)
        }

    @Test
    fun fileChangeSnapshot_mergesSamePathRowsAcrossItemIds() =
        runTest {
            val store = MessageTimelineStore()

            store.upsertStreamingSystemItemSnapshot(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = "file-1",
                kind = CodexMessageKind.fileChange,
                snapshot = "Status: running\n\nPath: src/App.kt\nKind: update\nTotals: +1 -0",
            )
            store.upsertStreamingSystemItemSnapshot(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = "file-2",
                kind = CodexMessageKind.fileChange,
                snapshot = "Status: completed\n\nPath: ./src/App.kt\nKind: update\nTotals: +2 -1",
            )

            val messages = store.messagesByThread.value["thread-1"].orEmpty()
            assertEquals(1, messages.size)
            assertEquals("file-2", messages.single().itemId)
            assertEquals(true, messages.single().text.contains("Totals: +2 -1"))
        }

    @Test
    fun fileChangeSnapshot_keepsDistinctPathsInSameTurn() =
        runTest {
            val store = MessageTimelineStore()

            store.upsertStreamingSystemItemSnapshot(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = "file-1",
                kind = CodexMessageKind.fileChange,
                snapshot = "Status: running\n\nPath: src/App.kt\nKind: update\nTotals: +1 -0",
            )
            store.upsertStreamingSystemItemSnapshot(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = "file-2",
                kind = CodexMessageKind.fileChange,
                snapshot = "Status: completed\n\nPath: src/Other.kt\nKind: update\nTotals: +2 -1",
            )

            val messages = store.messagesByThread.value["thread-1"].orEmpty()
            assertEquals(2, messages.size)
            assertEquals(listOf("file-1", "file-2"), messages.map { it.itemId })
        }

    @Test
    fun fileChangeCompletion_doesNotOverwriteDifferentPathStreamingRow() =
        runTest {
            val store = MessageTimelineStore()

            store.upsertStreamingSystemItemSnapshot(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = "file-1",
                kind = CodexMessageKind.fileChange,
                snapshot = "Status: running\n\nPath: src/App.kt\nKind: update\nTotals: +1 -0",
            )
            store.completeSystemItem(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = "file-2",
                kind = CodexMessageKind.fileChange,
                text = "Status: completed\n\nPath: src/Other.kt\nKind: update\nTotals: +2 -1",
            )

            val messages = store.messagesByThread.value["thread-1"].orEmpty()
            assertEquals(2, messages.size)
            assertEquals(listOf("file-1", "file-2"), messages.map { it.itemId })
        }

    @Test
    fun commandExecutionCompletion_reconcilesFailedChevronRowsAcrossItemIds() =
        runTest {
            val store = MessageTimelineStore()

            store.completeSystemItem(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = "cmd-1",
                kind = CodexMessageKind.commandExecution,
                text = "running> Gradle",
            )
            store.completeSystemItem(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = "cmd-2",
                kind = CodexMessageKind.commandExecution,
                text = "failed> Gradle",
            )

            val messages = store.messagesByThread.value["thread-1"].orEmpty()
            assertEquals(1, messages.size)
            assertEquals("failed> Gradle", messages.single().text)
            assertEquals("cmd-2", messages.single().itemId)
        }

    @Test
    fun appendMirroredUser_insertsBeforeExistingTurnActivityWhenUserArrivesLate() =
        runTest {
            val store = MessageTimelineStore()

            store.appendThinkingDelta(
                threadId = "thread-1",
                turnId = "turn-1",
                itemId = null,
                delta = "Thinking...",
            )
            store.appendMirroredUser(
                threadId = "thread-1",
                turnId = "turn-1",
                text = "hello from desktop",
            )

            val messages =
                store.messagesByThread.value["thread-1"]
                    .orEmpty()
            assertEquals(2, messages.size)
            assertEquals(CodexMessageRole.user, messages[0].role)
            assertEquals(CodexMessageRole.system, messages[1].role)
        }

    @Test
    fun confirmLatestPendingUserMessage_marksPendingAsConfirmed() =
        runTest {
            val store = MessageTimelineStore()

            store.appendPendingUserMessage(
                threadId = "thread-1",
                text = "hello",
            )
            store.confirmLatestPendingUserMessage(
                threadId = "thread-1",
                turnId = "turn-1",
            )

            val message = store.messagesByThread.value["thread-1"].orEmpty().single()
            assertEquals(CodexMessageDeliveryState.confirmed, message.deliveryState)
            assertEquals("turn-1", message.turnId)
        }

    @Test
    fun appendStructuredInputPromptMarker_addsEphemeralRow() =
        runTest {
            val store = MessageTimelineStore()

            store.appendStructuredInputPromptMarker(
                threadId = "t1",
                turnId = "turn-x",
                messageId = "req-1",
                bodyText = "Choose speed",
            )

            val row = store.messagesByThread.value["t1"].orEmpty().single()
            assertEquals(CodexMessageKind.userInputPrompt, row.kind)
            assertEquals("req-1", row.id)
            assertEquals("Choose speed", row.text)
            assertEquals("turn-x", row.turnId)
        }

    @Test
    fun removeEphemeralPendingServerMarker_removesStructuredInputAndApprovalById() =
        runTest {
            val store = MessageTimelineStore()

            store.appendPendingApprovalMarker(
                threadId = "t-app",
                turnId = null,
                itemId = null,
                messageId = "ap-9",
                bodyText = "approve me",
            )
            store.appendStructuredInputPromptMarker(
                threadId = "t-in",
                turnId = null,
                messageId = "si-9",
                bodyText = "type me",
            )

            store.removeEphemeralPendingServerMarker("si-9")

            assertEquals(0, store.messagesByThread.value["t-in"].orEmpty().size)
            assertEquals(1, store.messagesByThread.value["t-app"].orEmpty().size)

            store.removeEphemeralPendingServerMarker("ap-9")

            assertEquals(0, store.messagesByThread.value["t-app"].orEmpty().size)
        }
}

