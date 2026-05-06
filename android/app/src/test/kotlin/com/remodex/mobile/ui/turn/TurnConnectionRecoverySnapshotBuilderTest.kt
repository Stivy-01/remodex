package com.remodex.mobile.ui.turn

import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.ui.home.RootReconnectAttempt
import com.remodex.mobile.ui.home.RootReconnectRecoveryAction
import com.remodex.mobile.ui.home.RootReconnectUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurnConnectionRecoverySnapshotBuilderTest {
    @Test
    fun makeSnapshot_hidesWhenConnectedOrNoCandidate() {
        assertNull(
            TurnConnectionRecoverySnapshotBuilder.makeSnapshot(
                hasReconnectCandidate = false,
                connectionState = ConnectionState.Offline,
                reconnectUiState = RootReconnectUiState(),
            ),
        )
        assertNull(
            TurnConnectionRecoverySnapshotBuilder.makeSnapshot(
                hasReconnectCandidate = true,
                connectionState = ConnectionState.Connected,
                reconnectUiState = RootReconnectUiState(lastErrorMessage = "Lost"),
            ),
        )
    }

    @Test
    fun makeSnapshot_showsProgressWhileReconnecting() {
        val snapshot =
            TurnConnectionRecoverySnapshotBuilder.makeSnapshot(
                hasReconnectCandidate = true,
                connectionState = ConnectionState.Connecting,
                reconnectUiState = RootReconnectUiState(),
            )

        assertEquals(TurnConnectionRecoveryStatus.Reconnecting, snapshot?.status)
        assertEquals(TurnConnectionRecoveryTrailing.Progress, snapshot?.trailing)
    }

    @Test
    fun makeSnapshot_prefersWakeAndScanActions() {
        val wake =
            TurnConnectionRecoverySnapshotBuilder.makeSnapshot(
                hasReconnectCandidate = true,
                connectionState = ConnectionState.Error("Lost"),
                reconnectUiState =
                    RootReconnectUiState(
                        lastErrorMessage = "Wake first.",
                        wakeDisplayAvailable = true,
                    ),
            )
        assertEquals(TurnConnectionRecoveryTrailing.Action("Wake"), wake?.trailing)
        assertEquals("Wake first.", wake?.summary)

        val scan =
            TurnConnectionRecoverySnapshotBuilder.makeSnapshot(
                hasReconnectCandidate = true,
                connectionState = ConnectionState.Error("Pairing lost"),
                reconnectUiState =
                    RootReconnectUiState(
                        recoveryAction = RootReconnectRecoveryAction.ScanNewQr,
                    ),
            )
        assertEquals(TurnConnectionRecoveryTrailing.Action("Scan QR"), scan?.trailing)
    }

    @Test
    fun makeSnapshot_wakeAttemptUsesProgress() {
        val snapshot =
            TurnConnectionRecoverySnapshotBuilder.makeSnapshot(
                hasReconnectCandidate = true,
                connectionState = ConnectionState.Offline,
                reconnectUiState =
                    RootReconnectUiState(
                        attempt = RootReconnectAttempt.WakeDisplay,
                        wakeDisplayAvailable = true,
                    ),
            )

        assertTrue(snapshot?.trailing is TurnConnectionRecoveryTrailing.Progress)
        assertEquals(TurnConnectionRecoveryStatus.Reconnecting, snapshot?.status)
    }
}
