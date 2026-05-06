package com.remodex.mobile.ui.turn

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R

/**
 * Compact expandable “Thinking” timeline row (parity with reference ThinkingBubble styling).
 */
@Composable
internal fun TurnThinkingTimelineRow(
    bodyMarkdown: String,
    contentColor: Color,
    useMarkdown: Boolean,
    isStreaming: Boolean,
    messageId: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(messageId) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val mute = colors.onSurfaceVariant

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.turn_timeline_kind_thinking),
                style = MaterialTheme.typography.labelLarge,
                color = mute.copy(alpha = 0.6f),
                maxLines = 1,
            )
            if (isStreaming) {
                Spacer(Modifier.width(8.dp))
                ThinkingStreamingStripe()
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = mute.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }
        if (expanded && bodyMarkdown.isNotBlank()) {
            if (useMarkdown) {
                TurnRichMarkdownBody(
                    markdown = bodyMarkdown,
                    contentColor = mute.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = bodyMarkdown,
                    style = MaterialTheme.typography.bodySmall,
                    color = mute.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun ThinkingStreamingStripe(modifier: Modifier = Modifier) {
    val tone = MaterialTheme.colorScheme.secondary
    Box(
        modifier =
            modifier
                .size(width = 32.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tone.copy(alpha = 0.55f)),
    )
}
