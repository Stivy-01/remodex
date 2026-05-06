package com.remodex.mobile.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import com.remodex.mobile.R
import com.remodex.mobile.core.model.AIUnifiedPatchParser
import com.remodex.mobile.core.model.GitRepoSyncResult
import com.remodex.mobile.data.RepoDiffLastTurnFileRow
import com.remodex.mobile.ui.agent.truncatePathMiddle
import com.remodex.mobile.ui.theme.RemodexGitAddition
import com.remodex.mobile.ui.turn.RepoMarkdownFileLink
import kotlinx.coroutines.delay

enum class GitRepoDiffScope {
    LastTurn,
    FullWorkingTree,
}

private data class GitRepoDiffRenderableRow(
    val stableKey: String,
    val displayPath: String,
    val chunk: String,
)

private enum class GitRepoDiffUiTab(
    val stringRes: Int,
) {
    Summary(R.string.git_repo_diff_tab_summary),
    Review(R.string.git_repo_diff_tab_review),
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun GitRepoDiffBottomSheet(
    visible: Boolean,
    scope: GitRepoDiffScope,
    onScopeChange: (GitRepoDiffScope) -> Unit,
    lastTurnRows: List<RepoDiffLastTurnFileRow>,
    fullTreePatch: String,
    isFullTreeLoading: Boolean,
    fullTreeError: String?,
    gitStatus: GitRepoSyncResult?,
    focusPathQuery: String?,
    onFocusPathQueryConsumed: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    LaunchedEffect(sheetState) {
        sheetState.expand()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        var selectedTabIx by remember { mutableIntStateOf(GitRepoDiffUiTab.Review.ordinal) }
        val selectedTab =
            GitRepoDiffUiTab.entries.getOrElse(selectedTabIx) { GitRepoDiffUiTab.Review }

        val trimmedFull = remember(fullTreePatch) { fullTreePatch.trim() }
        val rows =
            remember(scope, trimmedFull, lastTurnRows) {
                when (scope) {
                    GitRepoDiffScope.LastTurn ->
                        lastTurnRows.map {
                            GitRepoDiffRenderableRow(
                                stableKey = it.stableKey,
                                displayPath = it.path,
                                chunk = it.chunk,
                            )
                        }
                    GitRepoDiffScope.FullWorkingTree ->
                        if (trimmedFull.isBlank()) {
                            emptyList()
                        } else {
                            AIUnifiedPatchParser.splitUnifiedPatchIntoFileChunks(trimmedFull).mapIndexed {
                                    i,
                                    pair,
                                ->
                                GitRepoDiffRenderableRow(
                                    stableKey = "full:$i:${pair.first}",
                                    displayPath = pair.first,
                                    chunk = pair.second,
                                )
                            }
                        }
                }
            }

        val reviewLazyListState = rememberLazyListState()
        var markdownExpandRowStableKey by remember { mutableStateOf<String?>(null) }
        val onFocusConsumedUpdated = rememberUpdatedState(onFocusPathQueryConsumed)

        LaunchedEffect(visible) {
            if (!visible) {
                markdownExpandRowStableKey = null
            }
        }

        LaunchedEffect(focusPathQuery, rows, trimmedFull, isFullTreeLoading, scope) {
            val q = focusPathQuery?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect

            selectedTabIx = GitRepoDiffUiTab.Review.ordinal

            val waitingPatch =
                scope == GitRepoDiffScope.FullWorkingTree &&
                    trimmedFull.isBlank() &&
                    isFullTreeLoading
            if (waitingPatch) {
                return@LaunchedEffect
            }

            delay(48)

            val ix =
                rows.indexOfFirst {
                    RepoMarkdownFileLink.rowMatchesQuery(it.displayPath, q)
                }
            if (ix >= 0) {
                markdownExpandRowStableKey = rows[ix].stableKey
                reviewLazyListState.animateScrollToItem(ix)
                delay(50)
            } else {
                markdownExpandRowStableKey = null
            }
            onFocusConsumedUpdated.value()
        }

        val edits = remember { mutableStateMapOf<String, TextFieldValue>() }
        LaunchedEffect(scope) {
            edits.clear()
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.git_repo_diff_sheet_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.ok))
                }
            }

            GitRepoDiffSegmentedTabs(
                selectedTab = selectedTab,
                onSelected = { selectedTabIx = it.ordinal },
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                GitRepoDiffScopePill(
                    selected = scope == GitRepoDiffScope.LastTurn,
                    onClick = {
                        edits.clear()
                        onScopeChange(GitRepoDiffScope.LastTurn)
                    },
                    iconRes = LucideR.drawable.lucide_ic_clock,
                    label = stringResource(R.string.git_repo_diff_scope_last_turn),
                )
                GitRepoDiffScopePill(
                    selected = scope == GitRepoDiffScope.FullWorkingTree,
                    onClick = {
                        edits.clear()
                        onScopeChange(GitRepoDiffScope.FullWorkingTree)
                    },
                    iconRes = LucideR.drawable.lucide_ic_git_branch,
                    label = stringResource(R.string.git_repo_diff_scope_full_tree),
                )
            }

            when {
                scope == GitRepoDiffScope.FullWorkingTree && fullTreeError != null ->
                    GitRepoDiffMessage(
                        text = fullTreeError,
                        isError = true,
                        modifier = Modifier.weight(1f),
                    )

                scope == GitRepoDiffScope.FullWorkingTree && isFullTreeLoading && fullTreePatch.isBlank() ->
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                rows.isEmpty() && scope == GitRepoDiffScope.LastTurn ->
                    GitRepoDiffMessage(
                        text = stringResource(R.string.git_repo_diff_empty_last_turn),
                        modifier = Modifier.weight(1f),
                    )

                rows.isEmpty() ->
                    GitRepoDiffMessage(
                        text = stringResource(R.string.git_repo_diff_empty),
                        modifier = Modifier.weight(1f),
                    )

                else ->
                    GitRepoDiffContent(
                        uiTab = selectedTab,
                        rows = rows,
                        gitStatus = gitStatus,
                        edits = edits,
                        reviewLazyListState = reviewLazyListState,
                        markdownExpandRowStableKey = markdownExpandRowStableKey,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                    )
            }
        }
    }
}

@Composable
private fun GitRepoDiffSegmentedTabs(
    selectedTab: GitRepoDiffUiTab,
    onSelected: (GitRepoDiffUiTab) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(0.5.dp, colors.outlineVariant.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.28f))
                .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        GitRepoDiffUiTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) {
                                colors.primaryContainer.copy(alpha = 0.42f)
                            } else {
                                colors.surface.copy(alpha = 0.02f)
                            },
                        )
                        .clickable { onSelected(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(tab.stringRes),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color =
                            if (selected) {
                                colors.onSurface
                            } else {
                                colors.onSurfaceVariant
                            },
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier =
                            Modifier
                                .width(72.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (selected) {
                                        colors.primary
                                    } else {
                                        colors.primary.copy(alpha = 0f)
                                    },
                                ),
                    )
                }
            }
        }
    }
}

@Composable
private fun GitRepoDiffScopePill(
    selected: Boolean,
    onClick: () -> Unit,
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(18.dp),
        color =
            if (selected) {
                colors.primaryContainer
            } else {
                colors.surface
            },
        contentColor =
            if (selected) {
                colors.onPrimaryContainer
            } else {
                colors.onSurfaceVariant
            },
        border =
            androidx.compose.foundation.BorderStroke(
                0.5.dp,
                if (selected) colors.primary.copy(alpha = 0.24f) else colors.outlineVariant.copy(alpha = 0.36f),
            ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GitRepoDiffMessage(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun GitRepoDiffContent(
    uiTab: GitRepoDiffUiTab,
    rows: List<GitRepoDiffRenderableRow>,
    gitStatus: GitRepoSyncResult?,
    edits: SnapshotStateMap<String, TextFieldValue>,
    reviewLazyListState: LazyListState,
    markdownExpandRowStableKey: String?,
    modifier: Modifier = Modifier,
) {
    when (uiTab) {
        GitRepoDiffUiTab.Summary ->
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.stableKey }) { row ->
                    GitRepoDiffSummaryRow(
                        path = row.displayPath,
                        chunk = row.chunk,
                        gitStatus = gitStatus,
                    )
                }
            }
        GitRepoDiffUiTab.Review ->
            LazyColumn(
                modifier = modifier,
                state = reviewLazyListState,
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(rows, key = { it.stableKey }) { row ->
                    GitRepoDiffExpandableFile(
                        rowKey = row.stableKey,
                        path = row.displayPath,
                        chunk = row.chunk,
                        gitStatus = gitStatus,
                        edits = edits,
                        expandForMarkdownFocus =
                            markdownExpandRowStableKey != null &&
                                row.stableKey == markdownExpandRowStableKey,
                    )
                }
            }
    }
}

@Composable
private fun GitRepoDiffSummaryRow(
    path: String,
    chunk: String,
    gitStatus: GitRepoSyncResult?,
) {
    val (adds, dels) =
        remember(chunk) { AIUnifiedPatchParser.additionsDeletionsForDisplay(chunk) }
    val gitFile = remember(path, gitStatus) { findGitStatusForPatchPath(path, gitStatus?.files.orEmpty()) }
    val staging = gitFile?.let { GitPathStagingUi.fromPorcelain(it.status) }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border =
            androidx.compose.foundation.BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GitRepoDiffFileTile()
            Text(
                text = truncatePathMiddle(path, maxLen = 42),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            staging?.let { GitRepoDiffStagingBadge(staging = it) }
            GitRepoDiffStatBadge(text = "+$adds", positive = true)
            GitRepoDiffStatBadge(text = "-$dels", positive = false)
        }
    }
}

@Composable
private fun GitRepoDiffStagingDot(staging: GitPathStagingUi) {
    val label =
        when {
            staging.isUntracked -> stringResource(R.string.git_repo_diff_staging_untracked)
            staging.staged && staging.unstaged -> stringResource(R.string.git_repo_diff_staging_mixed)
            staging.staged && !staging.unstaged -> stringResource(R.string.git_repo_diff_staging_staged_only)
            !staging.staged && staging.unstaged -> stringResource(R.string.git_repo_diff_staging_unstaged_only)
            else -> null
        }
    if (label != null) {
        Text(
            text = "· $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GitRepoDiffFileTile() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_file_diff),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun GitRepoDiffStatBadge(
    text: String,
    positive: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color =
            if (positive) {
                RemodexGitAddition.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.62f)
            },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color =
                if (positive) {
                    RemodexGitAddition
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun GitRepoDiffStagingBadge(staging: GitPathStagingUi) {
    val label =
        when {
            staging.isUntracked -> stringResource(R.string.git_repo_diff_staging_untracked)
            staging.staged && staging.unstaged -> stringResource(R.string.git_repo_diff_staging_mixed)
            staging.staged && !staging.unstaged -> stringResource(R.string.git_repo_diff_staging_staged_only)
            !staging.staged && staging.unstaged -> stringResource(R.string.git_repo_diff_staging_unstaged_only)
            else -> null
        } ?: return
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun GitRepoDiffExpandableFile(
    rowKey: String,
    path: String,
    chunk: String,
    gitStatus: GitRepoSyncResult?,
    edits: SnapshotStateMap<String, TextFieldValue>,
    expandForMarkdownFocus: Boolean = false,
) {
    var expanded by remember(rowKey) { mutableStateOf(false) }
    LaunchedEffect(expandForMarkdownFocus) {
        if (expandForMarkdownFocus) expanded = true
    }
    /** When [chunkLooksLikeUnifiedDiff], Preview shows [GitPatchHighlightedBlock]; Edit is a plain text patch editor. */
    var diffPatchEditMode by remember(rowKey) { mutableStateOf(false) }
    val patchBodyForStats = edits[rowKey]?.text ?: chunk
    val (adds, dels) =
        remember(patchBodyForStats) {
            AIUnifiedPatchParser.additionsDeletionsForDisplay(patchBodyForStats)
        }
    val gitFile = remember(path, gitStatus) { findGitStatusForPatchPath(path, gitStatus?.files.orEmpty()) }
    val staging = gitFile?.let { GitPathStagingUi.fromPorcelain(it.status) }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border =
            androidx.compose.foundation.BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GitRepoDiffFileTile()
                Text(
                    text = truncatePathMiddle(path, maxLen = 42),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                staging?.let { GitRepoDiffStagingBadge(staging = it) }
                GitRepoDiffStatBadge(text = "+$adds", positive = true)
                GitRepoDiffStatBadge(text = "-$dels", positive = false)
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(16.dp))
                    when {
                        chunkIsTimelinePlaceholderEcho(chunk) ->
                            Text(
                                text = stringResource(R.string.git_repo_diff_timeline_no_real_patch),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp),
                            )
                        chunkLooksLikeUnifiedDiff(chunk) ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    GitRepoDiffPatchModeButton(
                                        selected = !diffPatchEditMode,
                                        onClick = { diffPatchEditMode = false },
                                        iconRes = LucideR.drawable.lucide_ic_eye,
                                        label = stringResource(R.string.git_repo_diff_mode_preview),
                                        modifier = Modifier.weight(1f),
                                    )
                                    GitRepoDiffPatchModeButton(
                                        selected = diffPatchEditMode,
                                        onClick = { diffPatchEditMode = true },
                                        iconRes = LucideR.drawable.lucide_ic_square_pen,
                                        label = stringResource(R.string.git_repo_diff_mode_edit),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                val patchText = edits[rowKey]?.text ?: chunk
                                if (!diffPatchEditMode) {
                                    GitPatchHighlightedBlock(
                                        patch = patchText,
                                        verticalScrollEnabled = false,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .border(
                                                    0.5.dp,
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                    RoundedCornerShape(14.dp),
                                                )
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)),
                                    )
                                } else {
                                    val fieldValue =
                                        edits.getOrPut(rowKey) { TextFieldValue(chunk) }
                                    DiffPatchTextEditor(
                                        value = fieldValue,
                                        onValueChange = { edits[rowKey] = it },
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 360.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .border(
                                                    0.5.dp,
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                    RoundedCornerShape(14.dp),
                                                )
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)),
                                    )
                                }
                            }
                        else -> {
                            val initial = remember(rowKey, chunk) { TextFieldValue(chunk) }
                            val fieldValue = edits[rowKey] ?: initial
                            DiffPatchTextEditor(
                                value = fieldValue,
                                onValueChange = { edits[rowKey] = it },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 360.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            0.5.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                            RoundedCornerShape(14.dp),
                                        )
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitRepoDiffPatchModeButton(
    selected: Boolean,
    onClick: () -> Unit,
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        color =
            if (selected) {
                colors.primaryContainer
            } else {
                colors.surface.copy(alpha = 0.18f)
            },
        contentColor =
            if (selected) {
                colors.onPrimaryContainer
            } else {
                colors.onSurface
            },
        border =
            androidx.compose.foundation.BorderStroke(
                0.5.dp,
                if (selected) colors.primary.copy(alpha = 0.24f) else colors.outlineVariant.copy(alpha = 0.36f),
            ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DiffPatchTextEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
            ),
        modifier =
            modifier
                .verticalScroll(scrollState)
                .padding(8.dp),
    )
}

private fun chunkLooksLikeUnifiedDiff(chunk: String): Boolean {
    val t = chunk.trim().replace("\r\n", "\n")
    if (t.isEmpty()) return false
    if (chunkIsTimelinePlaceholderEcho(t)) return false
    return t.startsWith("diff --git ") ||
        t.lineSequence().any { it.startsWith("@@ ") } ||
        (t.contains("--- ") && t.contains("+++ "))
}

private fun chunkIsTimelinePlaceholderEcho(chunk: String): Boolean {
    val lines =
        chunk
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    return lines.isNotEmpty() && lines.all { it == "[file change]" }
}
