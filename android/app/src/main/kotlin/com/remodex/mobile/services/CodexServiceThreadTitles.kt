package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.JSONValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun CodexService.persistedThreadRename(threadId: String?): String? {
    val tid = normalizedThreadTitleId(threadId) ?: return null
    return persistedThreadRenameById[tid]?.trim()?.takeIf { it.isNotEmpty() }
}

internal fun CodexService.applyPersistedThreadRename(thread: CodexThread): CodexThread =
    thread.withPersistedThreadRename(persistedThreadRename(thread.id))

internal fun CodexThread.withPersistedThreadRename(rename: String?): CodexThread {
    val trimmed = rename?.trim()?.takeIf { it.isNotEmpty() } ?: return this
    return copy(title = trimmed, name = trimmed)
}

internal fun mergeThreadListWithPersistedRenames(
    fetched: List<CodexThread>,
    previous: List<CodexThread>,
    persistedRename: (String) -> String?,
): List<CodexThread> {
    val previousById = previous.associateBy { it.id }
    return fetched.map { incoming ->
        val existing = previousById[incoming.id]
        val merged =
            if (existing == null) {
                incoming
            } else {
                incoming.withMissingDisplayFieldsFrom(existing)
            }
        merged.withPersistedThreadRename(persistedRename(incoming.id))
    }
}

internal suspend fun CodexService.renameThreadInternal(
    threadId: String,
    name: String,
) {
    val tid = normalizedThreadTitleId(threadId) ?: throw CodexServiceError.InvalidInput("Missing thread id")
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) throw CodexServiceError.InvalidInput("Thread name is empty")

    persistThreadRename(tid, trimmedName)
    publishThreads(
        upsertThreadTitle(
            list = _threads.value,
            threadId = tid,
            title = trimmedName,
            createIfMissing = true,
        ),
    )

    sendThreadNameSetRpc(tid, trimmedName)
    scope.launch { runCatching { refreshThreadsInternal() } }
}

internal suspend fun CodexService.renameThreadForRepository(
    threadId: String,
    name: String,
) = withContext(Dispatchers.IO) {
    renameThreadInternal(threadId, name)
}

internal fun CodexService.scheduleAutomaticThreadTitleGenerationIfNeeded(
    seed: String?,
    threadId: String,
    attachments: List<CodexImageAttachment>,
) {
    val tid = normalizedThreadTitleId(threadId) ?: return
    val normalizedSeed = seed?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val fallbackTitle = fallbackThreadTitle(normalizedSeed)
    val allowedTitles = setOf(CodexThread.DEFAULT_DISPLAY_TITLE, "New Thread", fallbackTitle)
    applyAutomaticThreadTitle(tid, fallbackTitle, allowedTitles)

    scope.launch {
        val generatedTitle =
            generatedThreadTitleOrNull(
                seed = normalizedSeed,
                threadId = tid,
                attachmentCount = attachments.size,
            ) ?: return@launch
        applyAutomaticThreadTitle(tid, generatedTitle, allowedTitles)
    }
}

internal fun CodexService.automaticThreadTitleSeedIfNeeded(
    userInput: String,
    attachments: List<CodexImageAttachment>,
    threadId: String,
): String? {
    val tid = normalizedThreadTitleId(threadId) ?: return null
    val thread = _threads.value.firstOrNull { it.id == tid } ?: return null
    return automaticThreadTitleSeedCandidate(
        userInput = userInput,
        hasAttachments = attachments.isNotEmpty(),
        thread = thread,
        hasExistingUserChatMessage = hasExistingUserChatMessage(tid),
        persistedName = persistedThreadRename(tid),
    )
}

internal fun automaticThreadTitleSeedCandidate(
    userInput: String,
    hasAttachments: Boolean,
    thread: CodexThread,
    hasExistingUserChatMessage: Boolean,
    persistedName: String?,
): String? {
    if (!persistedName.isNullOrBlank()) return null
    val generic =
        CodexThread.isGenericPlaceholderTitle(thread.title) ||
            CodexThread.isGenericPlaceholderTitle(thread.displayTitle)
    if (!generic) return null
    if (hasExistingUserChatMessage) return null
    val trimmedInput = userInput.trim()
    if (trimmedInput.isNotEmpty()) return trimmedInput
    return if (hasAttachments) IMAGE_REQUEST_TITLE_SEED else null
}

internal fun fallbackThreadTitle(seed: String): String {
    val title =
        seed
            .trim()
            .split(Regex("\\s+"))
            .asSequence()
            .map { word -> word.trim { ch -> !ch.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
            .take(4)
            .joinToString(" ")
    if (title.isEmpty()) return CodexThread.DEFAULT_DISPLAY_TITLE
    return title.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

internal fun applyAutomaticThreadTitleSnapshot(
    list: List<CodexThread>,
    threadId: String,
    title: String,
    allowedCurrentTitles: Set<String>,
    persistedName: String?,
): ThreadTitleApplyResult {
    val tid = normalizedThreadTitleId(threadId) ?: return ThreadTitleApplyResult(list, false, null)
    val trimmedTitle = title.trim()
    if (trimmedTitle.isEmpty()) return ThreadTitleApplyResult(list, false, null)
    val idx = list.indexOfFirst { it.id == tid }
    if (idx < 0) return ThreadTitleApplyResult(list, false, null)

    val thread = list[idx]
    val normalizedAllowed = allowedCurrentTitles.map(::normalizedAutomaticTitleComparisonValue).toSet()
    val currentCandidates =
        listOf(persistedName, thread.name, thread.title, thread.displayTitle)
            .mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .map(::normalizedAutomaticTitleComparisonValue)
            .filter { it.isNotEmpty() }
    val defaultTitle = normalizedAutomaticTitleComparisonValue(CodexThread.DEFAULT_DISPLAY_TITLE)
    val newThreadTitle = normalizedAutomaticTitleComparisonValue("New Thread")
    val legacyConversationTitle = normalizedAutomaticTitleComparisonValue("Conversation")
    val canReplace =
        currentCandidates.isEmpty() ||
            currentCandidates.all { candidate ->
                candidate in normalizedAllowed ||
                    candidate == defaultTitle ||
                    candidate == newThreadTitle ||
                    candidate == legacyConversationTitle
            }
    if (!canReplace) return ThreadTitleApplyResult(list, false, null)

    val next =
        list.mapIndexed { i, existing ->
            if (i == idx) existing.copy(title = trimmedTitle, name = trimmedTitle) else existing
        }
    return ThreadTitleApplyResult(sortThreadsForBridge(next), true, trimmedTitle)
}

internal data class ThreadTitleApplyResult(
    val threads: List<CodexThread>,
    val applied: Boolean,
    val persistedName: String?,
)

private fun CodexService.applyAutomaticThreadTitle(
    threadId: String,
    title: String,
    allowedCurrentTitles: Set<String>,
): Boolean {
    val result =
        applyAutomaticThreadTitleSnapshot(
            list = _threads.value,
            threadId = threadId,
            title = title,
            allowedCurrentTitles = allowedCurrentTitles,
            persistedName = persistedThreadRename(threadId),
        )
    if (!result.applied) return false
    publishThreads(result.threads)
    result.persistedName?.let { persistThreadRename(threadId, it) }
    scope.launch { runCatching { sendThreadNameSetRpc(threadId, result.persistedName.orEmpty()) } }
    return true
}

private suspend fun CodexService.generatedThreadTitleOrNull(
    seed: String,
    threadId: String,
    attachmentCount: Int,
): String? {
    val params = linkedMapOf<String, JSONValue>()
    params["message"] = JSONValue.Str(seed)
    params["attachmentCount"] = JSONValue.NumLong(attachmentCount.toLong())
    runtimeModelIdentifierForTurn(threadId)?.trim()?.takeIf { it.isNotEmpty() }?.let { model ->
        params["model"] = JSONValue.Str(model)
    }
    _threads.value.firstOrNull { it.id == threadId }?.gitWorkingDirectory
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { cwd -> params["cwd"] = JSONValue.Str(cwd) }

    return runCatching {
        val response = sendRequestImpl("thread/generateTitle", JSONValue.Obj(params))
        (response.result as? JSONValue.Obj)
            ?.map
            ?.get("title")
            ?.stringValue
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}

private suspend fun CodexService.sendThreadNameSetRpc(
    threadId: String,
    name: String,
) {
    sendRequestImpl(
        "thread/name/set",
        JSONValue.Obj(
            mapOf(
                "thread_id" to JSONValue.Str(threadId),
                "name" to JSONValue.Str(name),
            ),
        ),
    )
}

private fun CodexService.persistThreadRename(
    threadId: String,
    name: String,
) {
    val tid = normalizedThreadTitleId(threadId) ?: return
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) return
    persistedThreadRenameById[tid] = trimmedName
    sessionPersistence.saveThreadRename(tid, trimmedName)
}

private fun CodexService.hasExistingUserChatMessage(threadId: String): Boolean =
    messageTimelineStore.messagesByThread.value[threadId].orEmpty().any { message ->
        message.role == CodexMessageRole.user && message.kind == CodexMessageKind.chat
    }

private fun upsertThreadTitle(
    list: List<CodexThread>,
    threadId: String,
    title: String,
    createIfMissing: Boolean,
): List<CodexThread> {
    val idx = list.indexOfFirst { it.id == threadId }
    val next =
        if (idx >= 0) {
            list.mapIndexed { i, thread ->
                if (i == idx) thread.copy(title = title, name = title) else thread
            }
        } else if (createIfMissing) {
            list + CodexThread(id = threadId, title = title, name = title)
        } else {
            list
        }
    return sortThreadsForBridge(next)
}

private fun normalizedAutomaticTitleComparisonValue(value: String): String =
    value.trim().lowercase()

private fun normalizedThreadTitleId(threadId: String?): String? =
    threadId?.trim()?.takeIf { it.isNotEmpty() }

private const val IMAGE_REQUEST_TITLE_SEED = "Image request"
