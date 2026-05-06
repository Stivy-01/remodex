package com.remodex.mobile.ui.home

import com.remodex.mobile.core.model.CodexSecureTransportError
import com.remodex.mobile.core.model.CodexTrustedSessionResolveError
import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.pairing.LoopbackRelayException

enum class RootReconnectAttempt {
    Auto,
    Manual,
    WakeDisplay,
}

enum class RootReconnectRecoveryAction {
    RetrySavedPairing,
    ScanNewQr,
}

internal data class RootTrustedReconnectFailureDecision(
    val recoveryAction: RootReconnectRecoveryAction,
    val shouldDropSavedRelaySession: Boolean = false,
    val fallbackMessage: String,
)

internal class RootTrustedReconnectFailureBudget(
    private val maxFailures: Int = MAX_TRUSTED_RECONNECT_FAILURES,
) {
    private var consecutiveTrustedFailures = 0

    fun reset() {
        consecutiveTrustedFailures = 0
    }

    fun record(error: Throwable): RootTrustedReconnectFailureDecision {
        val action = reconnectRecoveryActionFor(error)
        if (action == RootReconnectRecoveryAction.ScanNewQr) {
            consecutiveTrustedFailures = 0
            return RootTrustedReconnectFailureDecision(
                recoveryAction = RootReconnectRecoveryAction.ScanNewQr,
                fallbackMessage = "This saved pairing is stale. Scan a new QR code to reconnect.",
            )
        }

        consecutiveTrustedFailures += 1
        if (consecutiveTrustedFailures >= maxFailures) {
            consecutiveTrustedFailures = 0
            return RootTrustedReconnectFailureDecision(
                recoveryAction = RootReconnectRecoveryAction.ScanNewQr,
                shouldDropSavedRelaySession = true,
                fallbackMessage = "The saved relay session expired. Scan a new QR code to reconnect.",
            )
        }

        return RootTrustedReconnectFailureDecision(
            recoveryAction = RootReconnectRecoveryAction.RetrySavedPairing,
            fallbackMessage = "Could not reconnect. Tap Reconnect to try again.",
        )
    }

    private companion object {
        const val MAX_TRUSTED_RECONNECT_FAILURES = 3
    }
}

data class RootReconnectUiState(
    val attempt: RootReconnectAttempt? = null,
    val lastErrorMessage: String? = null,
    val recoveryAction: RootReconnectRecoveryAction = RootReconnectRecoveryAction.RetrySavedPairing,
    val wakeDisplayAvailable: Boolean = false,
) {
    val isAttempting: Boolean
        get() = attempt != null

    val isWakingDisplay: Boolean
        get() = attempt == RootReconnectAttempt.WakeDisplay
}

internal fun shouldAttemptSavedPairingReconnect(
    phase: RootPhase,
    hasRelayPairing: Boolean,
    sessionReady: Boolean,
    connectionState: ConnectionState,
): Boolean {
    if (phase != RootPhase.Main) return false
    if (!hasRelayPairing || sessionReady) return false
    return connectionState !is ConnectionState.Connecting &&
        connectionState !is ConnectionState.Connected
}

internal fun reconnectRecoveryActionFor(error: Throwable): RootReconnectRecoveryAction {
    when (error) {
        is CodexTrustedSessionResolveError.RePairRequired,
        is CodexTrustedSessionResolveError.NoTrustedMac,
        is CodexTrustedSessionResolveError.UnsupportedRelay,
        is LoopbackRelayException,
        -> return RootReconnectRecoveryAction.ScanNewQr
        is CodexTrustedSessionResolveError.MacOffline,
        is CodexTrustedSessionResolveError.Network,
        is CodexTrustedSessionResolveError.InvalidResponse,
        is CodexSecureTransportError.TimedOut,
        -> return RootReconnectRecoveryAction.RetrySavedPairing
        is CodexSecureTransportError.InvalidHandshake ->
            return if (error.message.requiresFreshPairing()) {
                RootReconnectRecoveryAction.ScanNewQr
            } else {
                RootReconnectRecoveryAction.RetrySavedPairing
            }
    }

    return if (error.message.requiresFreshPairing()) {
        RootReconnectRecoveryAction.ScanNewQr
    } else {
        RootReconnectRecoveryAction.RetrySavedPairing
    }
}

private fun String?.requiresFreshPairing(): Boolean {
    val text = this?.lowercase().orEmpty()
    if (text.isBlank()) return false
    val freshPairingHints =
        listOf(
            "scan a fresh qr",
            "scan a new qr",
            "re-pair",
            "re pair",
            "pairing is incomplete",
            "pairing metadata is missing",
            "session id did not match",
            "identity key did not match",
            "signature could not be verified",
        )
    return freshPairingHints.any { text.contains(it) }
}
