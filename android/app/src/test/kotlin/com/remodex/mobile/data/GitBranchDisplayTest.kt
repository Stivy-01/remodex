package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.GitBranchesWithStatusResult
import com.remodex.mobile.core.model.GitRepoSyncResult
import com.remodex.mobile.services.GitActionsError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitBranchDisplayTest {
    @Test
    fun gitWorkingDirectoryForGitActions_nullThread() {
        assertNull(null.gitWorkingDirectoryForGitActions())
    }

    @Test
    fun gitWorkingDirectoryForGitActions_requiresNormalizedPath() {
        assertNull(CodexThread(id = "t1", cwd = "   ").gitWorkingDirectoryForGitActions())
    }

    @Test
    fun gitWorkingDirectoryForGitActions_excludesPseudoDefaultBucket() {
        assertNull(CodexThread(id = "t1", cwd = "_default").gitWorkingDirectoryForGitActions())
    }

    @Test
    fun gitWorkingDirectoryForGitActions_returnsNormalizedPath() {
        assertEquals(
            "/Users/me/proj",
            CodexThread(id = "t1", cwd = "/Users/me/proj///").gitWorkingDirectoryForGitActions(),
        )
    }

    @Test
    fun summaryFrom_sortsElsewhereAndReadsDirty() {
        val result =
            GitBranchesWithStatusResult(
                branches = listOf("main", "feat"),
                branchesCheckedOutElsewhere = setOf("z", "a"),
                worktreePathByBranch = emptyMap(),
                localCheckoutPath = null,
                currentBranch = "feat",
                defaultBranch = "main",
                status =
                    GitRepoSyncResult(
                        repoRoot = "/r",
                        currentBranch = "feat",
                        trackingBranch = null,
                        isDirty = true,
                        aheadCount = 0,
                        behindCount = 0,
                        localOnlyCommitCount = 0,
                        state = "",
                        canPush = false,
                        isPublishedToRemote = false,
                        files = emptyList(),
                        repoDiffTotals = null,
                    ),
            )
        val summary = GitBranchDisplayMapper.summaryFrom(result)
        assertEquals("feat", summary.currentBranch)
        assertEquals("main", summary.defaultBranch)
        assertEquals(listOf("a", "z"), summary.branchesCheckedOutElsewhere)
        assertEquals(listOf("feat", "main"), summary.branches)
        assertEquals(true, summary.isDirty)
    }

    @Test
    fun summaryFrom_dirtyFalseWhenStatusMissing() {
        val result =
            GitBranchesWithStatusResult(
                branches = emptyList(),
                branchesCheckedOutElsewhere = emptySet(),
                worktreePathByBranch = emptyMap(),
                localCheckoutPath = null,
                currentBranch = null,
                defaultBranch = null,
                status = null,
            )
        val summary = GitBranchDisplayMapper.summaryFrom(result)
        assertEquals(false, summary.isDirty)
        assertEquals(emptyList(), summary.branches)
    }

    @Test
    fun userVisibleMessage_mapsDisconnected() {
        val msg = GitBranchDisplayMapper.userVisibleMessage(GitActionsError.Disconnected)
        assertEquals("Not connected to bridge.", msg)
    }
}
