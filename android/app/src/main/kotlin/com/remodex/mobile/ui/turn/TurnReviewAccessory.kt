package com.remodex.mobile.ui.turn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexReviewTarget
import com.remodex.mobile.ui.theme.RemodexModalBottomSheet

internal fun reviewSelectableDefaultBranch(
    defaultBranch: String?,
    availableBranches: List<String>,
): String? {
    val normalizedDefaultBranch =
        defaultBranch
            ?.trim()
            .takeIf { !it.isNullOrEmpty() }
            ?: return null
    return normalizedDefaultBranch.takeIf { availableBranches.contains(normalizedDefaultBranch) }
}

internal fun reviewBaseBranchChoices(
    defaultBranch: String?,
    availableBranches: List<String>,
): List<String> {
    val normalizedBranches =
        availableBranches
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    val selectableDefaultBranch = reviewSelectableDefaultBranch(defaultBranch, normalizedBranches)
    return buildList {
        selectableDefaultBranch?.let { add(it) }
        normalizedBranches.filterNot { it == selectableDefaultBranch }.forEach { add(it) }
    }
}

internal fun resolveReviewBaseBranch(
    selectedBaseBranch: String?,
    defaultBranch: String?,
    availableBranches: List<String>,
): String? {
    val normalizedSelectedBaseBranch =
        selectedBaseBranch
            ?.trim()
            .takeIf { !it.isNullOrEmpty() }
    val normalizedBranches =
        availableBranches
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    if (normalizedSelectedBaseBranch != null && normalizedBranches.contains(normalizedSelectedBaseBranch)) {
        return normalizedSelectedBaseBranch
    }
    return reviewSelectableDefaultBranch(defaultBranch, normalizedBranches)
}

internal fun reviewBaseBranchSelectionDisabled(
    branch: String,
    currentBranch: String?,
): Boolean {
    val normalizedCurrentBranch =
        currentBranch
            ?.trim()
            .takeIf { !it.isNullOrEmpty() }
    return normalizedCurrentBranch != null && branch == normalizedCurrentBranch
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TurnReviewAccessoryCard(
    target: CodexReviewTarget,
    selectedBaseBranch: String?,
    availableBranches: List<String>,
    currentBranch: String?,
    defaultBranch: String?,
    onSelectCurrentChanges: () -> Unit,
    onSelectBaseBranch: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedBaseBranch =
        remember(selectedBaseBranch, defaultBranch, availableBranches) {
            resolveReviewBaseBranch(
                selectedBaseBranch = selectedBaseBranch,
                defaultBranch = defaultBranch,
                availableBranches = availableBranches,
            )
        }
    val baseBranchChoices =
        remember(defaultBranch, availableBranches) {
            reviewBaseBranchChoices(defaultBranch, availableBranches)
        }
    var showBaseBranchPicker by rememberSaveable { mutableStateOf(false) }
    val title =
        when (target) {
            CodexReviewTarget.uncommittedChanges -> stringResource(R.string.turn_review_current_title)
            CodexReviewTarget.baseBranch ->
                resolvedBaseBranch?.let {
                    stringResource(R.string.turn_review_base_title, it)
                } ?: stringResource(R.string.turn_review_base_title_unset)
        }
    val detail =
        when (target) {
            CodexReviewTarget.uncommittedChanges -> stringResource(R.string.turn_review_current_detail)
            CodexReviewTarget.baseBranch -> stringResource(R.string.turn_review_base_detail)
        }
    val canOpenBaseBranchPicker =
        baseBranchChoices.any { branch ->
            !reviewBaseBranchSelectionDisabled(branch, currentBranch)
        }

    LaunchedEffect(target, baseBranchChoices, canOpenBaseBranchPicker) {
        if (target != CodexReviewTarget.baseBranch || !canOpenBaseBranchPicker) {
            showBaseBranchPicker = false
        }
    }

    if (showBaseBranchPicker && canOpenBaseBranchPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        RemodexModalBottomSheet(
            onDismissRequest = { showBaseBranchPicker = false },
            sheetState = sheetState,
        ) {
            ReviewBaseBranchPickerSheet(
                branches = baseBranchChoices,
                selectedBaseBranch = resolvedBaseBranch,
                defaultBranch = reviewSelectableDefaultBranch(defaultBranch, baseBranchChoices),
                currentBranch = currentBranch,
                onSelectBranch = { branch ->
                    showBaseBranchPicker = false
                    onSelectBaseBranch(branch)
                },
            )
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.turn_review_clear))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSelectCurrentChanges,
                    enabled = target != CodexReviewTarget.uncommittedChanges,
                ) {
                    Text(text = stringResource(R.string.turn_review_current_button))
                }
                OutlinedButton(
                    onClick = { showBaseBranchPicker = true },
                    enabled = canOpenBaseBranchPicker,
                ) {
                    Text(
                        text =
                            resolvedBaseBranch?.let { branch ->
                                stringResource(R.string.turn_review_base_button, branch)
                            } ?: stringResource(R.string.turn_review_base_button_unset),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewBaseBranchPickerSheet(
    branches: List<String>,
    selectedBaseBranch: String?,
    defaultBranch: String?,
    currentBranch: String?,
    onSelectBranch: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.turn_review_base_picker_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (branches.isEmpty()) {
            Text(
                text = stringResource(R.string.turn_review_base_picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(320.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(
                items = branches,
                key = { it },
            ) { branch ->
                ReviewBaseBranchRow(
                    branch = branch,
                    isSelected = branch == selectedBaseBranch,
                    isCurrent = branch == currentBranch?.trim(),
                    isDefault = branch == defaultBranch,
                    isDisabled = reviewBaseBranchSelectionDisabled(branch, currentBranch),
                    onClick = { onSelectBranch(branch) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ReviewBaseBranchRow(
    branch: String,
    isSelected: Boolean,
    isCurrent: Boolean,
    isDefault: Boolean,
    isDisabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !isDisabled, onClick = onClick)
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = branch,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (isDisabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isCurrent) {
                    ReviewBranchBadge(stringResource(R.string.git_branch_badge_current))
                }
                if (isDefault) {
                    ReviewBranchBadge(stringResource(R.string.git_branch_badge_default))
                }
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint =
                    if (isDisabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }
    }
}

@Composable
private fun ReviewBranchBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
