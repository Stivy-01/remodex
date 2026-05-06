package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexThread
import kotlin.test.Test
import kotlin.test.assertEquals

class RunningThreadRefreshPolicyTest {
    @Test
    fun inactiveRunningThreadIds_excludesActiveAndUnavailableThreads() {
        val threads = listOf(CodexThread(id = "t1"), CodexThread(id = "t2"), CodexThread(id = "t3"))

        assertEquals(
            listOf("t2", "t3"),
            RunningThreadRefreshPolicy.inactiveRunningThreadIds(
                threads = threads,
                activeThreadId = "t1",
                runningTurnIdByThread = mapOf("t1" to "turn-1", "t2" to "turn-2", "missing" to "turn-x"),
                protectedRunningFallbackThreadIds = setOf("t3", "missing-fallback"),
            ),
        )
    }

    @Test
    fun inactiveRunningThreadIds_dedupesAndHonorsLimit() {
        val threads = listOf(CodexThread(id = "t1"), CodexThread(id = "t2"), CodexThread(id = "t3"))

        assertEquals(
            listOf("t1", "t2"),
            RunningThreadRefreshPolicy.inactiveRunningThreadIds(
                threads = threads,
                activeThreadId = null,
                runningTurnIdByThread = mapOf("t1" to "turn-1", "t2" to "turn-2"),
                protectedRunningFallbackThreadIds = setOf("t1", "t3"),
                limit = 2,
            ),
        )
    }
}
