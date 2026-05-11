package com.remodex.mobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Light “agent minimal” chrome: warm background + white/frosted controls.
 * Dark theme bakes the old translucent graphite tone into an opaque fill so
 * content behind the composer cannot read as a rectangular band.
 */
@Composable
internal fun isAgentLightChrome(): Boolean =
    MaterialTheme.colorScheme.background.luminance() >= 0.43f

/**
 * Main turn composer shell: frosted white, hairline border, soft shadow (light only).
 */
@Composable
internal fun RemodexComposerCapsuleChrome(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RemodexComposerCapsuleShape
    if (isAgentLightChrome()) {
        val fill = AgentLightColors.Surface.copy(alpha = 0.93f).compositeOver(MaterialTheme.colorScheme.background)
        Box(
            modifier =
                modifier
                    .shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.06f),
                        spotColor = Color.Black.copy(alpha = 0.06f),
                    )
                    .clip(shape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.62f), shape)
                    .background(fill, shape),
        ) {
            content()
        }
    } else {
        val fill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
            .compositeOver(MaterialTheme.colorScheme.background)
        Box(
            modifier =
                modifier
                    .shadow(
                        elevation = 3.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.12f),
                        spotColor = Color.Black.copy(alpha = 0.12f),
                    )
                    .clip(shape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape)
                    .background(fill, shape),
        ) {
            content()
        }
    }
}
