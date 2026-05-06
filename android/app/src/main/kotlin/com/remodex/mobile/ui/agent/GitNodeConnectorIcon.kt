package com.remodex.mobile.ui.agent

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Git-actions menu affordance: hollow circle with horizontal connectors (timeline / node glyph),
 * matching the in-app reference for repo git controls.
 */
@Composable
fun GitNodeConnectorIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val cx = w / 2f
        val r = minOf(w, h) * 0.19f
        val sw = maxOf(1.4f, minOf(w, h) * 0.075f)
        drawLine(
            color = tint,
            start = Offset(0f, cy),
            end = Offset((cx - r).coerceAtLeast(0f), cy),
            strokeWidth = sw,
        )
        drawCircle(
            color = tint,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = sw),
        )
        drawLine(
            color = tint,
            start = Offset((cx + r).coerceAtMost(w), cy),
            end = Offset(w, cy),
            strokeWidth = sw,
        )
    }
}
