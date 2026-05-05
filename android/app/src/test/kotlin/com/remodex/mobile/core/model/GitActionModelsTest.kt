package com.remodex.mobile.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitActionModelsTest {
    @Test
    fun gitCreateManagedWorktreeResult_parsesBridgePayload() {
        val json =
            mapOf(
                "worktreePath" to JSONValue.Str("/tmp/wt"),
                "alreadyExisted" to JSONValue.Bool(true),
                "baseBranch" to JSONValue.Str("main"),
                "headMode" to JSONValue.Str("detached"),
                "transferredChanges" to JSONValue.Bool(false),
            )
        val r = GitCreateManagedWorktreeResult.fromJson(json)
        assertEquals("/tmp/wt", r.worktreePath)
        assertTrue(r.alreadyExisted)
        assertEquals("main", r.baseBranch)
        assertEquals("detached", r.headMode)
        assertFalse(r.transferredChanges)
    }

    @Test
    fun gitManagedHandoffTransferResult_parsesBridgePayload() {
        val json =
            mapOf(
                "success" to JSONValue.Bool(true),
                "targetPath" to JSONValue.Str("/dest"),
                "transferredChanges" to JSONValue.Bool(true),
            )
        val r = GitManagedHandoffTransferResult.fromJson(json)
        assertTrue(r.success)
        assertEquals("/dest", r.targetPath)
        assertTrue(r.transferredChanges)
    }

    @Test
    fun gitBranchesWithStatusResult_includesNestedStatus() {
        val status =
            mapOf(
                "repoRoot" to JSONValue.Str("/repo"),
                "branch" to JSONValue.Str("feat"),
                "dirty" to JSONValue.Bool(false),
                "ahead" to JSONValue.NumLong(0),
                "behind" to JSONValue.NumLong(0),
                "localOnlyCommitCount" to JSONValue.NumLong(0),
                "state" to JSONValue.Str("up_to_date"),
                "canPush" to JSONValue.Bool(false),
                "publishedToRemote" to JSONValue.Bool(true),
                "files" to JSONValue.Arr(emptyList()),
            )
        val json =
            mapOf(
                "branches" to JSONValue.Arr(listOf(JSONValue.Str("feat"), JSONValue.Str("main"))),
                "branchesCheckedOutElsewhere" to JSONValue.Arr(emptyList()),
                "worktreePathByBranch" to JSONValue.Obj(emptyMap()),
                "localCheckoutPath" to JSONValue.Str("/repo"),
                "current" to JSONValue.Str("feat"),
                "default" to JSONValue.Str("main"),
                "status" to JSONValue.Obj(status),
            )
        val r = GitBranchesWithStatusResult.fromJson(json)
        assertEquals(listOf("feat", "main"), r.branches)
        assertEquals("/repo", r.status?.repoRoot)
        assertEquals("feat", r.currentBranch)
    }
}
