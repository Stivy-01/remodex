package com.remodex.mobile.ui.turn

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexAccessMode
import com.remodex.mobile.ui.theme.AgentLightColors
import com.remodex.mobile.ui.theme.RemodexFullAccessIconDark
import com.remodex.mobile.ui.theme.RemodexFullAccessIconLight
import com.remodex.mobile.ui.theme.isAgentLightChrome
import com.remodex.mobile.data.CodexRepository

/**
 * Single-row controls below attachments, above the composer capsule (Swift [TurnComposerSecondaryBar]).
 */
@Composable
internal fun TurnComposerSecondaryBar(
    threadId: String,
    repository: CodexRepository,
    isWorktreeProject: Boolean,
    worktreeHandoffEnabled: Boolean,
    isHandingOffWorktree: Boolean,
    onWorktreeHandoff: () -> Unit,
    selectedAccessMode: CodexAccessMode,
    accessPickerEnabled: Boolean,
    onSelectAccessMode: (CodexAccessMode) -> Unit,
    gitBranchPaneState: GitBranchPaneState,
    branchPickerEnabled: Boolean,
    isSwitchingGitBranch: Boolean,
    onRefreshGitBranches: () -> Unit,
    onCheckoutGitBranch: (String) -> Unit,
    onCreateGitBranch: (String) -> Unit,
    onOpenBranchSelector: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var runtimeMenuExpanded by remember { mutableStateOf(false) }
    var accessMenuExpanded by remember { mutableStateOf(false) }
    val codexCloudUrl = "https://chatgpt.com/codex"
    val runtimeTitle =
        stringResource(
            if (isWorktreeProject) {
                R.string.composer_runtime_worktree
            } else {
                R.string.composer_runtime_local
            },
        )
    val accessIconRes =
        when (selectedAccessMode) {
            CodexAccessMode.fullAccess -> LucideR.drawable.lucide_ic_shield_alert
            CodexAccessMode.onRequest -> LucideR.drawable.lucide_ic_shield
        }
    val agentLightChrome = isAgentLightChrome()
    val envPillShape = RoundedCornerShape(50)
    val envPillBg =
        if (agentLightChrome) {
            AgentLightColors.Surface.copy(alpha = 0.93f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        }
    val envPillOutline: Modifier =
        if (agentLightChrome) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, envPillShape) else Modifier
    val envLabelTint =
        if (agentLightChrome) AgentLightColors.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    val envIconMuted =
        if (agentLightChrome) AgentLightColors.IconMuted else MaterialTheme.colorScheme.onSurfaceVariant
    val accessIconTint =
        when (selectedAccessMode) {
            CodexAccessMode.fullAccess -> {
                val darkSurface =
                    MaterialTheme.colorScheme.background.luminance() < 0.38f
                if (darkSurface) RemodexFullAccessIconDark else RemodexFullAccessIconLight
            }

            CodexAccessMode.onRequest -> envIconMuted
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box {
            Surface(
                shape = envPillShape,
                color = envPillBg,
                modifier =
                    envPillOutline
                        .clickable {
                            runtimeMenuExpanded = true
                        },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter =
                            if (isWorktreeProject) {
                                painterResource(LucideR.drawable.lucide_ic_git_branch)
                            } else {
                                painterResource(LucideR.drawable.lucide_ic_laptop)
                            },
                        contentDescription = null,
                        tint = envIconMuted,
                        modifier = Modifier.size(ComposerFootnoteIconDp),
                    )
                    Text(
                        text = runtimeTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = envLabelTint,
                    )
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_chevron_down),
                        contentDescription = null,
                        tint = envIconMuted,
                        modifier = Modifier.size(ComposerFootnoteIconDp),
                    )
                }
            }
            DropdownMenu(
                expanded = runtimeMenuExpanded,
                onDismissRequest = { runtimeMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.composer_runtime_menu_cloud)) },
                    onClick = {
                        runtimeMenuExpanded = false
                        runCatching { uriHandler.openUri(codexCloudUrl) }
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(LucideR.drawable.lucide_ic_cloud),
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (isHandingOffWorktree) {
                                    R.string.composer_worktree_preparing
                                } else if (isWorktreeProject) {
                                    R.string.composer_runtime_menu_handoff_local
                                } else {
                                    R.string.composer_runtime_menu_worktree_handoff
                                },
                            ),
                        )
                    },
                    onClick = {
                        runtimeMenuExpanded = false
                        if (worktreeHandoffEnabled && !isHandingOffWorktree) {
                            onWorktreeHandoff()
                        }
                    },
                    enabled = worktreeHandoffEnabled && !isHandingOffWorktree,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.composer_runtime_menu_local_disabled)) },
                    onClick = {},
                    enabled = false,
                )
            }
        }

        Box {
            Surface(
                shape = envPillShape,
                color = envPillBg,
                modifier =
                    envPillOutline
                        .clickable(enabled = accessPickerEnabled) {
                            if (accessPickerEnabled) accessMenuExpanded = true
                        },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(accessIconRes),
                        contentDescription = stringResource(R.string.turn_runtime_access_label),
                        tint = accessIconTint,
                        modifier = Modifier.size(ComposerFootnoteIconDp),
                    )
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_chevron_down),
                        contentDescription = null,
                        tint = envIconMuted,
                        modifier = Modifier.size(ComposerFootnoteIconDp),
                    )
                }
            }
            DropdownMenu(
                expanded = accessMenuExpanded,
                onDismissRequest = { accessMenuExpanded = false },
            ) {
                CodexAccessMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.menuTitle) },
                        onClick = {
                            accessMenuExpanded = false
                            onSelectAccessMode(mode)
                        },
                        enabled = accessPickerEnabled,
                    )
                }
            }
        }

        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = 160.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            TurnGitBranchAccessory(
                state = gitBranchPaneState,
                branchPickerEnabled = branchPickerEnabled,
                isSwitchingBranch = isSwitchingGitBranch,
                onRefreshBranches = onRefreshGitBranches,
                onCheckoutBranch = onCheckoutGitBranch,
                onCreateBranch = onCreateGitBranch,
                onOpenBranchSelector = onOpenBranchSelector,
                compact = true,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        TurnComposerUsageRing(
            threadId = threadId,
            repository = repository,
        )
    }
}
