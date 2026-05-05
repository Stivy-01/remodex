package com.remodex.mobile.ui.turn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurnWorktreeHandoffModelsTest {
    @Test
    fun baseBranchChoices_prioritizeDefaultThenCurrentThenRemaining() {
        val choices =
            worktreeHandoffBaseBranchChoices(
                availableBranches = listOf("feature", "main", "release"),
                currentBranch = "feature",
                defaultBranch = "main",
            )

        assertEquals(listOf("main", "feature", "release"), choices)
    }

    @Test
    fun baseBranchChoices_trimAndDeduplicateBlankValues() {
        val choices =
            worktreeHandoffBaseBranchChoices(
                availableBranches = listOf(" ", " main ", "dev", "main"),
                currentBranch = " dev ",
                defaultBranch = " ",
            )

        assertEquals(listOf("dev", "main"), choices)
    }

    @Test
    fun sheetModel_missingAssociatedWorktreeShowsCreateChoice() {
        val model =
            worktreeHandoffSheetModel(
                isWorktreeProject = false,
                inProgress = false,
                selectedBaseBranch = "main",
                localTargetPath = null,
                associatedWorktreePath = " ",
            )

        assertTrue(model.requiresBaseBranch)
        assertTrue(model.showsCreateWorktreeChoice)
        assertTrue(model.canConfirm)
        assertEquals(null, model.targetPath)
    }

    @Test
    fun sheetModel_confirmDisabledDuringProgress() {
        val model =
            worktreeHandoffSheetModel(
                isWorktreeProject = false,
                inProgress = true,
                selectedBaseBranch = "main",
                localTargetPath = null,
                associatedWorktreePath = null,
            )

        assertFalse(model.canConfirm)
    }

    @Test
    fun sheetModel_usesAssociatedWorktreeWhenPresent() {
        val model =
            worktreeHandoffSheetModel(
                isWorktreeProject = false,
                inProgress = false,
                selectedBaseBranch = null,
                localTargetPath = null,
                associatedWorktreePath = " /wt/existing ",
            )

        assertFalse(model.requiresBaseBranch)
        assertFalse(model.showsCreateWorktreeChoice)
        assertTrue(model.canConfirm)
        assertEquals("/wt/existing", model.targetPath)
    }
}
