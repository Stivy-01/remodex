package com.remodex.mobile.core.model

sealed interface TurnGitPreflightOperation {
    data class CheckoutBranch(
        val branch: String,
    ) : TurnGitPreflightOperation

    data class CreateManagedWorktree(
        val baseBranch: String,
        val changeTransfer: GitWorktreeChangeTransferMode,
    ) : TurnGitPreflightOperation

    data class CreateBranch(
        val branch: String,
    ) : TurnGitPreflightOperation

    data object SyncUpdate : TurnGitPreflightOperation

    data object Commit : TurnGitPreflightOperation

    data object Push : TurnGitPreflightOperation

    data object CreatePullRequest : TurnGitPreflightOperation

    data object DiscardRuntimeChanges : TurnGitPreflightOperation

    companion object {
        fun checkoutBranch(branch: String): TurnGitPreflightOperation = CheckoutBranch(branch)

        fun createManagedWorktree(
            baseBranch: String,
            changeTransfer: GitWorktreeChangeTransferMode,
        ): TurnGitPreflightOperation = CreateManagedWorktree(baseBranch, changeTransfer)

        fun createBranch(branch: String): TurnGitPreflightOperation = CreateBranch(branch)

        val syncUpdate: TurnGitPreflightOperation = SyncUpdate
        val commit: TurnGitPreflightOperation = Commit
        val push: TurnGitPreflightOperation = Push
        val createPullRequest: TurnGitPreflightOperation = CreatePullRequest
        val discardRuntimeChanges: TurnGitPreflightOperation = DiscardRuntimeChanges
    }
}

object TurnGitPreflightPolicy {
    fun alertFor(
        status: GitRepoSyncResult?,
        branches: GitBranchesWithStatusResult?,
        operation: TurnGitPreflightOperation,
    ): TurnGitSyncAlert? {
        val effectiveStatus = status ?: branches?.status
        val currentBranch = effectiveStatus?.currentBranch.orTrimmed() ?: branches?.currentBranch.orTrimmed()
        val defaultBranch = branches?.defaultBranch.orTrimmed()

        if (operation is TurnGitPreflightOperation.DiscardRuntimeChanges) {
            return discardRuntimeAlert(effectiveStatus)
        }

        if (operation is TurnGitPreflightOperation.CheckoutBranch) {
            branchOpenedElsewhereAlert(branches, operation.branch)?.let { return it }
        }

        remoteStateAlert(effectiveStatus, operation)?.let { return it }
        protectedBranchAlert(currentBranch, defaultBranch, operation)?.let { return it }

        return when (operation) {
            is TurnGitPreflightOperation.CheckoutBranch ->
                dirtyCheckoutAlert(effectiveStatus, operation.branch)

            is TurnGitPreflightOperation.CreateManagedWorktree ->
                managedWorktreeAlert(
                    status = effectiveStatus,
                    currentBranch = currentBranch,
                    defaultBranch = defaultBranch,
                    baseBranch = operation.baseBranch,
                    changeTransfer = operation.changeTransfer,
                )

            is TurnGitPreflightOperation.CreateBranch ->
                createBranchAlert(
                    status = effectiveStatus,
                    currentBranch = currentBranch,
                    defaultBranch = defaultBranch,
                    branch = operation.branch,
                )

            TurnGitPreflightOperation.SyncUpdate,
            TurnGitPreflightOperation.Commit,
            TurnGitPreflightOperation.Push,
            TurnGitPreflightOperation.CreatePullRequest,
            TurnGitPreflightOperation.DiscardRuntimeChanges,
            -> null
        }
    }

    private fun remoteStateAlert(
        status: GitRepoSyncResult?,
        operation: TurnGitPreflightOperation,
    ): TurnGitSyncAlert? {
        val state = status?.state.orEmpty()
        if (state == "dirty_and_behind") {
            return TurnGitSyncAlert.withDefaultButtons(
                title = "Local changes + remote update",
                message = "You have local changes and the remote branch moved ahead. Clean up or update from desktop before continuing.",
                action = TurnGitSyncAlertAction.dismissOnly,
            )
        }

        if (state == "behind_only" && operation.blocksOnRemoteUpdate()) {
            return TurnGitSyncAlert.withDefaultButtons(
                title = "Remote update available",
                message = "The remote branch has new commits. Pull with rebase before pushing or creating a pull request.",
                action = TurnGitSyncAlertAction.pullRebase,
            )
        }

        if (state == "diverged" && operation.isMutating()) {
            return TurnGitSyncAlert.withDefaultButtons(
                title = "Remote history diverged",
                message = "Local and remote history both moved. Pull with rebase to reconcile them before continuing.",
                action = TurnGitSyncAlertAction.pullRebase,
            )
        }

        return null
    }

    private fun protectedBranchAlert(
        currentBranch: String?,
        defaultBranch: String?,
        operation: TurnGitPreflightOperation,
    ): TurnGitSyncAlert? {
        if (!operation.blocksOnProtectedBranch()) return null
        val branch = currentBranch?.takeIf { it.isNotEmpty() } ?: return null
        val isDefault = defaultBranch?.let { sameBranch(branch, it) } == true
        val isMainNamed = branch == "main" || branch == "master"
        if (!isDefault && !isMainNamed) return null

        val label = defaultBranch?.takeIf { isDefault } ?: branch
        if (operation is TurnGitPreflightOperation.Commit) {
            return TurnGitSyncAlert(
                title = "Commit on $label?",
                message = "Are you sure you want to commit $label branch?",
                buttons =
                    listOf(
                        TurnGitSyncAlertButton(
                            title = "Cancel",
                            role = TurnGitSyncAlertButtonRole.cancel,
                            action = TurnGitSyncAlertAction.dismissOnly,
                        ),
                        TurnGitSyncAlertButton(
                            title = "Commit Anyway",
                            action = TurnGitSyncAlertAction.continuePendingGitOperation,
                        ),
                    ),
            )
        }

        return TurnGitSyncAlert.withDefaultButtons(
            title = "Protected branch",
            message = "This operation is blocked on $label. Switch to a feature branch or worktree first.",
            action = TurnGitSyncAlertAction.dismissOnly,
        )
    }

    private fun dirtyCheckoutAlert(
        status: GitRepoSyncResult?,
        branch: String,
    ): TurnGitSyncAlert? {
        if (status?.isDirty != true) return null
        val target = branch.trim().ifEmpty { "the selected branch" }
        return TurnGitSyncAlert.withDefaultButtons(
            title = "Switch branches with local changes?",
            message = dirtyFilesMessage(
                intro = "Checkout can overwrite files that differ on $target. Continue only if these local changes are safe to carry across.",
                files = status.files,
            ),
            action = TurnGitSyncAlertAction.continuePendingGitOperation,
        )
    }

    private fun managedWorktreeAlert(
        status: GitRepoSyncResult?,
        currentBranch: String?,
        defaultBranch: String?,
        baseBranch: String,
        changeTransfer: GitWorktreeChangeTransferMode,
    ): TurnGitSyncAlert? {
        val base = baseBranch.trim()
        if (
            status?.isDirty == true &&
            changeTransfer != GitWorktreeChangeTransferMode.none &&
            !sameBranch(currentBranch, base)
        ) {
            return TurnGitSyncAlert.withDefaultButtons(
                title = "Move local changes from current branch",
                message = "Creating a managed worktree can ${changeTransfer.name} local changes only from the current branch. Switch the base branch to ${currentBranch.orEmpty()} or clean up local changes before creating the worktree.",
                action = TurnGitSyncAlertAction.dismissOnly,
            )
        }

        return localOnlyCommitsOnDefaultAlert(
            status = status,
            currentBranch = currentBranch,
            defaultBranch = defaultBranch,
            messagePrefix = "Creating a managed worktree from $base starts from the current HEAD, but those commits stay in ${defaultBranch.orEmpty()}'s history too.",
            continueTitle = "Create Anyway",
        )
    }

    private fun createBranchAlert(
        status: GitRepoSyncResult?,
        currentBranch: String?,
        defaultBranch: String?,
        branch: String,
    ): TurnGitSyncAlert? {
        if (status?.isDirty == true) {
            return TurnGitSyncAlert(
                title = "Bring local changes to '$branch'?",
                message = dirtyFilesMessage(
                    intro = "You are creating '$branch' from ${currentBranch ?: "the current branch"}. Carry your local changes onto the new branch, or commit first.",
                    files = status.files,
                ),
                buttons =
                    listOf(
                        TurnGitSyncAlertButton(
                            title = "Cancel",
                            role = TurnGitSyncAlertButtonRole.cancel,
                            action = TurnGitSyncAlertAction.dismissOnly,
                        ),
                        TurnGitSyncAlertButton(
                            title = "Carry to New Branch",
                            action = TurnGitSyncAlertAction.continuePendingGitOperation,
                        ),
                        TurnGitSyncAlertButton(
                            title = "Commit & Continue",
                            action = TurnGitSyncAlertAction.commitAndContinuePendingGitOperation,
                        ),
                    ),
            )
        }

        return localOnlyCommitsOnDefaultAlert(
            status = status,
            currentBranch = currentBranch,
            defaultBranch = defaultBranch,
            messagePrefix = "Creating '$branch' now starts the new branch from the current HEAD, but those commits stay in ${defaultBranch.orEmpty()}'s history.",
            continueTitle = "Create Anyway",
        )
    }

    private fun localOnlyCommitsOnDefaultAlert(
        status: GitRepoSyncResult?,
        currentBranch: String?,
        defaultBranch: String?,
        messagePrefix: String,
        continueTitle: String,
    ): TurnGitSyncAlert? {
        val count = status?.localOnlyCommitCount ?: 0
        val default = defaultBranch?.takeIf { it.isNotEmpty() } ?: return null
        if (count <= 0 || !sameBranch(currentBranch, default)) return null

        val commitLabel = if (count == 1) "1 local commit" else "$count local commits"
        return TurnGitSyncAlert(
            title = "Local commits stay on $default",
            message = "$default already has $commitLabel that are not on the remote. $messagePrefix",
            buttons =
                listOf(
                    TurnGitSyncAlertButton(
                        title = "Cancel",
                        role = TurnGitSyncAlertButtonRole.cancel,
                        action = TurnGitSyncAlertAction.dismissOnly,
                    ),
                    TurnGitSyncAlertButton(
                        title = continueTitle,
                        action = TurnGitSyncAlertAction.continuePendingGitOperation,
                    ),
                ),
        )
    }

    private fun branchOpenedElsewhereAlert(
        branches: GitBranchesWithStatusResult?,
        branch: String,
    ): TurnGitSyncAlert? {
        val target = branch.trim()
        if (target.isEmpty()) return null
        if (!branches?.branchesCheckedOutElsewhere.orEmpty().contains(target)) return null
        val path = branches?.worktreePathByBranch?.get(target)?.trim().orEmpty()
        if (path.isNotEmpty()) return null
        return TurnGitSyncAlert.withDefaultButtons(
            title = "Branch open in another worktree",
            message = "This branch is already checked out elsewhere, but the bridge did not provide a worktree path to switch to.",
            action = TurnGitSyncAlertAction.dismissOnly,
        )
    }

    private fun discardRuntimeAlert(status: GitRepoSyncResult?): TurnGitSyncAlert {
        val ahead = status?.aheadCount ?: 0
        val warning =
            if (ahead > 0) {
                val label = if (ahead == 1) "1 local commit" else "$ahead local commits"
                " This also deletes $label that have not been pushed."
            } else {
                ""
            }
        return TurnGitSyncAlert(
            title = "Discard local changes?",
            message = "This resets the current branch to match the remote and removes local uncommitted changes.$warning This cannot be undone from the app.",
            buttons =
                listOf(
                    TurnGitSyncAlertButton(
                        title = "Cancel",
                        role = TurnGitSyncAlertButtonRole.cancel,
                        action = TurnGitSyncAlertAction.dismissOnly,
                    ),
                    TurnGitSyncAlertButton(
                        title = "Discard Changes",
                        role = TurnGitSyncAlertButtonRole.destructive,
                        action = TurnGitSyncAlertAction.discardRuntimeChanges,
                    ),
                ),
        )
    }

    private fun dirtyFilesMessage(
        intro: String,
        files: List<GitChangedFile>,
    ): String {
        if (files.isEmpty()) return intro
        val preview = files.take(3).joinToString(separator = "\n") { "- ${it.path}" }
        val remaining = files.size - files.take(3).size
        val suffix = if (remaining > 0) "\n- +$remaining more files" else ""
        return "$intro\n\nFiles with local changes:\n$preview$suffix"
    }

    private fun TurnGitPreflightOperation.blocksOnRemoteUpdate(): Boolean =
        this is TurnGitPreflightOperation.Push ||
            this is TurnGitPreflightOperation.CreatePullRequest

    private fun TurnGitPreflightOperation.isMutating(): Boolean =
        this !is TurnGitPreflightOperation.SyncUpdate &&
            this !is TurnGitPreflightOperation.Commit &&
            this !is TurnGitPreflightOperation.DiscardRuntimeChanges

    private fun TurnGitPreflightOperation.blocksOnProtectedBranch(): Boolean =
        this is TurnGitPreflightOperation.Commit ||
            this is TurnGitPreflightOperation.Push ||
            this is TurnGitPreflightOperation.CreatePullRequest

    private fun String?.orTrimmed(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun sameBranch(
        left: String?,
        right: String?,
    ): Boolean {
        val l = left.orTrimmed() ?: return false
        val r = right.orTrimmed() ?: return false
        return l == r
    }
}
