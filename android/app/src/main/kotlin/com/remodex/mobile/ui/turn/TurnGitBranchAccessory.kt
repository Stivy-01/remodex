package com.remodex.mobile.ui.turn

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.ui.theme.AgentLightColors
import com.remodex.mobile.ui.theme.isAgentLightChrome
import com.remodex.mobile.data.GitBranchDisplaySummary
import com.remodex.mobile.data.GitBranchPickerRules

/** Read-only Git branch / worktree snapshot for the active thread (J.7c), with optional picker. */
sealed class GitBranchPaneState {
    data object UnavailableNoProject : GitBranchPaneState()

    data object AwaitingBridge : GitBranchPaneState()

    data object Loading : GitBranchPaneState()

    data class Loaded(
        val summary: GitBranchDisplaySummary,
    ) : GitBranchPaneState()

    data class Failed(
        val message: String,
    ) : GitBranchPaneState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TurnGitBranchAccessory(
    state: GitBranchPaneState,
    branchPickerEnabled: Boolean,
    isSwitchingBranch: Boolean,
    onRefreshBranches: () -> Unit,
    onCheckoutBranch: (String) -> Unit,
    onCreateBranch: (String) -> Unit,
    onOpenBranchSelector: () -> Unit = {},
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var createDialogOpen by rememberSaveable { mutableStateOf(false) }
    var newBranchName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(branchPickerEnabled, state) {
        if (!branchPickerEnabled || state !is GitBranchPaneState.Loaded) {
            sheetOpen = false
        }
    }

    if (compact && state !is GitBranchPaneState.Loaded && state !is GitBranchPaneState.Loading) {
        return
    }

    if (sheetOpen && state is GitBranchPaneState.Loaded) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                sheetOpen = false
                searchQuery = ""
            },
            sheetState = sheetState,
        ) {
            GitBranchPickerSheetContent(
                summary = state.summary,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                isSwitchingBranch = isSwitchingBranch,
                onRefresh = {
                    onRefreshBranches()
                },
                onSelectBranch = { branch ->
                    sheetOpen = false
                    searchQuery = ""
                    onCheckoutBranch(branch)
                },
                onOpenCreateBranch = {
                    newBranchName = ""
                    createDialogOpen = true
                },
            )
        }
    }
    if (createDialogOpen && state is GitBranchPaneState.Loaded) {
        CreateBranchDialog(
            branchName = newBranchName,
            onBranchNameChange = { newBranchName = it },
            existingBranches = state.summary.branches,
            currentBranch = state.summary.currentBranch,
            isSwitchingBranch = isSwitchingBranch,
            onDismiss = {
                createDialogOpen = false
                newBranchName = ""
            },
            onCreate = { branch ->
                sheetOpen = false
                searchQuery = ""
                createDialogOpen = false
                newBranchName = ""
                onCreateBranch(branch)
            },
        )
    }

    when (state) {
        GitBranchPaneState.UnavailableNoProject ->
            MutedHint(
                text = stringResource(R.string.git_branch_unavailable_no_project),
                modifier = modifier,
            )

        GitBranchPaneState.AwaitingBridge ->
            MutedHint(
                text = stringResource(R.string.git_branch_awaiting_bridge),
                modifier = modifier,
            )

        GitBranchPaneState.Loading ->
            Row(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.git_branch_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        is GitBranchPaneState.Loaded ->
            if (compact) {
                LoadedGitBranchPill(
                    summary = state.summary,
                    branchPickerEnabled = branchPickerEnabled,
                    isSwitchingBranch = isSwitchingBranch,
                    onOpenPicker = {
                        onOpenBranchSelector()
                        sheetOpen = true
                    },
                    modifier = modifier,
                )
            } else {
                LoadedGitBranchStrip(
                    summary = state.summary,
                    branchPickerEnabled = branchPickerEnabled,
                    isSwitchingBranch = isSwitchingBranch,
                    onOpenPicker = {
                        onOpenBranchSelector()
                        sheetOpen = true
                    },
                    modifier = modifier,
                )
            }

        is GitBranchPaneState.Failed ->
            Text(
                text = state.message,
                modifier =
                    modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
    }
}

@Composable
private fun GitBranchPickerSheetContent(
    summary: GitBranchDisplaySummary,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSwitchingBranch: Boolean,
    onRefresh: () -> Unit,
    onSelectBranch: (String) -> Unit,
    onOpenCreateBranch: () -> Unit,
) {
    val elsewhere = summary.branchesCheckedOutElsewhere.toSet()
    val defaultBr = summary.defaultBranch
    val current = summary.currentBranch
    val q = searchQuery.trim().lowercase()
    fun matchesSearch(b: String): Boolean = q.isEmpty() || b.lowercase().contains(q)
    val defaultRowBranch = defaultBr?.takeIf(::matchesSearch)
    val nonDefaultBranches = run {
        val base =
            summary.branches.filter { it != defaultBr }.filter(::matchesSearch).sorted()
        if (q.isNotEmpty() || current.isNullOrBlank() || current == defaultBr || !base.contains(current)) {
            base
        } else {
            listOf(current) + base.filter { it != current }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.git_branch_picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.git_branch_search_hint)) },
            singleLine = true,
            enabled = !isSwitchingBranch,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onOpenCreateBranch,
            enabled = !isSwitchingBranch,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.git_branch_create_new))
        }
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(320.dp),
        ) {
            defaultRowBranch?.let { br ->
                item(key = "default-$br") {
                    BranchPickerRow(
                        branch = br,
                        isDefault = true,
                        isCurrent = br == current,
                        badgeElsewhere =
                            elsewhereBadgeKind(br, elsewhere),
                        disabled =
                            isSwitchingBranch ||
                                GitBranchPickerRules.isBranchRowDisabledForCheckout(
                                    br,
                                    current,
                                    elsewhere,
                                    summary.worktreePathByBranch,
                                ),
                        onClick = { onSelectBranch(br) },
                    )
                    HorizontalDivider()
                }
            }
            items(
                items = nonDefaultBranches,
                key = { it },
            ) { branch ->
                BranchPickerRow(
                    branch = branch,
                    isDefault = false,
                    isCurrent = branch == current,
                    badgeElsewhere =
                        elsewhereBadgeKind(branch, elsewhere),
                    disabled =
                        isSwitchingBranch ||
                            GitBranchPickerRules.isBranchRowDisabledForCheckout(
                                branch,
                                current,
                                elsewhere,
                                summary.worktreePathByBranch,
                            ),
                    onClick = { onSelectBranch(branch) },
                )
                HorizontalDivider()
            }
        }
        if (nonDefaultBranches.isEmpty() && defaultRowBranch == null) {
            Text(
                text = stringResource(R.string.git_branch_picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        TextButton(
            onClick = onRefresh,
            enabled = !isSwitchingBranch,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(
                text =
                    if (isSwitchingBranch) {
                        stringResource(R.string.git_branch_switching)
                    } else {
                        stringResource(R.string.git_branch_reload_list)
                    },
            )
        }
    }
}

@Composable
private fun CreateBranchDialog(
    branchName: String,
    onBranchNameChange: (String) -> Unit,
    existingBranches: List<String>,
    currentBranch: String?,
    isSwitchingBranch: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val trimmed = branchName.trim()
    val error =
        when {
            trimmed.isEmpty() -> null
            !isLikelyValidBranchName(trimmed) -> stringResource(R.string.git_branch_create_invalid)
            existingBranches.any { it == trimmed } || currentBranch == trimmed ->
                stringResource(R.string.git_branch_create_exists)
            else -> null
        }
    val canCreate = trimmed.isNotEmpty() && error == null && !isSwitchingBranch

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.git_branch_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = branchName,
                    onValueChange = onBranchNameChange,
                    label = { Text(stringResource(R.string.git_branch_create_name_label)) },
                    singleLine = true,
                    enabled = !isSwitchingBranch,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(trimmed) },
                enabled = canCreate,
            ) {
                Text(stringResource(R.string.git_branch_create_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.git_branch_create_cancel))
            }
        },
    )
}

private fun isLikelyValidBranchName(name: String): Boolean {
    if (name.isBlank()) return false
    if (name.any { it.isWhitespace() }) return false
    if (name.contains("..") || name.contains("\\") || name.endsWith(".") || name.endsWith("/")) return false
    if (name.startsWith("/") || name.startsWith("-")) return false
    return name.none { it in "~^:?*[" }
}

private enum class ElsewhereBadgeKind {
    OpenElsewhere,
}

private fun elsewhereBadgeKind(
    branch: String,
    elsewhere: Set<String>,
): ElsewhereBadgeKind? {
    if (!elsewhere.contains(branch)) return null
    return ElsewhereBadgeKind.OpenElsewhere
}

@Composable
private fun BranchPickerRow(
    branch: String,
    isDefault: Boolean,
    isCurrent: Boolean,
    badgeElsewhere: ElsewhereBadgeKind?,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    val badgeText =
        when (badgeElsewhere) {
            ElsewhereBadgeKind.OpenElsewhere -> stringResource(R.string.git_branch_badge_open_elsewhere)
            null -> null
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !disabled, onClick = onClick)
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = branch,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (disabled) {
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
                    BranchBadge(stringResource(R.string.git_branch_badge_current))
                }
                if (isDefault) {
                    BranchBadge(stringResource(R.string.git_branch_badge_default))
                }
                if (badgeText != null) {
                    BranchBadge(badgeText)
                }
            }
        }
        if (isCurrent) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint =
                    if (disabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }
    }
}

@Composable
private fun BranchBadge(text: String) {
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

@Composable
private fun MutedHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier =
            modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LoadedGitBranchPill(
    summary: GitBranchDisplaySummary,
    branchPickerEnabled: Boolean,
    isSwitchingBranch: Boolean,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unknown = stringResource(R.string.git_branch_placeholder_unknown)
    val branchName = summary.currentBranch ?: unknown
    val chrome = isAgentLightChrome()
    val pillShape = RoundedCornerShape(50)
    val pillFill =
        if (chrome) {
            AgentLightColors.Surface.copy(alpha = 0.93f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        }
    val outlineMod: Modifier =
        if (chrome) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, pillShape) else Modifier
    val glyphTint =
        if (chrome) AgentLightColors.IconMuted else MaterialTheme.colorScheme.onSurfaceVariant
    val labelTint =
        if (chrome) AgentLightColors.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    val baseMod =
        if (branchPickerEnabled) {
            modifier.clickable(onClick = onOpenPicker)
        } else {
            modifier
        }
    Surface(
        shape = pillShape,
        color = pillFill,
        modifier = outlineMod.then(baseMod),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountTree,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = glyphTint,
            )
            Text(
                text = branchName,
                style = MaterialTheme.typography.labelMedium,
                color = labelTint,
                maxLines = 1,
            )
            if (isSwitchingBranch) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = glyphTint,
                )
            }
        }
    }
}

@Composable
private fun LoadedGitBranchStrip(
    summary: GitBranchDisplaySummary,
    branchPickerEnabled: Boolean,
    isSwitchingBranch: Boolean,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unknown = stringResource(R.string.git_branch_placeholder_unknown)
    val current = summary.currentBranch ?: unknown
    val defaultBr = summary.defaultBranch ?: unknown
    val dirtySuffix =
        if (summary.isDirty) {
            stringResource(R.string.git_branch_dirty_marker)
        } else {
            ""
        }
    val elsewhereSuffix =
        if (summary.branchesCheckedOutElsewhere.isEmpty()) {
            ""
        } else {
            stringResource(
                R.string.git_branch_elsewhere_suffix,
                summary.branchesCheckedOutElsewhere.size,
            )
        }
    val line =
        stringResource(
            R.string.git_branch_line_current_default,
            current,
            defaultBr,
        ) + dirtySuffix + elsewhereSuffix
    val cd =
        stringResource(
            R.string.git_branch_cd_loaded,
            current,
            defaultBr,
            summary.branchesCheckedOutElsewhere.size,
        )
    val stripModifier =
        if (branchPickerEnabled) {
            modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenPicker)
        } else {
            modifier.fillMaxWidth()
        }
    val hintRunning = stringResource(R.string.git_branch_picker_disabled_running)
    val hintSwitching = stringResource(R.string.git_branch_switching_inline)
    val footerHint =
        when {
            isSwitchingBranch -> hintSwitching
            !branchPickerEnabled && summary.branches.isNotEmpty() -> hintRunning
            else -> null
        }
    Column(modifier = stripModifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = line,
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics { contentDescription = cd },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (branchPickerEnabled) {
                    Text(
                        text = stringResource(R.string.git_branch_tap_to_switch),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        footerHint?.let {
            Text(
                text = it,
                modifier =
                    Modifier
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
