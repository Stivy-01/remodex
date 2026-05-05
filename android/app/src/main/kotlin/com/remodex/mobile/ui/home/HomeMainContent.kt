package com.remodex.mobile.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.ui.agent.CodeAgentScreen

/**
 * Home route: stato vuoto o conversazione a tutto schermo (Fase J).
 *
 * Pannello bridge debug (solo build DEBUG) — da riattivare se serve:
 * ```
 * Column(Modifier.fillMaxSize().padding(...)) {
 *   if (BuildConfig.DEBUG) {
 *     Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
 *       BridgeConnectionTestPanel(repository, Modifier.fillMaxWidth())
 *     }
 *     HorizontalDivider()
 *   }
 *   Box(Modifier.fillMaxWidth().weight(1f)) { ... }
 * }
 * ```
 * Import: `BridgeConnectionTestPanel`, `BuildConfig`, `Column`, `Arrangement`, `heightIn`,
 * `rememberScrollState`, `verticalScroll`, `HorizontalDivider`.
 */
@Composable
fun HomeMainContent(
    repository: CodexRepository,
    reconnectUiState: RootReconnectUiState = RootReconnectUiState(),
    onReconnectSavedPairing: () -> Unit = {},
    onWakeSavedComputer: () -> Unit = {},
    onOpenPairingScanner: () -> Unit = {},
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val activeThreadId by repository.activeThreadId.collectAsStateWithLifecycle()
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val tid = activeThreadId
        if (tid.isNullOrBlank()) {
            HomeEmptyStateView(Modifier.fillMaxWidth())
        } else {
            CodeAgentScreen(
                threadId = tid,
                repository = repository,
                reconnectUiState = reconnectUiState,
                onReconnectSavedPairing = onReconnectSavedPairing,
                onWakeSavedComputer = onWakeSavedComputer,
                onOpenPairingScanner = onOpenPairingScanner,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
