package com.remodex.mobile.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Bottom “environment” strip: context usage + git / worktree controls (SwiftUI: [TurnComposerSecondaryBar]).
 * Slots stay composable so [com.remodex.mobile.ui.turn.TurnComposerBar] can hide them when the field is focused.
 */
@Composable
fun EnvironmentBar(
    usageStrip: @Composable () -> Unit,
    gitBranchStrip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        usageStrip()
        gitBranchStrip()
    }
}
