package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexAccessMode
import com.remodex.mobile.core.model.CodexBridgeUpdatePrompt
import com.remodex.mobile.core.model.CodexCollaborationModeKind
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexModelOption
import com.remodex.mobile.core.model.CodexRateLimitBucket
import com.remodex.mobile.core.model.CodexReviewTarget
import com.remodex.mobile.core.model.CodexServiceTier
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.CodexTurnMention
import com.remodex.mobile.core.model.CodexTurnSkillMention
import com.remodex.mobile.core.model.CommandExecutionDetails
import com.remodex.mobile.core.model.ContextWindowUsage
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.PendingApprovalDecision
import com.remodex.mobile.core.model.PendingApprovalRequest
import com.remodex.mobile.core.model.PendingStructuredInputRequest
import com.remodex.mobile.core.model.RPCMessage
import com.remodex.mobile.core.model.ThreadHistoryPaginationState
import com.remodex.mobile.core.transport.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadListSyncPaginationTest {
    @Test
    fun fetchMerged_walksActiveAndArchivedPages() = runTest {
        val requested = mutableListOf<Pair<Boolean, JSONValue?>>()
        val repo =
            object : TestRepository() {
                override suspend fun sendRequest(method: String, params: JSONValue?): RPCMessage {
                    assertEquals("thread/list", method)
                    val map = (params as JSONValue.Obj).map
                    val archived = map["archived"]?.boolValue == true
                    val cursor = map["cursor"]
                    requested += archived to cursor
                    val page =
                        when (archived to cursor?.stringValue) {
                            false to null ->
                                page("active-1", next = "active-next")
                            false to "active-next" ->
                                page("active-2", next = null)
                            true to null ->
                                page("archived-1", next = "archived-next")
                            true to "archived-next" ->
                                page("archived-2", next = null)
                            else -> error("unexpected page request: archived=$archived cursor=$cursor")
                        }
                    return RPCMessage.success(id = JSONValue.NumLong(requested.size.toLong()), result = page)
                }
            }

        val result = ThreadListSync.fetchMerged(repo)

        assertEquals(listOf("active-2", "archived-2", "active-1", "archived-1"), result.map { it.id })
        val expectedRequests: List<Pair<Boolean, JSONValue?>> =
            listOf(
                false to JSONValue.Null,
                false to JSONValue.Str("active-next"),
                true to JSONValue.Null,
                true to JSONValue.Str("archived-next"),
            )
        assertEquals(
            expectedRequests,
            requested,
        )
    }

    private fun page(
        id: String,
        next: String?,
    ): JSONValue.Obj =
        JSONValue.Obj(
            mapOf(
                "data" to
                    JSONValue.Arr(
                        listOf(
                            JSONValue.Obj(
                                mapOf(
                                    "id" to JSONValue.Str(id),
                                    "title" to JSONValue.Str(id),
                                    "updatedAt" to JSONValue.Str("2026-05-04T12:00:0${id.last()}.000Z"),
                                ),
                            ),
                        ),
                    ),
                "nextCursor" to (next?.let { JSONValue.Str(it) } ?: JSONValue.Null),
            ),
        )
}

private abstract class TestRepository : CodexRepository {
    override val isSessionReady: StateFlow<Boolean> = MutableStateFlow(true)
    override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected)
    override val threads: StateFlow<List<CodexThread>> = MutableStateFlow(emptyList())
    override val activeThreadId: StateFlow<String?> = MutableStateFlow(null)
    override val messagesByThread: StateFlow<Map<String, List<CodexMessage>>> = MutableStateFlow(emptyMap())
    override val threadHistoryPaginationByThread: StateFlow<Map<String, ThreadHistoryPaginationState>> =
        MutableStateFlow(emptyMap())
    override val loadingOlderHistoryThreadIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override val olderHistoryErrorByThread: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    override val commandExecutionDetailsByItemId: StateFlow<Map<String, CommandExecutionDetails>> =
        MutableStateFlow(emptyMap())
    override val turnDraftQueueDepthByThread: StateFlow<Map<String, Int>> = MutableStateFlow(emptyMap())
    override val turnDraftQueuePreviewByThread: StateFlow<Map<String, List<QueuedTurnDraftPreview>>> =
        MutableStateFlow(emptyMap())
    override val pendingBranchPickerThreadId: StateFlow<String?> = MutableStateFlow(null)
    override val runningTurnIdByThread: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    override val protectedRunningFallbackThreadIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override val availableModels: StateFlow<List<CodexModelOption>> = MutableStateFlow(emptyList())
    override val isLoadingModels: StateFlow<Boolean> = MutableStateFlow(false)
    override val modelsErrorMessage: StateFlow<String?> = MutableStateFlow(null)
    override val rateLimitBuckets: StateFlow<List<CodexRateLimitBucket>> = MutableStateFlow(emptyList())
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
    override suspend fun setActiveThreadId(threadId: String?) = error("unused")
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
    override suspend fun transcribeBridgeVoiceWav(wavBytes: ByteArray, durationSeconds: Double): String = error("unused")
    override suspend fun startThread(model: String?, cwd: String?, serviceTier: String?): CodexThread = error("unused")
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
    override suspend fun startReview(threadId: String, target: CodexReviewTarget, baseBranch: String?) = error("unused")
}
