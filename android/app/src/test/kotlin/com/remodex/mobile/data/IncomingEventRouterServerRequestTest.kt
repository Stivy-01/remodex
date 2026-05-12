package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.ContextWindowUsage
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.PendingApprovalDecision
import com.remodex.mobile.core.model.PendingApprovalRequest
import com.remodex.mobile.core.model.PendingStructuredInputRequest
import com.remodex.mobile.core.model.RPCMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class IncomingEventRouterServerRequestTest {
    @Test
    fun dispatchServerRequest_enqueuesCommandApprovalAndReturnsDecisionObject() =
        runTest {
            val pending =
                CompletableDeferred<Pair<PendingApprovalRequest, (PendingApprovalDecision) -> Unit>>()
            val response = CompletableDeferred<RPCMessage>()
            newRouter(
                onApprovalRequest = { request, respond -> pending.complete(request to respond) },
            ).dispatchServerRequest(
                method = "item/commandExecution/requestApproval",
                requestId = JSONValue.Str("approval-1"),
                params =
                    JSONValue.Obj(
                        mapOf(
                            "command" to JSONValue.Str("git status"),
                            "reason" to JSONValue.Str("Inspect workspace"),
                        ),
                    ),
                respond = { response.complete(it) },
            )

            val (request, respond) = pending.await()
            assertEquals("item/commandExecution/requestApproval", request.method)
            assertEquals("git status", request.command)
            assertEquals("Inspect workspace", request.reason)
            respond(PendingApprovalDecision.Decline)

            val message = response.await()
            assertEquals(JSONValue.Str("approval-1"), message.id)
            assertEquals(JSONValue.Obj(mapOf("decision" to JSONValue.Str("decline"))), message.result)
            assertNull(message.error)
            assertNull(message.jsonrpc)
        }

    @Test
    fun dispatchServerRequest_appendsPendingApprovalTimelineMarkerWhenThreadScoped() =
        runTest {
            val timeline = MessageTimelineStore()
            val pending =
                CompletableDeferred<Pair<PendingApprovalRequest, (PendingApprovalDecision) -> Unit>>()
            val response = CompletableDeferred<RPCMessage>()
            newRouter(
                messageTimeline = timeline,
                onApprovalRequest = { request, respond -> pending.complete(request to respond) },
            ).dispatchServerRequest(
                method = "item/commandExecution/requestApproval",
                requestId = JSONValue.Str("approval-timeline"),
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thr-99"),
                            "turnId" to JSONValue.Str("turn-aa"),
                            "itemId" to JSONValue.Str("item-ii"),
                            "command" to JSONValue.Str("git diff"),
                            "reason" to JSONValue.Str("Review changes"),
                        ),
                    ),
                respond = { response.complete(it) },
            )

            val (request, respond) = pending.await()
            assertEquals("thr-99", request.threadId)
            assertEquals("turn-aa", request.turnId)
            assertEquals("item-ii", request.itemId)

            val row = timeline.messagesByThread.value["thr-99"]?.singleOrNull()
            assertNotNull(row)
            assertEquals(CodexMessageKind.pendingApproval, row.kind)
            assertEquals(request.id, row.id)
            assertEquals("turn-aa", row.turnId)
            assertEquals("item-ii", row.itemId)
            assertEquals(
                "Review changes\n\nCommand: git diff",
                row.text,
            )

            respond(PendingApprovalDecision.Accept)
            response.await()
            Unit
        }

    @Test
    fun dispatchServerRequest_commandApprovalAcceptForSessionUsesDecisionPayload() =
        runTest {
            val pending =
                CompletableDeferred<Pair<PendingApprovalRequest, (PendingApprovalDecision) -> Unit>>()
            val response = CompletableDeferred<RPCMessage>()
            newRouter(
                onApprovalRequest = { request, respond -> pending.complete(request to respond) },
            ).dispatchServerRequest(
                method = "item/command_execution/request_approval",
                requestId = JSONValue.Str("approval-session"),
                params = JSONValue.Obj(mapOf("command" to JSONValue.Str("npm test"))),
                respond = { response.complete(it) },
            )

            val (_, respond) = pending.await()
            respond(PendingApprovalDecision.AcceptForSession)

            val message = response.await()
            assertEquals(JSONValue.Str("approval-session"), message.id)
            assertEquals(
                JSONValue.Obj(mapOf("decision" to JSONValue.Str("acceptForSession"))),
                message.result,
            )
            assertNull(message.error)
        }

    @Test
    fun dispatchServerRequest_autoApprovesWhenFullAccessIsEnabled() =
        runTest {
            val response = routerResponseFor(
                method = "desktop/custom/requestApproval",
                requestId = JSONValue.NumLong(42),
                shouldAutoApproveRequests = true,
            )

            assertEquals(JSONValue.NumLong(42), response.id)
            assertEquals(JSONValue.Obj(mapOf("decision" to JSONValue.Str("accept"))), response.result)
            assertNull(response.error)
            assertNull(response.jsonrpc)
        }

    @Test
    fun dispatchServerRequest_answersStructuredUserInputAfterUserResponse() =
        runTest {
            val pending =
                CompletableDeferred<
                    Pair<PendingStructuredInputRequest, (answersByQuestionId: Map<String, List<String>>) -> Unit>,
                >()
            val response = CompletableDeferred<RPCMessage>()
            newRouter(
                onStructuredInputRequest = { request, respond -> pending.complete(request to respond) },
            ).dispatchServerRequest(
                method = "item/tool/requestUserInput",
                requestId = JSONValue.Str("input-1"),
                params =
                    JSONValue.Obj(
                        mapOf(
                            "questions" to
                                JSONValue.Arr(
                                    listOf(
                                        JSONValue.Obj(
                                            mapOf(
                                                "id" to JSONValue.Str("mode"),
                                                "header" to JSONValue.Str("Mode"),
                                                "question" to JSONValue.Str("Pick a mode"),
                                                "options" to
                                                    JSONValue.Arr(
                                                        listOf(
                                                            JSONValue.Obj(
                                                                mapOf(
                                                                    "label" to JSONValue.Str("Plan"),
                                                                    "description" to JSONValue.Str("Plan first"),
                                                                ),
                                                            ),
                                                        ),
                                                    ),
                                            ),
                                        ),
                                    ),
                                ),
                        ),
                    ),
                respond = { response.complete(it) },
            )

            val (request, respond) = pending.await()
            assertEquals("mode", request.questions.single().id)
            assertEquals("Pick a mode", request.questions.single().question)
            assertEquals("Plan", request.questions.single().options.single().label)
            respond(mapOf("mode" to listOf("Plan")))

            val message = response.await()
            assertEquals(JSONValue.Str("input-1"), message.id)
            assertEquals(
                JSONValue.Obj(
                    mapOf(
                        "answers" to
                            JSONValue.Obj(
                                mapOf(
                                    "mode" to
                                        JSONValue.Obj(
                                            mapOf(
                                                "answers" to JSONValue.Arr(listOf(JSONValue.Str("Plan"))),
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
                message.result,
            )
            assertNull(message.error)
            assertNull(message.jsonrpc)
        }

    @Test
    fun dispatchServerRequest_appendsStructuredInputTimelineMarkerWhenThreadScoped() =
        runTest {
            val timeline = MessageTimelineStore()
            val pending =
                CompletableDeferred<
                    Pair<PendingStructuredInputRequest, (answersByQuestionId: Map<String, List<String>>) -> Unit>,
                >()
            val response = CompletableDeferred<RPCMessage>()
            newRouter(
                messageTimeline = timeline,
                onStructuredInputRequest = { request, respond -> pending.complete(request to respond) },
            ).dispatchServerRequest(
                method = "item/tool/requestUserInput",
                requestId = JSONValue.Str("input-timeline"),
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thr-structured"),
                            "turnId" to JSONValue.Str("turn-si"),
                            "questions" to
                                JSONValue.Arr(
                                    listOf(
                                        JSONValue.Obj(
                                            mapOf(
                                                "id" to JSONValue.Str("mode"),
                                                "header" to JSONValue.Str("Mode"),
                                                "question" to JSONValue.Str("Choose speed"),
                                                "options" to JSONValue.Arr(emptyList()),
                                            ),
                                        ),
                                    ),
                                ),
                        ),
                    ),
                respond = { response.complete(it) },
            )

            val (request, respond) = pending.await()
            val row = timeline.messagesByThread.value["thr-structured"]?.singleOrNull()
            assertNotNull(row)
            assertEquals(CodexMessageKind.userInputPrompt, row.kind)
            assertEquals(request.id, row.id)
            assertEquals("Choose speed", row.text)

            respond(mapOf("mode" to listOf("fast")))
            response.await()
            Unit
        }

    @Test
    fun dispatchServerRequest_rejectsUnsupportedServerRequest() =
        runTest {
            val response = routerResponseFor(
                method = "item/tool/unsupported",
                requestId = JSONValue.Str("unsupported-1"),
            )

            assertEquals(JSONValue.Str("unsupported-1"), response.id)
            assertEquals(-32601, response.error?.code)
            assertNull(response.jsonrpc)
        }

    @Test
    fun dispatchNotification_routesRateLimitUpdates() {
        var updatedParams: Map<String, JSONValue>? = null
        val payload =
            mapOf(
                "rateLimits" to
                    JSONValue.Obj(
                        mapOf(
                            "primary" to
                                JSONValue.Obj(
                                    mapOf("usedPercent" to JSONValue.NumLong(34L)),
                                ),
                        ),
                    ),
            )

        newRouter(
            onRateLimitsUpdated = { params -> updatedParams = params },
        ).dispatchNotification(
            method = "account/rateLimits/updated",
            params = JSONValue.Obj(payload),
        )

        assertEquals(payload, updatedParams)
    }

    @Test
    fun dispatchNotification_threadTokenUsageUpdated_emitsDecodedUsage() {
        var captured: Pair<String, ContextWindowUsage>? = null
        newRouter(
            onThreadContextUsageLive = { tid, u -> captured = tid to u },
        ).dispatchNotification(
            method = "thread/tokenUsage/updated",
            params =
                JSONValue.Obj(
                    mapOf(
                        "threadId" to JSONValue.Str("thr-live"),
                        "usage" to
                            JSONValue.Obj(
                                mapOf(
                                    "tokensUsed" to JSONValue.NumLong(17),
                                    "tokenLimit" to JSONValue.NumLong(258),
                                ),
                            ),
                    ),
                ),
        )
        assertEquals("thr-live", captured?.first)
        assertEquals(17, captured?.second?.tokensUsed)
        assertEquals(258, captured?.second?.tokenLimit)
    }

    @Test
    fun dispatchNotification_codexEventEnvelope_token_count_emitsUsage() {
        var captured: Pair<String, ContextWindowUsage>? = null
        newRouter(
            onThreadContextUsageLive = { tid, u -> captured = tid to u },
        ).dispatchNotification(
            method = "codex/event",
            params =
                JSONValue.Obj(
                    mapOf(
                        "msg" to
                            JSONValue.Obj(
                                mapOf(
                                    "type" to JSONValue.Str("token_count"),
                                    "threadId" to JSONValue.Str("thr-env"),
                                    "info" to
                                        JSONValue.Obj(
                                            mapOf(
                                                "total_tokens" to JSONValue.NumLong(42),
                                                "model_context_window" to JSONValue.NumLong(400),
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
        )
        assertEquals("thr-env", captured?.first)
        assertEquals(42, captured?.second?.tokensUsed)
        assertEquals(400, captured?.second?.tokenLimit)
    }

    @Test
    fun dispatchNotification_codex_event_named_token_count_emitsUsage() {
        var captured: Pair<String, ContextWindowUsage>? = null
        newRouter(
            onThreadContextUsageLive = { tid, u -> captured = tid to u },
            resolveAmbiguousUsageThreadId = { "solo-thread" },
        ).dispatchNotification(
            method = "codex/event/token_count",
            params =
                JSONValue.Obj(
                    mapOf(
                        "msg" to
                            JSONValue.Obj(
                                mapOf(
                                    "info" to
                                        JSONValue.Obj(
                                            mapOf(
                                                "total_tokens" to JSONValue.NumLong(7),
                                                "model_context_window" to JSONValue.NumLong(70),
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
        )
        assertEquals("solo-thread", captured?.first)
        assertEquals(7, captured?.second?.tokensUsed)
    }

    @Test
    fun dispatchNotification_threadTokenUsageUpdated_missingThreadId_doesNotEmit() {
        var callCount = 0
        newRouter(
            onThreadContextUsageLive = { _, _ -> callCount++ },
        ).dispatchNotification(
            method = "thread/tokenUsage/updated",
            params =
                JSONValue.Obj(
                    mapOf(
                        "usage" to
                            JSONValue.Obj(
                                mapOf(
                                    "tokensUsed" to JSONValue.NumLong(1),
                                    "tokenLimit" to JSONValue.NumLong(2),
                                ),
                            ),
                    ),
                ),
        )
        assertEquals(0, callCount)
    }

    @Test
    fun dispatchNotification_threadTokenUsageUpdated_nonObjectUsage_doesNotEmit() {
        var callCount = 0
        newRouter(
            onThreadContextUsageLive = { _, _ -> callCount++ },
        ).dispatchNotification(
            method = "thread/tokenUsage/updated",
            params =
                JSONValue.Obj(
                    mapOf(
                        "threadId" to JSONValue.Str("thr-x"),
                        "usage" to JSONValue.Str("nope"),
                    ),
                ),
        )
        assertEquals(0, callCount)
    }

    @Test
    fun dispatchNotification_threadNameUpdated_updatesTitleWhenNoLocalRename() {
        val threads = MutableStateFlow(listOf(CodexThread(id = "thread-1", title = "Conversation")))
        newRouter(threads = threads).dispatchNotification(
            method = "thread/name/updated",
            params =
                JSONValue.Obj(
                    mapOf(
                        "threadId" to JSONValue.Str("thread-1"),
                        "name" to JSONValue.Str("Server Rename"),
                    ),
                ),
        )

        assertEquals("Server Rename", threads.value.single().displayTitle)
    }

    @Test
    fun dispatchNotification_threadNameUpdated_doesNotOverwritePersistedLocalRename() {
        val threads = MutableStateFlow(listOf(CodexThread(id = "thread-1", title = "Phone Rename", name = "Phone Rename")))
        newRouter(
            threads = threads,
            persistedThreadRename = { tid -> if (tid == "thread-1") "Phone Rename" else null },
        ).dispatchNotification(
            method = "thread/name/updated",
            params =
                JSONValue.Obj(
                    mapOf(
                        "threadId" to JSONValue.Str("thread-1"),
                        "name" to JSONValue.Str("Server Rename"),
                    ),
                ),
        )

        assertEquals("Phone Rename", threads.value.single().displayTitle)
    }

    @Test
    fun dispatchNotification_threadNameUpdated_emptyNameClearsTitleWithoutLocalRename() {
        val threads = MutableStateFlow(listOf(CodexThread(id = "thread-1", title = "Server Rename", name = "Server Rename")))
        newRouter(threads = threads).dispatchNotification(
            method = "thread/name/updated",
            params =
                JSONValue.Obj(
                    mapOf(
                        "threadId" to JSONValue.Str("thread-1"),
                        "name" to JSONValue.Str("   "),
                    ),
                ),
        )

        assertEquals(CodexThread.DEFAULT_DISPLAY_TITLE, threads.value.single().displayTitle)
        assertNull(threads.value.single().name)
        assertNull(threads.value.single().title)
    }

    private suspend fun routerResponseFor(
        method: String,
        requestId: JSONValue,
        shouldAutoApproveRequests: Boolean = false,
    ): RPCMessage {
        val response = CompletableDeferred<RPCMessage>()
        newRouter(
            shouldAutoApproveRequests = { shouldAutoApproveRequests },
        ).dispatchServerRequest(
            method = method,
            requestId = requestId,
            params = null,
            respond = { response.complete(it) },
        )
        return response.await()
    }

    private fun newRouter(
        threads: MutableStateFlow<List<CodexThread>> = MutableStateFlow(emptyList()),
        messageTimeline: MessageTimelineStore = MessageTimelineStore(),
        shouldAutoApproveRequests: () -> Boolean = { false },
        onApprovalRequest: (PendingApprovalRequest, (PendingApprovalDecision) -> Unit) -> Unit = { _, _ -> },
        onStructuredInputRequest: (
            PendingStructuredInputRequest,
            (answersByQuestionId: Map<String, List<String>>) -> Unit,
        ) -> Unit = { _, _ -> },
        onRateLimitsUpdated: (Map<String, JSONValue>?) -> Unit = { _ -> },
        onThreadContextUsageLive: (String, ContextWindowUsage) -> Unit = { _, _ -> },
        resolveAmbiguousUsageThreadId: () -> String? = { null },
        persistedThreadRename: (String) -> String? = { null },
    ): IncomingEventRouter =
        IncomingEventRouter(
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            threads = threads,
            activeThreadId = MutableStateFlow(null),
            messageTimeline = messageTimeline,
            onRequestThreadSync = {},
            onHydrateThread = {},
            onTurnLifecycle = { _, _ -> },
            onTurnFinished = {},
            isTurnStreamingActive = { _, _ -> false },
            shouldAutoApproveRequests = shouldAutoApproveRequests,
            onApprovalRequest = onApprovalRequest,
            onStructuredInputRequest = onStructuredInputRequest,
            onRateLimitsUpdated = onRateLimitsUpdated,
            onThreadContextUsageLive = onThreadContextUsageLive,
            resolveAmbiguousUsageThreadId = resolveAmbiguousUsageThreadId,
            persistedThreadRename = persistedThreadRename,
        )
}
