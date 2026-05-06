package com.remodex.mobile.services

import com.remodex.mobile.R
import com.remodex.mobile.core.model.ContextWindowUsage
import com.remodex.mobile.core.model.ContextWindowUsageCodec
import com.remodex.mobile.core.model.JSONValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Thread context window via `thread/contextWindow/read`.
 * Parity with [CodexService+Status.swift](../../../../../../../CodexMobile/CodexMobile/Services/CodexService+Status.swift) `refreshContextWindowUsage`.
 */
internal suspend fun CodexService.refreshContextWindowUsageForRepository(threadId: String) =
    withContext(Dispatchers.IO) {
        refreshContextWindowUsageInternal(threadId)
    }

/**
 * Push updates from bridge notifications (`thread/tokenUsage/updated`, legacy token_count).
 * Dedupes identical snapshots to avoid churn.
 */
internal fun CodexService.applyLiveContextWindowUsage(
    threadId: String,
    usage: ContextWindowUsage,
) {
    val tid = threadId.trim()
    if (tid.isEmpty()) return
    val prev = _contextWindowUsageByThread.value[tid]
    if (prev != null &&
        prev.tokensUsed == usage.tokensUsed &&
        prev.tokenLimit == usage.tokenLimit
    ) {
        return
    }
    _contextWindowUsageErrorByThread.value =
        _contextWindowUsageErrorByThread.value.filterKeys { it != tid }
    _contextWindowUsageByThread.value = _contextWindowUsageByThread.value + (tid to usage)
}

internal suspend fun CodexService.refreshContextWindowUsageInternal(threadId: String) {
    val tid = threadId.trim()
    if (tid.isEmpty()) return

    _contextWindowUsageLoadingThreads.value = _contextWindowUsageLoadingThreads.value + tid
    try {
        if (!sessionReady) {
            _contextWindowUsageErrorByThread.value =
                _contextWindowUsageErrorByThread.value + (tid to appContext.getString(R.string.usage_context_window_disconnected))
            return
        }

        val params = mutableMapOf<String, JSONValue>("threadId" to JSONValue.Str(tid))
        val turnId = _runningTurnIdByThread.value[tid]?.trim().orEmpty()
        if (turnId.isNotEmpty()) {
            params["turnId"] = JSONValue.Str(turnId)
        }

        val response =
            withTimeout(12_000L) {
                sendRequestImpl("thread/contextWindow/read", JSONValue.Obj(params))
            }
        val resultObject = response.result?.objectValue
        if (resultObject == null) {
            _contextWindowUsageErrorByThread.value =
                _contextWindowUsageErrorByThread.value + (tid to appContext.getString(R.string.usage_context_window_invalid_response))
            return
        }

        val usageValue = resultObject["usage"]
        _contextWindowUsageErrorByThread.value = _contextWindowUsageErrorByThread.value.filterKeys { it != tid }

        val decoded = ContextWindowUsageCodec.decode(usageValue) ?: ContextWindowUsage.Zero
        _contextWindowUsageByThread.value = _contextWindowUsageByThread.value + (tid to decoded)
    } catch (e: Exception) {
        val msg = e.message?.trim().orEmpty()
        _contextWindowUsageErrorByThread.value =
            _contextWindowUsageErrorByThread.value + (
                tid to
                    if (msg.isEmpty()) {
                        appContext.getString(R.string.usage_context_window_load_failed)
                    } else {
                        msg
                    }
            )
    } finally {
        _contextWindowUsageLoadingThreads.value = _contextWindowUsageLoadingThreads.value - tid
    }
}
