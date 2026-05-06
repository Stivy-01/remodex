package com.remodex.mobile.ui.home

import com.remodex.mobile.core.model.CodexSecureTransportError
import com.remodex.mobile.core.model.CodexTrustedSessionResolveError
import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.pairing.LoopbackRelayException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootReconnectModelsTest {
    @Test
    fun shouldAttemptSavedPairingReconnect_allowsMainOfflineWithSavedPairing() {
        assertTrue(
            shouldAttemptSavedPairingReconnect(
                phase = RootPhase.Main,
                hasRelayPairing = true,
                sessionReady = false,
                connectionState = ConnectionState.Offline,
            ),
        )
    }

    @Test
    fun shouldAttemptSavedPairingReconnect_blocksScannerAndActiveConnections() {
        assertFalse(
            shouldAttemptSavedPairingReconnect(
                phase = RootPhase.PairingScan,
                hasRelayPairing = true,
                sessionReady = false,
                connectionState = ConnectionState.Offline,
            ),
        )
        assertFalse(
            shouldAttemptSavedPairingReconnect(
                phase = RootPhase.Main,
                hasRelayPairing = true,
                sessionReady = false,
                connectionState = ConnectionState.Connecting,
            ),
        )
        assertFalse(
            shouldAttemptSavedPairingReconnect(
                phase = RootPhase.Main,
                hasRelayPairing = true,
                sessionReady = true,
                connectionState = ConnectionState.Connected,
            ),
        )
    }

    @Test
    fun reconnectRecoveryActionFor_returnsScanNewQrForPairingMismatch() {
        assertEquals(
            RootReconnectRecoveryAction.ScanNewQr,
            reconnectRecoveryActionFor(
                CodexSecureTransportError.InvalidHandshake(
                    "The secure bridge session ID did not match the saved pairing.",
                ),
            ),
        )
        assertEquals(
            RootReconnectRecoveryAction.ScanNewQr,
            reconnectRecoveryActionFor(CodexTrustedSessionResolveError.NoTrustedMac),
        )
        assertEquals(
            RootReconnectRecoveryAction.ScanNewQr,
            reconnectRecoveryActionFor(LoopbackRelayException("Relay uses 127.0.0.1 / localhost.")),
        )
    }

    @Test
    fun reconnectRecoveryActionFor_keepsSavedPairingForTemporaryFailures() {
        assertEquals(
            RootReconnectRecoveryAction.RetrySavedPairing,
            reconnectRecoveryActionFor(CodexTrustedSessionResolveError.MacOffline("Mac is offline.")),
        )
        assertEquals(
            RootReconnectRecoveryAction.RetrySavedPairing,
            reconnectRecoveryActionFor(CodexSecureTransportError.TimedOut("Timed out.")),
        )
    }

    @Test
    fun trustedReconnectFailureBudget_dropsSavedRelaySessionAfterRepeatedTemporaryFailures() {
        val budget = RootTrustedReconnectFailureBudget(maxFailures = 3)

        val first = budget.record(CodexSecureTransportError.TimedOut("Timed out."))
        val second = budget.record(CodexSecureTransportError.TimedOut("Timed out."))
        val third = budget.record(CodexSecureTransportError.TimedOut("Timed out."))

        assertEquals(RootReconnectRecoveryAction.RetrySavedPairing, first.recoveryAction)
        assertFalse(first.shouldDropSavedRelaySession)
        assertEquals(RootReconnectRecoveryAction.RetrySavedPairing, second.recoveryAction)
        assertFalse(second.shouldDropSavedRelaySession)
        assertEquals(RootReconnectRecoveryAction.ScanNewQr, third.recoveryAction)
        assertTrue(third.shouldDropSavedRelaySession)
    }

    @Test
    fun trustedReconnectFailureBudget_doesNotDropSessionForImmediatePairingMismatch() {
        val budget = RootTrustedReconnectFailureBudget(maxFailures = 3)

        val decision =
            budget.record(
                CodexSecureTransportError.InvalidHandshake(
                    "The secure bridge session ID did not match the saved pairing.",
                ),
            )

        assertEquals(RootReconnectRecoveryAction.ScanNewQr, decision.recoveryAction)
        assertFalse(decision.shouldDropSavedRelaySession)
    }

    @Test
    fun trustedReconnectFailureBudget_resetsAfterSuccess() {
        val budget = RootTrustedReconnectFailureBudget(maxFailures = 2)

        budget.record(CodexSecureTransportError.TimedOut("Timed out."))
        budget.reset()
        val decision = budget.record(CodexSecureTransportError.TimedOut("Timed out."))

        assertEquals(RootReconnectRecoveryAction.RetrySavedPairing, decision.recoveryAction)
        assertFalse(decision.shouldDropSavedRelaySession)
    }
}
