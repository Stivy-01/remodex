package com.remodex.mobile.ui.agent

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs

internal fun dampScrollDelta(
    delta: Float,
    speedPxPerSecond: Float,
    lowSpeedPxPerSecond: Float = 500f,
    highSpeedPxPerSecond: Float = 6000f,
    minSensitivity: Float = 0.70f,
    maxSensitivity: Float = 1.00f,
): Float {
    val t =
        ((speedPxPerSecond - lowSpeedPxPerSecond) /
            (highSpeedPxPerSecond - lowSpeedPxPerSecond))
            .coerceIn(0f, 1f)

    val eased = t * t
    val sensitivity = maxSensitivity - ((maxSensitivity - minSensitivity) * eased)

    return delta * sensitivity
}

@Composable
internal fun rememberChatScrollPacingNestedScrollConnection(
    enabled: Boolean = true,
    lowSpeedPxPerSecond: Float = 500f,
    highSpeedPxPerSecond: Float = 6000f,
    minSensitivity: Float = 0.70f,
    maxSensitivity: Float = 1.00f,
): NestedScrollConnection {
    var lastTimeNanos by remember { mutableLongStateOf(0L) }

    return remember(
        enabled,
        lowSpeedPxPerSecond,
        highSpeedPxPerSecond,
        minSensitivity,
        maxSensitivity,
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!enabled) return Offset.Zero
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                val now = System.nanoTime()
                val dtSeconds =
                    if (lastTimeNanos == 0L) {
                        0f
                    } else {
                        (now - lastTimeNanos) / 1_000_000_000f
                    }
                lastTimeNanos = now

                if (dtSeconds <= 0f) return Offset.Zero

                val speedPxPerSecond = abs(available.y) / dtSeconds
                val dampedY =
                    dampScrollDelta(
                        delta = available.y,
                        speedPxPerSecond = speedPxPerSecond,
                        lowSpeedPxPerSecond = lowSpeedPxPerSecond,
                        highSpeedPxPerSecond = highSpeedPxPerSecond,
                        minSensitivity = minSensitivity,
                        maxSensitivity = maxSensitivity,
                    )

                return Offset(
                    x = 0f,
                    y = available.y - dampedY,
                )
            }
        }
    }
}

@Composable
internal fun rememberCappedChatFlingBehavior(
    maxFlingVelocityPxPerSecond: Float = 3000f,
): FlingBehavior {
    val defaultFlingBehavior = ScrollableDefaults.flingBehavior()

    return remember(defaultFlingBehavior, maxFlingVelocityPxPerSecond) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                val velocityLimit = maxFlingVelocityPxPerSecond.coerceAtLeast(0f)
                val cappedVelocity =
                    initialVelocity.coerceIn(
                        -velocityLimit,
                        velocityLimit,
                    )

                return with(defaultFlingBehavior) {
                    performFling(cappedVelocity)
                }
            }
        }
    }
}
