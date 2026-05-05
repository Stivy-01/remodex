package com.remodex.mobile.ui.turn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CommandExecutionDetails

private const val GroupChevronCollapsed = 0f
private const val GroupChevronExpanded = 90f

/**
 * Collapsed header + optional stack of [TurnMessageRow] for a long run of identical tool rows.
 */
@Composable
internal fun TurnTimelineGroupedRunsRow(
    groupKey: String,
    collapsedTitle: String,
    messages: List<CodexMessage>,
    commandExecutionDetailsByItemId: Map<String, CommandExecutionDetails>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(groupKey) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val onBubble = colors.onSurfaceVariant
    val expandLabel = stringResource(R.string.turn_timeline_group_toggle_expand_cd, messages.size)
    val collapseLabel = stringResource(R.string.turn_timeline_group_toggle_collapse_cd)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .clickable {
                        expanded = !expanded
                    }
                    .semantics {
                        contentDescription =
                            if (expanded) collapseLabel else expandLabel
                        role = Role.Button
                    }
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = collapsedTitle,
                style = MaterialTheme.typography.titleMedium,
                color = onBubble.copy(alpha = 0.92f),
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                modifier =
                    Modifier
                        .size(22.dp)
                        .rotate(
                            if (expanded) GroupChevronExpanded else GroupChevronCollapsed,
                        ),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (msg in messages) {
                    TurnMessageRow(
                        message = msg,
                        commandExecutionDetails = msg.itemId?.let { commandExecutionDetailsByItemId[it] },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
