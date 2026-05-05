package com.remodex.mobile.data

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.GitWorktreeChangeTransferMode
import com.remodex.mobile.services.GitActionsService
import com.remodex.mobile.services.GitActionsError

/**
 * Orchestrates managed-worktree creation + thread start, and managed handoff + project rebind.
 * Parity: [CodexMobile/CodexMobile/Services/Coordination/WorktreeFlowCoordinator.swift] (subset for J.7c).
 *
 * UI and branch resolution stay out of this layer; callers supply [baseProjectPath] and [baseBranch].
 */
class WorktreeFlowCoordinator(
    private val repository: CodexRepository,
) {
    /**
     * Forks [sourceThreadId] via native `thread/fork`; when [targetProjectPath] is provided, the fork
     * is patched/rebound to that local project path by the service layer.
     */
    suspend fun forkThreadToProjectPath(
        sourceThreadId: String,
        targetProjectPath: String? = null,
    ): CodexThread = repository.forkThread(sourceThreadId = sourceThreadId, targetProjectPath = targetProjectPath)

    /**
     * Creates a managed worktree under [baseProjectPath] from [baseBranch], then starts a thread with
     * [CodexRepository.startThread] using the new worktree as `cwd`.
     *
     * On [startThread] failure, removes the new worktree via [git/removeWorktree] when cleanup is safe
     * (same rules as iOS [failedNewWorktreeChatDisposition]): skips removal if the worktree already
     * existed or the error suggests the thread may still have been created.
     */
    suspend fun startNewManagedWorktreeChat(
        baseProjectPath: String,
        baseBranch: String,
        changeTransfer: GitWorktreeChangeTransferMode = GitWorktreeChangeTransferMode.none,
    ): CodexThread {
        val normalizedBaseProjectPath =
            requiredProjectPath(baseProjectPath, "A valid local project path is required.", WorktreeFlowErrorCode.missing_handoff_source)
        val gitAtBase = GitActionsService(repository, normalizedBaseProjectPath)
        val created = gitAtBase.createManagedWorktree(baseBranch, changeTransfer)
        try {
            return repository.startThread(cwd = created.worktreePath)
        } catch (e: Exception) {
            val cleanupResult = cleanupResultForFailedNewWorktreeChat(created.worktreePath, created.alreadyExisted, e)
            throw WorktreeFlowException(
                code = WorktreeFlowErrorCode.worktree_chat_start_failed,
                message = failedNewWorktreeChatMessage(e, cleanupResult),
                cleanupDisposition = cleanupResult.disposition,
                cause = e,
            )
        }
    }

    /**
     * Moves tracked changes from [sourceProjectPath] into the target checkout, then rebinds the thread.
     * Does not remove worktrees on failure (transfer or rebind may leave partial state; cleanup is a later concern).
     */
    suspend fun handoffThreadToProjectPath(
        threadId: String,
        sourceProjectPath: String,
        targetProjectPath: String,
    ): WorktreeFlowHandoffOutcome =
        handoffThreadToProjectPath(
            threadId = threadId,
            sourceProjectPath = requiredProjectPath(
                sourceProjectPath,
                "The current handoff source is not available on this Mac.",
                WorktreeFlowErrorCode.missing_handoff_source,
            ),
            projectPath = targetProjectPath,
            transferTrackedChangesFromSource = true,
            didTransferTrackedChangesBeforeRebind = false,
            cleanupManagedWorktreeOnFailedRebind = false,
            createdManagedWorktree = false,
        )

    /**
     * Rebinds a local checkout thread into its associated managed worktree, or creates one from
     * [baseBranchForNewWorktree] when no associated path is known yet.
     */
    suspend fun handoffThreadToWorktree(
        threadId: String,
        sourceProjectPath: String,
        associatedWorktreePath: String?,
        baseBranchForNewWorktree: String?,
        changeTransfer: GitWorktreeChangeTransferMode = GitWorktreeChangeTransferMode.move,
    ): WorktreeFlowHandoffOutcome {
        val normalizedSource =
            requiredProjectPath(
                sourceProjectPath,
                "The current handoff source is not available on this Mac.",
                WorktreeFlowErrorCode.missing_handoff_source,
            )
        val associated =
            associatedWorktreePath?.trim()?.takeIf { it.isNotEmpty() }
        if (associated != null) {
            return handoffThreadToProjectPath(
                threadId = threadId,
                sourceProjectPath = normalizedSource,
                projectPath = associated,
                transferTrackedChangesFromSource = true,
                didTransferTrackedChangesBeforeRebind = false,
                cleanupManagedWorktreeOnFailedRebind = false,
                createdManagedWorktree = false,
            )
        }
        val base =
            baseBranchForNewWorktree?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw WorktreeFlowException(
                    code = WorktreeFlowErrorCode.missing_base_branch,
                    message = "A base branch is required to create the managed worktree.",
                )
        val created = GitActionsService(repository, normalizedSource).createManagedWorktree(base, changeTransfer)
        return handoffThreadToProjectPath(
            threadId = threadId,
            sourceProjectPath = normalizedSource,
            projectPath = created.worktreePath,
            transferTrackedChangesFromSource = false,
            didTransferTrackedChangesBeforeRebind = created.transferredChanges,
            cleanupManagedWorktreeOnFailedRebind = !created.alreadyExisted,
            createdManagedWorktree = !created.alreadyExisted,
        )
    }

    private suspend fun handoffThreadToProjectPath(
        threadId: String,
        sourceProjectPath: String,
        projectPath: String,
        transferTrackedChangesFromSource: Boolean,
        didTransferTrackedChangesBeforeRebind: Boolean,
        cleanupManagedWorktreeOnFailedRebind: Boolean,
        createdManagedWorktree: Boolean,
    ): WorktreeFlowHandoffOutcome {
        val normalizedProjectPath =
            requiredProjectPath(projectPath, "Could not resolve the target project path.", WorktreeFlowErrorCode.missing_handoff_target)
        val resolvedProjectPath = canonicalProjectPath(normalizedProjectPath)
        var didTransferTrackedChanges = didTransferTrackedChangesBeforeRebind

        try {
            if (transferTrackedChangesFromSource) {
                val transfer = GitActionsService(repository, sourceProjectPath).transferManagedHandoff(resolvedProjectPath)
                if (!transfer.success) {
                    throw WorktreeHandoffTransferException(
                        message = "Managed handoff did not complete (success=false).",
                    )
                }
                didTransferTrackedChanges = transfer.transferredChanges
            }
            val movedThread = repository.moveThreadToProjectPath(threadId, resolvedProjectPath)
            return WorktreeFlowHandoffOutcome.Moved(
                WorktreeFlowHandoffMove(
                    thread = movedThread,
                    projectPath = resolvedProjectPath,
                    transferredChanges = didTransferTrackedChanges,
                    createdManagedWorktree = createdManagedWorktree,
                ),
            )
        } catch (e: Exception) {
            if (e is WorktreeHandoffTransferException) {
                throw e
            }
            if (isMissingManagedWorktreeTargetError(e)) {
                if (didTransferTrackedChanges) {
                    val recoveryDetail =
                        recoverFailedThreadRebind(
                            didTransferTrackedChanges = true,
                            sourceProjectPath = sourceProjectPath,
                            reboundProjectPath = resolvedProjectPath,
                            cleanupManagedWorktreeOnFailedRebind = cleanupManagedWorktreeOnFailedRebind,
                        )
                    throw WorktreeFlowException(
                        code = WorktreeFlowErrorCode.missing_handoff_target,
                        message =
                            failedMessage(
                                fallback = "The managed worktree is no longer available on this Mac.",
                                error = e,
                                recoveryDetail = recoveryDetail,
                            ),
                        cause = e,
                    )
                }
                return WorktreeFlowHandoffOutcome.MissingAssociatedWorktree
            }

            val recoveryDetail =
                recoverFailedThreadRebind(
                    didTransferTrackedChanges = didTransferTrackedChanges,
                    sourceProjectPath = sourceProjectPath,
                    reboundProjectPath = resolvedProjectPath,
                    cleanupManagedWorktreeOnFailedRebind = cleanupManagedWorktreeOnFailedRebind,
                )
            throw WorktreeFlowException(
                code = WorktreeFlowErrorCode.handoff_rebind_failed,
                message =
                    failedMessage(
                        fallback = "Could not hand off the thread to the target worktree.",
                        error = e,
                        recoveryDetail = recoveryDetail,
                    ),
                cause = e,
            )
        }
    }

    private suspend fun recoverFailedThreadRebind(
        didTransferTrackedChanges: Boolean,
        sourceProjectPath: String?,
        reboundProjectPath: String?,
        cleanupManagedWorktreeOnFailedRebind: Boolean,
    ): String? {
        val notices = mutableListOf<String>()
        var canSafelyCleanupManagedWorktree = true

        if (didTransferTrackedChanges) {
            if (
                reboundProjectPath.isNullOrBlank() ||
                sourceProjectPath.isNullOrBlank() ||
                comparableProjectPath(reboundProjectPath) == comparableProjectPath(sourceProjectPath)
            ) {
                notices.add("The moved changes were kept in the temporary worktree because the original checkout could not be restored automatically.")
                notices.add("The temporary worktree was kept so the moved changes stay available.")
                return notices.joinToString("\n\n")
            }

            try {
                val rollback = GitActionsService(repository, reboundProjectPath).transferManagedHandoff(sourceProjectPath)
                if (!rollback.success) {
                    throw WorktreeHandoffTransferException("Managed handoff rollback did not complete (success=false).")
                }
            } catch (e: Exception) {
                canSafelyCleanupManagedWorktree = false
                notices.add("Tracked changes could not be moved back automatically: ${rollbackFailureMessage(e)}.")
            }
        }

        if (cleanupManagedWorktreeOnFailedRebind && canSafelyCleanupManagedWorktree && !reboundProjectPath.isNullOrBlank()) {
            try {
                GitActionsService(repository, reboundProjectPath).removeManagedWorktree(branch = null)
            } catch (e: Exception) {
                notices.add("The temporary worktree could not be removed automatically: ${cleanupFailureMessage(e)}.")
            }
        } else if (cleanupManagedWorktreeOnFailedRebind && !canSafelyCleanupManagedWorktree) {
            notices.add("The temporary worktree was kept so the moved changes stay available.")
        }

        return notices.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    private suspend fun cleanupResultForFailedNewWorktreeChat(
        worktreePath: String,
        alreadyExisted: Boolean,
        error: Throwable,
    ): WorktreeFlowCleanupResult =
        when (val disposition = failedNewWorktreeChatDisposition(error)) {
            is WorktreeFlowCleanupDisposition.CleanupSafe -> {
                if (alreadyExisted) {
                    WorktreeFlowCleanupResult(WorktreeFlowCleanupDispositionValue.notNeeded)
                } else {
                    try {
                        GitActionsService(repository, worktreePath).removeManagedWorktree(branch = null)
                        WorktreeFlowCleanupResult(WorktreeFlowCleanupDispositionValue.removed)
                    } catch (cleanupError: Exception) {
                        WorktreeFlowCleanupResult(
                            disposition = WorktreeFlowCleanupDispositionValue.failed,
                            detail = cleanupError.message?.trim().orEmpty(),
                        )
                    }
                }
            }

            is WorktreeFlowCleanupDisposition.PreserveWorktree ->
                WorktreeFlowCleanupResult(
                    disposition = WorktreeFlowCleanupDispositionValue.preserved,
                    detail = disposition.detail,
                )
        }

    private fun failedNewWorktreeChatMessage(
        error: Throwable,
        cleanupResult: WorktreeFlowCleanupResult,
    ): String {
        val baseMessage =
            error.message?.takeIf { it.isNotBlank() } ?: "Unable to create a worktree chat right now."
        return when (cleanupResult.disposition) {
            WorktreeFlowCleanupDispositionValue.notNeeded -> baseMessage
            WorktreeFlowCleanupDispositionValue.removed ->
                "$baseMessage\n\nThe temporary worktree was removed automatically."

            WorktreeFlowCleanupDispositionValue.preserved -> {
                val suffix =
                    cleanupResult.detail?.trim()?.takeIf { it.isNotEmpty() }
                        ?: "The new worktree was kept in case the chat was already created. Wait a moment, then check your thread list."
                "$baseMessage\n\n$suffix"
            }

            WorktreeFlowCleanupDispositionValue.failed -> {
                val detail = cleanupResult.detail?.trim().orEmpty()
                val suffix =
                    if (detail.isEmpty()) {
                        "We could not remove the temporary worktree automatically."
                    } else {
                        "We could not remove the temporary worktree automatically: $detail"
                    }
                "$baseMessage\n\n$suffix"
            }
        }
    }

    private fun failedMessage(
        fallback: String,
        error: Throwable,
        recoveryDetail: String?,
    ): String {
        val baseMessage = error.message?.takeIf { it.isNotBlank() } ?: fallback
        val detail = recoveryDetail?.trim()?.takeIf { it.isNotEmpty() } ?: return baseMessage
        return "$baseMessage\n\n$detail"
    }

    private fun rollbackFailureMessage(error: Throwable): String =
        error.message?.trim()?.takeIf { it.isNotEmpty() } ?: "check the original checkout before retrying"

    private fun cleanupFailureMessage(error: Throwable): String =
        error.message?.trim()?.takeIf { it.isNotEmpty() } ?: "remove it manually before retrying"

    private fun isMissingManagedWorktreeTargetError(error: Throwable): Boolean =
        error is GitActionsError.BridgeFailure && error.errorCode == "missing_handoff_target"

    private fun requiredProjectPath(
        rawPath: String?,
        message: String,
        code: WorktreeFlowErrorCode = WorktreeFlowErrorCode.generic,
    ): String =
        CodexThread.normalizeProjectPath(rawPath)
            ?: throw WorktreeFlowException(code = code, message = message)

    private fun canonicalProjectPath(rawPath: String): String =
        CodexThread.normalizeProjectPath(rawPath)
            ?: rawPath.trim()

    private fun comparableProjectPath(rawPath: String?): String? =
        rawPath?.let { canonicalProjectPath(it) }

    companion object {
        internal fun shouldCleanupWorktreeAfterFailedThreadStart(error: Throwable): Boolean =
            failedNewWorktreeChatDisposition(error) is WorktreeFlowCleanupDisposition.CleanupSafe

        private fun failedNewWorktreeChatDisposition(error: Throwable): WorktreeFlowCleanupDisposition =
            when (error) {
                CodexServiceError.Disconnected,
                is CodexServiceError.InvalidResponse,
                -> WorktreeFlowCleanupDisposition.PreserveWorktree(
                    "The connection dropped after the chat request was sent, so the new worktree was kept in case the chat still appears after sync.",
                )

                is CodexServiceError.RpcFailure -> {
                    val msg = error.rpcError.message.lowercase()
                    if (
                        msg.contains("timeout") ||
                        msg.contains("temporarily unavailable") ||
                        msg.contains("connection") ||
                        msg.contains("network")
                    ) {
                        WorktreeFlowCleanupDisposition.PreserveWorktree(
                            "The runtime may still be finalizing the new chat. The worktree was kept so we do not delete a chat that may already exist.",
                        )
                    } else {
                        WorktreeFlowCleanupDisposition.CleanupSafe
                    }
                }

                is CodexServiceError.InvalidServerURL,
                is CodexServiceError.InvalidInput,
                CodexServiceError.EncodingFailed,
                CodexServiceError.NoPendingApproval,
                CodexServiceError.ThreadRemovedOnServer,
                -> WorktreeFlowCleanupDisposition.CleanupSafe

                else ->
                    WorktreeFlowCleanupDisposition.PreserveWorktree(
                        "The runtime may have created the new chat before the error reached the app.",
                    )
            }

    }
}

data class WorktreeFlowHandoffMove(
    val thread: CodexThread,
    val projectPath: String,
    val transferredChanges: Boolean,
    val createdManagedWorktree: Boolean,
)

sealed class WorktreeFlowHandoffOutcome {
    data class Moved(
        val move: WorktreeFlowHandoffMove,
    ) : WorktreeFlowHandoffOutcome()

    data object MissingAssociatedWorktree : WorktreeFlowHandoffOutcome()
}

enum class WorktreeFlowCleanupDispositionValue {
    removed,
    preserved,
    failed,
    notNeeded,
}

data class WorktreeFlowCleanupResult(
    val disposition: WorktreeFlowCleanupDispositionValue,
    val detail: String? = null,
)

enum class WorktreeFlowErrorCode {
    generic,
    missing_base_branch,
    missing_handoff_source,
    missing_handoff_target,
    handoff_transfer_failed,
    handoff_rebind_failed,
    worktree_chat_start_failed,
}

open class WorktreeFlowException(
    val code: WorktreeFlowErrorCode,
    message: String,
    val cleanupDisposition: WorktreeFlowCleanupDispositionValue? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

class WorktreeHandoffTransferException(
    message: String,
) : WorktreeFlowException(
        code = WorktreeFlowErrorCode.handoff_transfer_failed,
        message = message,
    )

private sealed class WorktreeFlowCleanupDisposition {
    data object CleanupSafe : WorktreeFlowCleanupDisposition()

    data class PreserveWorktree(
        val detail: String?,
    ) : WorktreeFlowCleanupDisposition()
}
