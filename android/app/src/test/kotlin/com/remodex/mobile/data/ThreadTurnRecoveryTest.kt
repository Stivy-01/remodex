package com.remodex.mobile.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadTurnRecoveryTest {
    @Test
    fun actionFor_prefersRunningTurnId() {
        assertEquals(
            ThreadTurnRecoveryAction.Running("turn-1"),
            ThreadTurnRecovery.actionFor(
                ThreadTurnInterruptSnapshot(
                    interruptibleTurnId = "turn-1",
                    hasInterruptibleTurnWithoutId = true,
                    latestTurnId = "turn-1",
                ),
            ),
        )
    }

    @Test
    fun actionFor_usesFallbackWhenRuntimeHasRunningTurnWithoutId() {
        assertEquals(
            ThreadTurnRecoveryAction.ProtectedFallback,
            ThreadTurnRecovery.actionFor(
                ThreadTurnInterruptSnapshot(
                    interruptibleTurnId = null,
                    hasInterruptibleTurnWithoutId = true,
                    latestTurnId = null,
                ),
            ),
        )
    }

    @Test
    fun actionFor_idleWhenNoInterruptibleTurnExists() {
        assertEquals(
            ThreadTurnRecoveryAction.Idle,
            ThreadTurnRecovery.actionFor(
                ThreadTurnInterruptSnapshot(
                    interruptibleTurnId = null,
                    hasInterruptibleTurnWithoutId = false,
                    latestTurnId = "turn-complete",
                ),
            ),
        )
    }
}
