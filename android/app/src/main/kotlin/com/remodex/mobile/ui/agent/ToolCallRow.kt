package com.remodex.mobile.ui.agent

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CommandExecutionDetails
import com.remodex.mobile.ui.turn.TurnCommandExecutionCard

/**
 * System row for command / tool execution. SwiftUI: dedicated tool row views in Turn timeline.
 */
@Composable
fun ToolCallRow(
    message: CodexMessage,
    contentColor: Color,
    details: CommandExecutionDetails? = null,
    modifier: Modifier = Modifier,
) {
    TurnCommandExecutionCard(
        message = message,
        contentColor = contentColor,
        details = details,
        modifier = modifier,
    )
}
