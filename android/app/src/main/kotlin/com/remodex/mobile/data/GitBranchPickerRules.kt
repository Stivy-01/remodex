package com.remodex.mobile.data

/**
 * Branch picker / checkout guardrails (parity iOS [remodexCurrentBranchSelectionIsDisabled]).
 * A branch checked out in another worktree stays disabled only when no [worktreePathByBranch]
 * mapping exists—the open/handoff flow can resolve rebind + navigation when [worktreePathByBranch].
 */
object GitBranchPickerRules {
    fun isBranchRowDisabledForCheckout(
        branch: String,
        currentBranch: String?,
        branchesCheckedOutElsewhere: Set<String>,
        worktreePathByBranch: Map<String, String>,
    ): Boolean {
        if (branchesCheckedOutElsewhere.contains(branch)) {
            val p = worktreePathByBranch[branch]?.trim().orEmpty()
            if (p.isEmpty()) {
                return true
            }
        }
        val cur = currentBranch?.trim().orEmpty()
        if (cur.isNotEmpty() && branch == cur) {
            return true
        }
        return false
    }

    fun isCheckoutBlockedElsewhere(
        branch: String,
        branchesCheckedOutElsewhere: Set<String>,
        worktreePathByBranch: Map<String, String>,
    ): Boolean {
        if (!branchesCheckedOutElsewhere.contains(branch)) return false
        return worktreePathByBranch[branch]?.trim().orEmpty().isEmpty()
    }
}
