package com.remodex.mobile.ui.turn

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import com.remodex.mobile.R
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun VoiceRecordingCapsule(
    audioLevels: List<Float>,
    durationSeconds: Double,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingRecordingDot()
            RecordingWaveform(
                audioLevels = audioLevels,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(18.dp),
            )
            Text(
                text = formatRecordingDuration(durationSeconds),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(18.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_x),
                        contentDescription = stringResource(R.string.turn_voice_cancel_recording_cd),
                        modifier = Modifier.size(9.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PulsingRecordingDot() {
    val transition = rememberInfiniteTransition(label = "voice-recording-dot")
    val opacity by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "opacity",
    )
    Box(
        modifier =
            Modifier
                .size(6.dp)
                .alpha(opacity)
                .background(MaterialTheme.colorScheme.onSurface, CircleShape),
    )
}

@Composable
private fun RecordingWaveform(
    audioLevels: List<Float>,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier) {
        val spacingPx = 1.5.dp.toPx()
        val idealBarWidthPx = 2.dp.toPx()
        val slotCount = max(1, ((size.width + spacingPx) / (idealBarWidthPx + spacingPx)).toInt())
        val levels = displayedLevels(audioLevels, slotCount)
        val totalSpacing = (slotCount - 1) * spacingPx
        val barWidth = max(1f, (size.width - totalSpacing) / slotCount)
        val minHeight = 2.dp.toPx()
        val maxHeight = size.height
        val midY = size.height / 2f

        levels.forEachIndexed { index, level ->
            val clamped = level.coerceIn(0f, 1f)
            val height = minHeight + (maxHeight - minHeight) * clamped
            val x = index * (barWidth + spacingPx)
            drawRoundRect(
                color = barColor.copy(alpha = 0.15f + clamped * 0.65f),
                topLeft = Offset(x, midY - height / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            )
        }
    }
}

private fun displayedLevels(
    audioLevels: List<Float>,
    slotCount: Int,
): List<Float> {
    if (audioLevels.isEmpty()) return List(slotCount) { 0f }
    val tail = audioLevels.takeLast(slotCount * 3)
    if (tail.size <= slotCount) {
        return List(slotCount - tail.size) { 0f } + tail
    }
    return List(slotCount) { index ->
        val start = ((index.toDouble() / slotCount) * tail.size).toInt()
        val end = max(start + 1, (((index + 1).toDouble() / slotCount) * tail.size).toInt())
        tail.subList(start, min(end, tail.size)).maxOrNull() ?: 0f
    }
}

private fun formatRecordingDuration(durationSeconds: Double): String {
    val totalSeconds = durationSeconds.toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
