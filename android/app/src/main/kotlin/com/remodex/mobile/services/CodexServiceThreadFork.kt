package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexBridgeUpdatePrompt
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.CodexThreadSyncState
import com.remodex.mobile.core.model.JSONValue
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Native `thread/fork` (parity [CodexService+ThreadFork.swift] / [CodexService+ThreadForkCompatibility.swift]).
 * Sends only `{ threadId }` in the JSON-RPC params, matching iOS unit expectations.
 */
internal suspend fun CodexService.forkThreadForRepository(
    sourceThreadId: String,
    targetProjectPath: String?,
): CodexThread =
    withContext(Dispatchers.IO) {
        forkThreadInternal(sourceThreadId, targetProjectPath)
    }

internal suspend fun CodexService.forkThreadInternal(
    sourceThreadId: String,
    targetProjectPath: String?,
): CodexThread {
    if (!sessionReady) throw CodexServiceError.Disconnected
    val sourceId = sourceThreadId.trim()
    if (sourceId.isEmpty()) throw CodexServiceError.InvalidInput("Missing thread id")
    val sourceThread =
        _threads.value.find { it.id == sourceId }
            ?: throw CodexServiceError.InvalidInput("Thread not found.")

    val explicitTarget =
        targetProjectPath?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            CodexThread.normalizeProjectPath(raw) ?: raw
        }
    val resolvedProjectPath = explicitTarget ?: sourceThread.gitWorkingDirectory

    val sourceModel = sourceThread.model?.trim()?.takeIf { it.isNotEmpty() }
    val sourceModelProvider = sourceThread.modelProvider?.trim()?.takeIf { it.isNotEmpty() }

    val response =
        try {
            sendRequestWithApprovalPolicyFallback(
                "thread/fork",
                JSONValue.Obj(mapOf("threadId" to JSONValue.Str(sourceId))),
            )
        } catch (e: Throwable) {
            if (consumeUnsupportedThreadFork(e)) {
                throw CodexServiceError.InvalidInput(
                    "This computer bridge does not support native thread forks yet. Update Remodex on your computer and retry.",
                )
            }
            throw e
        }

    val resultObj = response.result as? JSONValue.Obj
        ?: throw CodexServiceError.InvalidResponse("thread/fork missing result")
    val threadEl =
        resultObj.map["thread"] as? JSONValue.Obj
            ?: throw CodexServiceError.InvalidResponse("thread/fork response missing thread")

    var decoded =
        runCatching {
            CodexThread.fromJsonObject(jsonObjectFromRpc(threadEl))
        }.getOrElse {
            throw CodexServiceError.InvalidResponse("thread/fork thread decode failed")
        }

    val now = Instant.now()
    decoded =
        decoded.copy(
            syncState = CodexThreadSyncState.live,
            forkedFromThreadId = decoded.forkedFromThreadId ?: sourceId,
            createdAt = decoded.createdAt ?: now,
            updatedAt = decoded.updatedAt ?: now,
        )

    if (resolvedProjectPath != null) {
        decoded = decoded.copy(cwd = resolvedProjectPath)
    } else if (decoded.normalizedProjectPath == null) {
        val responseCwd = (resultObj.map["cwd"] as? JSONValue.Str)?.value?.trim()?.takeIf { it.isNotEmpty() }
        val n = responseCwd?.let { CodexThread.normalizeProjectPath(it) ?: it }
        if (n != null) {
            decoded = decoded.copy(cwd = n)
        }
    }

    if (decoded.model == null && sourceModel != null) {
        decoded = decoded.copy(model = sourceModel)
    }
    if (decoded.modelProvider == null && sourceModelProvider != null) {
        decoded = decoded.copy(modelProvider = sourceModelProvider)
    }

    var patched = applyAuthoritativeProjectPathToServerThread(decoded)
    publishThreads(upsertThreadRow(_threads.value, patched))

    resolvedProjectPath?.let { beginAuthoritativeProjectPathTransition(patched.id, it) }

    patched.normalizedProjectPath?.let { norm ->
        rememberAssociatedManagedWorktreePathIfWorktree(norm, patched.id)
    }

    val forkId = patched.id.trim()
    _activeThreadId.value = forkId
    sessionPersistence.saveLastActiveThreadId(forkId)
    requestImmediateThreadListSync()

    val delaysMs = listOf(0L, 250L, 800L)
    var hydrated: CodexThread? = null
    for (delayMs in delaysMs) {
        if (delayMs > 0) delay(delayMs)
        try {
            ensureThreadResumedInternal(
                threadId = forkId,
                force = true,
                preferredProjectPath = resolvedProjectPath,
                modelIdentifierOverride = sourceModel,
            )
        } catch (e: Throwable) {
            if (!shouldAllowProjectRebindWithoutResume(e)) throw e
            continue
        }
        syncThreadHistoryInternal(forkId, force = true)
        val hasMessages =
            messageTimelineStore.messagesByThread.value[forkId].orEmpty().isNotEmpty()
        if (hydratedThreadIds.contains(forkId) || hasMessages) {
            hydrated = _threads.value.find { it.id == forkId }
            break
        }
    }

    val base = hydrated ?: _threads.value.find { it.id == forkId } ?: patched
    val finalThread = patchForkThreadClientMetadata(base, resolvedProjectPath, sourceModel, sourceModelProvider)
    publishThreads(upsertThreadRow(_threads.value, finalThread))
    resumedThreadIds.add(forkId)
    return finalThread
}

private fun CodexService.patchForkThreadClientMetadata(
    thread: CodexThread,
    targetProjectPath: String?,
    sourceModelIdentifier: String?,
    sourceModelProvider: String?,
): CodexThread {
    var t = thread
    var didPatch = false
    val target = targetProjectPath?.let { CodexThread.normalizeProjectPath(it) ?: it }
    if (target != null && t.normalizedProjectPath != target) {
        t = t.copy(cwd = target)
        didPatch = true
    }
    if (t.model == null && sourceModelIdentifier != null) {
        t = t.copy(model = sourceModelIdentifier)
        didPatch = true
    }
    if (t.modelProvider == null && sourceModelProvider != null) {
        t = t.copy(modelProvider = sourceModelProvider)
        didPatch = true
    }
    if (!didPatch) return t
    var merged = applyAuthoritativeProjectPathToServerThread(t)
    publishThreads(upsertThreadRow(_threads.value, merged))
    merged.normalizedProjectPath?.let { norm ->
        rememberAssociatedManagedWorktreePathIfWorktree(norm, merged.id)
    }
    return merged
}

internal fun CodexService.consumeUnsupportedThreadFork(error: Throwable): Boolean {
    if (!shouldTreatAsUnsupportedThreadFork(error)) return false
    markThreadForkUnsupportedForCurrentBridge()
    return true
}

private fun shouldTreatAsUnsupportedThreadFork(error: Throwable): Boolean {
    val rpc =
        when (error) {
            is CodexServiceError.RpcFailure -> error.rpcError
            else -> return false
        }
    if (rpc.code == -32601) return true
    val message = rpc.message.lowercase()
    val mentionsUnsupportedMethod =
        message.contains("method not found") ||
            message.contains("unknown method") ||
            message.contains("not implemented") ||
            message.contains("does not support")
    val mentionsForkSpecificUnsupported =
        (message.contains("thread/fork") || message.contains("thread fork")) &&
            (message.contains("unsupported") || message.contains("not supported"))
    if (rpc.code != -32600 && rpc.code != -32602 && rpc.code != -32000) {
        return mentionsUnsupportedMethod || mentionsForkSpecificUnsupported
    }
    return mentionsUnsupportedMethod || mentionsForkSpecificUnsupported
}

private fun CodexService.markThreadForkUnsupportedForCurrentBridge() {
    supportsThreadFork = false
    if (hasPresentedThreadForkBridgeUpdatePrompt) return
    hasPresentedThreadForkBridgeUpdatePrompt = true
    _bridgeUpdatePrompt.value =
        CodexBridgeUpdatePrompt(
            title = "Update Remodex on your computer to use /fork",
            message =
                "This computer bridge does not support native conversation forks yet. " +
                    "Update the Remodex npm package to use /fork and worktree fork flows.",
            command = "npm install -g remodex@latest",
        )
}
