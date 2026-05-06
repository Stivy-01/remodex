package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun CodexService.deleteThreadLocallyForRepository(threadId: String) =
    withContext(Dispatchers.IO) {
        deleteThreadLocallyInternal(threadId)
    }

internal suspend fun CodexService.deleteThreadLocallyInternal(threadId: String) {
    val tid = threadId.trim().takeIf { it.isNotEmpty() }
        ?: throw CodexServiceError.InvalidInput("Missing thread id")

    noteTurnFinished(tid)
    resumedThreadIds.remove(tid)
    hydratedThreadIds.remove(tid)
    loadingHistory.remove(tid)
    _protectedRunningFallbackThreadIds.value = _protectedRunningFallbackThreadIds.value - tid
    _runningTurnIdByThread.value = _runningTurnIdByThread.value - tid
    messageTimelineStore.removeThreadMessages(tid)
    sessionPersistence.removeThreadRename(tid)
    associatedManagedWorktreePathByThreadId.remove(tid)
    sessionPersistence.removeAssociatedManagedWorktreePath(tid)
    sessionPersistence.addLocallyDeletedThreadId(tid)

    if (_activeThreadId.value == tid) {
        _activeThreadId.value = null
        sessionPersistence.saveLastActiveThreadId(null)
    }
    publishThreads(_threads.value.filter { it.id != tid })
}
