package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageDeliveryState
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class MessageTimelineStoreTest {
    @Test
    fun init_dedupesPersistedPhotoUserEchoRows() {
        val payload = "data:image/jpeg;base64,abc"
        val firstAttachment =
            CodexImageAttachment(
                id = "persisted-photo-1",
                thumbnailBase64JPEG = "thumb-1",
                payloadDataURL = payload,
                sourceURL = "content://picked/photo",
            )
        val secondAttachment =
            CodexImageAttachment(
                id = "persisted-photo-2",
                thumbnailBase64JPEG = "thumb-2",
                payloadDataURL = payload,
                sourceURL = payload,
            )
        val store =
            MessageTimelineStore(
                initialMessages =
                    mapOf(
                        "thread-1" to
                            listOf(
                                userMessage(
                                    id = "persisted-user-1",
                                    itemId = "user-input",
                                    attachments = listOf(firstAttachment),
                                ),
                                userMessage(
                                    id = "persisted-user-2",
                                    itemId = "user-message",
                                    attachments = listOf(secondAttachment),
                                ),
                            ),
                    ),
            )

        val messages = store.messagesByThread.value["thread-1"].orEmpty()
        assertEquals(1, messages.size)
        assertEquals("persisted-user-1", messages.single().id)
        assertEquals("user-message", messages.single().itemId)
        assertEquals(listOf(firstAttachment), messages.single().attachments)
    }

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
    fun appendMirroredUser_mergesPhotoEchoWithDifferentAttachmentMetadata() =
        runTest {
            val store = MessageTimelineStore()
            val payload = "data:image/jpeg;base64,abc"
            val localAttachment =
                CodexImageAttachment(
                    id = "local-photo",
                    thumbnailBase64JPEG = "local-thumb",
                    payloadDataURL = payload,
                    sourceURL = "content://picked/photo",
                )
            val mirroredAttachment =
                CodexImageAttachment(
                    id = "mirrored-photo",
                    thumbnailBase64JPEG = "mirrored-thumb",
                    payloadDataURL = payload,
                    sourceURL = payload,
                )

            store.appendPendingUserMessage("thread-1", "look", listOf(localAttachment))
            store.appendMirroredUser("thread-1", "turn-1", "look", listOf(mirroredAttachment))

            val messages = store.messagesByThread.value["thread-1"].orEmpty()
            assertEquals(1, messages.size)
            assertEquals(CodexMessageDeliveryState.confirmed, messages.single().deliveryState)
            assertEquals("turn-1", messages.single().turnId)
            assertEquals(listOf(localAttachment), messages.single().attachments)
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

    private fun userMessage(
        id: String,
        itemId: String,
        attachments: List<CodexImageAttachment>,
    ): CodexMessage =
        CodexMessage(
            id = id,
            threadId = "thread-1",
            role = CodexMessageRole.user,
            kind = CodexMessageKind.chat,
            text = "look",
            createdAt = Instant.parse("2024-01-01T00:00:01Z"),
            turnId = "turn-phone",
            itemId = itemId,
            deliveryState = CodexMessageDeliveryState.confirmed,
            attachments = attachments,
        )
}

