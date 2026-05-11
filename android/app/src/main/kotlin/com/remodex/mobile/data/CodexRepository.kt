package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexBridgeUpdatePrompt
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexAccessMode
import com.remodex.mobile.core.model.CodexCollaborationModeKind
import com.remodex.mobile.core.model.CommandExecutionDetails
import com.remodex.mobile.core.model.ContextWindowUsage
import com.remodex.mobile.core.model.CodexModelOption
import com.remodex.mobile.core.model.CodexRateLimitBucket
import com.remodex.mobile.core.model.CodexReviewTarget
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
import com.remodex.mobile.core.model.UsageStatusRefreshPolicy
import com.remodex.mobile.core.transport.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Facade for bridge WebSocket, secure transport, and JSON-RPC.
 */
interface CodexRepository {
    val isSessionReady: StateFlow<Boolean>

    val connectionState: StateFlow<ConnectionState>

    /** Sidebar threads from the thread list RPC plus incremental thread lifecycle notifications. */
    val threads: StateFlow<List<CodexThread>>

    /** Local selection; does not call the bridge until send/UI layers do. */
    val activeThreadId: StateFlow<String?>

    /** Per-thread message rows (persisted + live notifications). */
    val messagesByThread: StateFlow<Map<String, List<CodexMessage>>>

    /** Cursor-backed history state for large threads. */
    val threadHistoryPaginationByThread: StateFlow<Map<String, ThreadHistoryPaginationState>>
        get() = MutableStateFlow(emptyMap())

    val loadingOlderHistoryThreadIds: StateFlow<Set<String>>
        get() = MutableStateFlow(emptySet())

    val olderHistoryErrorByThread: StateFlow<Map<String, String>>
        get() = MutableStateFlow(emptyMap())

    /** Live structured command metadata keyed by command item id; timeline text remains authoritative history. */
    val commandExecutionDetailsByItemId: StateFlow<Map<String, CommandExecutionDetails>>
        get() = MutableStateFlow(emptyMap())

    /** Local queued draft count per thread (J22 foundation). */
    val turnDraftQueueDepthByThread: StateFlow<Map<String, Int>>
        get() = MutableStateFlow(emptyMap())
    val turnDraftQueuePreviewByThread: StateFlow<Map<String, List<QueuedTurnDraftPreview>>>
        get() = MutableStateFlow(emptyMap())

    /** One-shot UI request to open the branch picker for a newly-created repo-bound thread. */
    val pendingBranchPickerThreadId: StateFlow<String?>
        get() = MutableStateFlow(null)

    /** Thread id → turn id in esecuzione (per UI Stop). */
    val runningTurnIdByThread: StateFlow<Map<String, String>>

    /**
     * Thread con run attivo ma senza `turnId` ancora noto (parity iOS `protectedRunningFallback` / `runningThreadIDs`).
     */
    val protectedRunningFallbackThreadIds: StateFlow<Set<String>>

    val availableModels: StateFlow<List<CodexModelOption>>

    val isLoadingModels: StateFlow<Boolean>

    val modelsErrorMessage: StateFlow<String?>

    /** Account rate limits from the bridge (`account/rateLimits/read` + push updates). */
    val rateLimitBuckets: StateFlow<List<CodexRateLimitBucket>>

    val isLoadingRateLimits: StateFlow<Boolean>

    val rateLimitsErrorMessage: StateFlow<String?>

    /** True after a successful read or an `account/rateLimits/updated` merge. */
    val hasResolvedRateLimitsSnapshot: StateFlow<Boolean>

    /** Cached `thread/contextWindow/read` snapshots keyed by thread id. */
    val contextWindowUsageByThread: StateFlow<Map<String, ContextWindowUsage>>

    /** Thread ids currently waiting on `thread/contextWindow/read`. */
    val contextWindowUsageLoadingThreads: StateFlow<Set<String>>

    /** Per-thread last refresh error (cleared on success). */
    val contextWindowUsageErrorByThread: StateFlow<Map<String, String>>

    val selectedModelId: StateFlow<String?>

    val selectedReasoningEffort: StateFlow<String?>

    val selectedAccessMode: StateFlow<CodexAccessMode>

    val selectedServiceTier: StateFlow<CodexServiceTier?>

    val pendingApprovalRequest: StateFlow<PendingApprovalRequest?>

    val pendingStructuredInputRequest: StateFlow<PendingStructuredInputRequest?>

    /** Recoverable npm / pairing prompts from the service layer (bridge upgrade, unsupported runtime fields). */
    val bridgeUpdatePrompt: StateFlow<CodexBridgeUpdatePrompt?>

    /**
     * Relay `x-role`: `mac` (bridge) or `iphone` (mobile client). When [role] is null, Android sends
     * `iphone` for compatibility with hosted relay deployments that have not rolled out the Android alias.
     * Non-empty [token] is also sent as `Authorization: Bearer`.
     */
    suspend fun connect(
        serverUrl: String,
        token: String,
        role: String? = null,
    )

    suspend fun disconnect()

    suspend fun setActiveThreadId(threadId: String?)

    suspend fun refreshModels()

    /** Fetches rate limit buckets via `account/rateLimits/read` (with param-shape retry). */
    suspend fun refreshRateLimits()

    /** Fetches context window usage via `thread/contextWindow/read` for [threadId]. */
    suspend fun refreshContextWindowUsage(threadId: String)

    /** Refreshes the combined usage status for the current thread: context window + account rate limits. */
    suspend fun refreshUsageStatus(threadId: String?) {
        val tid = threadId?.trim().orEmpty()
        if (tid.isNotEmpty()) {
            refreshContextWindowUsage(tid)
        }
        refreshRateLimits()
    }

    /** Mirrors iOS `shouldAutoRefreshUsageStatus(threadId:)`. */
    fun shouldAutoRefreshUsageStatus(threadId: String?): Boolean =
        UsageStatusRefreshPolicy.shouldAutoRefresh(
            sessionReady = isSessionReady.value,
            connected = connectionState.value is ConnectionState.Connected,
            threadId = threadId,
            contextWindowUsageByThread = contextWindowUsageByThread.value,
            hasResolvedRateLimitsSnapshot = hasResolvedRateLimitsSnapshot.value,
        )

    suspend fun setSelectedModelId(modelId: String?)

    suspend fun setSelectedReasoningEffort(reasoningEffort: String?)

    suspend fun setSelectedAccessMode(accessMode: CodexAccessMode)

    suspend fun setSelectedServiceTier(serviceTier: CodexServiceTier?)

    suspend fun resolvePendingApproval(
        requestId: String,
        decision: PendingApprovalDecision,
    )

    suspend fun resolvePendingStructuredInput(
        requestId: String,
        answersByQuestionId: Map<String, List<String>>,
    )

    /** Clears [bridgeUpdatePrompt] after the user dismisses the sheet. */
    fun dismissBridgeUpdatePrompt()

    /** Best-effort `thread/list` refresh (active + archived); no-op when disconnected. */
    suspend fun refreshThreads()

    /** Loads [thread/read] (includeTurns) and merges into [messagesByThread]. */
    suspend fun syncThreadHistory(
        threadId: String,
        force: Boolean = false,
    )

    /** Loads the next older [thread/read] page when the bridge/runtime exposes a cursor. */
    suspend fun loadOlderThreadHistory(threadId: String) {
        syncThreadHistory(threadId, force = true)
    }

    suspend fun sendRequest(
        method: String,
        params: JSONValue?,
    ): RPCMessage

    suspend fun renameThread(
        threadId: String,
        name: String,
    ): Unit = throw UnsupportedOperationException("renameThread is not implemented by this repository")

    /** Removes a chat from Android's local list without mutating the paired desktop runtime. */
    suspend fun deleteThreadLocally(threadId: String): Unit =
        throw UnsupportedOperationException("deleteThreadLocally is not implemented by this repository")

    /** Archives every root thread in a sidebar project group so the folder disappears from the live list. */
    suspend fun archiveThreadGroup(threadIds: List<String>): List<String> =
        throw UnsupportedOperationException("archiveThreadGroup is not implemented by this repository")

    /** Removes every thread in a sidebar group without issuing per-thread RPC mutations. */
    suspend fun deleteLocalThreadGroup(threadIds: List<String>): List<String> =
        throw UnsupportedOperationException("deleteLocalThreadGroup is not implemented by this repository")

    /**
     * WAV clip → transcript: `voice/resolveAuth` on the bridge, then ChatGPT `/backend-api/transcribe` (J.7e).
     * No microphone UI; callers supply bytes + duration for preflight.
     */
    suspend fun transcribeBridgeVoiceWav(
        wavBytes: ByteArray,
        durationSeconds: Double,
    ): String

    /**
     * Creates a thread via `thread/start` (parity with iOS `startThreadImpl`).
     * Updates [threads] and [activeThreadId] on success.
     */
    suspend fun startThread(
        model: String? = null,
        cwd: String? = null,
        serviceTier: String? = null,
    ): CodexThread

    fun requestBranchPickerForThread(threadId: String) {}

    fun consumeBranchPickerRequest(threadId: String) {}

    /**
     * Same-thread rebind: updates local [threads] + forces [thread/resume] with the preferred cwd
     * while [authoritative] guards prevent stale [thread/list]/[thread/read] from snapping cwd back
     * until the runtime matches. Parity: iOS [CodexService.moveThreadToProjectPath].
     */
    suspend fun moveThreadToProjectPath(
        threadId: String,
        projectPath: String,
    ): CodexThread

    /**
     * Forks an existing thread via `thread/fork` and opens the fork as active.
     * [targetProjectPath] lets callers preserve/project-bind local cwd metadata.
     */
    suspend fun forkThread(
        sourceThreadId: String,
        targetProjectPath: String? = null,
    ): CodexThread = throw UnsupportedOperationException("thread/fork is not implemented by this repository")

    /** In-flight rebind: preferred cwd, or null if none (parity [currentAuthoritativeProjectPath]). */
    fun currentAuthoritativeProjectPathFor(threadId: String): String?

    /** In-memory only until J.7c persistence (parity [associatedManagedWorktreePath] on iOS). */
    fun associatedManagedWorktreePathFor(threadId: String): String?

    /** Invia messaggio utente con `turn/start` (input item `type: text`). */
    suspend fun startTurn(
        threadId: String,
        text: String,
        attachments: List<CodexImageAttachment> = emptyList(),
        skillMentions: List<CodexTurnSkillMention> = emptyList(),
        fileMentions: List<CodexTurnMention> = emptyList(),
        collaborationMode: CodexCollaborationModeKind? = null,
    )

    /** Starts a native `review/start` turn. Attachments and plan mode are intentionally not supported. */
    suspend fun startReview(
        threadId: String,
        target: CodexReviewTarget,
        baseBranch: String? = null,
    ): Unit = throw UnsupportedOperationException("review/start is not implemented by this repository")

    /** Queue a local draft when a thread is already running (J22 foundation). */
    suspend fun enqueueTurnDraft(
        threadId: String,
        text: String,
        attachments: List<CodexImageAttachment> = emptyList(),
        skillMentions: List<CodexTurnSkillMention> = emptyList(),
        fileMentions: List<CodexTurnMention> = emptyList(),
        collaborationMode: CodexCollaborationModeKind? = null,
        prepend: Boolean = false,
    ) = Unit

    /** Pops the next queued local draft for [threadId], or null if empty. */
    suspend fun pollTurnDraft(threadId: String): QueuedTurnDraft? = null
    suspend fun removeQueuedTurnDraft(
        threadId: String,
        draftId: String,
    ): QueuedTurnDraft? = null

    /** Interrompe il turno attivo (`turn/interrupt`). [turnId] opzionale se già noto. */
    suspend fun interruptTurn(
        threadId: String,
        turnId: String? = null,
    )

    suspend fun sendNotification(
        method: String,
        params: JSONValue?,
    )
}
