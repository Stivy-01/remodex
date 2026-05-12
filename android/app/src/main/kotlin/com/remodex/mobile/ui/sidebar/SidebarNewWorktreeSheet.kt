package com.remodex.mobile.ui.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.core.model.GitBranchesWithStatusResult
import com.remodex.mobile.core.model.GitWorktreeChangeTransferMode
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.data.GitBranchDisplayMapper
import com.remodex.mobile.data.GitBranchDisplaySummary
import com.remodex.mobile.data.WorktreeNewChatDefaults
import com.remodex.mobile.data.loadGitBranchesWithStatus
import com.remodex.mobile.ui.theme.RemodexDropdownMenu
import com.remodex.mobile.ui.theme.RemodexModalBottomSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidebarNewWorktreeSheet(
    repository: CodexRepository,
    visible: Boolean,
    baseProjectPath: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: suspend (
        baseProjectPath: String,
        baseBranch: String,
        changeTransfer: GitWorktreeChangeTransferMode,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val basePath = baseProjectPath?.trim().orEmpty()
    if (!visible || basePath.isBlank()) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var loading by remember(basePath) { mutableStateOf(true) }
    var loadError by remember(basePath) { mutableStateOf<String?>(null) }
    var summary by remember(basePath) { mutableStateOf<GitBranchDisplaySummary?>(null) }
    var selectedBranch by remember(basePath) { mutableStateOf<String?>(null) }
    var selectedTransfer by remember(basePath) { mutableStateOf(GitWorktreeChangeTransferMode.none) }
    var showBranchMenu by remember { mutableStateOf(false) }
    var createError by remember(basePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(basePath) {
        loading = true
        loadError = null
        createError = null
        val result = loadGitBranchesWithStatus(repository, basePath)
        result
            .onSuccess { branches ->
                val next = GitBranchDisplayMapper.summaryFrom(branches)
                summary = next
                selectedBranch = WorktreeNewChatDefaults.baseBranch(next)
            }
            .onFailure { error ->
                summary = null
                selectedBranch = null
                loadError = GitBranchDisplayMapper.userVisibleMessage(error)
            }
        loading = false
    }

    RemodexModalBottomSheet(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.sidebar_worktree_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            WorktreeInfoBlock(
                label = stringResource(R.string.sidebar_worktree_sheet_project),
                value = basePath,
            )

            if (loading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.sidebar_worktree_sheet_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val branchSummary = summary
                val branch = selectedBranch
                if (branchSummary != null && branch != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.sidebar_worktree_sheet_branch),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) { showBranchMenu = true },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = branch,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        RemodexDropdownMenu(
                            expanded = showBranchMenu,
                            onDismissRequest = { showBranchMenu = false },
                        ) {
                            branchSummary.branches.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        selectedBranch = option
                                        showBranchMenu = false
                                    },
                                )
                            }
                        }
                    }

                    WorktreePreflightSummary(summary = branchSummary)
                    WorktreeChangeTransferSelector(
                        selected = selectedTransfer,
                        enabled = !busy,
                        onSelected = { selectedTransfer = it },
                    )
                }

                loadError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            createError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = !busy,
                    onClick = onDismiss,
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    enabled = !busy && !loading && selectedBranch != null && loadError == null,
                    onClick = {
                        val branch = selectedBranch ?: return@Button
                        createError = null
                        scope.launch {
                            try {
                                onCreate(basePath, branch, selectedTransfer)
                            } catch (e: Exception) {
                                createError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                            }
                        }
                    },
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.sidebar_worktree_sheet_create))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorktreeInfoBlock(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WorktreePreflightSummary(summary: GitBranchDisplaySummary) {
    val current = summary.currentBranch ?: stringResource(R.string.sidebar_worktree_sheet_unknown_branch)
    val default = summary.defaultBranch ?: stringResource(R.string.sidebar_worktree_sheet_unknown_branch)
    val dirty =
        if (summary.isDirty) {
            stringResource(R.string.sidebar_worktree_sheet_dirty)
        } else {
            stringResource(R.string.sidebar_worktree_sheet_clean)
        }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.sidebar_worktree_sheet_preflight),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.sidebar_worktree_sheet_preflight_body, current, default, dirty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorktreeChangeTransferSelector(
    selected: GitWorktreeChangeTransferMode,
    enabled: Boolean,
    onSelected: (GitWorktreeChangeTransferMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.sidebar_worktree_sheet_changes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorktreeTransferChip(
                text = stringResource(R.string.sidebar_worktree_sheet_changes_none),
                mode = GitWorktreeChangeTransferMode.none,
                selected = selected,
                enabled = enabled,
                onSelected = onSelected,
            )
            WorktreeTransferChip(
                text = stringResource(R.string.sidebar_worktree_sheet_changes_move),
                mode = GitWorktreeChangeTransferMode.move,
                selected = selected,
                enabled = enabled,
                onSelected = onSelected,
            )
            WorktreeTransferChip(
                text = stringResource(R.string.sidebar_worktree_sheet_changes_copy),
                mode = GitWorktreeChangeTransferMode.copy,
                selected = selected,
                enabled = enabled,
                onSelected = onSelected,
            )
        }
    }
}

@Composable
private fun WorktreeTransferChip(
    text: String,
    mode: GitWorktreeChangeTransferMode,
    selected: GitWorktreeChangeTransferMode,
    enabled: Boolean,
    onSelected: (GitWorktreeChangeTransferMode) -> Unit,
) {
    FilterChip(
        selected = selected == mode,
        enabled = enabled,
        onClick = { onSelected(mode) },
        label = { Text(text) },
    )
}
