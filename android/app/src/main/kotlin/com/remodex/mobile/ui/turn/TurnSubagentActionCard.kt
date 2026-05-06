package com.remodex.mobile.ui.turn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import com.remodex.mobile.R
import com.remodex.mobile.data.TurnSubagentAgentPresentation
import com.remodex.mobile.data.TurnTimelineRichContentCache
import com.remodex.mobile.ui.theme.RemodexGitAddition

@Composable
internal fun TurnSubagentActionCard(
    message: com.remodex.mobile.core.model.CodexMessage,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val preview = TurnTimelineRichContentCache.parseSubagent(message)
    val colors = MaterialTheme.colorScheme
    val agentCount = preview.agents.size
    val heading =
        when {
            agentCount > 0 -> "Spawning $agentCount agent${if (agentCount == 1) "" else "s"}"
            else -> stringResource(R.string.turn_timeline_kind_subagent)
        }
    var isExpanded by remember(message.id, preview.rawText) { mutableStateOf(true) }
    var expandedAgentThreadIds by remember(message.id, preview.rawText) { mutableStateOf(emptySet<String>()) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        SubagentHeaderRow(
            heading = preview.headline.ifBlank { heading },
            expanded = isExpanded,
            colors = colors,
            onToggle = { isExpanded = !isExpanded },
        )

        if (preview.agents.isNotEmpty()) {
            if (isExpanded) {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            when (preview.normalizedTool) {
                                "wait", "waitagent", "resumeagent" -> 9.dp
                                "spawnagent" -> 2.dp
                                else -> 4.dp
                            },
                        ),
                ) {
                    preview.agents.forEach { agent ->
                        val agentExpanded = agent.threadId in expandedAgentThreadIds
                        TurnSubagentAgentRow(
                            agent = agent,
                            expanded = agentExpanded,
                            onToggleExpanded = {
                                expandedAgentThreadIds =
                                    if (agentExpanded) {
                                        expandedAgentThreadIds - agent.threadId
                                    } else {
                                        expandedAgentThreadIds + agent.threadId
                                    }
                            },
                            statusText =
                                readableSubagentStatus(
                                    tool = preview.normalizedTool,
                                    rawStatus = agent.status ?: preview.status,
                                ),
                            modelLabel =
                                if (preview.normalizedTool == "spawnagent") {
                                    null
                                } else {
                                    agent.model
                                },
                            colors = colors,
                        )
                    }
                }
            }
        } else {
            TurnSubagentFallbackRow(
                title = preview.headline,
                message = preview.summaryText.takeIf { it != preview.headline },
                contentColor = contentColor,
            )
        }

        preview.promptText?.let { prompt ->
            Text(
                text = prompt,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = contentColor.copy(alpha = 0.58f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SubagentHeaderRow(
    heading: String,
    expanded: Boolean,
    colors: androidx.compose.material3.ColorScheme,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = heading,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = colors.onSurfaceVariant.copy(alpha = 0.58f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter =
                painterResource(
                    if (expanded) {
                        LucideR.drawable.lucide_ic_chevron_down
                    } else {
                        LucideR.drawable.lucide_ic_chevron_right
                    },
                ),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = colors.onSurfaceVariant.copy(alpha = 0.42f),
        )
    }
}

@Composable
private fun TurnSubagentAgentRow(
    agent: TurnSubagentAgentPresentation,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    statusText: String,
    modelLabel: String?,
    colors: androidx.compose.material3.ColorScheme,
) {
    val labelParts = parseSubagentLabel(agent.label)
    val agentColor = subagentAccentColor(labelParts.nickname, colors.secondary)
    val message = agent.message?.trim()?.takeIf { it.isNotEmpty() } ?: statusText

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = labelParts.nickname,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = agentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (labelParts.roleSuffix.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = labelParts.roleSuffix,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = colors.onSurface.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!modelLabel.isNullOrBlank()) {
                Text(
                    text = modelLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.onSurfaceVariant.copy(alpha = 0.68f),
                    maxLines = 1,
                )
            }
            Icon(
                painter =
                    painterResource(
                        if (expanded) {
                            LucideR.drawable.lucide_ic_chevron_down
                        } else {
                            LucideR.drawable.lucide_ic_chevron_right
                        },
                    ),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = colors.onSurfaceVariant.copy(alpha = 0.42f),
            )
        }
        if (expanded) {
            SubagentAgentDetails(
                agent = agent,
                statusText = statusText,
                colors = colors,
            )
        }
    }
}

@Composable
private fun SubagentAgentDetails(
    agent: TurnSubagentAgentPresentation,
    statusText: String,
    colors: androidx.compose.material3.ColorScheme,
) {
    Column(
        modifier = Modifier.padding(start = 10.dp, top = 1.dp, bottom = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        agent.prompt?.let {
            SubagentDetailLine(label = "Prompt", value = it, colors = colors)
        }
        agent.message?.let {
            SubagentDetailLine(label = "Latest", value = it, colors = colors)
        }
        SubagentDetailLine(label = "Status", value = statusText, colors = colors)
        SubagentDetailLine(label = "Thread", value = agent.threadId, colors = colors)
    }
}

@Composable
private fun SubagentDetailLine(
    label: String,
    value: String,
    colors: androidx.compose.material3.ColorScheme,
) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = colors.onSurfaceVariant.copy(alpha = 0.62f),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TurnSubagentFallbackRow(
    title: String,
    message: String?,
    contentColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                ),
            color = contentColor.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = contentColor.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class SubagentLabelParts(
    val nickname: String,
    val roleSuffix: String,
)

private fun parseSubagentLabel(label: String): SubagentLabelParts {
    val trimmed = label.trim()
    val open = trimmed.lastIndexOf('[')
    if (!trimmed.endsWith("]") || open < 0) {
        return SubagentLabelParts(trimmed.ifEmpty { "Agent" }, "")
    }
    val nickname = trimmed.substring(0, open).trim().ifEmpty { "Agent" }
    val role = trimmed.substring(open + 1, trimmed.length - 1).trim()
    return SubagentLabelParts(
        nickname = nickname,
        roleSuffix = if (role.isEmpty()) "" else "($role)",
    )
}

private fun subagentAccentColor(name: String, fallback: Color): Color {
    val accents =
        listOf(
            Color(0xFFE64D4D),
            RemodexGitAddition,
            Color(0xFF668CF2),
            Color(0xFFD99940),
            Color(0xFFB373D9),
            Color(0xFF40C7D1),
            Color(0xFFE68099),
            Color(0xFFA6BF4D),
        )
    if (name.isBlank()) return fallback
    val index = kotlin.math.abs(name.hashCode()) % accents.size
    return accents[index]
}

private fun readableSubagentStatus(
    tool: String?,
    rawStatus: String?,
): String {
    val label = normalizedStatusLabel(rawStatus)
    return when (tool) {
        "spawnagent" ->
            when (label) {
                "running" -> "Starting child thread"
                "completed" -> "Child thread created"
                "failed" -> "Could not create child thread"
                "stopped" -> "Spawn interrupted"
                "queued" -> "Queued for spawn"
                else -> "Preparing child thread"
            }
        "wait", "waitagent" ->
            when (label) {
                "running" -> "Still working"
                "completed" -> "Finished"
                "failed" -> "Finished with error"
                "stopped" -> "Stopped early"
                "queued" -> "Queued"
                else -> "Waiting for updates"
            }
        "sendinput" ->
            when (label) {
                "running" -> "Working on new instructions"
                "completed" -> "Processed the update"
                "failed" -> "Update failed"
                "stopped" -> "Update interrupted"
                "queued" -> "Queued update"
                else -> "Instructions sent"
            }
        "resumeagent" ->
            when (label) {
                "running" -> "Back to work"
                "completed" -> "Resumed and completed"
                "failed" -> "Resume failed"
                "stopped" -> "Resume interrupted"
                "queued" -> "Queued to resume"
                else -> "Resuming agent"
            }
        "closeagent" ->
            when (label) {
                "running" -> "Closing"
                "completed" -> "Closed"
                "failed" -> "Close failed"
                "stopped" -> "Close interrupted"
                "queued" -> "Queued to close"
                else -> "Closing agent"
            }
        else ->
            when (label) {
                "running" -> "Working now"
                "completed" -> "Completed"
                "failed" -> "Ended with error"
                "stopped" -> "Stopped"
                "queued" -> "Queued"
                else -> "Idle"
            }
    }
}

private fun normalizedStatusLabel(rawStatus: String?): String {
    return when (
        rawStatus
            ?.trim()
            ?.lowercase()
            ?.replace("_", "")
            ?.replace("-", "")
            ?: "unknown"
    ) {
        "running", "inprogress" -> "running"
        "completed", "done", "finished", "success" -> "completed"
        "failed", "error", "errored" -> "failed"
        "stopped", "cancelled", "canceled", "interrupted" -> "stopped"
        "queued", "pending" -> "queued"
        else -> "idle"
    }
}
