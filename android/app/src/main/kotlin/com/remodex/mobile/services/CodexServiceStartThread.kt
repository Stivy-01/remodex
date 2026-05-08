package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.RPCMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mirrors [CodexService.startThreadImpl] in
 * [CodexService+ThreadsTurns.swift](../../../../../../../../CodexMobile/CodexMobile/Services/CodexService+ThreadsTurns.swift).
 */
internal suspend fun CodexService.startThreadInternal(
    model: String? = null,
    cwd: String? = null,
    serviceTier: String? = null,
): CodexThread {
    if (!sessionReady) throw CodexServiceError.Disconnected
    val normalizedCwd = CodexThread.normalizeProjectPath(cwd?.trim()?.takeIf { it.isNotEmpty() })
    val resolvedModel =
        model?.trim()?.takeIf { it.isNotEmpty() }
            ?: selectedModelOption()?.model?.trim()?.takeIf { it.isNotEmpty() }
    val resolvedServiceTier =
        serviceTier?.trim()?.takeIf { it.isNotEmpty() }
            ?: _selectedServiceTier.value?.name
    var includesServiceTier = shouldWireServiceTier(supportsServiceTier, resolvedServiceTier != null)
    var threadStartRpc: RPCMessage? = null
    while (true) {
        val params =
            buildMap {
                resolvedModel?.let { put("model", JSONValue.Str(it)) }
                normalizedCwd?.let { put("cwd", JSONValue.Str(it)) }
                if (includesServiceTier) {
                    resolvedServiceTier?.let { put("serviceTier", JSONValue.Str(it)) }
                }
            }
        val payload =
            if (params.isEmpty()) JSONValue.Obj(emptyMap()) else JSONValue.Obj(params)
        try {
            threadStartRpc = sendRequestWithSandboxAndApprovalFallback("thread/start", payload)
            break
        } catch (e: Throwable) {
            if (includesServiceTier && shouldRetryTurnStartWithoutServiceTier(e)) {
                markServiceTierUnsupportedForCurrentBridge()
                includesServiceTier = false
                continue
            }
            throw e
        }
    }
    val result =
        threadStartRpc?.result ?: throw CodexServiceError.InvalidResponse("thread/start missing result")
    val resultObj = result as? JSONValue.Obj ?: throw CodexServiceError.InvalidResponse("thread/start result not object")
    val threadEl =
        resultObj.map["thread"] as? JSONValue.Obj
            ?: throw CodexServiceError.InvalidResponse("thread/start response missing thread")
    val decoded =
        runCatching {
            CodexThread.fromJsonObject(jsonObjectFromRpc(threadEl))
        }.getOrElse {
            throw CodexServiceError.InvalidResponse("thread/start thread decode failed")
        }
    val patched = applyPreferredProjectFallback(decoded, normalizedCwd)
    publishThreads(upsertThreadRow(_threads.value, patched))
    _activeThreadId.value = patched.id
    sessionPersistence.saveLastActiveThreadId(patched.id)
    resumedThreadIds.add(patched.id)
    runCatching { syncThreadHistoryInternal(patched.id, force = true) }
    return patched
}

private fun applyPreferredProjectFallback(
    thread: CodexThread,
    preferred: String?,
): CodexThread {
    if (thread.normalizedProjectPath != null || preferred == null) return thread
    return thread.copy(cwd = preferred)
}

suspend fun CodexService.startThreadForRepository(
    model: String?,
    cwd: String?,
    serviceTier: String?,
): CodexThread =
    withContext(Dispatchers.IO) {
        startThreadInternal(model, cwd, serviceTier)
    }
