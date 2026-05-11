package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.RPCError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Parity: [CodexThreadProjectRoutingTests.swift](../../../../../CodexMobile/CodexMobileTests/CodexThreadProjectRoutingTests.swift)
 * (authoritative path merge, stale server, rollout-missing allowlist).
 */
class ThreadProjectRoutingTest {
    @Test
    fun applyAuthoritative_overridesStaleServerCwd() {
        val map = mutableMapOf("thread-1" to "/tmp/remodex-worktree")
        val server = CodexThread(id = "thread-1", title = "Source", cwd = "/tmp/remodex-local")
        val merged = applyAuthoritativeProjectPathMerge(server, map, true)
        assertEquals("/tmp/remodex-worktree", merged.gitWorkingDirectory)
        assertTrue(map.isNotEmpty(), "stays in flight until server matches")
    }

    @Test
    fun applyAuthoritative_clearsWhenServerMatches() {
        val map = mutableMapOf("thread-1" to "/tmp/remodex-worktree")
        val server = CodexThread(id = "thread-1", title = "Source", cwd = "/tmp/remodex-worktree")
        val merged = applyAuthoritativeProjectPathMerge(server, map, true)
        assertEquals("/tmp/remodex-worktree", merged.gitWorkingDirectory)
        assertTrue(map.isEmpty(), "iOS: treatAsServerState + match clears map")
    }

    @Test
    fun rolloutMissing_doesNotRollbackLocalRebind_inAllowlist() {
        val e =
            CodexServiceError.RpcFailure(
                RPCError(
                    code = -32600,
                    message = "no rollout found for thread id thread-1",
                    data = null,
                ),
            )
        assertTrue(shouldAllowProjectRebindWithoutResume(e))
    }

    @Test
    fun otherRpcErrorsNotAllowlist() {
        val e = CodexServiceError.RpcFailure(RPCError(-32600, "something else", null))
        assertFalse(shouldAllowProjectRebindWithoutResume(e))
    }

    @Test
    fun confirm_clearsWhenObservedPathMatches() {
        val map = mutableMapOf("thread-1" to "/tmp/wt")
        confirmAuthoritativeProjectPathIfNeeded("thread-1", "/tmp/wt", map)
        assertTrue(map.isEmpty())
    }

    @Test
    fun confirm_doesNotClearWhenObservedPathDiffers() {
        val map = mutableMapOf("thread-1" to "/tmp/wt")
        confirmAuthoritativeProjectPathIfNeeded("thread-1", "/tmp/local", map)
        assertNotNull(map["thread-1"])
    }

    @Test
    fun serverMatchSequence_clearsAuthoritative() {
        val map = mutableMapOf("thread-1" to "/tmp/wt")
        var t = CodexThread(id = "thread-1", title = "Source", cwd = "/tmp/local")
        t = applyAuthoritativeProjectPathMerge(t, map, true)
        assertEquals("/tmp/wt", t.gitWorkingDirectory)
        assertTrue(map.isNotEmpty())
        t = CodexThread(id = "thread-1", title = "Source", cwd = "/tmp/wt")
        t = applyAuthoritativeProjectPathMerge(t, map, true)
        assertEquals("/tmp/wt", t.gitWorkingDirectory)
        assertTrue(map.isEmpty(), "iOS: second treatAsServer state removes guard")
    }

    @Test
    fun noRolloutFileFound_isAllowlist() {
        val e = CodexServiceError.RpcFailure(RPCError(-1, "No rollout file found for cwd", null))
        assertTrue(shouldAllowProjectRebindWithoutResume(e))
    }

    @Test
    fun newThreadProjectPath_usesRequestedPathWhenServerOmitsCwd() {
        val thread = CodexThread(id = "thread-1", title = "New Thread", cwd = null)
        val patched = applyRequestedProjectPathForNewThread(thread, "/tmp/repo")
        assertEquals("/tmp/repo", patched.gitWorkingDirectory)
    }

    @Test
    fun newThreadProjectPath_usesRequestedPathWhenServerReturnsStaleCwd() {
        val thread = CodexThread(id = "thread-1", title = "New Thread", cwd = "/tmp/other")
        val patched = applyRequestedProjectPathForNewThread(thread, "/tmp/repo")
        assertEquals("/tmp/repo", patched.gitWorkingDirectory)
    }

    @Test
    fun newThreadProjectPath_ignoresInvalidRequestedPath() {
        val thread = CodexThread(id = "thread-1", title = "New Thread", cwd = "/tmp/server")
        val patched = applyRequestedProjectPathForNewThread(thread, "_default")
        assertEquals("/tmp/server", patched.gitWorkingDirectory)
    }
}
