package com.remodex.mobile.services

import com.remodex.mobile.R
import com.remodex.mobile.core.model.ContextWindowUsage
import com.remodex.mobile.core.model.ContextWindowUsageCodec
import com.remodex.mobile.core.model.JSONValue
import kotlinx.coroutines.CancellationException
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
    synchronized(contextWindowUsageStateLock) {
        contextWindowUsageRevisionByThread[tid] = nextContextWindowUsageRevision(tid)
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
}

internal suspend fun CodexService.refreshContextWindowUsageInternal(threadId: String) {
    val tid = threadId.trim()
    if (tid.isEmpty()) return

    val revision = beginContextWindowUsageRefresh(tid)
    try {
        if (!sessionReady) {
            applyContextWindowUsageRefreshError(
                tid,
                appContext.getString(R.string.usage_context_window_disconnected),
                revision,
            )
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
            applyContextWindowUsageRefreshError(
                tid,
                appContext.getString(R.string.usage_context_window_invalid_response),
                revision,
            )
            return
        }

        val usageValue = resultObject["usage"]
        val decoded = ContextWindowUsageCodec.decode(usageValue) ?: ContextWindowUsage.Zero
        applyContextWindowUsageRefreshSuccess(tid, decoded, revision)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val msg = e.message?.trim().orEmpty()
        applyContextWindowUsageRefreshError(
            tid,
            if (msg.isEmpty()) {
                appContext.getString(R.string.usage_context_window_load_failed)
            } else {
                msg
            },
            revision,
        )
    } finally {
        finishContextWindowUsageRefresh(tid, revision)
    }
}

private fun CodexService.beginContextWindowUsageRefresh(tid: String): Long =
    synchronized(contextWindowUsageStateLock) {
        val revision = nextContextWindowUsageRevision(tid)
        contextWindowUsageRevisionByThread[tid] = revision
        contextWindowUsageLoadingRevisionByThread[tid] = revision
        _contextWindowUsageLoadingThreads.value = _contextWindowUsageLoadingThreads.value + tid
        revision
    }

private fun CodexService.applyContextWindowUsageRefreshSuccess(
    tid: String,
    usage: ContextWindowUsage,
    revision: Long,
) {
    synchronized(contextWindowUsageStateLock) {
        if (contextWindowUsageRevisionByThread[tid] != revision) return
        _contextWindowUsageErrorByThread.value =
            _contextWindowUsageErrorByThread.value.filterKeys { it != tid }
        _contextWindowUsageByThread.value = _contextWindowUsageByThread.value + (tid to usage)
    }
}

private fun CodexService.applyContextWindowUsageRefreshError(
    tid: String,
    message: String,
    revision: Long,
) {
    synchronized(contextWindowUsageStateLock) {
        if (contextWindowUsageRevisionByThread[tid] != revision) return
        _contextWindowUsageErrorByThread.value =
            _contextWindowUsageErrorByThread.value + (tid to message)
    }
}

private fun CodexService.finishContextWindowUsageRefresh(
    tid: String,
    revision: Long,
) {
    synchronized(contextWindowUsageStateLock) {
        if (contextWindowUsageLoadingRevisionByThread[tid] != revision) return
        contextWindowUsageLoadingRevisionByThread.remove(tid)
        _contextWindowUsageLoadingThreads.value = _contextWindowUsageLoadingThreads.value - tid
    }
}

private fun CodexService.nextContextWindowUsageRevision(tid: String): Long =
    (contextWindowUsageRevisionByThread[tid] ?: 0L) + 1L
