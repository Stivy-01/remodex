package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexAccessMode
import com.remodex.mobile.core.model.ContextWindowUsage
import com.remodex.mobile.core.model.CodexCollaborationModeKind
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexModelOption
import com.remodex.mobile.core.model.CodexBridgeUpdatePrompt
import com.remodex.mobile.core.model.CodexServiceTier
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.GitWorktreeChangeTransferMode
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.PendingApprovalDecision
import com.remodex.mobile.core.model.PendingApprovalRequest
import com.remodex.mobile.core.model.PendingStructuredInputRequest
import com.remodex.mobile.core.model.RPCError
import com.remodex.mobile.core.model.RPCMessage
import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.data.CodexRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest

class GitActionsServiceTest {
    @Test
    fun branchesWithStatus_sendsCwdInParams() =
        runTest {
            var captured: Pair<String, JSONValue?>? = null
            val repo =
                GitActionsFakeRepository { method, params ->
                    captured = method to params
                    RPCMessage.success(
                        id = null,
                        result =
                            JSONValue.Obj(
                                mapOf(
                                    "branches" to JSONValue.Arr(listOf(JSONValue.Str("a"))),
                                    "branchesCheckedOutElsewhere" to JSONValue.Arr(emptyList()),
                                    "worktreePathByBranch" to JSONValue.Obj(emptyMap()),
                                    "localCheckoutPath" to JSONValue.Str("/p"),
                                    "current" to JSONValue.Str("a"),
                                    "default" to JSONValue.Str("main"),
                                ),
                            ),
                    )
                }
            GitActionsService(repo, "  /project/foo  ").branchesWithStatus()
            val params = captured!!.second as JSONValue.Obj
            val m = params.map
            assertEquals("git/branchesWithStatus", captured!!.first)
            assertEquals("/project/foo", m["cwd"]?.stringValue)
        }

    @Test
    fun checkout_sendsBranchAndCwd() =
        runTest {
            var captured: Pair<String, JSONValue?>? = null
            val repo =
                GitActionsFakeRepository { method, params ->
                    captured = method to params
                    RPCMessage.success(
                        id = null,
                        result =
                            JSONValue.Obj(
                                mapOf(
                                    "current" to JSONValue.Str("topic"),
                                    "tracking" to JSONValue.Str("origin/topic"),
                                ),
                            ),
                    )
                }
            val r = GitActionsService(repo, "/repo").checkout("topic")
            val params = captured!!.second as JSONValue.Obj
            val m = params.map
            assertEquals("git/checkout", captured!!.first)
            assertEquals("/repo", m["cwd"]?.stringValue)
            assertEquals("topic", m["branch"]?.stringValue)
            assertEquals("topic", r.currentBranch)
        }

    @Test
    fun transferManagedHandoff_sendsTargetPathAndCwd() =
        runTest {
            var captured: Pair<String, JSONValue?>? = null
            val repo =
                GitActionsFakeRepository { method, params ->
                    captured = method to params
                    RPCMessage.success(
                        id = null,
                        result =
                            JSONValue.Obj(
                                mapOf(
                                    "success" to JSONValue.Bool(true),
                                    "targetPath" to JSONValue.Str("/dest"),
                                    "transferredChanges" to JSONValue.Bool(true),
                                ),
                            ),
                    )
                }
            val r = GitActionsService(repo, "/src").transferManagedHandoff("/dest")
            val m = (captured!!.second as JSONValue.Obj).map
            assertEquals("git/transferManagedHandoff", captured!!.first)
            assertEquals("/src", m["cwd"]?.stringValue)
            assertEquals("/dest", m["targetPath"]?.stringValue)
            assertTrue(r.success)
            assertEquals("/dest", r.targetPath)
            assertTrue(r.transferredChanges)
        }

    @Test
    fun removeManagedWorktree_omitsBranchWhenNullOrBlank() =
        runTest {
            var map: Map<String, JSONValue>? = null
            val repo =
                GitActionsFakeRepository { _, params ->
                    map = (params as JSONValue.Obj).map
                    RPCMessage.success(
                        id = null,
                        result = JSONValue.Obj(mapOf("success" to JSONValue.Bool(true))),
                    )
                }
            GitActionsService(repo, "/wt").removeManagedWorktree(null)
            assertEquals(setOf("cwd"), map!!.keys)
            assertEquals("/wt", map!!["cwd"]?.stringValue)

            GitActionsService(repo, "/wt2").removeManagedWorktree("   ")
            assertEquals(setOf("cwd"), map!!.keys)
            assertEquals("/wt2", map!!["cwd"]?.stringValue)
        }

    @Test
    fun removeManagedWorktree_sendsTrimmedBranch() =
        runTest {
            var map: Map<String, JSONValue>? = null
            val repo =
                GitActionsFakeRepository { method, params ->
                    map = (params as JSONValue.Obj).map
                    assertEquals("git/removeWorktree", method)
                    RPCMessage.success(
                        id = null,
                        result = JSONValue.Obj(mapOf("success" to JSONValue.Bool(true))),
                    )
                }
            GitActionsService(repo, "/wt").removeManagedWorktree("  topic  ")
            assertEquals("/wt", map!!["cwd"]?.stringValue)
            assertEquals("topic", map!!["branch"]?.stringValue)
        }

    @Test
    fun createManagedWorktree_sendsChangeTransferNone() =
        runTest {
            var map: Map<String, JSONValue>? = null
            val repo =
                GitActionsFakeRepository { _, params ->
                    map = (params as JSONValue.Obj).map
                    RPCMessage.success(
                        id = null,
                        result =
                            JSONValue.Obj(
                                mapOf(
                                    "worktreePath" to JSONValue.Str("/w"),
                                    "alreadyExisted" to JSONValue.Bool(false),
                                    "baseBranch" to JSONValue.Str("main"),
                                    "headMode" to JSONValue.Str("branch"),
                                    "transferredChanges" to JSONValue.Bool(false),
                                ),
                            ),
                    )
                }
            val r =
                GitActionsService(repo, "/p").createManagedWorktree(
                    "main",
                    GitWorktreeChangeTransferMode.none,
                )
            assertEquals("none", map!!["changeTransfer"]?.stringValue)
            assertEquals("main", map!!["baseBranch"]?.stringValue)
            assertEquals("/p", map!!["cwd"]?.stringValue)
            assertFalse(r.transferredChanges)
        }

    @Test
    fun generateCommitMessage_sendsOptionalModelAndCwd() =
        runTest {
            var captured: Pair<String, JSONValue?>? = null
            val repo =
                GitActionsFakeRepository { method, params ->
                    captured = method to params
                    RPCMessage.success(
                        id = null,
                        result =
                            JSONValue.Obj(
                                mapOf(
                                    "subject" to JSONValue.Str("Update git flow"),
                                    "body" to JSONValue.Str("- Add commit draft UI"),
                                    "fullMessage" to JSONValue.Str("Update git flow\n\n- Add commit draft UI"),
                                ),
                            ),
                    )
                }

            val result = GitActionsService(repo, "/repo").generateCommitMessage("gpt-5.4-mini")

            val map = (captured!!.second as JSONValue.Obj).map
            assertEquals("git/generateCommitMessage", captured!!.first)
            assertEquals("/repo", map["cwd"]?.stringValue)
            assertEquals("gpt-5.4-mini", map["model"]?.stringValue)
            assertEquals("Update git flow\n\n- Add commit draft UI", result.fullMessage)
        }

    @Test
    fun initializeRepository_callsGitInitWithCwd() =
        runTest {
            var captured: Pair<String, JSONValue?>? = null
            val repo =
                GitActionsFakeRepository { method, params ->
                    captured = method to params
                    RPCMessage.success(
                        id = null,
                        result =
                            JSONValue.Obj(
                                mapOf(
                                    "repoRoot" to JSONValue.Str("/repo"),
                                    "status" to
                                        JSONValue.Obj(
                                            mapOf(
                                                "repoRoot" to JSONValue.Str("/repo"),
                                                "branch" to JSONValue.Str("main"),
                                                "dirty" to JSONValue.Bool(false),
                                            ),
                                        ),
                                ),
                            ),
                    )
                }

            val result = GitActionsService(repo, "/repo").initializeRepository()

            val map = (captured!!.second as JSONValue.Obj).map
            assertEquals("git/init", captured!!.first)
            assertEquals("/repo", map["cwd"]?.stringValue)
            assertEquals("/repo", result.repoRoot)
            assertEquals("main", result.status?.currentBranch)
        }

    @Test
    fun generatePullRequestDraft_sendsBaseBranchModelAndCwd() =
        runTest {
            var captured: Pair<String, JSONValue?>? = null
            val repo =
                GitActionsFakeRepository { method, params ->
                    captured = method to params
                    RPCMessage.success(
                        id = null,
                        result =
                            JSONValue.Obj(
                                mapOf(
                                    "title" to JSONValue.Str("Improve git flow"),
                                    "body" to JSONValue.Str("## Summary\n- Add PR UI\n\n## Testing\n- Unit tests\n\n## Notes\n- None"),
                                ),
                            ),
                    )
                }

            val result = GitActionsService(repo, "/repo").generatePullRequestDraft("main", "gpt-5.4-mini")

            val map = (captured!!.second as JSONValue.Obj).map
            assertEquals("git/generatePullRequestDraft", captured!!.first)
            assertEquals("/repo", map["cwd"]?.stringValue)
            assertEquals("main", map["baseBranch"]?.stringValue)
            assertEquals("gpt-5.4-mini", map["model"]?.stringValue)
            assertEquals("Improve git flow", result.title)
        }

    @Test
    fun missingWorkingDirectory_throwsBeforeSend() =
        runTest {
            val repo =
                GitActionsFakeRepository { _, _ ->
                    error("should not be called")
                }
            assertFailsWith<GitActionsError.MissingWorkingDirectory> {
                GitActionsService(repo, null).status()
            }
        }

    @Test
    fun mapsDisconnectedToGitActionsError() =
        runTest {
            val repo =
                GitActionsFakeRepository { _, _ ->
                    throw CodexServiceError.Disconnected
                }
            val err = assertFailsWith<GitActionsError> { GitActionsService(repo, "/x").status() }
            assertTrue(err is GitActionsError.Disconnected)
        }

    @Test
    fun mapsRpcFailureToBridgeFailure() =
        runTest {
            val repo =
                GitActionsFakeRepository { _, _ ->
                    throw CodexServiceError.RpcFailure(
                        RPCError(
                            code = -32000,
                            message = "failed",
                            data =
                                JSONValue.Obj(
                                    mapOf("errorCode" to JSONValue.Str("branch_not_found")),
                                ),
                        ),
                    )
                }
            val err = assertFailsWith<GitActionsError.BridgeFailure> { GitActionsService(repo, "/x").status() }
            assertEquals("branch_not_found", err.errorCode)
        }

    @Test
    fun mapsHandoffRpcCodesToDistinctFallbackMessages() =
        runTest {
            suspend fun mappedMessage(code: String): String {
                val repo =
                    GitActionsFakeRepository { _, _ ->
                        throw CodexServiceError.RpcFailure(
                            RPCError(
                                code = -32000,
                                message = "",
                                data = JSONValue.Obj(mapOf("errorCode" to JSONValue.Str(code))),
                            ),
                        )
                    }
                return assertFailsWith<GitActionsError.BridgeFailure> {
                    GitActionsService(repo, "/x").status()
                }.message.orEmpty()
            }

            assertEquals(
                "The handoff destination already has uncommitted changes. Clean it up before moving this thread there.",
                mappedMessage("handoff_target_dirty"),
            )
            assertEquals(
                "The selected handoff destination belongs to a different checkout.",
                mappedMessage("handoff_target_mismatch"),
            )
            assertEquals(
                "The current handoff source is not available on this Mac.",
                mappedMessage("missing_handoff_source"),
            )
            assertEquals(
                "The destination for this handoff is not available on this Mac.",
                mappedMessage("missing_handoff_target"),
            )
        }
}

private class GitActionsFakeRepository(
    private val onSend: suspend (String, JSONValue?) -> RPCMessage,
) : CodexRepository {
    override val isSessionReady: StateFlow<Boolean> = MutableStateFlow(true)
    override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected)
    override val threads: StateFlow<List<CodexThread>> = MutableStateFlow(emptyList())
    override val activeThreadId: StateFlow<String?> = MutableStateFlow(null)
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
    ) = error("unused")

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
    ): RPCMessage = onSend(method, params)

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
