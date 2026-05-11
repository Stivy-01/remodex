package com.remodex.mobile.ui.turn

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexAccessMode
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.ui.theme.AgentLightColors
import com.remodex.mobile.ui.theme.RemodexFullAccessIconDark
import com.remodex.mobile.ui.theme.RemodexFullAccessIconLight
import com.remodex.mobile.ui.theme.isAgentLightChrome

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
    onBranchPickerOpenChange: (Boolean) -> Unit = {},
    openPickerRequestKey: Int = 0,
    onOpenPickerRequestConsumed: () -> Unit = {},
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

    val envPillChrome: Modifier =
        if (agentLightChrome) {
            Modifier
                .shadow(
                    elevation = 8.dp,
                    shape = envPillShape,
                    ambientColor = Color.Black.copy(alpha = 0.06f),
                    spotColor = Color.Black.copy(alpha = 0.06f),
                )
                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f), envPillShape)
        } else {
            Modifier.shadow(
                elevation = 3.dp,
                shape = envPillShape,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.10f),
            )
        }

    val envLabelTint =
        if (agentLightChrome) {
            AgentLightColors.TextSecondary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    val envIconMuted =
        if (agentLightChrome) {
            AgentLightColors.IconMuted
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    val accessIconTint =
        when (selectedAccessMode) {
            CodexAccessMode.fullAccess -> {
                val darkSurface = MaterialTheme.colorScheme.background.luminance() < 0.38f
                if (darkSurface) RemodexFullAccessIconDark else RemodexFullAccessIconLight
            }

            CodexAccessMode.onRequest -> envIconMuted
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Surface(
                shape = envPillShape,
                color = envPillBg,
                modifier =
                    envPillChrome.clickable {
                        runtimeMenuExpanded = true
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
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
                        style = MaterialTheme.typography.labelSmall,
                        color = envLabelTint,
                        maxLines = 1,
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

        Box(contentAlignment = Alignment.CenterEnd) {
            TurnGitBranchAccessory(
                state = gitBranchPaneState,
                branchPickerEnabled = branchPickerEnabled,
                isSwitchingBranch = isSwitchingGitBranch,
                onRefreshBranches = onRefreshGitBranches,
                onCheckoutBranch = onCheckoutGitBranch,
                onCreateBranch = onCreateGitBranch,
                onOpenBranchSelector = onOpenBranchSelector,
                onBranchPickerOpenChange = onBranchPickerOpenChange,
                openPickerRequestKey = openPickerRequestKey,
                onOpenPickerRequestConsumed = onOpenPickerRequestConsumed,
                compact = true,
                modifier = Modifier.widthIn(max = 190.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Box {
            Surface(
                shape = envPillShape,
                color = envPillBg,
                modifier =
                    envPillChrome.clickable(enabled = accessPickerEnabled) {
                        if (accessPickerEnabled) {
                            accessMenuExpanded = true
                        }
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
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

        TurnComposerUsageRing(
            threadId = threadId,
            repository = repository,
        )
    }
}
