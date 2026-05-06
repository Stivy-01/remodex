package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexThread

object RunningThreadRefreshPolicy {
    fun inactiveRunningThreadIds(
        threads: List<CodexThread>,
        activeThreadId: String?,
        runningTurnIdByThread: Map<String, String>,
        protectedRunningFallbackThreadIds: Set<String>,
        limit: Int = 3,
    ): List<String> {
        val active = activeThreadId?.trim()?.takeIf { it.isNotEmpty() }
        val available = threads.map { it.id }.toSet()
        val candidates = runningTurnIdByThread.keys + protectedRunningFallbackThreadIds
        return candidates
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != active && it in available }
            .distinct()
            .take(limit.coerceAtLeast(0))
            .toList()
    }
}
