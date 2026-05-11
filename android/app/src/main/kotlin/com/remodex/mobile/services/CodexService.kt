package com.remodex.mobile.services

import android.content.Context
import com.remodex.mobile.core.model.CodexBridgeUpdatePrompt
import com.remodex.mobile.core.model.CodexAccessMode
import com.remodex.mobile.core.model.CodexCollaborationModeKind
import com.remodex.mobile.core.model.CommandExecutionDetails
import com.remodex.mobile.core.model.ContextWindowUsage
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexModelOption
import com.remodex.mobile.core.model.CodexRateLimitBucket
import com.remodex.mobile.core.model.CodexReviewTarget
import com.remodex.mobile.core.model.CodexSecureSession
import com.remodex.mobile.core.model.CodexServiceTier
import com.remodex.mobile.core.model.CodexTurnMention
import com.remodex.mobile.core.model.CodexTurnSkillMention
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.PendingApprovalDecision
import com.remodex.mobile.core.model.PendingApprovalRequest
import com.remodex.mobile.core.model.PendingStructuredInputRequest
import com.remodex.mobile.core.model.RPCMessage
import com.remodex.mobile.core.model.ThreadHistoryPaginationState
import com.remodex.mobile.core.persistence.CodexMessagePersistence
import com.remodex.mobile.core.persistence.SessionPersistence
import com.remodex.mobile.core.protocol.JsonRpcCodec
import com.remodex.mobile.core.security.SecureStore
import com.remodex.mobile.core.notification.RemodexLocalNotificationPresenter
import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.core.transport.SecureControlMultiplexer
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.data.CommandExecutionDetailsStore
import com.remodex.mobile.data.IncomingEventRouter
import com.remodex.mobile.data.MessageTimelineStore
import com.remodex.mobile.data.QueuedTurnDraft
import com.remodex.mobile.data.QueuedTurnDraftPreview
import com.remodex.mobile.data.TurnDraftQueueStore
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.WebSocket

private const val INITIAL_TIMELINE_TAIL_LIMIT = 48

/**
 * Android counterpart of [CodexService.swift](../../../../../../../../CodexMobile/CodexMobile/Services/CodexService.swift).
 *
 * Split across Kotlin files mirroring Swift extensions:
 * - [CodexServiceConnection] ← CodexService+Connection.swift
 * - [CodexServiceTransport] ← CodexService+Transport.swift
 * - [CodexServiceSecureTransport] ← CodexService+SecureTransport.swift
 * - [CodexServiceMessages] ← CodexService+Messages.swift
 * - [CodexServiceSync] ← CodexService+Sync.swift
 * - [CodexServiceHistory] ← CodexService+History.swift
 * - [CodexServiceVoice] ← CodexService+Voice.swift (bridge auth + ChatGPT transcribe)
 *
 * Push routing remains in [com.remodex.mobile.data.IncomingEventRouter] (Swift: CodexService+Incoming.swift).
 */
class CodexService(
    context: Context,
    internal val httpClient: OkHttpClient,
    internal val secureStore: SecureStore,
    internal val sessionPersistence: SessionPersistence,
    messagePersistence: CodexMessagePersistence,
) : CodexRepository {
    internal val appContext = context.applicationContext

    internal val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    internal val jsonRpc = JsonRpcCodec(json)

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Nullable so [resetBridgeSession] can run from extension files (no lateinit [isInitialized] outside the class). */
    internal var controlMux: SecureControlMultiplexer? = null
    internal var wireInbound: Channel<String>? = null
    internal var wireJob: Job? = null

    @Volatile
    internal var webSocket: WebSocket? = null

    @Volatile
    internal var secureSession: CodexSecureSession? = null

    @Volatile
    internal var sessionReady = false

    /** Cleared when the bridge reports unsupported `voice/resolveAuth` (parity iOS). */
    @Volatile
    internal var supportsBridgeVoiceAuth: Boolean = true

    /**
     * Cleared when a `turn/start` or `thread/start` error indicates the bridge does not support `serviceTier`
     * for this session; reset in [com.remodex.mobile.services.resetBridgeSession] (parity iOS `supportsServiceTier`).
     */
    @Volatile
    internal var supportsServiceTier: Boolean = true

    @Volatile
    internal var closingByClient: Boolean = false

    internal val wireDropHandling = AtomicBoolean(false)

    internal val pendingRpc = ConcurrentHashMap<String, CompletableDeferred<RPCMessage>>()

    internal val _isSessionReady = MutableStateFlow(false)
    override val isSessionReady: StateFlow<Boolean> = _isSessionReady.asStateFlow()

    internal val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Offline)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    internal val _threads = MutableStateFlow(sessionPersistence.loadCachedThreads())
    override val threads: StateFlow<List<CodexThread>> = _threads.asStateFlow()

    internal val _activeThreadId = MutableStateFlow(sessionPersistence.loadLastActiveThreadId())
    override val activeThreadId: StateFlow<String?> = _activeThreadId.asStateFlow()

    /** Thread id → turn id attivo (running), per Stop / interrupt (J.3). */
    internal val _runningTurnIdByThread = MutableStateFlow<Map<String, String>>(emptyMap())
    override val runningTurnIdByThread: StateFlow<Map<String, String>> = _runningTurnIdByThread.asStateFlow()

    internal val _protectedRunningFallbackThreadIds = MutableStateFlow<Set<String>>(emptySet())
    override val protectedRunningFallbackThreadIds: StateFlow<Set<String>> =
        _protectedRunningFallbackThreadIds.asStateFlow()

    internal val _pendingBranchPickerThreadId = MutableStateFlow<String?>(null)
    override val pendingBranchPickerThreadId: StateFlow<String?> = _pendingBranchPickerThreadId.asStateFlow()

    internal val _availableModels = MutableStateFlow<List<CodexModelOption>>(emptyList())
    override val availableModels: StateFlow<List<CodexModelOption>> = _availableModels.asStateFlow()

    internal val _isLoadingModels = MutableStateFlow(false)
    override val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    internal val _modelsErrorMessage = MutableStateFlow<String?>(null)
    override val modelsErrorMessage: StateFlow<String?> = _modelsErrorMessage.asStateFlow()

    internal val _rateLimitBuckets = MutableStateFlow<List<CodexRateLimitBucket>>(emptyList())
    override val rateLimitBuckets: StateFlow<List<CodexRateLimitBucket>> = _rateLimitBuckets.asStateFlow()

    internal val _isLoadingRateLimits = MutableStateFlow(false)
    override val isLoadingRateLimits: StateFlow<Boolean> = _isLoadingRateLimits.asStateFlow()

    internal val _rateLimitsErrorMessage = MutableStateFlow<String?>(null)
    override val rateLimitsErrorMessage: StateFlow<String?> = _rateLimitsErrorMessage.asStateFlow()

    internal val _hasResolvedRateLimitsSnapshot = MutableStateFlow(false)
    override val hasResolvedRateLimitsSnapshot: StateFlow<Boolean> = _hasResolvedRateLimitsSnapshot.asStateFlow()

    internal val _contextWindowUsageByThread = MutableStateFlow<Map<String, ContextWindowUsage>>(emptyMap())
    override val contextWindowUsageByThread: StateFlow<Map<String, ContextWindowUsage>> =
        _contextWindowUsageByThread.asStateFlow()

    internal val _contextWindowUsageLoadingThreads = MutableStateFlow<Set<String>>(emptySet())
    override val contextWindowUsageLoadingThreads: StateFlow<Set<String>> =
        _contextWindowUsageLoadingThreads.asStateFlow()

    internal val _contextWindowUsageErrorByThread = MutableStateFlow<Map<String, String>>(emptyMap())
    override val contextWindowUsageErrorByThread: StateFlow<Map<String, String>> =
        _contextWindowUsageErrorByThread.asStateFlow()

    private val runtimeSelection = sessionPersistence.loadRuntimeSelection()

    internal val _selectedModelId = MutableStateFlow(runtimeSelection.selectedModelId)
    override val selectedModelId: StateFlow<String?> = _selectedModelId.asStateFlow()

    internal val _selectedReasoningEffort = MutableStateFlow(runtimeSelection.selectedReasoningEffort)
    override val selectedReasoningEffort: StateFlow<String?> = _selectedReasoningEffort.asStateFlow()

    internal val _selectedAccessMode =
        MutableStateFlow(
            runCatching {
                runtimeSelection.selectedAccessMode?.let { CodexAccessMode.valueOf(it) }
            }.getOrNull() ?: CodexAccessMode.onRequest,
        )
    override val selectedAccessMode: StateFlow<CodexAccessMode> = _selectedAccessMode.asStateFlow()

    internal val _selectedServiceTier =
        MutableStateFlow(
            runCatching {
                runtimeSelection.selectedServiceTier?.let { CodexServiceTier.valueOf(it) }
            }.getOrNull(),
        )
    override val selectedServiceTier: StateFlow<CodexServiceTier?> = _selectedServiceTier.asStateFlow()

    internal val _pendingApprovalRequest = MutableStateFlow<PendingApprovalRequest?>(null)
    override val pendingApprovalRequest: StateFlow<PendingApprovalRequest?> =
        _pendingApprovalRequest.asStateFlow()

    internal val _pendingStructuredInputRequest = MutableStateFlow<PendingStructuredInputRequest?>(null)
    override val pendingStructuredInputRequest: StateFlow<PendingStructuredInputRequest?> =
        _pendingStructuredInputRequest.asStateFlow()

    internal val _bridgeUpdatePrompt = MutableStateFlow<CodexBridgeUpdatePrompt?>(null)
    override val bridgeUpdatePrompt: StateFlow<CodexBridgeUpdatePrompt?> =
        _bridgeUpdatePrompt.asStateFlow()

    /**
     * Once per session after [markServiceTierUnsupportedForCurrentBridge]; cleared on [resetBridgeSession]
     * (parity iOS `hasPresentedServiceTierBridgeUpdatePrompt`).
     */
    @Volatile
    internal var hasPresentedServiceTierBridgeUpdatePrompt: Boolean = false

    /** Cleared in [resetBridgeSession] (parity iOS `supportsThreadFork`). */
    @Volatile
    internal var supportsThreadFork: Boolean = true

    @Volatile
    internal var hasPresentedThreadForkBridgeUpdatePrompt: Boolean = false

    internal val turnDraftQueueStore = TurnDraftQueueStore()

    internal val pendingApprovalResponders =
        ConcurrentHashMap<String, (PendingApprovalDecision) -> Unit>()
    internal val pendingStructuredInputResponders =
        ConcurrentHashMap<String, (answersByQuestionId: Map<String, List<String>>) -> Unit>()

    internal val messageTimelineStore =
        MessageTimelineStore(
            persistence = messagePersistence,
            lastActiveThreadId = sessionPersistence.loadLastActiveThreadId(),
            initialTailLimit = INITIAL_TIMELINE_TAIL_LIMIT,
        )
    internal val commandExecutionDetailsStore = CommandExecutionDetailsStore()

    init {
        scope.launch {
            runCatching { messagePersistence.migrateLegacyMonolithToPerThreadIfNeeded() }
        }
    }

    /** Local-only background alerts (turn completion, approvals, structured input). */
    internal val localNotificationPresenter = RemodexLocalNotificationPresenter(appContext)

    override val messagesByThread: StateFlow<Map<String, List<CodexMessage>>> =
        messageTimelineStore.messagesByThread
    override val commandExecutionDetailsByItemId: StateFlow<Map<String, CommandExecutionDetails>> =
        commandExecutionDetailsStore.detailsByItemId

    override val turnDraftQueueDepthByThread: StateFlow<Map<String, Int>> =
        turnDraftQueueStore.depthByThread
    override val turnDraftQueuePreviewByThread: StateFlow<Map<String, List<QueuedTurnDraftPreview>>> =
        turnDraftQueueStore.previewByThread

    internal val hydratedThreadIds = Collections.synchronizedSet(mutableSetOf<String>())
    internal val _threadHistoryPaginationByThread =
        MutableStateFlow<Map<String, ThreadHistoryPaginationState>>(emptyMap())
    override val threadHistoryPaginationByThread: StateFlow<Map<String, ThreadHistoryPaginationState>> =
        _threadHistoryPaginationByThread.asStateFlow()
    internal val _loadingOlderHistoryThreadIds = MutableStateFlow<Set<String>>(emptySet())
    override val loadingOlderHistoryThreadIds: StateFlow<Set<String>> =
        _loadingOlderHistoryThreadIds.asStateFlow()
    internal val _olderHistoryErrorByThread = MutableStateFlow<Map<String, String>>(emptyMap())
    override val olderHistoryErrorByThread: StateFlow<Map<String, String>> =
        _olderHistoryErrorByThread.asStateFlow()

    /** Thread ids that already received a successful `thread/resume` this session — parity iOS `resumedThreadIDs`. */
    internal val resumedThreadIds = Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * In-flight same-thread rebind: preferred cwd until [thread/list]/[thread/read] match (parity
     * iOS [authoritativeProjectPathByThreadID] / [moveThreadToProjectPath]).
     */
    internal val authoritativeProjectPathByThreadId = ConcurrentHashMap<String, String>()

    internal val persistedThreadRenameById =
        ConcurrentHashMap<String, String>().also {
            it.putAll(sessionPersistence.loadThreadRenames())
        }

    /** First Codex managed worktree path associated with a thread for handoff heuristics. */
    internal val associatedManagedWorktreePathByThreadId =
        ConcurrentHashMap<String, String>().also {
            it.putAll(sessionPersistence.loadAssociatedManagedWorktreePaths())
        }

    /**
     * When set, [sendRequestImpl] (not handshake) uses this instead of the wire. Used for JVM tests.
     * Parity: iOS [requestTransportOverride] on [CodexService] for `CodexThreadProjectRoutingTests`.
     */
    @Volatile
    internal var testRpcRequestHandler: (suspend (String, JSONValue?) -> RPCMessage)? = null
    internal val loadingHistory = ConcurrentHashMap.newKeySet<String>()
    internal var supportsTurnCollaborationMode = true
    internal var supportsStructuredSkillInput = true
    internal var supportsStructuredMentionInput = true

    internal val incomingRouter by lazy {
        IncomingEventRouter(
            scope = scope,
            threads = _threads,
            activeThreadId = _activeThreadId,
            messageTimeline = messageTimelineStore,
            commandDetailsStore = commandExecutionDetailsStore,
            onRequestThreadSync = {
                scope.launch {
                    if (sessionReady) {
                        runCatching { refreshThreadsInternal() }
                    }
                }
            },
            onHydrateThread = { tid ->
                scope.launch(Dispatchers.IO) {
                    if (sessionReady) {
                        runCatching { syncThreadHistoryInternal(tid, force = false) }
                    }
                }
            },
            onTurnLifecycle = { th, t ->
                if (t != null) {
                    noteTurnStarted(th, t)
                } else {
                    noteProtectedRunningFallback(th, true)
                }
            },
            onTurnFinished = { th -> noteTurnFinished(th) },
            isTurnStreamingActive = { threadId, turnId ->
                val runMap = _runningTurnIdByThread.value
                val fb = _protectedRunningFallbackThreadIds.value
                val activeTid = runMap[threadId]
                when {
                    turnId.isNullOrBlank() -> activeTid != null || fb.contains(threadId)
                    else ->
                        activeTid == turnId ||
                            (activeTid == null && fb.contains(threadId))
                }
            },
            shouldAutoApproveRequests = { _selectedAccessMode.value == CodexAccessMode.fullAccess },
            onApprovalRequest = { request, respond ->
                enqueuePendingApprovalRequest(request, respond)
            },
            onStructuredInputRequest = { request, respond ->
                enqueuePendingStructuredInputRequest(request, respond)
            },
            onRateLimitsUpdated = { params -> handleRateLimitsUpdatedParams(params) },
            onThreadContextUsageLive = { tid, usage -> applyLiveContextWindowUsage(tid, usage) },
            resolveAmbiguousUsageThreadId = { resolveFallbackSingleThreadForUsage() },
            remapThreadFromServer = { t -> applyPersistedThreadRename(applyAuthoritativeProjectPathToServerThread(t)) },
            persistedThreadRename = { tid -> persistedThreadRename(tid) },
            onRunCompletionAttention = { th, turn, kind -> notifyRunCompletionAttention(th, turn, kind) },
        )
    }

    internal fun noteProtectedRunningFallback(
        threadId: String,
        active: Boolean,
    ) {
        val th = threadId.trim()
        if (th.isEmpty()) return
        _protectedRunningFallbackThreadIds.value =
            if (active) {
                _protectedRunningFallbackThreadIds.value + th
            } else {
                _protectedRunningFallbackThreadIds.value - th
            }
    }

    internal fun noteTurnStarted(
        threadId: String,
        turnId: String,
    ) {
        val th = threadId.trim()
        val t = turnId.trim()
        if (th.isEmpty() || t.isEmpty()) return
        _runningTurnIdByThread.value = _runningTurnIdByThread.value + (th to t)
        _protectedRunningFallbackThreadIds.value = _protectedRunningFallbackThreadIds.value - th
    }

    internal fun noteTurnFinished(threadId: String) {
        val th = threadId.trim()
        if (th.isEmpty()) return
        _runningTurnIdByThread.value = _runningTurnIdByThread.value.filterKeys { it != th }
        _protectedRunningFallbackThreadIds.value = _protectedRunningFallbackThreadIds.value - th
    }

    /** Single running thread fallback for legacy token usage events without explicit thread id. */
    internal fun resolveFallbackSingleThreadForUsage(): String? {
        val running = _runningTurnIdByThread.value.keys
        val fb = _protectedRunningFallbackThreadIds.value
        val candidates = running + fb
        return candidates.singleOrNull()
    }

    override suspend fun connect(
        serverUrl: String,
        token: String,
        role: String?,
    ) = connectImpl(serverUrl, token, role)

    override suspend fun disconnect() = disconnectImpl()

    override suspend fun setActiveThreadId(threadId: String?) = setActiveThreadIdImpl(threadId)

    override suspend fun refreshModels() = refreshModelsForRepository()

    override suspend fun setSelectedModelId(modelId: String?) = setSelectedModelIdForRepository(modelId)

    override suspend fun setSelectedReasoningEffort(reasoningEffort: String?) =
        setSelectedReasoningEffortForRepository(reasoningEffort)

    override suspend fun setSelectedAccessMode(accessMode: CodexAccessMode) =
        setSelectedAccessModeForRepository(accessMode)

    override suspend fun setSelectedServiceTier(serviceTier: CodexServiceTier?) =
        setSelectedServiceTierForRepository(serviceTier)

    override suspend fun resolvePendingApproval(
        requestId: String,
        decision: PendingApprovalDecision,
    ) = resolvePendingApprovalForRepository(requestId, decision)

    override suspend fun resolvePendingStructuredInput(
        requestId: String,
        answersByQuestionId: Map<String, List<String>>,
    ) = resolvePendingStructuredInputForRepository(requestId, answersByQuestionId)

    override fun dismissBridgeUpdatePrompt() {
        _bridgeUpdatePrompt.value = null
    }

    override suspend fun refreshThreads() =
        withContext(Dispatchers.IO) {
            refreshThreadsInternal()
        }

    override suspend fun syncThreadHistory(
        threadId: String,
        force: Boolean,
    ) = withContext(Dispatchers.IO) {
        syncThreadHistoryInternal(threadId, force)
    }

    override suspend fun loadOlderThreadHistory(threadId: String) = withContext(Dispatchers.IO) {
        loadOlderThreadHistoryInternal(threadId)
    }

    override suspend fun refreshRateLimits() = refreshRateLimitsForRepository()

    override suspend fun refreshContextWindowUsage(threadId: String) =
        refreshContextWindowUsageForRepository(threadId)

    override suspend fun refreshUsageStatus(threadId: String?) =
        refreshUsageStatusForRepository(threadId)

    override fun shouldAutoRefreshUsageStatus(threadId: String?): Boolean =
        shouldAutoRefreshUsageStatusForRepository(threadId)

    override suspend fun sendRequest(
        method: String,
        params: JSONValue?,
    ): RPCMessage = sendRequestImpl(method, params)

    override suspend fun renameThread(
        threadId: String,
        name: String,
    ) = renameThreadForRepository(threadId, name)

    override suspend fun deleteThreadLocally(threadId: String) = deleteThreadLocallyForRepository(threadId)

    override suspend fun archiveThreadGroup(threadIds: List<String>) = archiveThreadGroupForRepository(threadIds)

    override suspend fun deleteLocalThreadGroup(threadIds: List<String>) = deleteLocalThreadGroupForRepository(threadIds)

    override suspend fun transcribeBridgeVoiceWav(
        wavBytes: ByteArray,
        durationSeconds: Double,
    ): String = transcribeBridgeVoiceWavImpl(wavBytes, durationSeconds)

    override suspend fun startThread(
        model: String?,
        cwd: String?,
        serviceTier: String?,
    ): CodexThread =
        startThreadForRepository(
            model = model,
            cwd = cwd,
            serviceTier = serviceTier,
        )

    override fun requestBranchPickerForThread(threadId: String) {
        val tid = CodexThread.normalizeIdentifier(threadId) ?: return
        _pendingBranchPickerThreadId.value = tid
    }

    override fun consumeBranchPickerRequest(threadId: String) {
        val tid = CodexThread.normalizeIdentifier(threadId) ?: return
        if (_pendingBranchPickerThreadId.value == tid) {
            _pendingBranchPickerThreadId.value = null
        }
    }

    override suspend fun forkThread(
        sourceThreadId: String,
        targetProjectPath: String?,
    ): CodexThread = forkThreadForRepository(sourceThreadId, targetProjectPath)

    override suspend fun enqueueTurnDraft(
        threadId: String,
        text: String,
        attachments: List<CodexImageAttachment>,
        skillMentions: List<CodexTurnSkillMention>,
        fileMentions: List<CodexTurnMention>,
        collaborationMode: CodexCollaborationModeKind?,
        prepend: Boolean,
    ) = withContext(Dispatchers.IO) {
        turnDraftQueueStore.enqueue(
            threadId = threadId,
            text = text,
            attachments = attachments,
            skillMentions = skillMentions,
            fileMentions = fileMentions,
            collaborationMode = collaborationMode,
            prepend = prepend,
        )
    }

    override suspend fun pollTurnDraft(threadId: String): QueuedTurnDraft? =
        withContext(Dispatchers.IO) {
            turnDraftQueueStore.poll(threadId)
        }

    override suspend fun removeQueuedTurnDraft(
        threadId: String,
        draftId: String,
    ): QueuedTurnDraft? =
        withContext(Dispatchers.IO) {
            turnDraftQueueStore.remove(threadId, draftId)
        }

    override suspend fun startTurn(
        threadId: String,
        text: String,
        attachments: List<CodexImageAttachment>,
        skillMentions: List<CodexTurnSkillMention>,
        fileMentions: List<CodexTurnMention>,
        collaborationMode: CodexCollaborationModeKind?,
    ) = startTurnForRepository(threadId, text, attachments, skillMentions, fileMentions, collaborationMode)

    override suspend fun startReview(
        threadId: String,
        target: CodexReviewTarget,
        baseBranch: String?,
    ) = startReviewForRepository(threadId, target, baseBranch)

    override suspend fun interruptTurn(
        threadId: String,
        turnId: String?,
    ) = interruptTurnForRepository(threadId, turnId)

    override suspend fun sendNotification(
        method: String,
        params: JSONValue?,
    ) = sendNotificationImpl(method, params)

    override suspend fun moveThreadToProjectPath(
        threadId: String,
        projectPath: String,
    ) = withContext(Dispatchers.IO) {
        moveThreadToProjectPathImpl(threadId, projectPath)
    }

    override fun currentAuthoritativeProjectPathFor(threadId: String) =
        currentAuthoritativeProjectPathForImpl(threadId)

    override fun associatedManagedWorktreePathFor(threadId: String) =
        associatedManagedWorktreePathForImpl(threadId)
}
