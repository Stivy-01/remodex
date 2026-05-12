package com.remodex.mobile.services

import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.core.transport.SecureControlMultiplexer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mirrors [CodexService+Connection.swift](../../../../../../../../CodexMobile/CodexMobile/Services/CodexService+Connection.swift):
 * connect / disconnect lifecycle and session reset.
 */
internal suspend fun CodexService.connectImpl(
    serverUrl: String,
    token: String,
    role: String?,
) = withContext(Dispatchers.IO) {
    disconnectImpl(preservePresentationState = true)
    _connectionState.value = ConnectionState.Connecting
    controlMux = SecureControlMultiplexer(json) { }
    val inbound = Channel<String>(capacity = 64)
    wireInbound = inbound
    wireJob =
        scope.launch {
            for (line in inbound) {
                try {
                    processWireText(line)
                } catch (_: CancellationException) {
                    break
                } catch (_: Exception) {
                    // Keep reader alive (parity with iOS receive loop).
                }
            }
        }

    try {
        openWebSocketAwaitOpen(parseRelayHttpUrl(serverUrl), token, role)
        performSecureHandshake()
        initializeSession()
    } catch (t: Throwable) {
        closingByClient = true
        try {
            resetBridgeSession(preservePresentationState = true)
        } finally {
            closingByClient = false
        }
        _connectionState.value =
            ConnectionState.Error(
                t.message?.trim()?.takeIf { it.isNotEmpty() } ?: "Connection failed",
            )
        throw t
    }
    sessionReady = true
    _isSessionReady.value = true
    _connectionState.value = ConnectionState.Connected
    scope.launch(Dispatchers.IO) {
        runCatching {
            refreshThreadsInternal()
            refreshInactiveRunningThreadStatesInternal()
        }
    }
    scope.launch(Dispatchers.IO) {
        runCatching { refreshModelsInternal() }
    }
    scope.launch(Dispatchers.IO) {
        runCatching { refreshRateLimitsInternal() }
    }
    _activeThreadId.value?.let { activeId ->
        scope.launch(Dispatchers.IO) {
            catchUpThreadAfterSelectionOrReconnect(activeId)
            refreshInactiveRunningThreadStatesInternal()
        }
    }
    Unit
}

internal suspend fun CodexService.disconnectImpl(preservePresentationState: Boolean = false) =
    withContext(Dispatchers.IO) {
        closingByClient = true
        try {
            resetBridgeSession(preservePresentationState = preservePresentationState)
            _connectionState.value = ConnectionState.Offline
        } finally {
            closingByClient = false
        }
    }

internal suspend fun CodexService.setActiveThreadIdImpl(threadId: String?) {
    _activeThreadId.value = threadId?.trim()?.takeIf { it.isNotEmpty() }
    val id = _activeThreadId.value
    sessionPersistence.saveLastActiveThreadId(id)
    if (id != null && sessionReady) {
        scope.launch(Dispatchers.IO) {
            catchUpThreadAfterSelectionOrReconnect(id)
        }
    }
}

internal suspend fun CodexService.resetBridgeSession(preservePresentationState: Boolean = false) {
    sessionReady = false
    _isSessionReady.value = false
    supportsServiceTier = true
    supportsTurnCollaborationMode = true
    supportsThreadFork = true
    _bridgeUpdatePrompt.value = null
    hasPresentedServiceTierBridgeUpdatePrompt = false
    hasPresentedThreadForkBridgeUpdatePrompt = false
    if (!preservePresentationState) {
        _threads.value = emptyList()
        _activeThreadId.value = null
    }
    if (!preservePresentationState) {
        synchronized(runningTurnStateLock) {
            _runningTurnIdByThread.value = emptyMap()
            _protectedRunningFallbackThreadIds.value = emptySet()
        }
    }
    clearPendingServerRequests()
    hydratedThreadIds.clear()
    resumedThreadIds.clear()
    _threadHistoryPaginationByThread.value = emptyMap()
    _loadingOlderHistoryThreadIds.value = emptySet()
    _olderHistoryErrorByThread.value = emptyMap()
    authoritativeProjectPathByThreadId.clear()
    associatedManagedWorktreePathByThreadId.clear()
    associatedManagedWorktreePathByThreadId.putAll(sessionPersistence.loadAssociatedManagedWorktreePaths())
    turnDraftQueueStore.clear()
    testRpcRequestHandler = null
    loadingHistory.clear()
    incomingRouter.resetCaches()
    if (!preservePresentationState) {
        commandExecutionDetailsStore.clear()
    }
    wireJob?.cancel()
    wireJob = null
    wireInbound?.close()
    wireInbound = null
    webSocket?.close(1000, "client disconnect")
    webSocket = null
    secureSession = null
    controlMux?.reset()
    controlMux = null
    pendingRpc.values.forEach { d ->
        d.cancel(CancellationException("disconnected"))
    }
    pendingRpc.clear()
    _rateLimitBuckets.value = emptyList()
    _isLoadingRateLimits.value = false
    _rateLimitsErrorMessage.value = null
    _hasResolvedRateLimitsSnapshot.value = false
    _contextWindowUsageByThread.value = emptyMap()
    _contextWindowUsageLoadingThreads.value = emptySet()
    _contextWindowUsageErrorByThread.value = emptyMap()
}

internal fun CodexService.scheduleWireDrop(message: String) {
    if (closingByClient) return
    if (!wireDropHandling.compareAndSet(false, true)) return
    scope.launch(Dispatchers.IO) {
        try {
            if (!sessionReady) return@launch
            resetBridgeSession(preservePresentationState = true)
            _connectionState.value = ConnectionState.Error(message)
        } finally {
            wireDropHandling.set(false)
        }
    }
}
