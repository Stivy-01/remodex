package com.remodex.mobile.ui.turn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexCollaborationModeKind
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexPlanStepStatus
import com.remodex.mobile.data.QueuedTurnDraftPreview
import com.remodex.mobile.ui.theme.RemodexModalBottomSheet

@Composable
internal fun QueuedDraftsCard(
    previews: List<QueuedTurnDraftPreview>,
    totalCount: Int,
    canRestore: Boolean,
    onRestore: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val queueItemEmptyLabel = stringResource(R.string.turn_queue_item_empty)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.turn_queue_pending_count, totalCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            previews.take(3).forEachIndexed { idx, draft ->
                val line =
                    draft.text.lines().firstOrNull()?.trim().orEmpty().ifBlank { queueItemEmptyLabel }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${idx + 1}. $line",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val meta =
                            buildList {
                                if (draft.attachmentCount > 0) {
                                    add(stringResource(R.string.turn_queue_item_attachments, draft.attachmentCount))
                                }
                                if (draft.collaborationMode == CodexCollaborationModeKind.plan) {
                                    add(stringResource(R.string.turn_plan_mode_chip))
                                }
                            }.joinToString(" · ")
                        if (meta.isNotEmpty()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { onRestore(draft.id) }, enabled = canRestore) {
                        Text(stringResource(R.string.turn_queue_restore))
                    }
                    TextButton(onClick = { onRemove(draft.id) }) {
                        Text(stringResource(R.string.turn_queue_remove))
                    }
                }
            }
            if (previews.size > 3) {
                Text(
                    text = stringResource(R.string.turn_queue_more_items, previews.size - 3),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun WorktreeHandoffAccessoryCard(
    isWorktreeProject: Boolean,
    enabled: Boolean,
    inProgress: Boolean,
    onHandoff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        if (isWorktreeProject) {
                            stringResource(R.string.turn_worktree_handoff_to_local_title)
                        } else {
                            stringResource(R.string.turn_worktree_handoff_to_worktree_title)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.turn_worktree_handoff_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                )
            }
            TextButton(
                onClick = onHandoff,
                enabled = enabled && !inProgress,
            ) {
                if (inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    text =
                        if (isWorktreeProject) {
                            stringResource(R.string.turn_worktree_handoff_to_local_action)
                        } else {
                            stringResource(R.string.turn_worktree_handoff_to_worktree_action)
                        },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun WorktreeHandoffActionSheet(
    visible: Boolean,
    isWorktreeProject: Boolean,
    inProgress: Boolean,
    availableBaseBranches: List<String>,
    defaultBaseBranch: String?,
    currentBranch: String?,
    sourceProjectPath: String?,
    localTargetPath: String?,
    associatedWorktreePath: String?,
    hasAssociatedWorktree: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val choices =
        remember(availableBaseBranches, currentBranch, defaultBaseBranch) {
            worktreeHandoffBaseBranchChoices(
                availableBranches = availableBaseBranches,
                currentBranch = currentBranch,
                defaultBranch = defaultBaseBranch,
            )
        }
    var selectedBaseBranch by remember(choices, defaultBaseBranch) {
        mutableStateOf(defaultBaseBranch?.takeIf { it in choices } ?: choices.firstOrNull())
    }
    val model =
        worktreeHandoffSheetModel(
            isWorktreeProject = isWorktreeProject,
            inProgress = inProgress,
            selectedBaseBranch = selectedBaseBranch,
            localTargetPath = localTargetPath,
            associatedWorktreePath = associatedWorktreePath.takeIf { hasAssociatedWorktree },
        )
    val requiresBaseBranch = model.requiresBaseBranch
    val canConfirm = model.canConfirm

    RemodexModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text =
                    if (isWorktreeProject) {
                        stringResource(R.string.turn_worktree_handoff_sheet_to_local_title)
                    } else {
                        stringResource(R.string.turn_worktree_handoff_sheet_to_worktree_title)
                    },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text =
                    if (isWorktreeProject) {
                        stringResource(R.string.turn_worktree_handoff_sheet_to_local_message)
                    } else {
                        stringResource(R.string.turn_worktree_handoff_sheet_to_worktree_message)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sourceProjectPath?.takeIf { it.isNotBlank() }?.let { path ->
                Text(
                    text = "Source: $path",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isWorktreeProject) {
                model.targetPath?.let { path ->
                    Text(
                        text = "Target: $path",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (hasAssociatedWorktree) {
                model.targetPath?.let { path ->
                    Text(
                        text = "Target: $path",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (requiresBaseBranch) {
                Text(
                    text = "Create managed worktree from",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (choices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.turn_worktree_handoff_sheet_no_base_branch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    choices.take(8).forEach { branch ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RadioButton(
                                selected = selectedBaseBranch == branch,
                                onClick = { selectedBaseBranch = branch },
                                enabled = !inProgress,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = branch,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                val badges =
                                    buildList {
                                        if (branch == currentBranch) add(stringResource(R.string.git_branch_badge_current))
                                        if (branch == defaultBaseBranch) add(stringResource(R.string.git_branch_badge_default))
                                    }.joinToString(" · ")
                                if (badges.isNotBlank()) {
                                    Text(
                                        text = badges,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !inProgress,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = { onConfirm(if (requiresBaseBranch) selectedBaseBranch else null) },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1.4f),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    if (inProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text =
                                if (isWorktreeProject) {
                                    stringResource(R.string.turn_worktree_handoff_to_local_action)
                                } else {
                                    stringResource(R.string.turn_worktree_handoff_to_worktree_action)
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ForkThreadActionSheet(
    visible: Boolean,
    projectPath: String?,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    RemodexModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.turn_fork_sheet_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.turn_fork_sheet_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            projectPath
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { path ->
                    Text(
                        text = stringResource(R.string.turn_fork_sheet_project_path, path),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !inProgress,
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = onConfirm,
                    enabled = !inProgress,
                ) {
                    if (inProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(stringResource(R.string.turn_fork_sheet_confirm))
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PlanDetailsActionSheet(
    visible: Boolean,
    message: CodexMessage?,
    canApplyPlan: Boolean,
    onDismiss: () -> Unit,
    onApplyPlan: () -> Unit,
) {
    if (!visible || message == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snapshot = PlanAccessorySnapshot.fromMessage(message)
    RemodexModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.turn_plan_sheet_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = snapshot.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            snapshot.progressText?.let { progress ->
                Text(
                    text = stringResource(R.string.turn_plan_sheet_progress, progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = onApplyPlan,
                    enabled = canApplyPlan,
                ) {
                    Text(stringResource(R.string.turn_plan_apply_action))
                }
            }
        }
    }
}
