package com.remodex.mobile.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurnGitPreflightPolicyTest {
    @Test
    fun checkoutBranch_dirtyTreeRequiresConfirmation() {
        val alert =
            TurnGitPreflightPolicy.alertFor(
                status = status(isDirty = true, files = listOf(GitChangedFile("app.kt", "modified"))),
                branches = branches(),
                operation = TurnGitPreflightOperation.checkoutBranch("feature"),
            )

        assertNotNull(alert)
        assertEquals("Switch branches with local changes?", alert.title)
        assertTrue(alert.message.contains("app.kt"))
        assertEquals(TurnGitSyncAlertAction.continuePendingGitOperation, alert.buttons.last().action)
    }

    @Test
    fun createManagedWorktree_transferFromDifferentDirtyBranchBlocks() {
        val alert =
            TurnGitPreflightPolicy.alertFor(
                status = status(currentBranch = "feature", isDirty = true),
                branches = branches(currentBranch = "feature", defaultBranch = "main"),
                operation =
                    TurnGitPreflightOperation.createManagedWorktree(
                        baseBranch = "main",
                        changeTransfer = GitWorktreeChangeTransferMode.move,
                    ),
            )

        assertNotNull(alert)
        assertEquals(TurnGitSyncAlertAction.dismissOnly, alert.buttons.single().action)
    }

    @Test
    fun pushBehindRemoteOffersPullRebase() {
        val alert =
            TurnGitPreflightPolicy.alertFor(
                status = status(state = "behind_only", behindCount = 2),
                branches = branches(),
                operation = TurnGitPreflightOperation.push,
            )

        assertNotNull(alert)
        assertEquals("Remote update available", alert.title)
        assertEquals(TurnGitSyncAlertAction.pullRebase, alert.buttons.last().action)
    }

    @Test
    fun createPullRequestWhileDivergedOffersPullRebase() {
        val alert =
            TurnGitPreflightPolicy.alertFor(
                status = status(state = "diverged", aheadCount = 1, behindCount = 1),
                branches = branches(),
                operation = TurnGitPreflightOperation.createPullRequest,
            )

        assertNotNull(alert)
        assertEquals("Remote history diverged", alert.title)
        assertEquals(TurnGitSyncAlertAction.pullRebase, alert.buttons.last().action)
    }

    @Test
    fun dirtyAndBehindIsInformationalOnly() {
        val alert =
            TurnGitPreflightPolicy.alertFor(
                status = status(state = "dirty_and_behind", isDirty = true, behindCount = 3),
                branches = branches(),
                operation = TurnGitPreflightOperation.push,
            )

        assertNotNull(alert)
        assertEquals("Local changes + remote update", alert.title)
        assertEquals(TurnGitSyncAlertAction.dismissOnly, alert.buttons.last().action)
    }

    @Test
    fun createBranchWarnsWhenDefaultBranchHasLocalOnlyCommits() {
        val alert =
            TurnGitPreflightPolicy.alertFor(
                status = status(currentBranch = "main", localOnlyCommitCount = 2, state = "ahead_only", aheadCount = 2),
                branches = branches(currentBranch = "main", defaultBranch = "main"),
                operation = TurnGitPreflightOperation.createBranch("codex/topic"),
            )

        assertNotNull(alert)
        assertEquals("Local commits stay on main", alert.title)
        assertTrue(alert.message.contains("2 local commits"))
        assertEquals(TurnGitSyncAlertAction.continuePendingGitOperation, alert.buttons.last().action)
    }

    @Test
    fun branchCheckedOutElsewhereWithoutPathBlocksCheckout() {
        val alert =
            TurnGitPreflightPolicy.alertFor(
                status = status(),
                branches = branches(branchesCheckedOutElsewhere = setOf("feature")),
                operation = TurnGitPreflightOperation.checkoutBranch("feature"),
            )

        assertNotNull(alert)
        assertEquals("Branch open in another worktree", alert.title)
        assertEquals(TurnGitSyncAlertAction.dismissOnly, alert.buttons.single().action)
    }

    @Test
    fun branchCheckedOutElsewhereWithPathDoesNotBlockCheckoutPolicy() {
        val alert =
            TurnGitPreflightPolicy.alertFor(
                status = status(),
                branches =
                    branches(
                        branchesCheckedOutElsewhere = setOf("feature"),
                        worktreePathByBranch = mapOf("feature" to "/repo-feature"),
                    ),
                operation = TurnGitPreflightOperation.checkoutBranch("feature"),
            )

        assertNull(alert)
    }

    @Test
    fun discardRuntimeChangesWarnsAboutAheadCommits() {
        val alert =
            TurnGitPreflightPolicy.alertFor(
                status = status(aheadCount = 2),
                branches = branches(),
                operation = TurnGitPreflightOperation.discardRuntimeChanges,
            )

        assertNotNull(alert)
        assertTrue(alert.message.contains("2 local commits"))
        assertEquals(TurnGitSyncAlertAction.discardRuntimeChanges, alert.buttons.last().action)
    }

    private fun status(
        currentBranch: String = "feature",
        isDirty: Boolean = false,
        aheadCount: Int = 0,
        behindCount: Int = 0,
        localOnlyCommitCount: Int = 0,
        state: String = "up_to_date",
        files: List<GitChangedFile> = emptyList(),
    ): GitRepoSyncResult =
        GitRepoSyncResult(
            repoRoot = "/repo",
            currentBranch = currentBranch,
            trackingBranch = "origin/$currentBranch",
            isDirty = isDirty,
            aheadCount = aheadCount,
            behindCount = behindCount,
            localOnlyCommitCount = localOnlyCommitCount,
            state = state,
            canPush = aheadCount > 0,
            isPublishedToRemote = true,
            files = files,
            repoDiffTotals = null,
        )

    private fun branches(
        currentBranch: String = "feature",
        defaultBranch: String = "main",
        branchesCheckedOutElsewhere: Set<String> = emptySet(),
        worktreePathByBranch: Map<String, String> = emptyMap(),
        status: GitRepoSyncResult? = null,
    ): GitBranchesWithStatusResult =
        GitBranchesWithStatusResult(
            branches = listOf("main", "feature"),
            branchesCheckedOutElsewhere = branchesCheckedOutElsewhere,
            worktreePathByBranch = worktreePathByBranch,
            localCheckoutPath = "/repo",
            currentBranch = currentBranch,
            defaultBranch = defaultBranch,
            status = status,
        )
}
