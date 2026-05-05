package com.remodex.mobile.ui.turn

internal fun worktreeHandoffBaseBranchChoices(
    availableBranches: List<String>,
    currentBranch: String?,
    defaultBranch: String?,
): List<String> {
    val preferred =
        listOf(defaultBranch, currentBranch)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    val remaining = availableBranches.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
    return (preferred + remaining).distinct()
}

internal data class WorktreeHandoffSheetModel(
    val requiresBaseBranch: Boolean,
    val canConfirm: Boolean,
    val targetPath: String?,
    val showsCreateWorktreeChoice: Boolean,
)

internal fun worktreeHandoffSheetModel(
    isWorktreeProject: Boolean,
    inProgress: Boolean,
    selectedBaseBranch: String?,
    localTargetPath: String?,
    associatedWorktreePath: String?,
): WorktreeHandoffSheetModel {
    val normalizedAssociated = associatedWorktreePath?.trim()?.takeIf { it.isNotEmpty() }
    val requiresBaseBranch = !isWorktreeProject && normalizedAssociated == null
    val normalizedBase = selectedBaseBranch?.trim()?.takeIf { it.isNotEmpty() }
    return WorktreeHandoffSheetModel(
        requiresBaseBranch = requiresBaseBranch,
        canConfirm = !inProgress && (!requiresBaseBranch || normalizedBase != null),
        targetPath =
            if (isWorktreeProject) {
                localTargetPath?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                normalizedAssociated
            },
        showsCreateWorktreeChoice = requiresBaseBranch,
    )
}
