package com.remodex.mobile.core.model

object UsageStatusRefreshPolicy {
    fun shouldAutoRefresh(
        sessionReady: Boolean,
        connected: Boolean,
        threadId: String?,
        contextWindowUsageByThread: Map<String, ContextWindowUsage>,
        hasResolvedRateLimitsSnapshot: Boolean,
    ): Boolean {
        val tid = threadId?.trim().orEmpty()
        if (!sessionReady || !connected) return false
        val needsContextUsage = tid.isNotEmpty() && contextWindowUsageByThread[tid] == null
        val needsRateLimits = !hasResolvedRateLimitsSnapshot
        return needsContextUsage || needsRateLimits
    }
}
