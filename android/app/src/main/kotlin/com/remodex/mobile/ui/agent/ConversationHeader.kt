package com.remodex.mobile.ui.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import com.remodex.mobile.R
import com.remodex.mobile.core.model.GitDiffTotals
import com.remodex.mobile.core.model.TurnGitActionKind
import com.remodex.mobile.ui.theme.AgentLightColors
import com.remodex.mobile.ui.theme.RemodexGitAddition
import com.remodex.mobile.ui.theme.RemodexToolbarIconShape
import com.remodex.mobile.ui.theme.isAgentLightChrome

/** Truncate long filesystem paths for the header subtitle (middle ellipsis). */
fun truncatePathMiddle(
    path: String,
    maxLen: Int = 44,
): String {
    if (path.length <= maxLen) return path
    val ellipsis = "…"
    val inner = maxLen - ellipsis.length
    val head = inner / 2
    val tail = inner - head
    return path.take(head) + ellipsis + path.takeLast(tail)
}

/**
 * Main turn toolbar: title, repo path, running pill, diff totals (opens diff), git actions menu,
 * overflow (handoff / stop).
 * SwiftUI reference: [TurnToolbarContent](CodexMobile/CodexMobile/Views/Turn/TurnToolbarContent.swift).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationHeader(
    title: String,
    pathSubtitle: String?,
    onPathClick: (() -> Unit)?,
    showRunningPill: Boolean,
    repoDiffTotals: GitDiffTotals?,
    isLoadingRepoDiff: Boolean,
    onTapRepoDiff: (() -> Unit)?,
    showGitActions: Boolean,
    onGitAction: ((TurnGitActionKind) -> Unit)?,
    gitActionsBusy: Boolean,
    showsDiscardRuntimeRecovery: Boolean,
    isGitActionEnabled: Boolean,
    isGitInitialized: Boolean,
    showDesktopHandoff: Boolean,
    handingOffToDesktop: Boolean,
    showWorktreeHandoff: Boolean,
    handingOffWorktree: Boolean,
    isWorktreeProject: Boolean,
    showTurnStop: Boolean,
    onOpenDrawer: () -> Unit,
    onContinueDesktop: () -> Unit,
    onWorktreeHandoff: () -> Unit,
    onStopTurn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    var gitMenuExpanded by remember { mutableStateOf(false) }
    val showOverflow = showDesktopHandoff || showWorktreeHandoff || showTurnStop
    val diffCd = stringResource(R.string.cd_git_repo_diff_totals)
    val gitMenuCd = stringResource(R.string.cd_git_actions_menu)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeaderMenuButton(onOpenDrawer = onOpenDrawer)
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            lineHeight = 22.sp,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (showRunningPill) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.turn_top_bar_thinking),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
                HeaderActions(
                    isLoadingRepoDiff = isLoadingRepoDiff,
                    repoDiffTotals = repoDiffTotals,
                    onTapRepoDiff = onTapRepoDiff,
                    diffCd = diffCd,
                    showGitActions = showGitActions,
                    onGitAction = onGitAction,
                    gitActionsBusy = gitActionsBusy,
                    showsDiscardRuntimeRecovery = showsDiscardRuntimeRecovery,
                    isGitActionEnabled = isGitActionEnabled,
                    isGitInitialized = isGitInitialized,
                    gitMenuCd = gitMenuCd,
                    showOverflow = showOverflow,
                    overflowExpanded = overflowExpanded,
                    onSetOverflowExpanded = { overflowExpanded = it },
                    gitMenuExpanded = gitMenuExpanded,
                    onSetGitMenuExpanded = { gitMenuExpanded = it },
                    showDesktopHandoff = showDesktopHandoff,
                    handingOffToDesktop = handingOffToDesktop,
                    showWorktreeHandoff = showWorktreeHandoff,
                    handingOffWorktree = handingOffWorktree,
                    isWorktreeProject = isWorktreeProject,
                    showTurnStop = showTurnStop,
                    onContinueDesktop = onContinueDesktop,
                    onWorktreeHandoff = onWorktreeHandoff,
                    onStopTurn = onStopTurn,
                )
            }
            if (pathSubtitle != null && onPathClick != null) {
                val pathColor =
                    if (isAgentLightChrome()) {
                        MaterialTheme.colorScheme.outlineVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                val pathSemanticsLabel = stringResource(R.string.turn_thread_path_dialog_title)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .offset(y = (-2).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(36.dp))
                    Text(
                        text = pathSubtitle,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                lineHeight = 14.sp,
                            ),
                        fontFamily = FontFamily.Monospace,
                        color = pathColor,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clickable(onClick = onPathClick)
                                .semantics {
                                    contentDescription = pathSemanticsLabel
                                    role = Role.Button
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = pathColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderMenuButton(onOpenDrawer: () -> Unit) {
    val chrome = isAgentLightChrome()
    val menuIconTint =
        if (chrome) AgentLightColors.IconMuted else MaterialTheme.colorScheme.onSurface
    IconButton(onClick = onOpenDrawer, modifier = Modifier.size(48.dp)) {
        Surface(
            shape = RemodexToolbarIconShape,
            color =
                if (chrome) {
                    AgentLightColors.Surface.copy(alpha = 0.94f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            tonalElevation = if (chrome) 0.dp else 2.dp,
            modifier =
                Modifier
                    .size(36.dp)
                    .then(
                        if (chrome) {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RemodexToolbarIconShape,
                            )
                        } else {
                            Modifier
                        },
                    ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_menu),
                    contentDescription = stringResource(R.string.cd_open_navigation_drawer),
                    modifier = Modifier.size(20.dp),
                    tint = menuIconTint,
                )
            }
        }
    }
}

@Composable
private fun HeaderActions(
    isLoadingRepoDiff: Boolean,
    repoDiffTotals: GitDiffTotals?,
    onTapRepoDiff: (() -> Unit)?,
    diffCd: String,
    showGitActions: Boolean,
    onGitAction: ((TurnGitActionKind) -> Unit)?,
    gitActionsBusy: Boolean,
    showsDiscardRuntimeRecovery: Boolean,
    isGitActionEnabled: Boolean,
    isGitInitialized: Boolean,
    gitMenuCd: String,
    showOverflow: Boolean,
    overflowExpanded: Boolean,
    onSetOverflowExpanded: (Boolean) -> Unit,
    gitMenuExpanded: Boolean,
    onSetGitMenuExpanded: (Boolean) -> Unit,
    showDesktopHandoff: Boolean,
    handingOffToDesktop: Boolean,
    showWorktreeHandoff: Boolean,
    handingOffWorktree: Boolean,
    isWorktreeProject: Boolean,
    showTurnStop: Boolean,
    onContinueDesktop: () -> Unit,
    onWorktreeHandoff: () -> Unit,
    onStopTurn: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (isLoadingRepoDiff) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .size(22.dp)
                        .padding(end = 4.dp),
                strokeWidth = 2.dp,
            )
        } else if (repoDiffTotals != null) {
            val tapDiff = onTapRepoDiff
            Row(
                modifier =
                    Modifier.padding(end = 2.dp).then(
                        if (tapDiff != null) {
                            Modifier
                                .clickable(onClick = tapDiff)
                                .semantics {
                                    contentDescription = diffCd
                                    role = Role.Button
                                }
                        } else {
                            Modifier
                        }
                    ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "+${repoDiffTotals.additions}",
                    style = MaterialTheme.typography.labelLarge,
                    color = RemodexGitAddition,
                )
                Text(
                    text = "-${repoDiffTotals.deletions}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                if (repoDiffTotals.binaryFiles > 0) {
                    Text(
                        text = "B${repoDiffTotals.binaryFiles}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (showGitActions && onGitAction != null) {
            Box {
                IconButton(
                    onClick = { onSetGitMenuExpanded(true) },
                    enabled = isGitActionEnabled && !gitActionsBusy,
                    modifier = Modifier.semantics { contentDescription = gitMenuCd },
                ) {
                    if (gitActionsBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Box(modifier = Modifier.size(24.dp)) {
                            GitNodeConnectorIcon(
                                tint = LocalContentColor.current,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                GitActionsDropdown(
                    expanded = gitMenuExpanded,
                    onDismissRequest = { onSetGitMenuExpanded(false) },
                    showsDiscardRuntimeRecovery = showsDiscardRuntimeRecovery,
                    isGitActionEnabled = isGitActionEnabled,
                    isGitInitialized = isGitInitialized,
                    onSelect = {
                        onSetGitMenuExpanded(false)
                        onGitAction(it)
                    },
                )
            }
        }

        if (showOverflow) {
            Box {
                IconButton(onClick = { onSetOverflowExpanded(true) }) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_ellipsis_vertical),
                        contentDescription = stringResource(R.string.turn_top_bar_thread_actions_cd),
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { onSetOverflowExpanded(false) },
                ) {
                    if (showDesktopHandoff) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (handingOffToDesktop) {
                                        stringResource(R.string.turn_open_desktop_opening)
                                    } else {
                                        stringResource(R.string.turn_open_desktop)
                                    },
                                )
                            },
                            onClick = {
                                onSetOverflowExpanded(false)
                                if (!handingOffToDesktop) onContinueDesktop()
                            },
                            enabled = !handingOffToDesktop,
                        )
                    }
                    if (showWorktreeHandoff) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (handingOffWorktree) {
                                        stringResource(R.string.composer_worktree_preparing)
                                    } else if (isWorktreeProject) {
                                        stringResource(R.string.composer_runtime_menu_handoff_local)
                                    } else {
                                        stringResource(R.string.composer_runtime_menu_worktree_handoff)
                                    },
                                )
                            },
                            onClick = {
                                onSetOverflowExpanded(false)
                                if (!handingOffWorktree) onWorktreeHandoff()
                            },
                            enabled = !handingOffWorktree,
                        )
                    }
                    if (showTurnStop) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.turn_stop),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                onSetOverflowExpanded(false)
                                onStopTurn()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GitActionsDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    showsDiscardRuntimeRecovery: Boolean,
    isGitActionEnabled: Boolean,
    isGitInitialized: Boolean,
    onSelect: (TurnGitActionKind) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        Text(
            text = stringResource(R.string.git_action_section_update),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        DropdownMenuItem(
            text = { Text(TurnGitActionKind.syncNow.title) },
            onClick = { onSelect(TurnGitActionKind.syncNow) },
            enabled = isGitActionEnabled,
        )
        DropdownMenuItem(
            text = { Text(TurnGitActionKind.initialize.title) },
            onClick = { onSelect(TurnGitActionKind.initialize) },
            enabled = isGitActionEnabled && !isGitInitialized,
        )
        HorizontalDivider()
        Text(
            text = stringResource(R.string.git_action_section_write),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        listOf(
            TurnGitActionKind.commit,
            TurnGitActionKind.push,
            TurnGitActionKind.commitAndPush,
            TurnGitActionKind.createPR,
            TurnGitActionKind.previewCommitPushToast,
        ).forEach { kind ->
            DropdownMenuItem(
                text = { Text(kind.title) },
                onClick = { onSelect(kind) },
                enabled = isGitActionEnabled,
            )
        }
        if (showsDiscardRuntimeRecovery) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.git_action_section_recovery),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            DropdownMenuItem(
                text = {
                    Text(
                        TurnGitActionKind.discardRuntimeChangesAndSync.title,
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { onSelect(TurnGitActionKind.discardRuntimeChangesAndSync) },
                enabled = isGitActionEnabled,
            )
        }
    }
}
