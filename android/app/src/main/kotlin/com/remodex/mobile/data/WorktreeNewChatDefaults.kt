package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexThread
import java.time.Instant

/**
 * Resolves repo root and base branch for [WorktreeFlowCoordinator.startNewManagedWorktreeChat] from
 * thread list state, without forking or extra navigation. Branch loading uses [loadGitBranchesWithStatus]
 * at the call site; these helpers use the resulting [GitBranchDisplaySummary].
 */
object WorktreeNewChatDefaults {
    /**
     * Prefers the active thread's local Git working directory, then the most recently touched thread
     * that has a non-cloud path usable for [GitActionsService] (excludes `_default`).
     */
    fun baseProjectPath(
        activeThreadId: String?,
        threads: List<CodexThread>,
    ): String? {
        val byId = activeThreadId?.let { id -> threads.firstOrNull { it.id == id } }
        byId?.gitWorkingDirectoryForGitActions()?.let { return it }
        return threads
            .sortedByDescending { t ->
                t.updatedAt ?: t.createdAt ?: Instant.EPOCH
            }
            .firstOrNull { it.gitWorkingDirectoryForGitActions() != null }
            ?.gitWorkingDirectoryForGitActions()
    }

    /**
     * Prefers current checkout, then remote default, then the first local branch not checked out
     * elsewhere, then any first branch in the sorted list.
     */
    fun baseBranch(summary: GitBranchDisplaySummary): String? {
        summary.currentBranch?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        summary.defaultBranch?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val elsewhere = summary.branchesCheckedOutElsewhere.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        summary.branches.firstOrNull { it !in elsewhere }?.let { return it }
        return summary.branches.firstOrNull()
    }
}
