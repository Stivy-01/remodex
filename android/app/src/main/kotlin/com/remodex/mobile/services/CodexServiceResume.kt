package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.CodexThreadSyncState
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.data.ThreadHistoryDecoder

/**
 * Mirrors [CodexService.ensureThreadResumed] in
 * [CodexService+ThreadsTurns.swift](../../../../../../../../CodexMobile/CodexMobile/Services/CodexService+ThreadsTurns.swift).
 */
internal suspend fun CodexService.ensureThreadResumedInternal(
    threadId: String,
    force: Boolean = false,
    preferredProjectPath: String? = null,
    modelIdentifierOverride: String? = null,
): CodexThread? {
    if (!sessionReady) throw CodexServiceError.Disconnected
    val tid = threadId.trim()
    if (tid.isEmpty()) return null
    if (!force && resumedThreadIds.contains(tid)) {
        return _threads.value.find { it.id == tid }
    }

    val normalizedPreferred =
        CodexThread.normalizeProjectPath(preferredProjectPath?.trim()?.takeIf { it.isNotEmpty() })
    val cwd = normalizedPreferred ?: _threads.value.find { it.id == tid }?.gitWorkingDirectory
    val model = modelIdentifierOverride?.trim()?.takeIf { it.isNotEmpty() }

    val camelParams =
        buildMap {
            put("threadId", JSONValue.Str(tid))
            cwd?.let { put("cwd", JSONValue.Str(it)) }
            model?.let { put("model", JSONValue.Str(it)) }
        }

    val response =
        try {
            sendRequestWithSandboxAndApprovalFallback("thread/resume", JSONValue.Obj(camelParams))
        } catch (e: CodexServiceError.RpcFailure) {
            if (shouldRetryThreadResumeSnakeCase(e)) {
                val snake =
                    buildMap {
                        put("thread_id", JSONValue.Str(tid))
                        cwd?.let { put("cwd", JSONValue.Str(it)) }
                        model?.let { put("model", JSONValue.Str(it)) }
                    }
                sendRequestWithSandboxAndApprovalFallback("thread/resume", JSONValue.Obj(snake))
            } else {
                throw e
            }
        }

    val result = response.result
    if (result == null || result !is JSONValue.Obj) {
        resumedThreadIds.add(tid)
        return null
    }

    val threadEl = result.map["thread"] as? JSONValue.Obj
    if (threadEl != null) {
        val decoded =
            runCatching {
                CodexThread.fromJsonObject(jsonObjectFromRpc(threadEl))
            }.getOrNull()
        if (decoded != null) {
            var live = decoded.copy(syncState = CodexThreadSyncState.live)
            live = applyAuthoritativeProjectPathToServerThread(live)
            publishThreads(upsertThreadRow(_threads.value, live))
            confirmAuthoritativeProjectPathIfNeeded(
                tid,
                live.normalizedProjectPath,
                authoritativeProjectPathByThreadId,
            )
            val messages = ThreadHistoryDecoder.decodeFromThreadRead(tid, threadEl.map)
            if (messages.isNotEmpty()) {
                messageTimelineStore.mergeThreadHistory(tid, messages)
            }
            rehydrateRunningTurnFromThreadRead(tid, threadEl.map)
            hydratedThreadIds.add(tid)
            resumedThreadIds.add(tid)
            return live
        }
    } else {
        val i = _threads.value.indexOfFirst { it.id == tid }
        if (i >= 0) {
            publishThreads(
                _threads.value.mapIndexed { idx, t ->
                    if (idx == i) {
                        t.copy(syncState = CodexThreadSyncState.live)
                    } else {
                        t
                    }
                },
            )
        }
    }

    hydratedThreadIds.add(tid)
    resumedThreadIds.add(tid)
    return _threads.value.find { it.id == tid }
}

private fun shouldRetryThreadResumeSnakeCase(e: CodexServiceError.RpcFailure): Boolean {
    val c = e.rpcError.code
    if (c != -32600 && c != -32602) return false
    val m = e.rpcError.message.lowercase()
    val hints =
        listOf(
            "threadid",
            "thread_id",
            "cwd",
            "model",
            "unknown field",
            "missing field",
            "invalid",
        )
    return hints.any { m.contains(it) }
}
