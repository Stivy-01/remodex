package com.remodex.mobile.ui.agent

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.ui.turn.TurnFileChangeDetailCard

/**
 * System row summarizing file edits / diff preview in the timeline.
 */
@Composable
fun FileEditRow(
    message: CodexMessage,
    modifier: Modifier = Modifier,
) {
    TurnFileChangeDetailCard(
        message = message,
        modifier = modifier,
    )
}
