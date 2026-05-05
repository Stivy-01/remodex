package com.remodex.mobile.core.notification

import com.remodex.mobile.core.model.JSONValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TurnCompletionNotificationLogicTest {
    @Test
    fun parseTurnFailureMessage_requiresFailedStatus() {
        val p =
            mapOf(
                "turn" to
                    JSONValue.Obj(
                        mapOf(
                            "status" to JSONValue.Str("completed"),
                            "error" to
                                JSONValue.Obj(
                                    mapOf("message" to JSONValue.Str("x")),
                                ),
                        ),
                    ),
            )
        assertNull(TurnCompletionNotificationLogic.parseTurnFailureMessage(p))
    }

    @Test
    fun parseTurnFailureMessage_readsNestedError() {
        val p =
            mapOf(
                "turn" to
                    JSONValue.Obj(
                        mapOf(
                            "status" to JSONValue.Str("failed"),
                            "error" to
                                JSONValue.Obj(
                                    mapOf("message" to JSONValue.Str("disk full")),
                                ),
                        ),
                    ),
            )
        assertEquals("disk full", TurnCompletionNotificationLogic.parseTurnFailureMessage(p))
    }

    @Test
    fun parseTurnTerminalState_failedWhenFailureMessage() {
        val p = mapOf("status" to JSONValue.Str("completed"))
        assertEquals(
            TurnTerminalStateForNotification.Failed,
            TurnCompletionNotificationLogic.parseTurnTerminalState(p, "oops"),
        )
    }

    @Test
    fun parseTurnTerminalState_stoppedWhenInterrupted() {
        val p = mapOf("status" to JSONValue.Str("interrupted"))
        assertEquals(
            TurnTerminalStateForNotification.Stopped,
            TurnCompletionNotificationLogic.parseTurnTerminalState(p, null),
        )
    }

    @Test
    fun attentionKind_skipsStopped() {
        assertNull(
            TurnCompletionNotificationLogic.attentionKindFromTerminalState(
                TurnTerminalStateForNotification.Stopped,
            ),
        )
        assertEquals(
            RunCompletionAttentionKind.Completed,
            TurnCompletionNotificationLogic.attentionKindFromTerminalState(
                TurnTerminalStateForNotification.Completed,
            ),
        )
    }

    @Test
    fun normalizeThreadStatusType_trimsAndLowercases() {
        assertEquals("systemerror", TurnCompletionNotificationLogic.normalizeThreadStatusType(" System_Error "))
    }
}
