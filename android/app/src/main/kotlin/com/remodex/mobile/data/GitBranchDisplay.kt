package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.GitBranchesWithStatusResult
import com.remodex.mobile.services.GitActionsError
import com.remodex.mobile.services.GitActionsService

/**
 * Resolves the bridge `cwd` for git JSON-RPC for this thread, or null when no reliable local project
 * path is bound (parity iOS: pseudo buckets like `_default` are not Git roots).
 */
fun CodexThread?.gitWorkingDirectoryForGitActions(): String? {
    val path = this?.normalizedProjectPath ?: return null
    if (path == "_default") return null
    return path
}

data class GitBranchDisplaySummary(
    val currentBranch: String?,
    val defaultBranch: String?,
    val branchesCheckedOutElsewhere: List<String>,
    /** Local branch names for the interactive picker (sorted). */
    val branches: List<String>,
    val worktreePathByBranch: Map<String, String>,
    val isDirty: Boolean,
)

object GitBranchDisplayMapper {
    fun summaryFrom(result: GitBranchesWithStatusResult): GitBranchDisplaySummary {
        val elsewhere =
            result.branchesCheckedOutElsewhere
                .filter { it.isNotBlank() }
                .sorted()
        val dirty = result.status?.isDirty == true
        val branches =
            result.branches
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        return GitBranchDisplaySummary(
            currentBranch = result.currentBranch?.takeIf { it.isNotBlank() },
            defaultBranch = result.defaultBranch?.takeIf { it.isNotBlank() },
            branchesCheckedOutElsewhere = elsewhere,
            branches = branches,
            worktreePathByBranch = result.worktreePathByBranch,
            isDirty = dirty,
        )
    }

    fun userVisibleMessage(throwable: Throwable): String =
        when (throwable) {
            is GitActionsError.Disconnected -> "Not connected to bridge."
            is GitActionsError.MissingWorkingDirectory ->
                "The selected local folder is not available on this Mac."
            is GitActionsError.InvalidResponse -> "Invalid response from bridge."
            is GitActionsError.BridgeFailure ->
                throwable.message?.takeIf { it.isNotBlank() }
                    ?: "Git operation failed."
            else ->
                throwable.message?.takeIf { it.isNotBlank() }
                    ?: "Could not load Git branches."
        }
}

suspend fun loadGitBranchesWithStatus(
    repository: CodexRepository,
    cwd: String,
): Result<GitBranchesWithStatusResult> =
    runCatching {
        GitActionsService(repository, cwd).branchesWithStatus()
    }
