package com.remodex.mobile.core.model

import java.util.UUID

enum class GitWorktreeChangeTransferMode {
    move,
    copy,
    /** No tracked-change transfer; parity with iOS [GitWorktreeChangeTransferMode.none]. */
    none,
}

data class GitDiffTotals(
    val additions: Int,
    val deletions: Int,
    val binaryFiles: Int = 0,
) {
    val hasChanges: Boolean get() = additions > 0 || deletions > 0 || binaryFiles > 0

    companion object {
        fun fromOrNull(json: RPCObject?): GitDiffTotals? {
            if (json == null) return null
            val additions = json["additions"]?.intValue ?: 0
            val deletions = json["deletions"]?.intValue ?: 0
            val binaryFiles = json["binaryFiles"]?.intValue ?: 0
            val totals = GitDiffTotals(additions, deletions, binaryFiles)
            return if (totals.hasChanges) totals else null
        }
    }
}

data class GitChangedFile(
    val path: String,
    val status: String,
) {
    companion object {
        fun fromOrNull(json: RPCObject): GitChangedFile? {
            val path = json["path"]?.stringValue?.trim().orEmpty()
            if (path.isEmpty()) return null
            val status = json["status"]?.stringValue?.trim().orEmpty()
            return GitChangedFile(path, status)
        }
    }
}

data class GitRepoSyncResult(
    val repoRoot: String?,
    val currentBranch: String?,
    val trackingBranch: String?,
    val isDirty: Boolean,
    val aheadCount: Int,
    val behindCount: Int,
    val localOnlyCommitCount: Int,
    val state: String,
    val canPush: Boolean,
    val isPublishedToRemote: Boolean,
    val files: List<GitChangedFile>,
    val repoDiffTotals: GitDiffTotals?,
    val isRepo: Boolean = true,
) {
    val workingTreeDiffTotals: GitDiffTotals?
        get() =
            when {
                !isRepo -> null
                !isDirty -> GitDiffTotals(additions = 0, deletions = 0)
                else -> repoDiffTotals ?: GitDiffTotals(additions = 0, deletions = 0)
            }

    companion object {
        fun fromJson(json: RPCObject): GitRepoSyncResult =
            GitRepoSyncResult(
                repoRoot = json["repoRoot"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                currentBranch = json["branch"]?.stringValue,
                trackingBranch = json["tracking"]?.stringValue,
                isDirty = json["dirty"]?.boolValue ?: false,
                aheadCount = json["ahead"]?.intValue ?: 0,
                behindCount = json["behind"]?.intValue ?: 0,
                localOnlyCommitCount = json["localOnlyCommitCount"]?.intValue ?: 0,
                state = json["state"]?.stringValue ?: "up_to_date",
                canPush = json["canPush"]?.boolValue ?: false,
                isPublishedToRemote = json["publishedToRemote"]?.boolValue ?: false,
                files =
                    json["files"]?.arrayValue?.mapNotNull { v ->
                        v.objectValue?.let { GitChangedFile.fromOrNull(it) }
                    } ?: emptyList(),
                repoDiffTotals = GitDiffTotals.fromOrNull(json["diff"]?.objectValue),
                isRepo = json["isRepo"]?.boolValue ?: true,
            )
    }
}

data class GitRepoDiffResult(
    val patch: String,
) {
    companion object {
        fun fromJson(json: RPCObject): GitRepoDiffResult =
            GitRepoDiffResult(patch = json["patch"]?.stringValue ?: "")
    }
}

data class GitInitResult(
    val repoRoot: String,
    val status: GitRepoSyncResult?,
) {
    companion object {
        fun fromJson(json: RPCObject): GitInitResult =
            GitInitResult(
                repoRoot = json["repoRoot"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }
                    ?: json["path"]?.stringValue?.trim().orEmpty(),
                status = json["status"]?.objectValue?.let { GitRepoSyncResult.fromJson(it) },
            )
    }
}

data class GitCommitResult(
    val commitHash: String,
    val branch: String,
    val summary: String,
) {
    companion object {
        fun fromJson(json: RPCObject): GitCommitResult =
            GitCommitResult(
                commitHash = json["hash"]?.stringValue ?: "",
                branch = json["branch"]?.stringValue ?: "",
                summary = json["summary"]?.stringValue ?: "",
            )
    }
}

data class GitGeneratedCommitMessageResult(
    val subject: String,
    val body: String,
    val fullMessage: String,
) {
    companion object {
        fun fromJson(json: RPCObject): GitGeneratedCommitMessageResult =
            GitGeneratedCommitMessageResult(
                subject = json["subject"]?.stringValue ?: "",
                body = json["body"]?.stringValue ?: "",
                fullMessage = json["fullMessage"]?.stringValue ?: "",
            )
    }
}

data class GitGeneratedPullRequestDraftResult(
    val title: String,
    val body: String,
) {
    companion object {
        fun fromJson(json: RPCObject): GitGeneratedPullRequestDraftResult =
            GitGeneratedPullRequestDraftResult(
                title = json["title"]?.stringValue ?: "",
                body = json["body"]?.stringValue ?: "",
            )
    }
}

data class GitPushResult(
    val branch: String,
    val remote: String?,
    val status: GitRepoSyncResult?,
) {
    companion object {
        fun fromJson(json: RPCObject): GitPushResult =
            GitPushResult(
                branch = json["branch"]?.stringValue ?: "",
                remote = json["remote"]?.stringValue,
                status = json["status"]?.objectValue?.let { GitRepoSyncResult.fromJson(it) },
            )
    }
}

data class GitBranchesResult(
    val branches: List<String>,
    val branchesCheckedOutElsewhere: Set<String>,
    val worktreePathByBranch: Map<String, String>,
    val localCheckoutPath: String?,
    val currentBranch: String?,
    val defaultBranch: String?,
) {
    companion object {
        fun fromJson(json: RPCObject): GitBranchesResult =
            GitBranchesResult(
                branches = json["branches"]?.arrayValue?.mapNotNull { it.stringValue } ?: emptyList(),
                branchesCheckedOutElsewhere =
                    json["branchesCheckedOutElsewhere"]?.arrayValue?.mapNotNull { it.stringValue }?.toSet()
                        ?: emptySet(),
                worktreePathByBranch = stringDictionary(json["worktreePathByBranch"]?.objectValue),
                localCheckoutPath =
                    json["localCheckoutPath"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                currentBranch = json["current"]?.stringValue,
                defaultBranch = json["default"]?.stringValue,
            )
    }
}

data class GitCreateBranchResult(
    val branch: String,
    val status: GitRepoSyncResult?,
) {
    companion object {
        fun fromJson(json: RPCObject): GitCreateBranchResult =
            GitCreateBranchResult(
                branch = json["branch"]?.stringValue ?: "",
                status = json["status"]?.objectValue?.let { GitRepoSyncResult.fromJson(it) },
            )
    }
}

data class GitCreateWorktreeResult(
    val branch: String,
    val worktreePath: String,
    val alreadyExisted: Boolean,
) {
    companion object {
        fun fromJson(json: RPCObject): GitCreateWorktreeResult =
            GitCreateWorktreeResult(
                branch = json["branch"]?.stringValue ?: "",
                worktreePath = json["worktreePath"]?.stringValue ?: "",
                alreadyExisted = json["alreadyExisted"]?.boolValue ?: false,
            )
    }
}

/** Response from [git/createManagedWorktree] (Codex-managed worktree). */
data class GitCreateManagedWorktreeResult(
    val worktreePath: String,
    val alreadyExisted: Boolean,
    val baseBranch: String,
    val headMode: String,
    val transferredChanges: Boolean,
) {
    companion object {
        fun fromJson(json: RPCObject): GitCreateManagedWorktreeResult =
            GitCreateManagedWorktreeResult(
                worktreePath = json["worktreePath"]?.stringValue ?: "",
                alreadyExisted = json["alreadyExisted"]?.boolValue ?: false,
                baseBranch = json["baseBranch"]?.stringValue ?: "",
                headMode = json["headMode"]?.stringValue ?: "",
                transferredChanges = json["transferredChanges"]?.boolValue ?: false,
            )
    }
}

data class GitManagedHandoffTransferResult(
    val success: Boolean,
    val targetPath: String?,
    val transferredChanges: Boolean,
) {
    companion object {
        fun fromJson(json: RPCObject): GitManagedHandoffTransferResult =
            GitManagedHandoffTransferResult(
                success = json["success"]?.boolValue ?: false,
                targetPath = json["targetPath"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                transferredChanges = json["transferredChanges"]?.boolValue ?: false,
            )
    }
}

data class GitCheckoutResult(
    val currentBranch: String,
    val tracking: String?,
    val status: GitRepoSyncResult?,
) {
    companion object {
        fun fromJson(json: RPCObject): GitCheckoutResult =
            GitCheckoutResult(
                currentBranch = json["current"]?.stringValue ?: "",
                tracking = json["tracking"]?.stringValue,
                status = json["status"]?.objectValue?.let { GitRepoSyncResult.fromJson(it) },
            )
    }
}

data class GitPullResult(
    val success: Boolean,
    val status: GitRepoSyncResult?,
) {
    companion object {
        fun fromJson(json: RPCObject): GitPullResult =
            GitPullResult(
                success = json["success"]?.boolValue ?: false,
                status = json["status"]?.objectValue?.let { GitRepoSyncResult.fromJson(it) },
            )
    }
}

data class GitResetResult(
    val success: Boolean,
    val status: GitRepoSyncResult?,
) {
    companion object {
        fun fromJson(json: RPCObject): GitResetResult =
            GitResetResult(
                success = json["success"]?.boolValue ?: false,
                status = json["status"]?.objectValue?.let { GitRepoSyncResult.fromJson(it) },
            )
    }
}

data class GitRemoteUrlResult(
    val url: String,
    val ownerRepo: String?,
) {
    companion object {
        fun fromJson(json: RPCObject): GitRemoteUrlResult =
            GitRemoteUrlResult(
                url = json["url"]?.stringValue ?: "",
                ownerRepo = json["ownerRepo"]?.stringValue,
            )
    }
}

data class GitBranchesWithStatusResult(
    val branches: List<String>,
    val branchesCheckedOutElsewhere: Set<String>,
    val worktreePathByBranch: Map<String, String>,
    val localCheckoutPath: String?,
    val currentBranch: String?,
    val defaultBranch: String?,
    val status: GitRepoSyncResult?,
) {
    companion object {
        fun fromJson(json: RPCObject): GitBranchesWithStatusResult =
            GitBranchesWithStatusResult(
                branches = json["branches"]?.arrayValue?.mapNotNull { it.stringValue } ?: emptyList(),
                branchesCheckedOutElsewhere =
                    json["branchesCheckedOutElsewhere"]?.arrayValue?.mapNotNull { it.stringValue }?.toSet()
                        ?: emptySet(),
                worktreePathByBranch = stringDictionary(json["worktreePathByBranch"]?.objectValue),
                localCheckoutPath =
                    json["localCheckoutPath"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                currentBranch = json["current"]?.stringValue,
                defaultBranch = json["default"]?.stringValue,
                status = json["status"]?.objectValue?.let { GitRepoSyncResult.fromJson(it) },
            )
    }
}

private fun stringDictionary(json: Map<String, JSONValue>?): Map<String, String> {
    if (json == null) return emptyMap()
    return json.entries.mapNotNull { (k, v) ->
        val key = k.trim()
        val value = v.stringValue?.trim().orEmpty()
        if (key.isEmpty() || value.isEmpty()) null else key to value
    }.toMap()
}

enum class TurnGitActionKind {
    syncNow,
    initialize,
    commit,
    push,
    commitAndPush,
    createPR,
    previewCommitPushToast,
    discardRuntimeChangesAndSync;

    val title: String
        get() =
            when (this) {
                syncNow -> "Update"
                initialize -> "Initialize Git"
                commit -> "Commit"
                push -> "Push"
                commitAndPush -> "Commit & Push"
                createPR -> "Create PR"
                previewCommitPushToast -> "Preview Commit & Push Toast"
                discardRuntimeChangesAndSync -> "Discard Local Changes"
            }
}

data class TurnGitSyncAlert(
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val message: String,
    val buttons: List<TurnGitSyncAlertButton>,
) {
    companion object {
        fun withDefaultButtons(
            title: String,
            message: String,
            action: TurnGitSyncAlertAction,
        ): TurnGitSyncAlert =
            TurnGitSyncAlert(
                title = title,
                message = message,
                buttons = TurnGitSyncAlertButton.defaultButtonsFor(action),
            )
    }
}

data class TurnGitSyncAlertButton(
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val role: TurnGitSyncAlertButtonRole? = null,
    val action: TurnGitSyncAlertAction,
) {
    companion object {
        fun defaultButtonsFor(action: TurnGitSyncAlertAction): List<TurnGitSyncAlertButton> =
            when (action) {
                TurnGitSyncAlertAction.dismissOnly ->
                    listOf(
                        TurnGitSyncAlertButton(title = "OK", role = TurnGitSyncAlertButtonRole.cancel, action = action),
                    )
                TurnGitSyncAlertAction.pullRebase ->
                    listOf(
                        TurnGitSyncAlertButton(
                            title = "Cancel",
                            role = TurnGitSyncAlertButtonRole.cancel,
                            action = TurnGitSyncAlertAction.dismissOnly,
                        ),
                        TurnGitSyncAlertButton(title = "Pull & Rebase", role = null, action = action),
                    )
                TurnGitSyncAlertAction.continuePendingGitOperation ->
                    listOf(
                        TurnGitSyncAlertButton(
                            title = "Cancel",
                            role = TurnGitSyncAlertButtonRole.cancel,
                            action = TurnGitSyncAlertAction.dismissOnly,
                        ),
                        TurnGitSyncAlertButton(title = "Continue", role = null, action = action),
                    )
                TurnGitSyncAlertAction.commitAndContinuePendingGitOperation ->
                    listOf(
                        TurnGitSyncAlertButton(
                            title = "Cancel",
                            role = TurnGitSyncAlertButtonRole.cancel,
                            action = TurnGitSyncAlertAction.dismissOnly,
                        ),
                        TurnGitSyncAlertButton(title = "Commit & Continue", role = null, action = action),
                    )
                TurnGitSyncAlertAction.continueGitBranchOperation ->
                    listOf(
                        TurnGitSyncAlertButton(
                            title = "Cancel",
                            role = TurnGitSyncAlertButtonRole.cancel,
                            action = TurnGitSyncAlertAction.dismissOnly,
                        ),
                        TurnGitSyncAlertButton(title = "Continue", role = null, action = action),
                    )
                TurnGitSyncAlertAction.commitAndContinueGitBranchOperation ->
                    listOf(
                        TurnGitSyncAlertButton(
                            title = "Cancel",
                            role = TurnGitSyncAlertButtonRole.cancel,
                            action = TurnGitSyncAlertAction.dismissOnly,
                        ),
                        TurnGitSyncAlertButton(title = "Commit & Continue", role = null, action = action),
                    )
                TurnGitSyncAlertAction.discardRuntimeChanges ->
                    listOf(
                        TurnGitSyncAlertButton(
                            title = "Cancel",
                            role = TurnGitSyncAlertButtonRole.cancel,
                            action = TurnGitSyncAlertAction.dismissOnly,
                        ),
                        TurnGitSyncAlertButton(
                            title = "Discard Local Changes",
                            role = TurnGitSyncAlertButtonRole.destructive,
                            action = action,
                        ),
                    )
            }
    }
}

enum class TurnGitSyncAlertButtonRole {
    cancel,
    destructive,
}

enum class TurnGitSyncAlertAction {
    dismissOnly,
    pullRebase,
    continuePendingGitOperation,
    commitAndContinuePendingGitOperation,
    continueGitBranchOperation,
    commitAndContinueGitBranchOperation,
    discardRuntimeChanges,
}
