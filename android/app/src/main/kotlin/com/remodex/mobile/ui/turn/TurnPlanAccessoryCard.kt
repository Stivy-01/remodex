package com.remodex.mobile.ui.turn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.core.model.CodexPlanStep
import com.remodex.mobile.core.model.CodexPlanStepStatus

/**
 * J13 polish: compact active-plan card pinned above composer with status, progress rail, and inline details toggle.
 */
@Composable
internal fun TurnPlanAccessoryCard(
    message: CodexMessage,
    expanded: Boolean = false,
    onToggleExpanded: (() -> Unit)? = null,
    canApplyPlan: Boolean = false,
    onApplyPlan: (() -> Unit)? = null,
    onOpenDetailsSheet: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val snapshot = PlanAccessorySnapshot.fromMessage(message)
    val statusTint =
        when (snapshot.status) {
            PlanAccessoryStatus.Pending -> MaterialTheme.colorScheme.tertiary
            PlanAccessoryStatus.InProgress -> MaterialTheme.colorScheme.primary
            PlanAccessoryStatus.Completed -> MaterialTheme.colorScheme.secondary
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = CircleShape,
                    color = statusTint.copy(alpha = 0.18f),
                    modifier = Modifier.size(18.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = statusTint,
                        modifier = Modifier.size(8.dp),
                    ) {}
                }
                Text(
                    text = stringResource(R.string.turn_timeline_kind_plan),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text =
                        when (snapshot.status) {
                            PlanAccessoryStatus.Pending -> stringResource(R.string.turn_plan_status_pending)
                            PlanAccessoryStatus.InProgress -> stringResource(R.string.turn_plan_status_in_progress)
                            PlanAccessoryStatus.Completed -> stringResource(R.string.turn_plan_status_completed)
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusTint,
                )
                snapshot.progressText?.let { progress ->
                    Text(
                        text = "· $progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
                if (message.isStreaming) {
                    Text(
                        text = stringResource(R.string.turn_plan_status_streaming),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (onToggleExpanded != null) {
                    FilterChip(
                        selected = expanded,
                        onClick = onToggleExpanded,
                        label = {
                            Text(
                                text =
                                    if (expanded) {
                                        stringResource(R.string.turn_plan_hide_details)
                                    } else {
                                        stringResource(R.string.turn_plan_show_details)
                                    },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
                if (onApplyPlan != null) {
                    FilterChip(
                        selected = false,
                        onClick = onApplyPlan,
                        enabled = canApplyPlan,
                        label = {
                            Text(
                                text = stringResource(R.string.turn_plan_apply_action),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
                if (onOpenDetailsSheet != null) {
                    FilterChip(
                        selected = false,
                        onClick = onOpenDetailsSheet,
                        label = {
                            Text(
                                text = stringResource(R.string.turn_plan_open_sheet),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            Text(
                text = snapshot.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.92f),
                maxLines = if (expanded) 4 else 1,
            )

            if (snapshot.stepStatuses.isNotEmpty()) {
                StepStatusRail(statuses = snapshot.stepStatuses, accent = statusTint)
            }

            if (expanded) {
                snapshot.steps.forEachIndexed { index, step ->
                    val statusLabel =
                        when (step.status) {
                            CodexPlanStepStatus.inProgress -> stringResource(R.string.turn_plan_step_doing)
                            CodexPlanStepStatus.pending -> stringResource(R.string.turn_plan_step_todo)
                            CodexPlanStepStatus.completed -> stringResource(R.string.turn_plan_step_done)
                        }
                    Text(
                        text = "${index + 1}. [$statusLabel] ${step.step}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.95f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepStatusRail(
    statuses: List<CodexPlanStepStatus>,
    accent: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        statuses.forEach { status ->
            val tint =
                when (status) {
                    CodexPlanStepStatus.pending -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    CodexPlanStepStatus.inProgress -> accent.copy(alpha = 0.8f)
                    CodexPlanStepStatus.completed -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                }
            Surface(
                color = tint,
                shape = RoundedCornerShape(100),
                modifier = Modifier.size(width = 12.dp, height = 3.dp),
            ) {}
        }
    }
}

internal enum class PlanAccessoryStatus {
    Pending,
    InProgress,
    Completed,
}

internal data class PlanAccessorySnapshot(
    val summary: String,
    val status: PlanAccessoryStatus,
    val completedStepCount: Int,
    val totalStepCount: Int,
    val stepStatuses: List<CodexPlanStepStatus>,
    val steps: List<CodexPlanStep>,
) {
    val progressText: String?
        get() = if (totalStepCount > 0) "$completedStepCount/$totalStepCount" else null

    companion object {
        fun fromMessage(message: CodexMessage): PlanAccessorySnapshot {
            val steps = message.planState?.steps.orEmpty()
            val completed = steps.count { it.status == CodexPlanStepStatus.completed }
            return PlanAccessorySnapshot(
                summary = resolveSummary(message, steps),
                status = resolveStatus(steps, completed),
                completedStepCount = completed,
                totalStepCount = steps.size,
                stepStatuses = steps.map { it.status },
                steps = steps,
            )
        }

        private fun resolveStatus(
            steps: List<CodexPlanStep>,
            completedStepCount: Int,
        ): PlanAccessoryStatus {
            if (steps.any { it.status == CodexPlanStepStatus.inProgress }) return PlanAccessoryStatus.InProgress
            if (steps.isNotEmpty() && completedStepCount == steps.size) return PlanAccessoryStatus.Completed
            return PlanAccessoryStatus.Pending
        }

        private fun resolveSummary(
            message: CodexMessage,
            steps: List<CodexPlanStep>,
        ): String {
            steps.firstOrNull { it.status == CodexPlanStepStatus.inProgress }?.let { return it.step }
            steps.firstOrNull { it.status == CodexPlanStepStatus.pending }?.let { return it.step }
            steps.lastOrNull()?.let { return it.step }
            val explanation = message.planState?.explanation?.trim().orEmpty()
            if (explanation.isNotEmpty()) return explanation
            return message.text.trim().takeIf { it.isNotEmpty() } ?: "Open plan details"
        }
    }
}

internal fun selectPinnedPlanAccessoryMessage(messages: List<CodexMessage>): CodexMessage? {
    return messages
        .asReversed()
        .firstOrNull { it.shouldDisplayPinnedPlanAccessory() }
}

internal fun CodexMessage.shouldDisplayPinnedPlanAccessory(): Boolean {
    if (role != CodexMessageRole.system || kind != CodexMessageKind.plan) return false
    if (isStreaming) return true
    val steps = planState?.steps.orEmpty()
    if (steps.isEmpty()) return false
    return steps.any { it.status != CodexPlanStepStatus.completed }
}
