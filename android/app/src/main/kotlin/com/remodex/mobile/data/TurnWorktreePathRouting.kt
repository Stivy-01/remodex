package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.CodexThreadSyncState
import java.io.File
import java.time.Instant

/**
 * Path normalization / comparison for Git worktrees (parity CodexMobile [TurnWorktreeRouting]).
 */
object TurnWorktreePathRouting {

    /** Stable comparison key for cwd / bridge-reported repo paths when matching threads. */
    fun comparableGitProjectPath(raw: String?): String? {
        val trimmed = CodexThread.normalizeProjectPath(raw) ?: return null
        return runCatching {
            File(trimmed).canonicalFile.absolutePath
                .replace('\\', '/')
                .trimEnd('/')
        }.getOrElse {
            trimmed.replace('\\', '/').trimEnd('/')
        }
    }

    /** Prefer an existing live thread already bound to this checkout (parity iOS [liveThreadForCheckedOutElsewhereBranch]). */
    fun liveThreadAtProjectPath(
        projectPath: String,
        threads: List<CodexThread>,
        currentThreadId: String,
    ): CodexThread? {
        val resolved = comparableGitProjectPath(projectPath) ?: return null
        val matches =
            threads.filter { thread ->
                thread.syncState == CodexThreadSyncState.live &&
                    thread.id != currentThreadId &&
                    comparableGitProjectPath(thread.normalizedProjectPath) == resolved
            }
        if (matches.isEmpty()) return null
        return matches.maxWith(
            compareBy<CodexThread> { it.updatedAt ?: Instant.EPOCH }.thenBy { it.id },
        )
    }
}
