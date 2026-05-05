package com.remodex.mobile.ui.turn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TurnReviewAccessoryTest {
    @Test
    fun selectableDefaultBranch_requiresKnownBranch() {
        assertEquals(
            "main",
            reviewSelectableDefaultBranch(
                defaultBranch = "main",
                availableBranches = listOf("main", "feature"),
            ),
        )
        assertNull(
            reviewSelectableDefaultBranch(
                defaultBranch = "release",
                availableBranches = listOf("main", "feature"),
            ),
        )
    }

    @Test
    fun baseBranchChoices_keepsDefaultFirstAndDedupesWhitespace() {
        assertEquals(
            listOf("main", "feature/a", "feature/b"),
            reviewBaseBranchChoices(
                defaultBranch = " main ",
                availableBranches = listOf("feature/b", "main", "feature/a", "feature/a", " "),
            ),
        )
    }

    @Test
    fun resolveReviewBaseBranch_prefersValidSelectionAndFallsBackToDefault() {
        assertEquals(
            "feature/a",
            resolveReviewBaseBranch(
                selectedBaseBranch = "feature/a",
                defaultBranch = "main",
                availableBranches = listOf("main", "feature/a"),
            ),
        )
        assertEquals(
            "main",
            resolveReviewBaseBranch(
                selectedBaseBranch = "missing",
                defaultBranch = "main",
                availableBranches = listOf("main", "feature/a"),
            ),
        )
        assertNull(
            resolveReviewBaseBranch(
                selectedBaseBranch = "missing",
                defaultBranch = "release",
                availableBranches = listOf("main", "feature/a"),
            ),
        )
    }

    @Test
    fun baseBranchSelectionDisabled_onlyMatchesCurrentBranch() {
        assertEquals(
            true,
            reviewBaseBranchSelectionDisabled(
                branch = "main",
                currentBranch = "main",
            ),
        )
        assertEquals(
            false,
            reviewBaseBranchSelectionDisabled(
                branch = "feature",
                currentBranch = "main",
            ),
        )
    }
}
