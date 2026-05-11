package com.remodex.mobile.ui.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatScrollPacingTest {
    @Test
    fun slowDragKeepsNativeSensitivity() {
        assertEquals(
            expected = 20f,
            actual = dampScrollDelta(delta = 20f, speedPxPerSecond = 250f),
            absoluteTolerance = 0.0001f,
        )
    }

    @Test
    fun veryFastDragKeepsMinimumSensitivity() {
        assertEquals(
            expected = 14f,
            actual = dampScrollDelta(delta = 20f, speedPxPerSecond = 7000f),
            absoluteTolerance = 0.0001f,
        )
    }

    @Test
    fun fastDragIsProgressivelyDampedWithoutStopping() {
        val damped = dampScrollDelta(delta = -20f, speedPxPerSecond = 3500f)

        assertTrue(damped < -14f)
        assertTrue(damped > -20f)
    }
}
