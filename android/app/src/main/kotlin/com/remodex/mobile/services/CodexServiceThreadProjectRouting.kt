package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.projectIconSystemNameFor
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Same-thread project path rebind and authoritative cwd guards. Parity:
 * [CodexService+ThreadProjectRouting.swift](../../../../../CodexMobile/CodexMobile/Services/CodexService+ThreadProjectRouting.swift).
 */
internal fun applyAuthoritativeProjectPathMerge(
    thread: CodexThread,
    authoritativeByThreadId: MutableMap<String, String>,
    treatAsServerState: Boolean,
): CodexThread {
    val tid = thread.id.trim()
    if (tid.isEmpty()) return thread
    val authRaw = authoritativeByThreadId[tid] ?: return thread
    val auth = CodexThread.normalizeProjectPath(authRaw) ?: return thread
    val threadPath = thread.normalizedProjectPath
    if (threadPath == auth) {
        if (treatAsServerState) {
            authoritativeByThreadId.remove(tid)
        }
        return thread
    }
    return thread.copy(cwd = auth)
}

internal fun confirmAuthoritativeProjectPathIfNeeded(
    threadId: String,
    observedProjectPath: String?,
    authoritativeByThreadId: MutableMap<String, String>,
) {
    val tid = threadId.trim()
    if (tid.isEmpty()) return
    if (authoritativeByThreadId[tid] == null) return
    val expected = CodexThread.normalizeProjectPath(authoritativeByThreadId[tid]) ?: return
    val observed = CodexThread.normalizeProjectPath(observedProjectPath) ?: return
    if (observed == expected) {
        authoritativeByThreadId.remove(tid)
    }
}

internal fun shouldAllowProjectRebindWithoutResume(error: Throwable): Boolean {
    val message: String? =
        when (error) {
            is CodexServiceError.RpcFailure -> error.rpcError.message.lowercase()
            is CodexServiceError -> error.toString().lowercase()
            else -> (error.message ?: error.toString()).lowercase()
        }
    if (message == null) return false
    return message.contains("no rollout found") ||
        message.contains("no rollout file found") ||
        (message.contains("rollout") && message.contains("is empty"))
}

internal fun CodexService.applyAuthoritativeProjectPathToServerThread(thread: CodexThread) =
    applyAuthoritativeProjectPathMerge(thread, authoritativeProjectPathByThreadId, treatAsServerState = true)

internal fun CodexService.beginAuthoritativeProjectPathTransition(
    threadId: String,
    projectPath: String,
) {
    val tid = threadId.trim()
    if (tid.isEmpty()) return
    val p = CodexThread.normalizeProjectPath(projectPath) ?: return
    authoritativeProjectPathByThreadId[tid] = p
}

internal fun CodexService.currentAuthoritativeProjectPathForImpl(threadId: String): String? {
    val tid = threadId.trim()
    if (tid.isEmpty()) return null
    return CodexThread.normalizeProjectPath(authoritativeProjectPathByThreadId[tid])
}

internal fun CodexService.associatedManagedWorktreePathForImpl(threadId: String): String? {
    val tid = threadId.trim()
    if (tid.isEmpty()) return null
    return CodexThread.normalizeProjectPath(associatedManagedWorktreePathByThreadId[tid])
}

internal fun CodexService.rememberAssociatedManagedWorktreePathIfWorktree(
    projectPath: String,
    forThreadId: String,
) {
    val tid = forThreadId.trim()
    if (tid.isEmpty()) return
    val norm = CodexThread.normalizeProjectPath(projectPath) ?: return
    if (projectIconSystemNameFor(norm) == "arrow.triangle.branch") {
        associatedManagedWorktreePathByThreadId[tid] = norm
        sessionPersistence.saveAssociatedManagedWorktreePath(tid, norm)
    }
}

private fun CodexService.restoreAssociatedManagedWorktreePath(
    path: String?,
    forThreadId: String,
) {
    val tid = forThreadId.trim()
    if (tid.isEmpty()) return
    if (path == null) {
        associatedManagedWorktreePathByThreadId.remove(tid)
        sessionPersistence.removeAssociatedManagedWorktreePath(tid)
    } else {
        val n = CodexThread.normalizeProjectPath(path)
        if (n == null) {
            associatedManagedWorktreePathByThreadId.remove(tid)
            sessionPersistence.removeAssociatedManagedWorktreePath(tid)
        } else {
            associatedManagedWorktreePathByThreadId[tid] = n
            sessionPersistence.saveAssociatedManagedWorktreePath(tid, n)
        }
    }
}

internal fun CodexService.requestImmediateThreadListSync() {
    scope.launch(Dispatchers.IO) {
        if (sessionReady) {
            runCatching { refreshThreadsInternal() }
        }
    }
}

internal suspend fun CodexService.moveThreadToProjectPathImpl(
    threadId: String,
    projectPath: String,
): CodexThread {
    if (!sessionReady) throw CodexServiceError.Disconnected
    val normalizedThreadId = threadId.trim()
    if (normalizedThreadId.isEmpty()) {
        throw CodexServiceError.InvalidInput("Thread id is required.")
    }
    val normalizedProjectPath = CodexThread.normalizeProjectPath(projectPath)
        ?: throw CodexServiceError.InvalidInput("A valid project path is required.")
    var current = _threads.value.find { it.id == normalizedThreadId }
        ?: throw CodexServiceError.InvalidInput("Thread not found.")
    val previousThread = current
    val previousAuth =
        authoritativeProjectPathByThreadId[normalizedThreadId]
            ?.let { CodexThread.normalizeProjectPath(it) }
    val previousManaged =
        associatedManagedWorktreePathByThreadId[normalizedThreadId]
            ?.let { CodexThread.normalizeProjectPath(it) }
    val wasResumed = resumedThreadIds.contains(normalizedThreadId)

    beginAuthoritativeProjectPathTransition(normalizedThreadId, normalizedProjectPath)
    rememberAssociatedManagedWorktreePathIfWorktree(normalizedProjectPath, normalizedThreadId)
    val now = Instant.now()
    current = current.copy(cwd = normalizedProjectPath, updatedAt = now)
    publishThreads(upsertThreadRow(_threads.value, current))
    _activeThreadId.value = normalizedThreadId
    sessionPersistence.saveLastActiveThreadId(normalizedThreadId)
    resumedThreadIds.remove(normalizedThreadId)

    try {
        val resumed = ensureThreadResumedInternal(normalizedThreadId, force = true, preferredProjectPath = normalizedProjectPath)
        val observed = resumed?.normalizedProjectPath
        confirmAuthoritativeProjectPathIfNeeded(
            normalizedThreadId,
            observed,
            authoritativeProjectPathByThreadId,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (shouldAllowProjectRebindWithoutResume(e)) {
            requestImmediateThreadListSync()
            return _threads.value.find { it.id == normalizedThreadId } ?: current
        }
        publishThreads(upsertThreadRow(_threads.value, previousThread))
        if (previousAuth != null) {
            authoritativeProjectPathByThreadId[normalizedThreadId] = previousAuth
        } else {
            authoritativeProjectPathByThreadId.remove(normalizedThreadId)
        }
        restoreAssociatedManagedWorktreePath(previousManaged, normalizedThreadId)
        if (wasResumed) {
            resumedThreadIds.add(normalizedThreadId)
        } else {
            resumedThreadIds.remove(normalizedThreadId)
        }
        requestImmediateThreadListSync()
        throw e
    }

    requestImmediateThreadListSync()
    return _threads.value.find { it.id == normalizedThreadId } ?: current
}
