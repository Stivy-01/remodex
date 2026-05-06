package com.remodex.mobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Light “agent minimal” chrome: warm background + white/frosted controls.
 * Dark theme uses standard Material surfaces (graphite).
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
        Box(
            modifier =
                modifier
                    .shadow(
                        elevation = 12.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.07f),
                        spotColor = Color.Black.copy(alpha = 0.07f),
                    )
                    .clip(shape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .background(AgentLightColors.Surface.copy(alpha = 0.92f), shape),
        ) {
            content()
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp,
        ) {
            content()
        }
    }
}
