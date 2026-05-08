package com.remodex.mobile.ui.agent

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.ui.turn.TurnMessageRow

/** Assistant chat / markdown block (same layout as [TurnMessageRow] for [CodexMessageRole.assistant]). */
@Composable
fun AssistantMessageBlock(
    message: CodexMessage,
    modifier: Modifier = Modifier,
    onOpenFullMessage: ((CodexMessage) -> Unit)? = null,
) {
    if (message.role == CodexMessageRole.assistant) {
        TurnMessageRow(message = message, modifier = modifier, onOpenFullMessage = onOpenFullMessage)
    }
}
