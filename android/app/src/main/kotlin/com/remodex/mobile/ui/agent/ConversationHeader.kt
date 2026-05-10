package com.remodex.mobile.ui.agent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val chrome = isAgentLightChrome()
    val pathColor =
        if (chrome) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.84f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(160.dp),
    ) {
        HeaderAtmosphere(
            lightChrome = chrome,
            modifier =
                Modifier
                    .matchParentSize()
                    .statusBarsPadding()
                    .padding(top = 4.dp),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(start = 14.dp, end = 10.dp, top = 12.dp),
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
private fun HeaderAtmosphere(
    lightChrome: Boolean,
    modifier: Modifier = Modifier,
) {
    val haze = if (lightChrome) Color(0xFF25282B) else Color.White
    val hazeAlpha = if (lightChrome) 0.052f else 0.074f
    val lowerHazeAlpha = if (lightChrome) 0.032f else 0.046f
    val lineAlpha = if (lightChrome) 0.095f else 0.13f
    Box(
        modifier =
            modifier
                .clipToBounds(),
    ) {
        Canvas(
            modifier =
                Modifier
                    .matchParentSize()
                    .blur(48.dp),
        ) {
            drawOval(
                brush =
                    Brush.radialGradient(
                        colors = listOf(haze.copy(alpha = hazeAlpha), haze.copy(alpha = 0f)),
                        center = Offset(size.width * 0.34f, size.height * 0.18f),
                        radius = size.width * 0.50f,
                    ),
                topLeft = Offset(size.width * -0.10f, size.height * -0.36f),
                size = Size(size.width * 0.88f, size.height * 1.00f),
            )
            drawOval(
                brush =
                    Brush.radialGradient(
                        colors = listOf(haze.copy(alpha = lowerHazeAlpha), haze.copy(alpha = 0f)),
                        center = Offset(size.width * 0.78f, size.height * 0.22f),
                        radius = size.width * 0.48f,
                    ),
                topLeft = Offset(size.width * 0.42f, size.height * -0.32f),
                size = Size(size.width * 0.76f, size.height * 0.96f),
            )
        }
        Canvas(modifier = Modifier.matchParentSize()) {
            val contour = Path().apply {
                moveTo(size.width * 0.08f, size.height * 0.38f)
                cubicTo(
                    size.width * 0.25f,
                    size.height * 0.31f,
                    size.width * 0.43f,
                    size.height * 0.39f,
                    size.width * 0.58f,
                    size.height * 0.34f,
                )
                cubicTo(
                    size.width * 0.74f,
                    size.height * 0.29f,
                    size.width * 0.84f,
                    size.height * 0.35f,
                    size.width * 0.94f,
                    size.height * 0.31f,
                )
            }
            drawPath(
                path = contour,
                color = haze.copy(alpha = lineAlpha),
                style = Stroke(width = 0.65.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun HeaderMenuButton(onOpenDrawer: () -> Unit) {
    HeaderFloatingIconButton(
        onClick = onOpenDrawer,
        contentDescription = stringResource(R.string.cd_open_navigation_drawer),
    ) { tint ->
        Icon(
            painter = painterResource(LucideR.drawable.lucide_ic_menu),
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            tint = tint,
        )
    }
}

@Composable
private fun HeaderFloatingIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable (Color) -> Unit,
) {
    val chrome = isAgentLightChrome()
    val iconTint =
        if (chrome) AgentLightColors.TextPrimary else MaterialTheme.colorScheme.onSurface
    val fill =
        if (chrome) {
            AgentLightColors.Surface.copy(alpha = 0.91f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
        }
    val border =
        if (chrome) {
            Color.White.copy(alpha = 0.58f)
        } else {
            Color.White.copy(alpha = 0.12f)
        }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .size(56.dp)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ),
    ) {
        Surface(
            shape = CircleShape,
            color = fill,
            tonalElevation = if (chrome) 0.dp else 3.dp,
            shadowElevation = if (chrome) 7.dp else 5.dp,
            modifier =
                Modifier
                    .size(46.dp)
                    .border(0.7.dp, border, CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                content(if (enabled) iconTint else iconTint.copy(alpha = 0.42f))
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
                HeaderFloatingIconButton(
                    onClick = { onSetGitMenuExpanded(true) },
                    enabled = isGitActionEnabled && !gitActionsBusy,
                    contentDescription = gitMenuCd,
                ) { tint ->
                    if (gitActionsBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(21.dp),
                            strokeWidth = 2.dp,
                            color = tint,
                        )
                    } else {
                        Box(modifier = Modifier.size(22.dp)) {
                            GitNodeConnectorIcon(
                                tint = tint,
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
                HeaderFloatingIconButton(
                    onClick = { onSetOverflowExpanded(true) },
                    contentDescription = stringResource(R.string.turn_top_bar_thread_actions_cd),
                ) { tint ->
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_ellipsis_vertical),
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = tint,
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
