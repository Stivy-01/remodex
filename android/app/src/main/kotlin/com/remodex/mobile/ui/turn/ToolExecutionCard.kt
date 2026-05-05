package com.remodex.mobile.ui.turn

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remodex.mobile.R
import com.remodex.mobile.core.model.ExecutionStatus
import com.remodex.mobile.core.model.ToolExecutionUi

@Composable
internal fun ToolExecutionCard(
    ui: ToolExecutionUi,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent =
        when (ui.status) {
            ExecutionStatus.Failed -> colors.error
            ExecutionStatus.Running -> colors.onSecondaryContainer
            ExecutionStatus.Cancelled -> colors.onSurfaceVariant
            ExecutionStatus.Completed -> colors.onPrimaryContainer
        }
    val openDetails = if (ui.isExpandable) onClick else null
    val expandable = openDetails != null
    val rowChevronSize = 22.dp
    val cdBase = stringResource(R.string.turn_tool_execution_card_cd)
    val cd =
        if (expandable) {
            "$cdBase, ${stringResource(R.string.turn_tool_execution_card_cd_open_details)}"
        } else {
            cdBase
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (expandable) {
                        Modifier
                            .semantics {
                                contentDescription = cd
                                role = Role.Button
                            }
                            .clickable(onClick = openDetails)
                    } else {
                        Modifier.semantics { contentDescription = cd }
                    },
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(TurnTimelineLeadSlotWidth),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = statusIcon(ui.status),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = accent,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = ui.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ui.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                Text(
                    text = sub,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            lineHeight = 18.sp,
                        ),
                    color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (expandable) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.turn_timeline_show_details),
                tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                modifier =
                    Modifier
                        .padding(start = 6.dp)
                        .size(rowChevronSize),
            )
        }
    }
}

private fun statusIcon(status: ExecutionStatus): ImageVector =
    when (status) {
        ExecutionStatus.Running -> Icons.Outlined.Schedule
        ExecutionStatus.Completed -> Icons.Outlined.CheckCircle
        ExecutionStatus.Failed -> Icons.Outlined.Error
        ExecutionStatus.Cancelled -> Icons.Outlined.Cancel
    }
