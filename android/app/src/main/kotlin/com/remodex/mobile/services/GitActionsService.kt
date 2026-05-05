package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.GitBranchesWithStatusResult
import com.remodex.mobile.core.model.GitCheckoutResult
import com.remodex.mobile.core.model.GitCommitResult
import com.remodex.mobile.core.model.GitCreateBranchResult
import com.remodex.mobile.core.model.GitCreateManagedWorktreeResult
import com.remodex.mobile.core.model.GitGeneratedCommitMessageResult
import com.remodex.mobile.core.model.GitGeneratedPullRequestDraftResult
import com.remodex.mobile.core.model.GitInitResult
import com.remodex.mobile.core.model.GitManagedHandoffTransferResult
import com.remodex.mobile.core.model.GitPullResult
import com.remodex.mobile.core.model.GitPushResult
import com.remodex.mobile.core.model.GitRemoteUrlResult
import com.remodex.mobile.core.model.GitRepoDiffResult
import com.remodex.mobile.core.model.GitRepoSyncResult
import com.remodex.mobile.core.model.GitResetResult
import com.remodex.mobile.core.model.GitWorktreeChangeTransferMode
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.data.CodexRepository

/**
 * Executes git JSON-RPC methods via the bridge, matching
 * [CodexMobile/CodexMobile/Services/GitActionsService.swift] `cwd` scoping.
 */
sealed class GitActionsError(
    message: String,
) : Exception(message) {
    data object Disconnected : GitActionsError("Not connected to bridge.")

    data object InvalidResponse : GitActionsError("Invalid response from bridge.")

    data object MissingWorkingDirectory :
        GitActionsError("The selected local folder is not available on this Mac.")

    class BridgeFailure(
        val errorCode: String?,
        message: String,
    ) : GitActionsError(message)
}

class GitActionsService(
    private val repository: CodexRepository,
    private val workingDirectory: String?,
) {
    suspend fun status(): GitRepoSyncResult {
        val json = request("git/status")
        return GitRepoSyncResult.fromJson(json)
    }

    suspend fun branchesWithStatus(): GitBranchesWithStatusResult {
        val json = request("git/branchesWithStatus")
        return GitBranchesWithStatusResult.fromJson(json)
    }

    suspend fun checkout(branch: String): GitCheckoutResult {
        val json =
            request(
                "git/checkout",
                mapOf("branch" to JSONValue.Str(branch)),
            )
        return GitCheckoutResult.fromJson(json)
    }

    suspend fun createBranch(branch: String): GitCreateBranchResult {
        val json =
            request(
                "git/createBranch",
                mapOf(
                    "name" to JSONValue.Str(branch),
                    "prefixRemodex" to JSONValue.Bool(false),
                ),
            )
        return GitCreateBranchResult.fromJson(json)
    }

    suspend fun diff(): GitRepoDiffResult {
        val json = request("git/diff")
        return GitRepoDiffResult.fromJson(json)
    }

    suspend fun initializeRepository(): GitInitResult {
        val json = request("git/init")
        return GitInitResult.fromJson(json)
    }

    suspend fun pull(): GitPullResult {
        val json = request("git/pull")
        return GitPullResult.fromJson(json)
    }

    suspend fun commit(message: String? = null): GitCommitResult {
        val extra =
            if (message.isNullOrBlank()) {
                emptyMap()
            } else {
                mapOf("message" to JSONValue.Str(message))
            }
        val json = request("git/commit", extra)
        return GitCommitResult.fromJson(json)
    }

    suspend fun generateCommitMessage(model: String? = null): GitGeneratedCommitMessageResult {
        val extra =
            if (model.isNullOrBlank()) {
                emptyMap()
            } else {
                mapOf("model" to JSONValue.Str(model))
            }
        val json = request("git/generateCommitMessage", extra)
        return GitGeneratedCommitMessageResult.fromJson(json)
    }

    suspend fun generatePullRequestDraft(
        baseBranch: String? = null,
        model: String? = null,
    ): GitGeneratedPullRequestDraftResult {
        val extra =
            buildMap {
                if (!baseBranch.isNullOrBlank()) put("baseBranch", JSONValue.Str(baseBranch))
                if (!model.isNullOrBlank()) put("model", JSONValue.Str(model))
            }
        val json = request("git/generatePullRequestDraft", extra)
        return GitGeneratedPullRequestDraftResult.fromJson(json)
    }

    suspend fun push(): GitPushResult {
        val json = request("git/push")
        return GitPushResult.fromJson(json)
    }

    /** Parity with iOS `git/resetToRemote` + confirm discard runtime changes. */
    suspend fun resetToRemoteDiscardRuntime(): GitResetResult {
        val json =
            request(
                "git/resetToRemote",
                mapOf("confirm" to JSONValue.Str("discard_runtime_changes")),
            )
        return GitResetResult.fromJson(json)
    }

    suspend fun remoteUrl(): GitRemoteUrlResult {
        val json = request("git/remoteUrl")
        return GitRemoteUrlResult.fromJson(json)
    }

    suspend fun createManagedWorktree(
        baseBranch: String,
        changeTransfer: GitWorktreeChangeTransferMode = GitWorktreeChangeTransferMode.move,
    ): GitCreateManagedWorktreeResult {
        val json =
            request(
                "git/createManagedWorktree",
                mapOf(
                    "baseBranch" to JSONValue.Str(baseBranch),
                    "changeTransfer" to JSONValue.Str(changeTransfer.name),
                ),
            )
        return GitCreateManagedWorktreeResult.fromJson(json)
    }

    suspend fun transferManagedHandoff(targetProjectPath: String): GitManagedHandoffTransferResult {
        val json =
            request(
                "git/transferManagedHandoff",
                mapOf("targetPath" to JSONValue.Str(targetProjectPath)),
            )
        return GitManagedHandoffTransferResult.fromJson(json)
    }

    suspend fun removeManagedWorktree(branch: String? = null) {
        val trimmed = branch?.trim().orEmpty()
        val extra =
            if (trimmed.isNotEmpty()) {
                mapOf("branch" to JSONValue.Str(trimmed))
            } else {
                emptyMap()
            }
        request("git/removeWorktree", extra)
    }

    private suspend fun request(
        method: String,
        extraParams: Map<String, JSONValue> = emptyMap(),
    ): Map<String, JSONValue> {
        val wd = normalizedWorkingDirectory(workingDirectory)
            ?: throw GitActionsError.MissingWorkingDirectory

        val scoped = extraParams + ("cwd" to JSONValue.Str(wd))

        return try {
            val response = repository.sendRequest(method, JSONValue.Obj(scoped))
            response.result?.objectValue ?: throw GitActionsError.InvalidResponse
        } catch (e: CodexServiceError) {
            throw mapCodexError(e)
        }
    }

    private companion object {
        fun normalizedWorkingDirectory(raw: String?): String? {
            val t = raw?.trim().orEmpty()
            return t.ifEmpty { null }
        }

        fun mapCodexError(e: CodexServiceError): GitActionsError =
            when (e) {
                CodexServiceError.Disconnected -> GitActionsError.Disconnected
                is CodexServiceError.RpcFailure -> {
                    val code = e.rpcError.data?.objectValue?.get("errorCode")?.stringValue
                    GitActionsError.BridgeFailure(
                        errorCode = code,
                        message = userMessageForGit(code, e.rpcError.message),
                    )
                }
                else ->
                    GitActionsError.BridgeFailure(
                        errorCode = null,
                        message = e.message ?: "Git operation failed.",
                    )
            }

        fun userMessageForGit(
            code: String?,
            fallback: String,
        ): String {
            val f = fallback.ifBlank { "Git operation failed." }
            return when (code) {
                "nothing_to_commit" -> "Nothing to commit."
                "nothing_to_push" -> "Nothing to push."
                "push_rejected" -> "Push rejected. Pull changes first."
                "branch_is_main" -> "Cannot operate on the main branch."
                "protected_branch" -> "This branch is protected."
                "branch_behind_remote" -> "Branch is behind remote. Pull first."
                "dirty_and_behind" -> "Uncommitted changes and branch is behind remote."
                "checkout_conflict_dirty_tree" ->
                    "Cannot switch branches: tracked local changes would be overwritten."
                "checkout_conflict_untracked_collision" ->
                    "Cannot switch branches: untracked files would be overwritten."
                "checkout_branch_in_other_worktree" ->
                    "Cannot switch branches: this branch is already open in another worktree."
                "pull_conflict" -> "Pull failed due to conflicts."
                "branch_exists" -> f
                "invalid_branch_name" -> f
                "missing_branch_name" -> "Branch name is required."
                "branch_not_found" -> f
                "missing_branch" -> f
                "missing_base_branch" -> f
                "branch_already_open_here" -> f
                "branch_in_other_worktree" -> f
                "confirmation_required" -> "Confirmation is required for this action."
                "stash_pop_conflict" -> "Stash pop failed due to conflicts."
                "missing_local_repo" -> "Run `remodex up` from an existing local directory first."
                "not_initialized" -> "This folder is not a Git repository yet."
                "missing_working_directory" ->
                    "The selected local folder is not available on this Mac."
                "cannot_remove_local_checkout" -> f
                "unmanaged_worktree" -> f
                "worktree_cleanup_failed" -> f
                "handoff_target_dirty" ->
                    handoffFallback(
                        f,
                        "The handoff destination already has uncommitted changes. Clean it up before moving this thread there.",
                    )
                "handoff_target_mismatch" ->
                    handoffFallback(f, "The selected handoff destination belongs to a different checkout.")
                "handoff_transfer_failed" ->
                    handoffFallback(f, "Could not move local changes into the handoff destination.")
                "missing_handoff_source" ->
                    handoffFallback(f, "The current handoff source is not available on this Mac.")
                "missing_handoff_target" ->
                    handoffFallback(f, "The destination for this handoff is not available on this Mac.")
                null -> f
                else -> f
            }
        }

        fun handoffFallback(
            fallback: String,
            handoffMessage: String,
        ): String = fallback.takeUnless { it == "Git operation failed." } ?: handoffMessage
    }
}
