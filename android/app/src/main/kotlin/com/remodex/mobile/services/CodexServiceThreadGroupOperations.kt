package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.CodexThreadSyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mirrors [CodexService+Sync.swift](../../../../../../../../CodexMobile/CodexMobile/Services/CodexService+Sync.swift)
 * archiveThreadGroup / deleteLocalThreadGroup.
 */
internal suspend fun CodexService.archiveThreadGroupForRepository(threadIds: List<String>): List<String> =
    withContext(Dispatchers.IO) {
        archiveThreadGroupInternal(threadIds)
    }

internal suspend fun CodexService.deleteLocalThreadGroupForRepository(threadIds: List<String>): List<String> =
    withContext(Dispatchers.IO) {
        deleteLocalThreadGroupInternal(threadIds)
    }

internal suspend fun CodexService.archiveThreadGroupInternal(threadIds: List<String>): List<String> {
    val rootThreadIds = collectRootThreadIds(threadIds)
    for (rootId in rootThreadIds) {
        archiveThreadInternal(rootId)
    }
    return rootThreadIds
}

internal suspend fun CodexService.deleteLocalThreadGroupInternal(threadIds: List<String>): List<String> {
    val rootThreadIds = collectRootThreadIds(threadIds)
    val subtreeThreadIds = rootThreadIds.flatMap { collectSubtreeThreadIds(it) }
    val allIds = (subtreeThreadIds + rootThreadIds).distinct()
    for (tid in allIds) {
        deleteThreadLocallyInternal(tid)
    }
    return rootThreadIds
}

private fun CodexService.collectRootThreadIds(threadIds: List<String>): List<String> {
    val allThreads = _threads.value
    val inputSet = threadIds.toSet()
    return inputSet.filter { tid ->
        val thread = allThreads.find { it.id == tid } ?: return@filter true
        thread.parentThreadId == null || thread.parentThreadId !in inputSet
    }
}

private fun CodexService.collectSubtreeThreadIds(parentId: String): List<String> {
    val allThreads = _threads.value
    val queue = ArrayDeque<String>()
    queue.add(parentId)
    val visited = mutableSetOf<String>()
    val descendants = mutableListOf<String>()
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (thread in allThreads) {
            if (thread.parentThreadId == current && visited.add(thread.id)) {
                descendants.add(thread.id)
                queue.add(thread.id)
            }
        }
    }
    return descendants
}

private suspend fun CodexService.archiveThreadInternal(threadId: String) {
    val subtreeIds = collectSubtreeThreadIds(threadId)
    val allIds = listOf(threadId) + subtreeIds
    for (tid in allIds) {
        setThreadArchivedLocally(tid, isArchived = true)
    }
    sendThreadArchiveRpc(threadId, unarchive = false)
}

private suspend fun CodexService.sendThreadArchiveRpc(threadId: String, unarchive: Boolean) {
    if (!sessionReady) return
    runCatching {
        sendRequestImpl(
            "thread/archive",
            com.remodex.mobile.core.model.JSONValue.Obj(
                mapOf("thread_id" to com.remodex.mobile.core.model.JSONValue.Str(threadId))
            ),
        )
    }
}

private suspend fun CodexService.setThreadArchivedLocally(
    threadId: String,
    isArchived: Boolean,
) {
    noteTurnFinished(threadId)
    resumedThreadIds.remove(threadId)
    hydratedThreadIds.remove(threadId)
    loadingHistory.remove(threadId)
    _protectedRunningFallbackThreadIds.value = _protectedRunningFallbackThreadIds.value - threadId
    _runningTurnIdByThread.value = _runningTurnIdByThread.value - threadId
    messageTimelineStore.removeThreadMessages(threadId)
    sessionPersistence.removeThreadRename(threadId)
    associatedManagedWorktreePathByThreadId.remove(threadId)
    sessionPersistence.removeAssociatedManagedWorktreePath(threadId)
    if (isArchived) {
        sessionPersistence.addLocallyArchivedThreadId(threadId)
    } else {
        sessionPersistence.removeLocallyArchivedThreadId(threadId)
    }

    val currentThreads = _threads.value.toMutableList()
    val idx = currentThreads.indexOfFirst { it.id == threadId }
    if (idx >= 0) {
        currentThreads[idx] = currentThreads[idx].copy(
            syncState = if (isArchived) CodexThreadSyncState.archivedLocal else CodexThreadSyncState.live,
        )
    }

    if (_activeThreadId.value == threadId) {
        _activeThreadId.value = null
        sessionPersistence.saveLastActiveThreadId(null)
    }

    publishThreads(currentThreads)
}
