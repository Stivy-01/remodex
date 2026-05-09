package com.remodex.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.remodex.mobile.AppContainer
import com.remodex.mobile.core.persistence.OnboardingPreferences
import com.remodex.mobile.core.persistence.SessionPersistence
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.pairing.buildWebSocketConnectParams
import com.remodex.mobile.pairing.reconnectUsingSavedRelaySnapshot
import com.remodex.mobile.services.DesktopHandoffService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class RootPhase {
    Onboarding,
    PairingScan,
    Main,
}

/**
 * Root shell: onboarding / pairing / main, auto-connect, restore thread.
 * Mirrors [ContentViewModel.swift](CodexMobile/CodexMobile/Views/Home/ContentViewModel.swift) in a reduced form.
 */
class RootViewModel(
    private val repository: CodexRepository,
    private val sessionPersistence: SessionPersistence,
    private val onboardingPreferences: OnboardingPreferences,
) : ViewModel() {
    private var lastAutoConnectAttemptMs = 0L
    private var restoredActiveThread = false
    private var autoReconnectJob: Job? = null
    private var manualReconnectJob: Job? = null
    private val trustedReconnectFailureBudget = RootTrustedReconnectFailureBudget()

    private val _phase = MutableStateFlow(computeInitialPhase())
    val phase: StateFlow<RootPhase> = _phase.asStateFlow()

    private val _reconnectUiState = MutableStateFlow(RootReconnectUiState())
    val reconnectUiState: StateFlow<RootReconnectUiState> = _reconnectUiState.asStateFlow()

    private fun computeInitialPhase(): RootPhase {
        if (!onboardingPreferences.hasSeenOnboarding()) {
            return RootPhase.Onboarding
        }
        if (!hasRelayPairing()) {
            return RootPhase.PairingScan
        }
        return RootPhase.Main
    }

    private fun hasRelayPairing(): Boolean {
        val s = sessionPersistence.loadRelaySnapshot()
        return !s.relaySessionId.isNullOrBlank() && !s.relayUrl.isNullOrBlank()
    }

    fun finishOnboarding() {
        onboardingPreferences.setHasSeenOnboarding(true)
        _phase.value = RootPhase.PairingScan
    }

    fun notifyPairingSuccess() {
        stopAutoReconnectForManualControl()
        trustedReconnectFailureBudget.reset()
        _reconnectUiState.value = RootReconnectUiState()
        _phase.value = RootPhase.Main
    }

    fun closePairingScanner() {
        _phase.value = RootPhase.Main
    }

    fun openPairingScanner() {
        stopAutoReconnectForManualControl()
        _phase.value = RootPhase.PairingScan
    }

    fun onAppLaunched() {
        autoReconnectJob?.cancel()
        autoReconnectJob = viewModelScope.launch {
            restoreActiveThreadIfNeeded(allowBeforeSessionReady = true)
            attemptAutoConnectIfNeeded(minIntervalMs = 0L)
        }
    }

    fun onAppForegrounded() {
        if (autoReconnectJob?.isActive == true || manualReconnectJob?.isActive == true) return
        autoReconnectJob = viewModelScope.launch {
            if (repository.isSessionReady.value) {
                restoreActiveThreadIfNeeded()
                val active = repository.activeThreadId.value ?: sessionPersistence.loadLastActiveThreadId()
                if (!active.isNullOrBlank()) {
                    repository.setActiveThreadId(active)
                    runCatching { repository.refreshUsageStatus(active) }
                } else {
                    repository.refreshThreads()
                }
                return@launch
            }
            attemptAutoConnectIfNeeded(minIntervalMs = 2_000L)
        }
    }

    fun reconnectSavedPairingManually() {
        stopAutoReconnectForManualControl()
        if (manualReconnectJob?.isActive == true) return
        manualReconnectJob =
            viewModelScope.launch {
                attemptSavedPairingReconnect(RootReconnectAttempt.Manual)
            }
    }

    fun wakeSavedComputerDisplay() {
        stopAutoReconnectForManualControl()
        if (manualReconnectJob?.isActive == true) return
        manualReconnectJob =
            viewModelScope.launch {
                wakeSavedComputerDisplayThenReconnect()
            }
    }

    /** Best-effort reconnect after npm bridge update (parity iOS `retryBridgeConnectionAfterUpdate`). */
    fun retryBridgeConnectionAfterUpdate() {
        reconnectSavedPairingManually()
    }

    suspend fun restoreActiveThreadIfNeeded(allowBeforeSessionReady: Boolean = false) {
        if (!allowBeforeSessionReady && !repository.isSessionReady.value) return
        val pending = AppContainer.consumePendingOpenThreadFromNotification()
        if (pending != null) {
            repository.setActiveThreadId(pending)
            restoredActiveThread = true
            return
        }
        if (restoredActiveThread) return
        val id = sessionPersistence.loadLastActiveThreadId() ?: return
        repository.setActiveThreadId(id)
        restoredActiveThread = true
    }

    fun persistActiveThreadId(threadId: String?) {
        sessionPersistence.saveLastActiveThreadId(threadId)
    }

    private suspend fun attemptAutoConnectIfNeeded(minIntervalMs: Long) {
        val snap = sessionPersistence.loadRelaySnapshot()
        val hasRelayPairing = !snap.relayUrl.isNullOrBlank() && !snap.relaySessionId.isNullOrBlank()
        if (!shouldAttemptSavedPairingReconnect(
                phase = _phase.value,
                hasRelayPairing = hasRelayPairing,
                sessionReady = repository.isSessionReady.value,
                connectionState = repository.connectionState.value,
            )
        ) {
            return
        }
        val now = System.currentTimeMillis()
        if (minIntervalMs > 0 && now - lastAutoConnectAttemptMs < minIntervalMs) return
        lastAutoConnectAttemptMs = now
        attemptSavedPairingReconnect(RootReconnectAttempt.Auto)
    }

    private suspend fun attemptSavedPairingReconnect(attempt: RootReconnectAttempt) {
        val snap = sessionPersistence.loadRelaySnapshot()
        val hasRelayPairing = !snap.relayUrl.isNullOrBlank() && !snap.relaySessionId.isNullOrBlank()
        if (!shouldAttemptSavedPairingReconnect(
                phase = _phase.value,
                hasRelayPairing = hasRelayPairing,
                sessionReady = repository.isSessionReady.value,
                connectionState = repository.connectionState.value,
            )
        ) {
            return
        }
        _reconnectUiState.value = RootReconnectUiState(attempt = attempt)
        runCatching {
            when (attempt) {
                RootReconnectAttempt.Auto -> {
                    val (url, token) =
                        buildWebSocketConnectParams(
                            snap,
                            sessionPersistence.loadLocalRelayHostOverride().orEmpty(),
                        )
                    repository.connect(serverUrl = url, token = token, role = null)
                }
                RootReconnectAttempt.Manual ->
                    reconnectUsingSavedRelaySnapshot(
                        repository,
                        snap,
                        sessionPersistence.loadLocalRelayHostOverride().orEmpty(),
                    )
                RootReconnectAttempt.WakeDisplay ->
                    DesktopHandoffService(
                        repository = repository,
                        savedRelaySnapshotProvider = { sessionPersistence.loadRelaySnapshot() },
                    ).wakeDisplay()
            }
        }.fold(
            onSuccess = {
                trustedReconnectFailureBudget.reset()
                _reconnectUiState.value = RootReconnectUiState()
                if (attempt != RootReconnectAttempt.WakeDisplay) {
                    viewModelScope.launch {
                        AppContainer.betaEngagementRepository.recordMissionEvent(
                            eventType = "reconnect_completed",
                            screen = "connection",
                        )
                    }
                }
            },
            onFailure = { error ->
                val recoveryDecision = trustedReconnectFailureBudget.record(error)
                if (recoveryDecision.shouldDropSavedRelaySession) {
                    sessionPersistence.clearRelaySession()
                    _phase.value = RootPhase.PairingScan
                }
                _reconnectUiState.value =
                    RootReconnectUiState(
                        recoveryAction = recoveryDecision.recoveryAction,
                        wakeDisplayAvailable =
                            recoveryDecision.recoveryAction == RootReconnectRecoveryAction.RetrySavedPairing,
                        lastErrorMessage =
                            if (recoveryDecision.shouldDropSavedRelaySession) {
                                recoveryDecision.fallbackMessage
                            } else {
                                error.message?.trim()?.takeIf { it.isNotEmpty() }
                                    ?: recoveryDecision.fallbackMessage
                            },
                    )
            },
        )
    }

    private suspend fun wakeSavedComputerDisplayThenReconnect() {
        val snap = sessionPersistence.loadRelaySnapshot()
        val hasRelayPairing = !snap.relayUrl.isNullOrBlank() && !snap.relaySessionId.isNullOrBlank()
        if (!hasRelayPairing) {
            _reconnectUiState.value =
                RootReconnectUiState(
                    recoveryAction = RootReconnectRecoveryAction.ScanNewQr,
                    lastErrorMessage = "Reconnect to your paired computer or scan a new QR code first.",
                )
            return
        }
        _reconnectUiState.value =
            RootReconnectUiState(
                attempt = RootReconnectAttempt.WakeDisplay,
                wakeDisplayAvailable = true,
                lastErrorMessage = "Waking your computer...",
            )
        runCatching {
            DesktopHandoffService(
                repository = repository,
                savedRelaySnapshotProvider = { sessionPersistence.loadRelaySnapshot() },
            ).wakeDisplay()
        }.fold(
            onSuccess = {
                if (repository.isSessionReady.value) {
                    _reconnectUiState.value = RootReconnectUiState()
                } else {
                    attemptSavedPairingReconnect(RootReconnectAttempt.Manual)
                }
            },
            onFailure = { error ->
                val recoveryDecision = trustedReconnectFailureBudget.record(error)
                if (recoveryDecision.shouldDropSavedRelaySession) {
                    sessionPersistence.clearRelaySession()
                    _phase.value = RootPhase.PairingScan
                }
                _reconnectUiState.value =
                    RootReconnectUiState(
                        recoveryAction = recoveryDecision.recoveryAction,
                        wakeDisplayAvailable =
                            recoveryDecision.recoveryAction == RootReconnectRecoveryAction.RetrySavedPairing,
                        lastErrorMessage =
                            if (recoveryDecision.shouldDropSavedRelaySession) {
                                recoveryDecision.fallbackMessage
                            } else {
                                error.message?.trim()?.takeIf { it.isNotEmpty() }
                                    ?: recoveryDecision.fallbackMessage
                            },
                    )
            },
        )
    }

    private fun stopAutoReconnectForManualControl() {
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        if (_reconnectUiState.value.attempt == RootReconnectAttempt.Auto) {
            _reconnectUiState.value = RootReconnectUiState()
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(RootViewModel::class.java)) {
                        return RootViewModel(
                            AppContainer.codexRepository,
                            AppContainer.sessionPersistence,
                            OnboardingPreferences(AppContainer.appContext),
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel: $modelClass")
                }
            }
    }
}
