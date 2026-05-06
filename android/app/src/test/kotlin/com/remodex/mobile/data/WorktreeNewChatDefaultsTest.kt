package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexThread
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorktreeNewChatDefaultsTest {
    @Test
    fun baseProjectPath_prefersActiveThread() {
        val t1 = CodexThread(id = "a", cwd = "/foo", updatedAt = Instant.parse("2020-01-01T00:00:00Z"))
        val t2 = CodexThread(id = "b", cwd = "/bar", updatedAt = Instant.parse("2025-01-01T00:00:00Z"))
        assertEquals("/bar", WorktreeNewChatDefaults.baseProjectPath("b", listOf(t1, t2)))
    }

    @Test
    fun baseProjectPath_activeCloud_fallsBackToNewestWithPath() {
        val cloud = CodexThread(id = "c", cwd = null, updatedAt = Instant.parse("2025-06-01T00:00:00Z"))
        val local = CodexThread(id = "d", cwd = "/z", updatedAt = Instant.parse("2024-01-01T00:00:00Z"))
        assertEquals("/z", WorktreeNewChatDefaults.baseProjectPath("c", listOf(cloud, local)))
    }

    @Test
    fun baseProjectPath_rejectsDefaultBucket() {
        val def = CodexThread(id = "e", cwd = "_default", updatedAt = Instant.parse("2025-01-01T00:00:00Z"))
        assertNull(WorktreeNewChatDefaults.baseProjectPath("e", listOf(def)))
    }

    @Test
    fun baseBranch_prefersCurrentThenDefaultThenFirstNotElsewhere() {
        val s1 =
            GitBranchDisplaySummary(
                currentBranch = "topic",
                defaultBranch = "main",
                branchesCheckedOutElsewhere = listOf("main"),
                branches = listOf("main", "other", "topic"),
                worktreePathByBranch = emptyMap(),
                isDirty = false,
            )
        assertEquals("topic", WorktreeNewChatDefaults.baseBranch(s1))

        val s2 =
            GitBranchDisplaySummary(
                currentBranch = null,
                defaultBranch = "develop",
                branchesCheckedOutElsewhere = emptyList(),
                branches = listOf("a", "develop"),
                worktreePathByBranch = emptyMap(),
                isDirty = false,
            )
        assertEquals("develop", WorktreeNewChatDefaults.baseBranch(s2))

        val s3 =
            GitBranchDisplaySummary(
                currentBranch = "  ",
                defaultBranch = null,
                branchesCheckedOutElsewhere = listOf("main"),
                branches = listOf("feature", "main"),
                worktreePathByBranch = emptyMap(),
                isDirty = false,
            )
        assertEquals("feature", WorktreeNewChatDefaults.baseBranch(s3))
    }
}
