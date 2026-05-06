package com.remodex.mobile.data

import com.remodex.mobile.core.model.JSONValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadTurnSnapshotTest {
    @Test
    fun fromThreadObject_returnsLatestInterruptibleTurn() {
        val snapshot =
            ThreadTurnSnapshot.fromThreadObject(
                mapOf(
                    "turns" to
                        JSONValue.Arr(
                            listOf(
                                turn(id = "turn-done", status = "completed"),
                                turn(id = "turn-live", status = "in_progress"),
                            ),
                        ),
                ),
            )

        assertEquals("turn-live", snapshot.interruptibleTurnId)
        assertEquals("turn-live", snapshot.latestTurnId)
        assertFalse(snapshot.hasInterruptibleTurnWithoutId)
    }

    @Test
    fun fromThreadObject_preservesFallbackWhenRunningTurnHasNoId() {
        val snapshot =
            ThreadTurnSnapshot.fromThreadObject(
                mapOf(
                    "turns" to
                        JSONValue.Arr(
                            listOf(
                                turn(id = "turn-done", status = "completed"),
                                JSONValue.Obj(mapOf("status" to JSONValue.Str("running"))),
                            ),
                        ),
                ),
            )

        assertNull(snapshot.interruptibleTurnId)
        assertEquals("turn-done", snapshot.latestTurnId)
        assertTrue(snapshot.hasInterruptibleTurnWithoutId)
    }

    @Test
    fun fromThreadObject_ignoresCompletedLatestTurnForInterrupt() {
        val snapshot =
            ThreadTurnSnapshot.fromThreadObject(
                mapOf(
                    "turns" to
                        JSONValue.Arr(
                            listOf(
                                turn(id = "turn-running", status = "started"),
                                turn(id = "turn-done", status = "completed"),
                            ),
                        ),
                ),
            )

        assertEquals("turn-running", snapshot.interruptibleTurnId)
        assertEquals("turn-done", snapshot.latestTurnId)
        assertFalse(snapshot.hasInterruptibleTurnWithoutId)
    }

    @Test
    fun fromThreadObject_treatsStatuslessTurnAsInterruptibleLikeIos() {
        val snapshot =
            ThreadTurnSnapshot.fromThreadObject(
                mapOf(
                    "turns" to
                        JSONValue.Arr(
                            listOf(
                                turn(id = "turn-done", status = "completed"),
                                JSONValue.Obj(mapOf("id" to JSONValue.Str("turn-statusless"))),
                            ),
                        ),
                ),
            )

        assertEquals("turn-statusless", snapshot.interruptibleTurnId)
        assertEquals("turn-statusless", snapshot.latestTurnId)
        assertFalse(snapshot.hasInterruptibleTurnWithoutId)
    }

    private fun turn(
        id: String,
        status: String,
    ): JSONValue.Obj =
        JSONValue.Obj(
            mapOf(
                "id" to JSONValue.Str(id),
                "status" to JSONValue.Str(status),
            ),
        )
}
