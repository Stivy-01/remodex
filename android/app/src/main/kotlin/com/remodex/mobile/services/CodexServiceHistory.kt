package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.ContextWindowUsageCodec
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.ThreadHistoryPaginationState
import com.remodex.mobile.core.model.isExplicitServerThreadMissing
import com.remodex.mobile.data.ThreadHistoryDecoder
import com.remodex.mobile.data.RunningThreadRefreshPolicy
import com.remodex.mobile.data.ThreadTurnRecovery
import com.remodex.mobile.data.ThreadTurnRecoveryAction
import com.remodex.mobile.data.ThreadTurnSnapshot
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Mirrors [CodexService+History.swift](../../../../../../../../CodexMobile/CodexMobile/Services/CodexService+History.swift).
 */
private const val INITIAL_HISTORY_TURN_LIMIT = 160
private const val OLDER_HISTORY_TURN_LIMIT = 160

internal suspend fun CodexService.syncThreadHistoryInternal(
    threadId: String,
    force: Boolean,
) {
    if (!sessionReady) return
    val tid = threadId.trim().ifEmpty { return }
    if (!force && hydratedThreadIds.contains(tid)) return
    if (!loadingHistory.add(tid)) return
    try {
        val response =
            try {
                sendThreadReadPage(
                    threadId = tid,
                    cursor = null,
                    limit = INITIAL_HISTORY_TURN_LIMIT,
                    allowLegacyFallback = true,
                )
            } catch (e: CodexServiceError.RpcFailure) {
                if (e.rpcError.isExplicitServerThreadMissing()) {
                    handleMissingThread(tid)
                    return
                }
                if (isThreadReadNotMaterialized(e)) {
                    markThreadHistoryPage(
                        tid,
                        olderCursor = null,
                        exhaustedOlderCursor = JSONValue.Null,
                        hasAuthoritativeStart = true,
                        initialLoaded = true,
                    )
                    hydratedThreadIds.add(tid)
                    return
                }
                if (e.rpcError.code == -32600 || e.rpcError.code == -32602) {
                    hydratedThreadIds.add(tid)
                    return
                }
                throw e
            }
        val resultObj = response.result as? JSONValue.Obj ?: return
        val threadEl = resultObj.map["thread"] as? JSONValue.Obj ?: return
        val decodedThread =
            runCatching {
                CodexThread.fromJsonObject(jsonObjectFromRpc(threadEl))
            }.getOrNull()
        if (decodedThread != null) {
            val merged = applyPersistedThreadRename(applyAuthoritativeProjectPathToServerThread(decodedThread))
            publishThreads(upsertThreadRow(_threads.value, merged))
        }
        val messages = ThreadHistoryDecoder.decodeFromThreadRead(tid, threadEl.map)
        if (messages.isNotEmpty()) {
            messageTimelineStore.mergeThreadHistory(tid, messages)
        }
        rehydrateRunningTurnFromThreadRead(tid, threadEl.map)
        ContextWindowUsageCodec.decodeFromThreadReadThreadObject(threadEl.map)?.let { usage ->
            applyLiveContextWindowUsage(tid, usage)
        }
        updatePaginationFromThreadRead(tid, resultObj.map, threadEl.map, initialLoad = true)
        hydratedThreadIds.add(tid)
    } catch (_: Exception) {
        // Non-fatal background hydration.
    } finally {
        loadingHistory.remove(tid)
    }
}

internal fun CodexService.rehydrateRunningTurnFromThreadRead(
    threadId: String,
    threadObject: Map<String, JSONValue>,
) {
    val tid = threadId.trim().ifEmpty { return }
    val snapshot = ThreadTurnSnapshot.fromThreadObject(threadObject)
    when (val action = ThreadTurnRecovery.actionFor(snapshot)) {
        is ThreadTurnRecoveryAction.Running -> noteTurnStarted(tid, action.turnId)
        ThreadTurnRecoveryAction.ProtectedFallback -> noteProtectedRunningFallback(tid, true)
        ThreadTurnRecoveryAction.Idle -> {
            // Avoid clearing local live state from a stale thread/read response. Reconnect resets local
            // running state first, so absence here naturally means "nothing to restore".
        }
    }
}

internal suspend fun CodexService.loadOlderThreadHistoryInternal(threadId: String) {
    if (!sessionReady) return
    val tid = threadId.trim().ifEmpty { return }
    val pageState = _threadHistoryPaginationByThread.value[tid] ?: return
    val cursor = pageState.olderCursor ?: return
    if (cursor == JSONValue.Null || pageState.hasAuthoritativeLocalHistoryStart) return
    if (tid in _loadingOlderHistoryThreadIds.value) return

    _loadingOlderHistoryThreadIds.value = _loadingOlderHistoryThreadIds.value + tid
    _olderHistoryErrorByThread.value = _olderHistoryErrorByThread.value - tid
    try {
        val response =
            sendThreadReadPage(
                threadId = tid,
                cursor = cursor,
                limit = OLDER_HISTORY_TURN_LIMIT,
                allowLegacyFallback = false,
            )
        val resultObj = response.result as? JSONValue.Obj ?: return
        val threadEl = resultObj.map["thread"] as? JSONValue.Obj ?: return
        val messages = ThreadHistoryDecoder.decodeFromThreadRead(tid, threadEl.map)
        if (messages.isNotEmpty()) {
            messageTimelineStore.mergeThreadHistory(tid, messages)
        }
        updatePaginationFromThreadRead(tid, resultObj.map, threadEl.map, initialLoad = false)
    } catch (e: Exception) {
        _olderHistoryErrorByThread.value =
            _olderHistoryErrorByThread.value + (tid to (e.message?.trim()?.takeIf { it.isNotEmpty() } ?: "Unable to load older history."))
    } finally {
        _loadingOlderHistoryThreadIds.value = _loadingOlderHistoryThreadIds.value - tid
    }
}

private suspend fun CodexService.sendThreadReadPage(
    threadId: String,
    cursor: JSONValue?,
    limit: Int,
    allowLegacyFallback: Boolean,
): com.remodex.mobile.core.model.RPCMessage {
    val fields =
        buildMap {
            put("threadId", JSONValue.Str(threadId))
            put("includeTurns", JSONValue.Bool(true))
            put("limit", JSONValue.NumLong(limit.toLong()))
            put("turnLimit", JSONValue.NumLong(limit.toLong()))
            put("pageSize", JSONValue.NumLong(limit.toLong()))
            if (cursor != null && cursor != JSONValue.Null) {
                put("cursor", cursor)
                put("turnCursor", cursor)
                put("beforeCursor", cursor)
            }
        }
    return try {
        sendRequestImpl("thread/read", JSONValue.Obj(fields))
    } catch (e: CodexServiceError.RpcFailure) {
        if (allowLegacyFallback && (e.rpcError.code == -32600 || e.rpcError.code == -32602)) {
            sendRequestImpl(
                "thread/read",
                JSONValue.Obj(
                    mapOf(
                        "threadId" to JSONValue.Str(threadId),
                        "includeTurns" to JSONValue.Bool(true),
                    ),
                ),
            )
        } else {
            throw e
        }
    }
}

private fun CodexService.updatePaginationFromThreadRead(
    threadId: String,
    resultObject: Map<String, JSONValue>,
    threadObject: Map<String, JSONValue>,
    initialLoad: Boolean,
) {
    val cursor =
        firstCursorValue(resultObject)
            ?: firstCursorValue(threadObject)
    val hasMore =
        firstBooleanValue(resultObject, "hasMore", "has_more", "hasOlder", "has_older", "hasPreviousPage")
            ?: firstBooleanValue(threadObject, "hasMore", "has_more", "hasOlder", "has_older", "hasPreviousPage")
    val exhausted = cursor == null || cursor == JSONValue.Null || hasMore == false
    markThreadHistoryPage(
        threadId = threadId,
        olderCursor = cursor,
        exhaustedOlderCursor = if (exhausted) cursor ?: JSONValue.Null else null,
        hasAuthoritativeStart = exhausted,
        initialLoaded = initialLoad || _threadHistoryPaginationByThread.value[threadId]?.initialTurnsLoaded == true,
    )
}

private fun CodexService.markThreadHistoryPage(
    threadId: String,
    olderCursor: JSONValue?,
    exhaustedOlderCursor: JSONValue?,
    hasAuthoritativeStart: Boolean,
    initialLoaded: Boolean,
) {
    val previous = _threadHistoryPaginationByThread.value[threadId] ?: ThreadHistoryPaginationState()
    _threadHistoryPaginationByThread.value =
        _threadHistoryPaginationByThread.value +
            (threadId to
                previous.copy(
                    olderCursor = olderCursor,
                    exhaustedOlderCursor = exhaustedOlderCursor ?: previous.exhaustedOlderCursor,
                    hasAuthoritativeLocalHistoryStart = hasAuthoritativeStart,
                    initialTurnsLoaded = initialLoaded,
                ))
}

private fun firstCursorValue(map: Map<String, JSONValue>): JSONValue? =
    listOf(
        "olderCursor",
        "older_cursor",
        "previousCursor",
        "previous_cursor",
        "prevCursor",
        "prev_cursor",
        "nextCursor",
        "next_cursor",
        "cursor",
    ).firstNotNullOfOrNull { key -> map[key]?.takeIf { it != JSONValue.Null } }

private fun firstBooleanValue(
    map: Map<String, JSONValue>,
    vararg keys: String,
): Boolean? = keys.firstNotNullOfOrNull { key -> map[key]?.boolValue }

private fun isThreadReadNotMaterialized(e: CodexServiceError.RpcFailure): Boolean {
    val message = e.rpcError.message.lowercase()
    return message.contains("not materialized") || message.contains("not yet materialized")
}

internal suspend fun CodexService.refreshInFlightTurnStateInternal(threadId: String): Boolean {
    if (!sessionReady) return false
    val tid = threadId.trim().ifEmpty { return false }
    val snapshot =
        try {
            fetchThreadTurnInterruptSnapshotImpl(tid)
        } catch (_: Exception) {
            return false
        }

    return when (val action = ThreadTurnRecovery.actionFor(snapshot)) {
        is ThreadTurnRecoveryAction.Running -> {
            noteTurnStarted(tid, action.turnId)
            true
        }
        ThreadTurnRecoveryAction.ProtectedFallback -> {
            noteProtectedRunningFallback(tid, true)
            true
        }
        ThreadTurnRecoveryAction.Idle -> {
            noteTurnFinished(tid)
            true
        }
    }
}

internal suspend fun CodexService.catchUpThreadAfterSelectionOrReconnect(threadId: String) {
    val tid = threadId.trim().ifEmpty { return }
    val refreshed = refreshInFlightTurnStateInternal(tid)
    val isRunning =
        _runningTurnIdByThread.value.containsKey(tid) ||
            _protectedRunningFallbackThreadIds.value.contains(tid)
    if (refreshed && isRunning) {
        runCatching { ensureThreadResumedInternal(tid, force = true) }
    } else {
        runCatching { syncThreadHistoryInternal(tid, force = false) }
    }
}

internal suspend fun CodexService.refreshInactiveRunningThreadStatesInternal(limit: Int = 3) {
    if (!sessionReady) return
    val candidates =
        RunningThreadRefreshPolicy.inactiveRunningThreadIds(
            threads = _threads.value,
            activeThreadId = _activeThreadId.value,
            runningTurnIdByThread = _runningTurnIdByThread.value,
            protectedRunningFallbackThreadIds = _protectedRunningFallbackThreadIds.value,
            limit = limit,
        )
    for (threadId in candidates) {
        refreshInFlightTurnStateInternal(threadId)
    }
}

internal fun jsonObjectFromRpc(obj: JSONValue.Obj): JsonObject =
    buildJsonObject {
        obj.map.forEach { (k, v) ->
            put(k, JSONValue.toJsonElement(v))
        }
    }

internal fun upsertThreadRow(
    list: List<CodexThread>,
    thread: CodexThread,
): List<CodexThread> {
    val i = list.indexOfFirst { it.id == thread.id }
    val merged =
        if (i >= 0) {
            val existing = list[i]
            val filled = thread.withMissingDisplayFieldsFrom(existing)
            list.mapIndexed { idx, t ->
                if (idx == i) {
                    filled.copy(syncState = existing.syncState)
                } else {
                    t
                }
            }
        } else {
            list + thread
        }
    return sortThreadsForBridge(merged)
}

internal fun sortThreadsForBridge(value: List<CodexThread>): List<CodexThread> {
    val past = Instant.EPOCH
    return value.sortedWith { lhs, rhs ->
        val l = lhs.updatedAt ?: lhs.createdAt ?: past
        val r = rhs.updatedAt ?: rhs.createdAt ?: past
        r.compareTo(l)
    }
}
