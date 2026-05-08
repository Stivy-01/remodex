package com.remodex.mobile.ui.agent

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.ui.home.RootReconnectUiState
import com.remodex.mobile.ui.turn.TurnConversationPane

/**
 * High-level “code agent” conversation surface: thread timeline + composer + runtime accessories.
 *
 * Implementation is delegated to [TurnConversationPane] while UI is split into smaller
 * composables ([MessageList], [ConversationHeader], [TurnComposerBar]) without rewriting bridge logic.
 */
@Composable
fun CodeAgentScreen(
    threadId: String,
    repository: CodexRepository,
    reconnectUiState: RootReconnectUiState = RootReconnectUiState(),
    onReconnectSavedPairing: () -> Unit = {},
    onWakeSavedComputer: () -> Unit = {},
    onOpenPairingScanner: () -> Unit = {},
    onGitContextChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TurnConversationPane(
        threadId = threadId,
        repository = repository,
        reconnectUiState = reconnectUiState,
        onReconnectSavedPairing = onReconnectSavedPairing,
        onWakeSavedComputer = onWakeSavedComputer,
        onOpenPairingScanner = onOpenPairingScanner,
        onGitContextChanged = onGitContextChanged,
        modifier = modifier,
    )
}
