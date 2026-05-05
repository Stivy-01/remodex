package com.remodex.mobile.ui.turn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.data.TurnTimelineRichContentCache

@Composable
internal fun TurnSubagentActionCard(
    message: CodexMessage,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val preview = TurnTimelineRichContentCache.parseSubagent(message)
    val colors = MaterialTheme.colorScheme

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(colors.primaryContainer.copy(alpha = 0.16f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = colors.onPrimaryContainer.copy(alpha = 0.92f),
            )
            Column(modifier = Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.turn_timeline_kind_subagent),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimaryContainer,
                )
                Text(
                    text = preview.headline,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.9f),
                )
            }
        }

        Text(
            text = preview.summaryText,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.88f),
        )

        preview.promptText?.let { prompt ->
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onPrimaryContainer.copy(alpha = 0.84f),
            )
        }

        if (preview.agents.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                preview.agents.forEachIndexed { index, agent ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(colors.surface.copy(alpha = 0.42f))
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = agent.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurface,
                            )
                            agent.status?.let { status ->
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor.copy(alpha = 0.76f),
                                )
                            }
                        }
                        val metadata =
                            buildList {
                                agent.role?.let { add(it) }
                                agent.model?.let { add(it) }
                            }.joinToString(" | ")
                        if (metadata.isNotBlank()) {
                            Text(
                                text = metadata,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.76f),
                            )
                        }
                        agent.message?.let { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    if (index < preview.agents.lastIndex) {
                        HorizontalDivider(color = colors.onPrimaryContainer.copy(alpha = 0.08f))
                    }
                }
            }
        } else if (preview.rawText.isNotBlank()) {
            Text(
                text = preview.rawText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onPrimaryContainer.copy(alpha = 0.86f),
            )
        }
    }
}
