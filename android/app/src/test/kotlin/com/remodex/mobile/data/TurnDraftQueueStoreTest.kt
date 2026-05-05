package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexCollaborationModeKind
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexTurnMention
import com.remodex.mobile.core.model.CodexTurnSkillMention
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnDraftQueueStoreTest {
    @Test
    fun enqueue_updates_depth_and_preview() = runTest {
        val store = TurnDraftQueueStore()
        store.enqueue(
            threadId = "t1",
            text = "hello world",
            attachments = listOf(imageAttachment()),
            skillMentions = listOf(CodexTurnSkillMention(id = "code-review", name = "Code Review")),
            fileMentions = listOf(CodexTurnMention(name = "B.kt", path = "src/a/B.kt")),
            collaborationMode = CodexCollaborationModeKind.plan,
        )

        assertEquals(1, store.depthByThread.value["t1"])
        val preview = store.previewByThread.value["t1"]?.single()
        assertNotNull(preview)
        assertEquals("hello world", preview?.text)
        assertEquals(1, preview?.attachmentCount)
        assertEquals(CodexCollaborationModeKind.plan, preview?.collaborationMode)
        val queued = store.poll("t1")
        assertEquals(listOf(CodexTurnSkillMention(id = "code-review", name = "Code Review")), queued?.skillMentions)
        assertEquals(listOf(CodexTurnMention(name = "B.kt", path = "src/a/B.kt")), queued?.fileMentions)
    }

    @Test
    fun remove_drops_specific_item_and_keeps_remaining_order() = runTest {
        val store = TurnDraftQueueStore()
        store.enqueue("t1", "first", emptyList(), emptyList(), emptyList(), null)
        store.enqueue("t1", "second", emptyList(), emptyList(), emptyList(), null)
        val previews = store.previewByThread.value["t1"].orEmpty()
        val firstId = previews.first().id

        val removed = store.remove("t1", firstId)
        assertNotNull(removed)

        val remaining = store.previewByThread.value["t1"].orEmpty()
        assertEquals(1, remaining.size)
        assertEquals("second", remaining.first().text)
        assertEquals(1, store.depthByThread.value["t1"])
    }

    @Test
    fun poll_returns_with_id_and_clears_state_when_empty() = runTest {
        val store = TurnDraftQueueStore()
        store.enqueue("t1", "only", emptyList(), emptyList(), emptyList(), null)
        val polled = store.poll("t1")
        assertTrue(!polled?.id.isNullOrBlank())
        assertTrue(store.depthByThread.value["t1"] == null)
        assertTrue(store.previewByThread.value["t1"] == null)
    }

    private fun imageAttachment(): CodexImageAttachment =
        CodexImageAttachment(
            thumbnailBase64JPEG = "abc",
            payloadDataURL = "data:image/jpeg;base64,abc",
        )
}

