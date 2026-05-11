package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageDeliveryState
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryMessageMergeTest {
    @Test
    fun merge_preservesExistingRowOrderWhenHistoryArrivesLate() {
        val existing =
            listOf(
                message(
                    id = "phone-local",
                    role = CodexMessageRole.user,
                    kind = CodexMessageKind.chat,
                    text = "phone follow-up",
                    turnId = "turn-phone",
                    itemId = null,
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:10Z"),
                    deliveryState = CodexMessageDeliveryState.confirmed,
                ),
            )
        val incoming =
            listOf(
                message(
                    id = "desktop-user",
                    role = CodexMessageRole.user,
                    kind = CodexMessageKind.chat,
                    text = "desktop first",
                    turnId = "turn-desktop",
                    itemId = null,
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:01Z"),
                ),
                message(
                    id = "desktop-assistant",
                    role = CodexMessageRole.assistant,
                    kind = CodexMessageKind.chat,
                    text = "desktop answer",
                    turnId = "turn-desktop",
                    itemId = "assistant-1",
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:02Z"),
                ),
            )

        val merged = HistoryMessageMerge.merge(existing, incoming)

        assertEquals(
            listOf("phone-local", "desktop-user", "desktop-assistant"),
            merged.map { it.id },
        )
    }

    @Test
    fun merge_movesConfirmedPhoneDuplicateToHistoryPosition() {
        val existing =
            listOf(
                message(
                    id = "phone-local",
                    role = CodexMessageRole.user,
                    kind = CodexMessageKind.chat,
                    text = "phone follow-up",
                    turnId = null,
                    itemId = null,
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:10Z"),
                    deliveryState = CodexMessageDeliveryState.pending,
                ),
            )
        val incoming =
            listOf(
                message(
                    id = "phone-history",
                    role = CodexMessageRole.user,
                    kind = CodexMessageKind.chat,
                    text = "phone follow-up",
                    turnId = "turn-phone",
                    itemId = "user-phone",
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:01Z"),
                ),
                message(
                    id = "desktop-user",
                    role = CodexMessageRole.user,
                    kind = CodexMessageKind.chat,
                    text = "desktop later",
                    turnId = "turn-desktop",
                    itemId = null,
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:02Z"),
                ),
            )

        val merged = HistoryMessageMerge.merge(existing, incoming)

        assertEquals(listOf("phone-local", "desktop-user"), merged.map { it.id })
        assertEquals(CodexMessageDeliveryState.confirmed, merged.first().deliveryState)
        assertEquals("turn-phone", merged.first().turnId)
    }

    @Test
    fun merge_reconcilesPhotoUserEchoWithDifferentAttachmentMetadata() {
        val payload = "data:image/jpeg;base64,abc"
        val localAttachment =
            CodexImageAttachment(
                id = "local-photo",
                thumbnailBase64JPEG = "local-thumb",
                payloadDataURL = payload,
                sourceURL = "content://picked/photo",
            )
        val historyAttachment =
            CodexImageAttachment(
                id = "history-photo",
                thumbnailBase64JPEG = "history-thumb",
                payloadDataURL = payload,
                sourceURL = payload,
            )
        val existing =
            listOf(
                message(
                    id = "phone-local",
                    role = CodexMessageRole.user,
                    kind = CodexMessageKind.chat,
                    text = "look",
                    turnId = null,
                    itemId = null,
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:10Z"),
                    deliveryState = CodexMessageDeliveryState.pending,
                    attachments = listOf(localAttachment),
                ),
            )
        val incoming =
            listOf(
                message(
                    id = "phone-history",
                    role = CodexMessageRole.user,
                    kind = CodexMessageKind.chat,
                    text = "look",
                    turnId = "turn-phone",
                    itemId = "user-phone",
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:01Z"),
                    attachments = listOf(historyAttachment),
                ),
            )

        val merged = HistoryMessageMerge.merge(existing, incoming)

        assertEquals(1, merged.size)
        assertEquals("phone-local", merged.single().id)
        assertEquals("turn-phone", merged.single().turnId)
        assertEquals(listOf(localAttachment), merged.single().attachments)
    }

    @Test
    fun merge_dedupesReplayedPhotoUserRowsWhenExistingTimelineIsEmpty() {
        val payload = "data:image/jpeg;base64,abc"
        val firstAttachment =
            CodexImageAttachment(
                id = "history-photo-1",
                thumbnailBase64JPEG = "thumb-1",
                payloadDataURL = payload,
                sourceURL = "content://picked/photo",
            )
        val secondAttachment =
            CodexImageAttachment(
                id = "history-photo-2",
                thumbnailBase64JPEG = "thumb-2",
                payloadDataURL = payload,
                sourceURL = payload,
            )
        val incoming =
            listOf(
                message(
                    id = "history-user-1",
                    role = CodexMessageRole.user,
                    kind = CodexMessageKind.chat,
                    text = "look",
                    turnId = "turn-phone",
                    itemId = "user-input",
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:01Z"),
                    attachments = listOf(firstAttachment),
                ),
                message(
                    id = "history-user-2",
                    role = CodexMessageRole.user,
                    kind = CodexMessageKind.chat,
                    text = "look",
                    turnId = "turn-phone",
                    itemId = "user-message",
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:02Z"),
                    attachments = listOf(secondAttachment),
                ),
            )

        val merged = HistoryMessageMerge.merge(existing = emptyList(), incoming = incoming)

        assertEquals(1, merged.size)
        assertEquals("history-user-1", merged.single().id)
        assertEquals("user-message", merged.single().itemId)
        assertEquals(listOf(firstAttachment), merged.single().attachments)
    }

    @Test
    fun merge_keepsDistinctNoItemMessagesWithSameLongPrefix() {
        val prefix = "x".repeat(180)
        val existing =
            listOf(
                message(
                    id = "assistant-1",
                    role = CodexMessageRole.assistant,
                    kind = CodexMessageKind.chat,
                    text = "$prefix one",
                    turnId = "turn-1",
                    itemId = null,
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                ),
            )
        val incoming =
            listOf(
                message(
                    id = "assistant-2",
                    role = CodexMessageRole.assistant,
                    kind = CodexMessageKind.chat,
                    text = "$prefix two",
                    turnId = "turn-1",
                    itemId = null,
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:01Z"),
                ),
            )

        val merged = HistoryMessageMerge.merge(existing, incoming)

        assertEquals(listOf("assistant-1", "assistant-2"), merged.map { it.id })
    }

    @Test
    fun merge_keepsRepeatedConfirmedUserMessagesWithoutIds() {
        val existing =
            listOf(
                message(
                    id = "repeat-1",
                    role = CodexMessageRole.user,
                    kind = CodexMessageKind.chat,
                    text = "repeat",
                    turnId = null,
                    itemId = null,
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                ),
            )
        val incoming =
            listOf(
                message(
                    id = "repeat-2",
                    role = CodexMessageRole.user,
                    kind = CodexMessageKind.chat,
                    text = "repeat",
                    turnId = null,
                    itemId = null,
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:01Z"),
                ),
            )

        val merged = HistoryMessageMerge.merge(existing, incoming)

        assertEquals(listOf("repeat-1", "repeat-2"), merged.map { it.id })
    }

    @Test
    fun merge_replacesStreamingFileChangeSubsetWithLaterSnapshot() {
        val existing =
            listOf(
                message(
                    id = "diff-1",
                    text = """
                    Edited src/App.kt +2 -1
                    """.trimIndent(),
                    turnId = "turn-1",
                    itemId = "filechange-1",
                    isStreaming = true,
                    createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                ),
            )
        val incoming =
            listOf(
                message(
                    id = "diff-2",
                    text = """
                    Edited src/App.kt +4 -2
                    Edited src/Composer.kt +6 -2
                    """.trimIndent(),
                    turnId = "turn-1",
                    itemId = "turn-diff-1",
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:01Z"),
                ),
            )

        val merged = HistoryMessageMerge.merge(existing, incoming)

        assertEquals(1, merged.size)
        assertEquals("Edited src/App.kt +4 -2\nEdited src/Composer.kt +6 -2", merged.single().text)
    }

    @Test
    fun merge_keepsDistinctCompletedFileChangeSnapshotsForSameTurn() {
        val existing =
            listOf(
                message(
                    id = "diff-1",
                    text = """
                    Status: completed

                    Path: src/App.kt
                    Kind: update
                    Totals: +2 -1
                    """.trimIndent(),
                    turnId = "turn-1",
                    itemId = "turn-diff-1",
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                ),
            )
        val incoming =
            listOf(
                message(
                    id = "diff-2",
                    text = """
                    Status: completed

                    Path: src/Composer.kt
                    Kind: update
                    Totals: +3 -1
                    """.trimIndent(),
                    turnId = "turn-1",
                    itemId = "turn-diff-2",
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:01Z"),
                ),
            )

        val merged = HistoryMessageMerge.merge(existing, incoming)

        assertEquals(2, merged.size)
        assertEquals(listOf("diff-1", "diff-2"), merged.map { it.id })
    }

    @Test
    fun merge_reconcilesThinkingSnapshotByTurnWhenItemIdChanges() {
        val existing =
            listOf(
                message(
                    id = "thinking-1",
                    kind = CodexMessageKind.thinking,
                    text = "Reading files",
                    turnId = "turn-1",
                    itemId = "turn:turn-1|kind:thinking",
                    isStreaming = true,
                    createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                ),
            )
        val incoming =
            listOf(
                message(
                    id = "thinking-2",
                    kind = CodexMessageKind.thinking,
                    text = "Reading files and checking reducers",
                    turnId = "turn-1",
                    itemId = "reasoning-1",
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:01Z"),
                ),
            )

        val merged = HistoryMessageMerge.merge(existing, incoming)

        assertEquals(1, merged.size)
        assertEquals("Reading files and checking reducers", merged.single().text)
        assertEquals("reasoning-1", merged.single().itemId)
        assertEquals(false, merged.single().isStreaming)
    }

    @Test
    fun merge_reconcilesCommandSnapshotByNormalizedPreviewWhenQuotingDiffers() {
        val existing =
            listOf(
                message(
                    id = "command-1",
                    kind = CodexMessageKind.commandExecution,
                    text = "running \"./gradlew.bat\" :app:testDebugUnitTest",
                    turnId = "turn-1",
                    itemId = "turn:turn-1|kind:commandExecution",
                    isStreaming = true,
                    createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                ),
            )
        val incoming =
            listOf(
                message(
                    id = "command-2",
                    kind = CodexMessageKind.commandExecution,
                    text = "completed './gradlew.bat' :app:testDebugUnitTest",
                    turnId = "turn-1",
                    itemId = "cmd-real-1",
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:01Z"),
                ),
            )

        val merged = HistoryMessageMerge.merge(existing, incoming)

        assertEquals(1, merged.size)
        assertEquals("completed './gradlew.bat' :app:testDebugUnitTest", merged.single().text)
        assertEquals("cmd-real-1", merged.single().itemId)
        assertEquals(false, merged.single().isStreaming)
    }

    @Test
    fun merge_reconcilesFailedCommandSnapshotWithChevronPhase() {
        val existing =
            listOf(
                message(
                    id = "command-1",
                    kind = CodexMessageKind.commandExecution,
                    text = "running> Gradle",
                    turnId = "turn-1",
                    itemId = "cmd-live-1",
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                ),
            )
        val incoming =
            listOf(
                message(
                    id = "command-2",
                    kind = CodexMessageKind.commandExecution,
                    text = "failed> Gradle",
                    turnId = "turn-1",
                    itemId = "cmd-history-1",
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:01Z"),
                ),
            )

        val merged = HistoryMessageMerge.merge(existing, incoming)

        assertEquals(1, merged.size)
        assertEquals("failed> Gradle", merged.single().text)
        assertEquals("cmd-history-1", merged.single().itemId)
    }

    @Test
    fun merge_keepsDistinctCompletedCommandsWithDifferentRealItemIds() {
        val existing =
            listOf(
                message(
                    id = "command-1",
                    kind = CodexMessageKind.commandExecution,
                    text = "completed npm test",
                    turnId = "turn-1",
                    itemId = "cmd-1",
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                ),
            )
        val incoming =
            listOf(
                message(
                    id = "command-2",
                    kind = CodexMessageKind.commandExecution,
                    text = "completed npm run lint",
                    turnId = "turn-1",
                    itemId = "cmd-2",
                    isStreaming = false,
                    createdAt = Instant.parse("2024-01-01T00:00:01Z"),
                ),
            )

        val merged = HistoryMessageMerge.merge(existing, incoming)

        assertEquals(2, merged.size)
        assertEquals(listOf("command-1", "command-2"), merged.map { it.id })
    }

    private fun message(
        id: String,
        role: CodexMessageRole = CodexMessageRole.system,
        kind: CodexMessageKind = CodexMessageKind.fileChange,
        text: String,
        turnId: String?,
        itemId: String?,
        isStreaming: Boolean,
        createdAt: Instant,
        deliveryState: CodexMessageDeliveryState = CodexMessageDeliveryState.confirmed,
        attachments: List<CodexImageAttachment> = emptyList(),
    ): CodexMessage =
        CodexMessage(
            id = id,
            threadId = "thread-1",
            role = role,
            kind = kind,
            text = text,
            createdAt = createdAt,
            turnId = turnId,
            itemId = itemId,
            isStreaming = isStreaming,
            deliveryState = deliveryState,
            attachments = attachments,
        )
}
