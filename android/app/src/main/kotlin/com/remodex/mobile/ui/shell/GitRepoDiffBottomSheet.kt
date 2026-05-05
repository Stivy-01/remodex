package com.remodex.mobile.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.core.model.AIUnifiedPatchParser
import com.remodex.mobile.core.model.GitRepoSyncResult
import com.remodex.mobile.data.RepoDiffLastTurnFileRow
import com.remodex.mobile.ui.agent.truncatePathMiddle
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.git_repo_diff_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.ok))
                }
            }

            PrimaryTabRow(selectedTabIndex = selectedTabIx) {
                GitRepoDiffUiTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIx == index,
                        onClick = { selectedTabIx = index },
                        text = { Text(stringResource(tab.stringRes)) },
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    selected = scope == GitRepoDiffScope.LastTurn,
                    onClick = {
                        edits.clear()
                        onScopeChange(GitRepoDiffScope.LastTurn)
                    },
                    label = { Text(stringResource(R.string.git_repo_diff_scope_last_turn)) },
                )
                FilterChip(
                    selected = scope == GitRepoDiffScope.FullWorkingTree,
                    onClick = {
                        edits.clear()
                        onScopeChange(GitRepoDiffScope.FullWorkingTree)
                    },
                    label = { Text(stringResource(R.string.git_repo_diff_scope_full_tree)) },
                )
            }

            HorizontalDivider()

            when {
                scope == GitRepoDiffScope.FullWorkingTree && fullTreeError != null ->
                    Text(text = fullTreeError, color = MaterialTheme.colorScheme.error)

                scope == GitRepoDiffScope.FullWorkingTree && isFullTreeLoading && fullTreePatch.isBlank() ->
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp),
                    )

                rows.isEmpty() && scope == GitRepoDiffScope.LastTurn ->
                    Text(
                        text = stringResource(R.string.git_repo_diff_empty_last_turn),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                rows.isEmpty() ->
                    Text(
                        text = stringResource(R.string.git_repo_diff_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                .heightIn(max = 620.dp),
                    )
            }
        }
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
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

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = truncatePathMiddle(path, maxLen = 46),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            staging?.let { GitRepoDiffStagingDot(staging = it) }
            Text(
                text = " +$adds",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = " -$dels",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
            )
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

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(4.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = truncatePathMiddle(path, maxLen = 46),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                staging?.let { GitRepoDiffStagingDot(staging = it) }
                Text(
                    text = " +$adds",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = " -$dels",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                        ) {
                            FilterChip(
                                selected = !diffPatchEditMode,
                                onClick = { diffPatchEditMode = false },
                                label = { Text(stringResource(R.string.git_repo_diff_mode_preview)) },
                            )
                            FilterChip(
                                selected = diffPatchEditMode,
                                onClick = { diffPatchEditMode = true },
                                label = { Text(stringResource(R.string.git_repo_diff_mode_edit)) },
                            )
                        }
                        val patchText = edits[rowKey]?.text ?: chunk
                        if (!diffPatchEditMode) {
                            GitPatchHighlightedBlock(
                                patch = patchText,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 360.dp),
                            )
                        } else {
                            val fieldValue =
                                edits.getOrPut(rowKey) { TextFieldValue(chunk) }
                            BasicTextField(
                                value = fieldValue,
                                onValueChange = { edits[rowKey] = it },
                                textStyle =
                                    TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                    ),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 360.dp)
                                        .padding(8.dp),
                            )
                        }
                    }
                else -> {
                    val initial = remember(rowKey, chunk) { TextFieldValue(chunk) }
                    val fieldValue = edits[rowKey] ?: initial
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { edits[rowKey] = it },
                        textStyle =
                            TextStyle(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .padding(8.dp),
                    )
                }
            }
        }
    }
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
