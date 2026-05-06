package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexAccessMode
import com.remodex.mobile.core.model.CodexCollaborationModeKind
import com.remodex.mobile.core.model.ContextWindowUsage
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexModelOption
import com.remodex.mobile.core.model.CodexBridgeUpdatePrompt
import com.remodex.mobile.core.model.CodexServiceTier
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.PendingApprovalDecision
import com.remodex.mobile.core.model.PendingApprovalRequest
import com.remodex.mobile.core.model.PendingStructuredInputRequest
import com.remodex.mobile.core.model.RPCError
import com.remodex.mobile.core.model.RPCMessage
import com.remodex.mobile.core.persistence.RelaySessionSnapshot
import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.data.CodexRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest

class DesktopHandoffServiceTest {
    @Test
    fun fallsBackToLegacyMacMethodWhenDesktopMethodIsUnknown() =
        runTest {
            val methods = mutableListOf<String>()
            val repository =
                HandoffFakeRepository { method, _ ->
                    methods += method
                    when (method) {
                        "desktop/continueOnDesktop" ->
                            throw CodexServiceError.RpcFailure(
                                RPCError(
                                    code = -32601,
                                    message = "Unknown desktop method: desktop/continueOnDesktop",
                                    data = null,
                                ),
                            )

                        "desktop/continueOnMac" ->
                            RPCMessage.success(
                                id = null,
                                result = JSONValue.Obj(mapOf("success" to JSONValue.Bool(true))),
                            )

                        else -> error("Unexpected method: $method")
                    }
                }

            DesktopHandoffService(repository).continueOnDesktop("thread-123")

            assertEquals(
                listOf("desktop/continueOnDesktop", "desktop/continueOnMac"),
                methods,
            )
        }

    @Test
    fun mapsUnsupportedPlatformBridgeErrorToDesktopMessage() =
        runTest {
            val repository =
                HandoffFakeRepository { _, _ ->
                    throw CodexServiceError.RpcFailure(
                        RPCError(
                            code = -32000,
                            message = "Desktop handoff is unavailable here.",
                            data =
                                JSONValue.Obj(
                                    mapOf(
                                        "errorCode" to JSONValue.Str("unsupported_platform"),
                                    ),
                                ),
                        ),
                    )
                }

            val error =
                assertFailsWith<DesktopHandoffError.BridgeFailure> {
                    DesktopHandoffService(repository).continueOnDesktop("thread-123")
                }

            assertEquals("unsupported_platform", error.errorCode)
            assertEquals(
                "Desktop handoff works only when the bridge is running on macOS or Windows.",
                error.message,
            )
        }

    @Test
    fun wakeDisplayUsesCurrentBridgeConnectionWhenAvailable() =
        runTest {
            val methods = mutableListOf<String>()
            val repository =
                HandoffFakeRepository { method, params ->
                    methods += method
                    assertEquals(emptyMap(), params?.objectValue)
                    RPCMessage.success(
                        id = null,
                        result = JSONValue.Obj(mapOf("success" to JSONValue.Bool(true))),
                    )
                }

            DesktopHandoffService(repository).wakeDisplay()

            assertEquals(listOf("desktop/wakeDisplay"), methods)
            assertEquals(null, repository.lastConnectUrl)
        }

    @Test
    fun wakeDisplayUsesSavedPairingWhenDisconnected() =
        runTest {
            val methods = mutableListOf<String>()
            val repository =
                HandoffFakeRepository(sessionReady = false) { method, _ ->
                    methods += method
                    RPCMessage.success(
                        id = null,
                        result = JSONValue.Obj(mapOf("success" to JSONValue.Bool(true))),
                    )
                }

            DesktopHandoffService(
                repository = repository,
                savedRelaySnapshotProvider = {
                    RelaySessionSnapshot(
                        relayUrl = "wss://relay.example/ws",
                        relaySessionId = "session-123",
                    )
                },
            ).wakeDisplay()

            assertEquals("wss://relay.example/ws/session-123", repository.lastConnectUrl)
            assertEquals("session-123", repository.lastConnectToken)
            assertEquals(listOf("desktop/wakeDisplay"), methods)
        }

    @Test
    fun wakeDisplayRequiresSavedPairingWhenDisconnected() =
        runTest {
            val repository =
                HandoffFakeRepository(sessionReady = false) { _, _ ->
                    error("wake RPC should not be called without saved pairing")
                }

            val error =
                assertFailsWith<DesktopHandoffError.BridgeFailure> {
                    DesktopHandoffService(repository).wakeDisplay()
                }

            assertEquals("saved_pair_required", error.errorCode)
        }
}

private class HandoffFakeRepository(
    sessionReady: Boolean = true,
    private val requestHandler: suspend (String, JSONValue?) -> RPCMessage,
) : CodexRepository {
    private val sessionReadyFlow = MutableStateFlow(sessionReady)
    var lastConnectUrl: String? = null
    var lastConnectToken: String? = null

    override val isSessionReady: StateFlow<Boolean> = sessionReadyFlow
    override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected)
    override val threads: StateFlow<List<CodexThread>> = MutableStateFlow(emptyList())
    override val activeThreadId: StateFlow<String?> = MutableStateFlow(null)
    override val messagesByThread: StateFlow<Map<String, List<CodexMessage>>> = MutableStateFlow(emptyMap())
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
    override val contextWindowUsageByThread: StateFlow<Map<String, ContextWindowUsage>> =
        MutableStateFlow(emptyMap())
    override val contextWindowUsageLoadingThreads: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override val contextWindowUsageErrorByThread: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    override val selectedModelId: StateFlow<String?> = MutableStateFlow(null)
    override val selectedReasoningEffort: StateFlow<String?> = MutableStateFlow(null)
    override val selectedAccessMode: StateFlow<CodexAccessMode> = MutableStateFlow(CodexAccessMode.onRequest)
    override val selectedServiceTier: StateFlow<CodexServiceTier?> = MutableStateFlow(null)
    override val pendingApprovalRequest: StateFlow<PendingApprovalRequest?> = MutableStateFlow(null)
    override val pendingStructuredInputRequest: StateFlow<PendingStructuredInputRequest?> = MutableStateFlow(null)
    override val bridgeUpdatePrompt: StateFlow<CodexBridgeUpdatePrompt?> = MutableStateFlow(null)

    override suspend fun connect(
        serverUrl: String,
        token: String,
        role: String?,
    ) {
        lastConnectUrl = serverUrl
        lastConnectToken = token
        sessionReadyFlow.value = true
    }

    override suspend fun disconnect() = error("unused")

    override suspend fun setActiveThreadId(threadId: String?) = error("unused")

    override suspend fun refreshModels() = error("unused")

    override suspend fun refreshRateLimits() = error("unused")

    override suspend fun refreshContextWindowUsage(threadId: String) = error("unused")

    override suspend fun setSelectedModelId(modelId: String?) = error("unused")

    override suspend fun setSelectedReasoningEffort(reasoningEffort: String?) = error("unused")

    override suspend fun setSelectedAccessMode(accessMode: CodexAccessMode) = error("unused")

    override suspend fun setSelectedServiceTier(serviceTier: CodexServiceTier?) = error("unused")

    override suspend fun resolvePendingApproval(
        requestId: String,
        decision: PendingApprovalDecision,
    ) = error("unused")

    override suspend fun resolvePendingStructuredInput(
        requestId: String,
        answersByQuestionId: Map<String, List<String>>,
    ) = error("unused")

    override fun dismissBridgeUpdatePrompt() {}

    override suspend fun refreshThreads() = error("unused")

    override suspend fun syncThreadHistory(
        threadId: String,
        force: Boolean,
    ) = error("unused")

    override suspend fun sendRequest(
        method: String,
        params: JSONValue?,
    ): RPCMessage = requestHandler(method, params)

    override suspend fun transcribeBridgeVoiceWav(
        wavBytes: ByteArray,
        durationSeconds: Double,
    ): String = error("unused")

    override suspend fun startThread(
        model: String?,
        cwd: String?,
        serviceTier: String?,
    ): CodexThread = error("unused")

    override suspend fun moveThreadToProjectPath(
        threadId: String,
        projectPath: String,
    ): CodexThread = error("unused")

    override fun currentAuthoritativeProjectPathFor(threadId: String): String? = null

    override fun associatedManagedWorktreePathFor(threadId: String): String? = null

    override suspend fun startTurn(
        threadId: String,
        text: String,
        attachments: List<CodexImageAttachment>,
        skillMentions: List<com.remodex.mobile.core.model.CodexTurnSkillMention>,
        fileMentions: List<com.remodex.mobile.core.model.CodexTurnMention>,
        collaborationMode: CodexCollaborationModeKind?,
    ) = error("unused")

    override suspend fun interruptTurn(
        threadId: String,
        turnId: String?,
    ) = error("unused")

    override suspend fun sendNotification(
        method: String,
        params: JSONValue?,
    ) = error("unused")
}
