package com.remodex.mobile.ui.sidebar

import com.remodex.mobile.core.model.CodexAccessMode
import com.remodex.mobile.core.model.CodexBridgeUpdatePrompt
import com.remodex.mobile.core.model.CodexCollaborationModeKind
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexModelOption
import com.remodex.mobile.core.model.CodexServiceTier
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.CodexTurnMention
import com.remodex.mobile.core.model.CodexTurnSkillMention
import com.remodex.mobile.core.model.ContextWindowUsage
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.PendingApprovalDecision
import com.remodex.mobile.core.model.PendingApprovalRequest
import com.remodex.mobile.core.model.PendingStructuredInputRequest
import com.remodex.mobile.core.model.RPCMessage
import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.data.CodexRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest

class SidebarNewChatStartContractTest {
    @Test
    fun startSidebarNewChat_startsSelectsAndWaitsForActiveThreadRow() =
        runTest {
            val repo = ContractRepository()

            val started =
                startSidebarNewChat(
                    repository = repo,
                    cwd = " /repo ",
                    openTimeoutMillis = 1_000,
                )

            assertEquals("thread-1", started.id)
            assertEquals("/repo", started.cwd)
            assertEquals("thread-1", repo.activeThreadId.value)
            assertEquals(listOf("start:/repo", "active:thread-1"), repo.events)
        }
}

private class ContractRepository : CodexRepository {
    val events = mutableListOf<String>()

    private val mutableThreads = MutableStateFlow<List<CodexThread>>(emptyList())
    private val mutableActiveThreadId = MutableStateFlow<String?>(null)

    override val isSessionReady: StateFlow<Boolean> = MutableStateFlow(true)
    override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected)
    override val threads: StateFlow<List<CodexThread>> = mutableThreads
    override val activeThreadId: StateFlow<String?> = mutableActiveThreadId
    override val messagesByThread: StateFlow<Map<String, List<CodexMessage>>> = MutableStateFlow(emptyMap())
    override val threadHistoryPaginationByThread: StateFlow<Map<String, com.remodex.mobile.core.model.ThreadHistoryPaginationState>> =
        MutableStateFlow(emptyMap())
    override val loadingOlderHistoryThreadIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override val olderHistoryErrorByThread: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    override val commandExecutionDetailsByItemId: StateFlow<Map<String, com.remodex.mobile.core.model.CommandExecutionDetails>> =
        MutableStateFlow(emptyMap())
    override val turnDraftQueueDepthByThread: StateFlow<Map<String, Int>> = MutableStateFlow(emptyMap())
    override val turnDraftQueuePreviewByThread: StateFlow<Map<String, List<com.remodex.mobile.data.QueuedTurnDraftPreview>>> =
        MutableStateFlow(emptyMap())
    override val pendingBranchPickerThreadId: StateFlow<String?> = MutableStateFlow(null)
    override val runningTurnIdByThread: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    override val protectedRunningFallbackThreadIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override val availableModels: StateFlow<List<CodexModelOption>> = MutableStateFlow(emptyList())
    override val isLoadingModels: StateFlow<Boolean> = MutableStateFlow(false)
    override val modelsErrorMessage: StateFlow<String?> = MutableStateFlow(null)
    override val rateLimitBuckets: StateFlow<List<com.remodex.mobile.core.model.CodexRateLimitBucket>> =
        MutableStateFlow(emptyList())
    override val isLoadingRateLimits: StateFlow<Boolean> = MutableStateFlow(false)
    override val rateLimitsErrorMessage: StateFlow<String?> = MutableStateFlow(null)
    override val hasResolvedRateLimitsSnapshot: StateFlow<Boolean> = MutableStateFlow(false)
    override val contextWindowUsageByThread: StateFlow<Map<String, ContextWindowUsage>> = MutableStateFlow(emptyMap())
    override val contextWindowUsageLoadingThreads: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override val contextWindowUsageErrorByThread: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    override val selectedModelId: StateFlow<String?> = MutableStateFlow(null)
    override val selectedReasoningEffort: StateFlow<String?> = MutableStateFlow(null)
    override val selectedAccessMode: StateFlow<CodexAccessMode> = MutableStateFlow(CodexAccessMode.onRequest)
    override val selectedServiceTier: StateFlow<CodexServiceTier?> = MutableStateFlow(null)
    override val pendingApprovalRequest: StateFlow<PendingApprovalRequest?> = MutableStateFlow(null)
    override val pendingStructuredInputRequest: StateFlow<PendingStructuredInputRequest?> = MutableStateFlow(null)
    override val bridgeUpdatePrompt: StateFlow<CodexBridgeUpdatePrompt?> = MutableStateFlow(null)

    override suspend fun connect(serverUrl: String, token: String, role: String?) = error("unused")
    override suspend fun disconnect() = error("unused")
    override suspend fun setActiveThreadId(threadId: String?) {
        events += "active:$threadId"
        mutableActiveThreadId.value = threadId
    }
    override suspend fun refreshModels() = error("unused")
    override suspend fun refreshRateLimits() = error("unused")
    override suspend fun refreshContextWindowUsage(threadId: String) = error("unused")
    override suspend fun setSelectedModelId(modelId: String?) = error("unused")
    override suspend fun setSelectedReasoningEffort(reasoningEffort: String?) = error("unused")
    override suspend fun setSelectedAccessMode(accessMode: CodexAccessMode) = error("unused")
    override suspend fun setSelectedServiceTier(serviceTier: CodexServiceTier?) = error("unused")
    override suspend fun resolvePendingApproval(requestId: String, decision: PendingApprovalDecision) = error("unused")
    override suspend fun resolvePendingStructuredInput(requestId: String, answersByQuestionId: Map<String, List<String>>) =
        error("unused")
    override fun dismissBridgeUpdatePrompt() = Unit
    override suspend fun refreshThreads() = error("unused")
    override suspend fun syncThreadHistory(threadId: String, force: Boolean) = error("unused")
    override suspend fun loadOlderThreadHistory(threadId: String) = error("unused")
    override suspend fun sendRequest(method: String, params: JSONValue?): RPCMessage = error("unused")
    override suspend fun transcribeBridgeVoiceWav(wavBytes: ByteArray, durationSeconds: Double): String = error("unused")
    override suspend fun startThread(model: String?, cwd: String?, serviceTier: String?): CodexThread {
        events += "start:$cwd"
        val thread = CodexThread(id = "thread-1", cwd = cwd)
        mutableThreads.value = listOf(thread)
        return thread
    }
    override suspend fun moveThreadToProjectPath(threadId: String, projectPath: String): CodexThread = error("unused")
    override fun currentAuthoritativeProjectPathFor(threadId: String): String? = null
    override fun associatedManagedWorktreePathFor(threadId: String): String? = null
    override suspend fun startTurn(
        threadId: String,
        text: String,
        attachments: List<CodexImageAttachment>,
        skillMentions: List<CodexTurnSkillMention>,
        fileMentions: List<CodexTurnMention>,
        collaborationMode: CodexCollaborationModeKind?,
    ) = error("unused")
    override suspend fun interruptTurn(threadId: String, turnId: String?) = error("unused")
    override suspend fun sendNotification(method: String, params: JSONValue?) = error("unused")
}
