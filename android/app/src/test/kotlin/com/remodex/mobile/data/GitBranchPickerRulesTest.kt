package com.remodex.mobile.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitBranchPickerRulesTest {
    @Test
    fun rowDisabled_whenElsewhereWithoutWorktreePath() {
        assertTrue(
            GitBranchPickerRules.isBranchRowDisabledForCheckout(
                branch = "feature",
                currentBranch = "main",
                branchesCheckedOutElsewhere = setOf("feature"),
                worktreePathByBranch = emptyMap(),
            ),
        )
    }

    @Test
    fun rowNotDisabled_whenElsewhereWithKnownWorktreePath_iOSparity() {
        assertFalse(
            GitBranchPickerRules.isBranchRowDisabledForCheckout(
                branch = "feature",
                currentBranch = "main",
                branchesCheckedOutElsewhere = setOf("feature"),
                worktreePathByBranch = mapOf("feature" to "/tmp/wt"),
            ),
        )
    }

    @Test
    fun rowDisabled_forCurrentBranch() {
        assertTrue(
            GitBranchPickerRules.isBranchRowDisabledForCheckout(
                branch = "main",
                currentBranch = "main",
                branchesCheckedOutElsewhere = emptySet(),
                worktreePathByBranch = emptyMap(),
            ),
        )
    }

    @Test
    fun checkoutBlockedElsewhere_onlyWithoutKnownPath() {
        assertTrue(
            GitBranchPickerRules.isCheckoutBlockedElsewhere(
                "feature",
                setOf("feature"),
                emptyMap(),
            ),
        )
        assertFalse(
            GitBranchPickerRules.isCheckoutBlockedElsewhere(
                "feature",
                setOf("feature"),
                mapOf("feature" to "/wt"),
            ),
        )
        assertFalse(
            GitBranchPickerRules.isCheckoutBlockedElsewhere(
                "main",
                setOf("feature"),
                emptyMap(),
            ),
        )
    }
}
