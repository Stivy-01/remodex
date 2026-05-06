package com.remodex.mobile.data

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexCollaborationModeKind
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexTurnMention
import com.remodex.mobile.core.model.CodexTurnSkillMention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local-only FIFO queue of composer sends per thread when a run is already active (J22 foundation).
 */
data class QueuedTurnDraft(
    val id: String,
    val text: String,
    val attachments: List<CodexImageAttachment>,
    val skillMentions: List<CodexTurnSkillMention>,
    val fileMentions: List<CodexTurnMention>,
    val collaborationMode: CodexCollaborationModeKind?,
)

data class QueuedTurnDraftPreview(
    val id: String,
    val text: String,
    val attachmentCount: Int,
    val collaborationMode: CodexCollaborationModeKind?,
)

internal class TurnDraftQueueStore {
    private val mutex = Mutex()
    private val queues = mutableMapOf<String, ArrayDeque<QueuedTurnDraft>>()
    private val _depthByThread = MutableStateFlow<Map<String, Int>>(emptyMap())
    val depthByThread: StateFlow<Map<String, Int>> = _depthByThread.asStateFlow()
    private val _previewByThread = MutableStateFlow<Map<String, List<QueuedTurnDraftPreview>>>(emptyMap())
    val previewByThread: StateFlow<Map<String, List<QueuedTurnDraftPreview>>> = _previewByThread.asStateFlow()

    private fun recomputeDepthsLocked() {
        _depthByThread.value =
            queues
                .mapValues { it.value.size }
                .filterValues { it > 0 }
        _previewByThread.value =
            queues
                .mapValues { (_, q) ->
                    q.map { draft ->
                        QueuedTurnDraftPreview(
                            id = draft.id,
                            text = draft.text,
                            attachmentCount = draft.attachments.size,
                            collaborationMode = draft.collaborationMode,
                        )
                    }
                }
                .filterValues { it.isNotEmpty() }
    }

    suspend fun enqueue(
        threadId: String,
        text: String,
        attachments: List<CodexImageAttachment>,
        skillMentions: List<CodexTurnSkillMention>,
        fileMentions: List<CodexTurnMention>,
        collaborationMode: CodexCollaborationModeKind?,
        prepend: Boolean = false,
    ) {
        val tid = threadId.trim()
        if (tid.isEmpty()) throw CodexServiceError.InvalidInput("Missing thread id")
        val trimmed = text.trim()
        val readyAttachments =
            attachments.filter { attachment ->
                !attachment.payloadDataURL.isNullOrBlank()
            }
        if (trimmed.isEmpty() && readyAttachments.isEmpty()) {
            throw CodexServiceError.InvalidInput("Message is empty")
        }
        mutex.withLock {
            val q = queues.getOrPut(tid) { ArrayDeque() }
            val draft =
                QueuedTurnDraft(
                    id = java.util.UUID.randomUUID().toString(),
                    text = trimmed,
                    attachments = readyAttachments,
                    skillMentions = skillMentions.normalizeSkillMentions(),
                    fileMentions = fileMentions.normalizeFileMentions(),
                    collaborationMode = collaborationMode,
                )
            if (prepend) {
                q.addFirst(draft)
            } else {
                q.addLast(draft)
            }
            recomputeDepthsLocked()
        }
    }

    private fun List<CodexTurnSkillMention>.normalizeSkillMentions(): List<CodexTurnSkillMention> =
        asSequence()
            .mapNotNull { mention ->
                val id = mention.id.trim()
                if (id.isEmpty()) return@mapNotNull null
                CodexTurnSkillMention(
                    id = id,
                    name = mention.name?.trim()?.takeIf { it.isNotEmpty() },
                    path = mention.path?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            .distinctBy { mention ->
                mention.id.lowercase() + "|" + mention.name.orEmpty().lowercase() + "|" + mention.path.orEmpty().lowercase()
            }
            .toList()

    private fun List<CodexTurnMention>.normalizeFileMentions(): List<CodexTurnMention> =
        asSequence()
            .mapNotNull { mention ->
                val name = mention.name.trim()
                val path = mention.path.trim()
                if (name.isEmpty() || path.isEmpty()) return@mapNotNull null
                CodexTurnMention(name = name, path = path)
            }
            .distinctBy { mention ->
                mention.name.lowercase() + "|" + mention.path.lowercase()
            }
            .toList()

    suspend fun poll(threadId: String): QueuedTurnDraft? {
        val tid = threadId.trim()
        if (tid.isEmpty()) return null
        return mutex.withLock {
            val q = queues[tid] ?: return@withLock null
            val d = q.removeFirstOrNull() ?: return@withLock null
            if (q.isEmpty()) {
                queues.remove(tid)
            }
            recomputeDepthsLocked()
            d
        }
    }

    suspend fun clear() {
        mutex.withLock {
            queues.clear()
            recomputeDepthsLocked()
        }
    }

    suspend fun remove(
        threadId: String,
        draftId: String,
    ): QueuedTurnDraft? {
        val tid = threadId.trim()
        val did = draftId.trim()
        if (tid.isEmpty() || did.isEmpty()) return null
        return mutex.withLock {
            val q = queues[tid] ?: return@withLock null
            val idx = q.indexOfFirst { it.id == did }
            if (idx < 0) return@withLock null
            val removed = q.removeAt(idx)
            if (q.isEmpty()) {
                queues.remove(tid)
            }
            recomputeDepthsLocked()
            removed
        }
    }
}
