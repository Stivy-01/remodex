package com.remodex.mobile.data

import com.remodex.mobile.core.model.ContextWindowUsage
import com.remodex.mobile.core.model.ContextWindowUsageCodec
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexPlanStep
import com.remodex.mobile.core.model.CodexPlanStepStatus
import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.PendingApprovalDecision
import com.remodex.mobile.core.model.PendingApprovalRequest
import com.remodex.mobile.core.model.PendingStructuredInputRequest
import com.remodex.mobile.core.model.RPCError
import com.remodex.mobile.core.model.RPCMessage
import com.remodex.mobile.core.notification.RunCompletionAttentionKind
import com.remodex.mobile.core.notification.TurnCompletionNotificationLogic
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

private enum class ServerRequestKind {
    StructuredInput,
    Approval,
    Unsupported,
}

/**
 * Routes Mac→phone JSON-RPC notifications and server-initiated requests.
 * Parity with [CodexService.handleNotification] / [CodexService.handleServerRequest] (iOS).
 */
internal class IncomingEventRouter(
    private val scope: CoroutineScope,
    private val threads: MutableStateFlow<List<CodexThread>>,
    private val activeThreadId: MutableStateFlow<String?>,
    private val messageTimeline: MessageTimelineStore,
    private val commandDetailsStore: CommandExecutionDetailsStore = CommandExecutionDetailsStore(),
    private val onRequestThreadSync: () -> Unit,
    private val onHydrateThread: (String) -> Unit,
    /** [turnId] null ⇒ run attivo senza id ancora (protected fallback iOS). */
    private val onTurnLifecycle: (threadId: String, turnId: String?) -> Unit,
    private val onTurnFinished: (threadId: String) -> Unit,
    /**
     * Streaming reasoning/delta attivo per quel thread+turn (parity iOS `isReasoningTurnActive`).
     */
    private val isTurnStreamingActive: (threadId: String, turnId: String?) -> Boolean,
    private val shouldAutoApproveRequests: () -> Boolean,
    private val onApprovalRequest: (PendingApprovalRequest, (PendingApprovalDecision) -> Unit) -> Unit,
    private val onStructuredInputRequest: (
        PendingStructuredInputRequest,
        (answersByQuestionId: Map<String, List<String>>) -> Unit,
    ) -> Unit,
    private val onRateLimitsUpdated: (Map<String, JSONValue>?) -> Unit = { _ -> },
    /**
     * Live context window from [thread/tokenUsage/updated] or legacy `codex/event` `token_count`
     * (parity iOS `handleThreadTokenUsageUpdated` / `handleLegacyTokenCountEvent`).
     */
    private val onThreadContextUsageLive: (String, ContextWindowUsage) -> Unit = { _, _ -> },
    /**
     * When token events omit thread id, single unambiguous running thread (parity `resolveContextUsageThreadID`).
     */
    private val resolveAmbiguousUsageThreadId: () -> String? = { null },
    /** [thread/started] and similar: server may send stale `cwd` during same-thread rebind. */
    private val remapThreadFromServer: (CodexThread) -> CodexThread = { it },
    /** Local user rename wins over server-pushed rename/title fallback. */
    private val persistedThreadRename: (String) -> String? = { null },
    /**
     * Local notification hook when a turn completes or fails in the background (iOS `notifyRunCompletionIfNeeded`).
     */
    private val onRunCompletionAttention: (
        threadId: String,
        turnId: String?,
        kind: RunCompletionAttentionKind,
    ) -> Unit = { _, _, _ -> },
) {
    private val threadIdByTurnId = ConcurrentHashMap<String, String>()

    fun resetCaches() {
        threadIdByTurnId.clear()
    }

    fun dispatchNotification(
        method: String,
        params: JSONValue?,
    ) {
        val m = method.trim()
        val obj = params?.objectValue
        if (m.startsWith("codex/event/")) {
            val suffix =
                normalizeMethod(m.removePrefix("codex/event/").trim()).replace("/", "")
            if (suffix == "tokencount" && obj != null) {
                val payload =
                    obj["msg"]?.objectValue
                        ?: obj["event"]?.objectValue
                        ?: obj
                handleLegacyTokenCountEvent(obj, payload)
                return
            }
        }
        if (m == "codex/event" && tryConsumeCodexTokenCountEnvelope(obj)) {
            return
        }
        if (m == "codex/event" && tryConsumeCodexCommandExecutionEnvelope(obj)) {
            return
        }
        when (m) {
            "thread/started" -> handleThreadStarted(obj)
            "thread/name/updated" -> handleThreadNameUpdated(obj)
            "turn/started" -> handleTurnStarted(obj)
            "turn/completed" -> handleTurnCompleted(obj)
            "turn/plan/updated" -> handleTurnPlanUpdated(obj)
            "item/agentMessage/delta",
            "codex/event/agent_message_content_delta",
            "codex/event/agent_message_delta",
            -> handleAgentDelta(obj)
            "item/completed",
            "codex/event/item_completed",
            "codex/event/agent_message",
            -> handleItemCompleted(obj)
            "codex/event/user_message" -> handleUserMirrored(obj)
            "codex/event/image_generation_end" -> handleImageGenerationEnd(obj)
            "item/reasoning/summaryTextDelta",
            "item/reasoning/summaryPartAdded",
            "item/reasoning/textDelta",
            -> handleReasoningDelta(obj)
            "item/plan/delta" -> handlePlanDelta(obj)
            "item/fileChange/outputDelta" -> handleFileChangeDelta(obj)
            "item/toolCall/outputDelta",
            "item/toolCall/output_delta",
            "item/tool_call/outputDelta",
            "item/tool_call/output_delta",
            -> handleToolCallOutputDelta(obj)
            "item/commandExecution/outputDelta",
            "item/commandExecution/output_delta",
            "item/command_execution/outputDelta",
            "item/command_execution/output_delta",
            -> handleCommandExecutionDelta(m, obj)
            "exec_command_begin",
            "codex/event/exec_command_begin",
            -> handleCommandExecutionState(m, obj)
            "exec_command_output_delta",
            "codex/event/exec_command_output_delta",
            -> handleCommandExecutionDelta(m, obj)
            "exec_command_end",
            "codex/event/exec_command_end",
            -> handleCommandExecutionState(m, obj)
            "turn/failed" -> handleTurnFailed(obj)
            "error",
            "codex/event/error",
            -> handleErrorNotification(obj)
            "account/rateLimits/updated" -> onRateLimitsUpdated(obj)
            "thread/tokenUsage/updated" -> handleThreadTokenUsagePush(obj)
            else -> {
                val nm = normalizeMethod(m)
                when {
                    m.startsWith("codex/event/") && m.contains("agent") && m.contains("delta") ->
                        handleAgentDelta(obj)
                    m.startsWith("codex/event/") && nm.contains("imagegeneration") ->
                        handleImageGenerationEnd(obj)
                    nm.contains("filechange") && (nm.contains("delta") || nm.contains("partadded")) ->
                        handleFileChangeDelta(obj)
                    nm.contains("toolcall") && (nm.contains("delta") || nm.contains("partadded")) ->
                        handleToolCallOutputDelta(obj)
                    (nm.contains("turndiff") || nm.contains("/diff/") || nm.startsWith("diff/")) &&
                        (nm.contains("delta") || nm.contains("partadded")) ->
                        handleFileChangeDelta(obj)
                    else -> Unit
                }
            }
        }
    }

    private fun tryConsumeCodexTokenCountEnvelope(params: Map<String, JSONValue>?): Boolean {
        val p = params ?: return false
        val msg = p["msg"]?.objectValue ?: return false
        val eventType = msg["type"]?.stringValue?.trim()?.lowercase() ?: return false
        if (eventType != "token_count") return false
        handleLegacyTokenCountEvent(p, msg)
        return true
    }

    private fun tryConsumeCodexCommandExecutionEnvelope(params: Map<String, JSONValue>?): Boolean {
        val p = params ?: return false
        val msg = p["msg"]?.objectValue ?: p["event"]?.objectValue ?: return false
        val eventType = msg["type"]?.stringValue?.trim() ?: return false
        return when (eventType) {
            "exec_command_begin" -> {
                handleCommandExecutionState(eventType, p)
                true
            }
            "exec_command_output_delta" -> {
                handleCommandExecutionDelta(eventType, p)
                true
            }
            "exec_command_end" -> {
                handleCommandExecutionState(eventType, p)
                true
            }
            else -> false
        }
    }

    private fun handleThreadTokenUsagePush(params: Map<String, JSONValue>?) {
        val p = params ?: return
        val threadId = IncomingNotificationParsers.extractThreadId(p) ?: return
        val usage = ContextWindowUsageCodec.decodeFromIncomingUsageParams(p) ?: return
        onThreadContextUsageLive(threadId.trim(), usage)
    }

    private fun handleLegacyTokenCountEvent(
        paramsObject: Map<String, JSONValue>,
        payload: Map<String, JSONValue>,
    ) {
        val normalized =
            IncomingNotificationParsers.normalizedLegacyTokenCountParams(paramsObject, payload)
        val usage =
            ContextWindowUsageCodec.decodeFromLegacyTokenCountPayload(payload)
                ?: ContextWindowUsageCodec.decodeFromIncomingUsageParams(normalized)
                ?: return
        val turnHint = IncomingNotificationParsers.extractTurnId(normalized)
        val threadId =
            resolveThreadId(normalized)
                ?: IncomingNotificationParsers.extractThreadId(normalized)
                ?: resolveAmbiguousUsageThreadId()
                ?: return
        turnHint?.let { recordTurnThread(it, threadId) }
        onThreadContextUsageLive(threadId.trim(), usage)
    }

    private fun normalizeMethod(method: String): String =
        method.lowercase().replace("_", "").replace("-", "")

    fun dispatchServerRequest(
        method: String,
        requestId: JSONValue,
        params: JSONValue?,
        respond: (RPCMessage) -> Unit,
    ) {
        val m = method.trim()
        scope.launch(Dispatchers.IO) {
            val nm = normalizeMethod(m)
            when (serverRequestKind(m, nm)) {
                ServerRequestKind.StructuredInput -> {
                    val request = buildStructuredInputRequest(requestId, params)
                    appendStructuredInputTimelineMarker(request)
                    onStructuredInputRequest(request) { answers ->
                        respond(
                            RPCMessage.success(
                                id = requestId,
                                result = structuredUserInputResult(answers),
                                includeJsonRpc = false,
                            ),
                        )
                    }
                }
                ServerRequestKind.Approval -> {
                    if (shouldAutoApproveRequests()) {
                        respond(
                            RPCMessage.success(
                                id = requestId,
                                result = approvalDecisionResult("accept"),
                                includeJsonRpc = false,
                            ),
                        )
                        return@launch
                    }
                    val request = buildApprovalRequest(m, requestId, params)
                    appendPendingApprovalTimelineMarker(request)
                    onApprovalRequest(request) { decision ->
                        val rpc =
                            when (decision) {
                                PendingApprovalDecision.Decline -> "decline"
                                PendingApprovalDecision.Accept -> "accept"
                                PendingApprovalDecision.AcceptForSession -> "acceptForSession"
                            }
                        respond(
                            RPCMessage.success(
                                id = requestId,
                                result = approvalDecisionResult(rpc),
                                includeJsonRpc = false,
                            ),
                        )
                    }
                }
                ServerRequestKind.Unsupported -> {
                    respond(
                        RPCMessage.failure(
                            id = requestId,
                            error =
                                RPCError(
                                    code = -32601,
                                    message = "Unsupported request method: $m",
                                ),
                            includeJsonRpc = false,
                        ),
                    )
                }
            }
        }
    }

    private fun serverRequestKind(
        method: String,
        normalizedMethod: String,
    ): ServerRequestKind =
        when {
            isStructuredInputServerRequestMethod(method) -> ServerRequestKind.StructuredInput
            isApprovalServerRequestMethod(normalizedMethod) -> ServerRequestKind.Approval
            else -> ServerRequestKind.Unsupported
        }

    private fun recordTurnThread(
        turnId: String?,
        threadId: String?,
    ) {
        val t = turnId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val th = threadId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        threadIdByTurnId[t] = th
    }

    private fun resolveThreadId(params: Map<String, JSONValue>?): String? {
        IncomingNotificationParsers.extractThreadId(params)?.let { return it }
        val turn = IncomingNotificationParsers.extractTurnId(params) ?: return null
        return threadIdByTurnId[turn]
    }

    private fun handleThreadStarted(params: Map<String, JSONValue>?) {
        val threadValue = params?.get("thread") ?: return
        val jo = jsonObjectFromJsonValue(threadValue) ?: return
        val thread = runCatching { CodexThread.fromJsonObject(jo) }.getOrNull() ?: return
        val merged = remapThreadFromServer(thread)
        threads.value = upsertThread(threads.value, merged)
        if (activeThreadId.value == null) {
            activeThreadId.value = merged.id
        }
        IncomingNotificationParsers.extractTurnId(params)?.let { recordTurnThread(it, merged.id) }
        onRequestThreadSync()
        scope.launch { onHydrateThread(merged.id) }
    }

    private fun handleThreadNameUpdated(params: Map<String, JSONValue>?) {
        if (params == null) return
        val threadId = extractThreadId(params) ?: return
        val event = envelopeEventObject(params)
        val renameKeys = listOf("threadName", "thread_name", "name", "title")
        val hasExplicitRenameField = hasAnyKey(params, renameKeys) || event?.let { hasAnyKey(it, renameKeys) } == true
        val name =
            firstString(params, renameKeys)
                ?: event?.let { firstString(it, renameKeys) }
        val localRename = CodexThread.normalizeIdentifier(persistedThreadRename(threadId))
        val list = threads.value
        val idx = list.indexOfFirst { it.id == threadId }
        if (localRename != null) {
            val next =
                if (idx >= 0) {
                    list.mapIndexed { i, t ->
                        if (i == idx) {
                            t.copy(title = localRename, name = localRename)
                        } else {
                            t
                        }
                    }
                } else {
                    list + CodexThread(id = threadId, title = localRename, name = localRename)
                }
            threads.value = sortThreadsForSidebar(next)
            return
        }
        val normalized = CodexThread.normalizeIdentifier(name)
        if (normalized == null) {
            if (hasExplicitRenameField && idx >= 0) {
                threads.value =
                    sortThreadsForSidebar(
                        list.mapIndexed { i, t ->
                            if (i == idx) {
                                t.copy(title = null, name = null)
                            } else {
                                t
                            }
                        },
                    )
                onRequestThreadSync()
            }
            return
        }
        val next =
            if (idx >= 0) {
                list.mapIndexed { i, t ->
                    if (i == idx) {
                        t.copy(title = normalized, name = normalized)
                    } else {
                        t
                    }
                }
            } else {
                list + CodexThread(id = threadId, title = normalized, name = normalized)
            }
        threads.value = sortThreadsForSidebar(next)
        onRequestThreadSync()
    }

    private fun handleTurnStarted(params: Map<String, JSONValue>?) {
        val p = params ?: return
        val threadId =
            resolveThreadId(p) ?: IncomingNotificationParsers.extractThreadId(p) ?: return
        val turnId = IncomingNotificationParsers.extractTurnIdForTurnLifecycleEvent(p)
        if (turnId != null) {
            recordTurnThread(turnId, threadId)
        }
        onTurnLifecycle(threadId, turnId)
        if (turnId != null) {
            scope.launch {
                messageTimeline.confirmLatestPendingUserMessage(threadId, turnId)
            }
        }
    }

    private fun handleTurnFailed(params: Map<String, JSONValue>?) {
        val p = params ?: return
        val threadId = resolveThreadId(p) ?: IncomingNotificationParsers.extractThreadId(p) ?: return
        onTurnFinished(threadId)
        handleErrorNotification(p)
    }

    private fun handleTurnCompleted(params: Map<String, JSONValue>?) {
        val p = params ?: return
        val threadId = resolveThreadId(p) ?: return
        val turnId = IncomingNotificationParsers.extractTurnIdForTurnLifecycleEvent(p)
        onTurnFinished(threadId)
        if (turnId != null) {
            scope.launch {
                messageTimeline.confirmLatestPendingUserMessage(threadId, turnId)
            }
        }
        val msg =
            firstString(
                p,
                listOf("failureMessage", "failure_message", "errorMessage", "message"),
            )
        if (!msg.isNullOrEmpty()) {
            scope.launch {
                messageTimeline.appendSystemLine(threadId, turnId, "Turn: $msg")
            }
        }
        val failureMsg = TurnCompletionNotificationLogic.parseTurnFailureMessage(p)
        val terminal = TurnCompletionNotificationLogic.parseTurnTerminalState(p, failureMsg)
        val attention = TurnCompletionNotificationLogic.attentionKindFromTerminalState(terminal)
        if (attention != null) {
            onRunCompletionAttention(threadId, turnId, attention)
        }
    }

    private fun handleAgentDelta(params: Map<String, JSONValue>?) {
        val delta = IncomingNotificationParsers.extractAssistantDelta(params) ?: return
        val turnId = IncomingNotificationParsers.extractTurnId(params) ?: return
        val threadId = resolveThreadId(params) ?: return
        val itemId = IncomingNotificationParsers.extractItemId(params)
        val assistantPhase = IncomingNotificationParsers.extractAssistantPhase(params)
        recordTurnThread(turnId, threadId)
        scope.launch {
            messageTimeline.appendAssistantDelta(threadId, turnId, itemId, delta, assistantPhase)
        }
    }

    private fun handleItemCompleted(params: Map<String, JSONValue>?) {
        val p = params ?: return
        val ev = envelopeEventObject(p)
        val itemObj = IncomingNotificationParsers.extractIncomingItemObject(p, ev)
        if (itemObj == null) {
            handleLegacyAgentCompleted(p)
            return
        }
        val decoded = ThreadHistoryDecoder.decodeCompletedItem(itemObj)
        if (decoded == null) {
            handleLegacyAgentCompleted(p)
            return
        }
        val threadId = resolveThreadId(p) ?: IncomingNotificationParsers.extractThreadId(p) ?: return
        val turnId = IncomingNotificationParsers.extractTurnId(p)
        val itemId = extractItemIdDeep(p, itemObj)
        recordTurnThread(turnId, threadId)
        when (decoded.kind) {
            CodexMessageKind.chat ->
                when (decoded.role) {
                    CodexMessageRole.assistant -> {
                        val text = decoded.text.trim()
                        if (text.isEmpty() && decoded.attachments.isEmpty()) return
                        val assistantPhase =
                            decoded.assistantPhase ?: IncomingNotificationParsers.extractAssistantPhase(p, itemObj)
                        scope.launch {
                            messageTimeline.completeAssistantMessage(
                                threadId = threadId,
                                turnId = turnId,
                                itemId = itemId,
                                text = text,
                                attachments = decoded.attachments,
                                assistantPhase = assistantPhase,
                            )
                        }
                    }
                    CodexMessageRole.user -> {
                        val text = decoded.text.trim()
                        if (text.isEmpty() && decoded.attachments.isEmpty()) return
                        scope.launch {
                            messageTimeline.appendMirroredUser(threadId, turnId, text, decoded.attachments)
                        }
                    }
                    else -> handleLegacyAgentCompleted(p)
                }
            CodexMessageKind.thinking,
            CodexMessageKind.fileChange,
            CodexMessageKind.commandExecution,
            -> {
                val text = decoded.text.trim()
                if (text.isEmpty()) return
                if (decoded.kind == CodexMessageKind.commandExecution) {
                    val state =
                        CommandExecutionEventParser.parse(
                            params = p,
                            eventObject = itemObj,
                            method = "item/completed",
                        )
                    commandDetailsStore.upsertFromState(
                        itemId = state.itemId ?: itemId,
                        fullCommand = state.fullCommand,
                        cwd = state.cwd,
                        exitCode = state.exitCode,
                        durationMs = state.durationMs,
                    )
                }
                scope.launch {
                    messageTimeline.completeSystemItem(threadId, turnId, itemId, decoded.kind, text)
                }
            }
            CodexMessageKind.plan -> {
                val text = decoded.text.trim()
                scope.launch {
                    messageTimeline.upsertPlanMessage(
                        threadId = threadId,
                        turnId = turnId,
                        itemId = itemId,
                        text = text.ifEmpty { null },
                        explanation = decoded.planState?.explanation,
                        steps = decoded.planState?.steps,
                        isStreaming = false,
                    )
                }
            }
            CodexMessageKind.subagentAction -> {
                val action = decoded.subagentAction ?: return
                scope.launch {
                    messageTimeline.upsertSubagentActionMessage(
                        threadId = threadId,
                        turnId = turnId,
                        itemId = itemId,
                        action = action,
                        isStreaming = false,
                    )
                }
            }
            else -> handleLegacyAgentCompleted(p)
        }
    }

    private fun extractItemIdDeep(
        params: Map<String, JSONValue>,
        itemObject: Map<String, JSONValue>,
    ): String? {
        listOf(
            itemObject["id"]?.stringValue,
            itemObject["call_id"]?.stringValue,
            itemObject["callId"]?.stringValue,
        ).forEach { s ->
            s?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return IncomingNotificationParsers.extractItemId(params)
    }

    private fun handleLegacyAgentCompleted(params: Map<String, JSONValue>) {
        val threadId = resolveThreadId(params) ?: return
        val turnId = IncomingNotificationParsers.extractTurnId(params)
        val itemId = IncomingNotificationParsers.extractItemId(params)
        val text =
            IncomingNotificationParsers.extractAssistantDelta(params)
                ?: IncomingNotificationParsers.extractUserMirrorText(params)
                ?: return
        if (text.isBlank()) return
        val assistantPhase = IncomingNotificationParsers.extractAssistantPhase(params)
        recordTurnThread(turnId, threadId)
        scope.launch {
            messageTimeline.completeAssistantMessage(
                threadId = threadId,
                turnId = turnId,
                itemId = itemId,
                text = text,
                assistantPhase = assistantPhase,
            )
        }
    }

    private fun handleUserMirrored(params: Map<String, JSONValue>?) {
        val text = IncomingNotificationParsers.extractUserMirrorText(params) ?: return
        val turnId = IncomingNotificationParsers.extractTurnId(params)
        val threadId = resolveThreadId(params) ?: return
        recordTurnThread(turnId, threadId)
        scope.launch {
            messageTimeline.appendMirroredUser(threadId, turnId, text)
        }
    }

    private fun handleReasoningDelta(params: Map<String, JSONValue>?) {
        val p = params ?: return
        val delta = IncomingNotificationParsers.extractTextDelta(p) ?: return
        if (delta.isEmpty()) return
        val turnId = IncomingNotificationParsers.extractTurnId(p)
        val threadId = resolveThreadId(p) ?: return
        recordTurnThread(turnId, threadId)
        val itemId = IncomingNotificationParsers.extractItemId(p)
        if (!isTurnStreamingActive(threadId, turnId)) {
            scope.launch {
                messageTimeline.mergeLateReasoningDelta(threadId, turnId, itemId, delta)
            }
            return
        }
        scope.launch {
            when {
                itemId != null ->
                    messageTimeline.appendStreamingSystemItemDelta(
                        threadId = threadId,
                        turnId = turnId,
                        itemId = itemId,
                        kind = CodexMessageKind.thinking,
                        delta = delta,
                    )
                !turnId.isNullOrEmpty() ->
                    messageTimeline.appendStreamingSystemItemDelta(
                        threadId = threadId,
                        turnId = turnId,
                        itemId = null,
                        kind = CodexMessageKind.thinking,
                        delta = delta,
                    )
                else ->
                    messageTimeline.appendSystemLine(
                        threadId = threadId,
                        turnId = null,
                        text = delta,
                        kind = CodexMessageKind.thinking,
                    )
            }
        }
    }

    private fun handlePlanDelta(params: Map<String, JSONValue>?) {
        val p = params ?: return
        val threadId =
            resolveThreadId(p)
                ?: firstString(p, listOf("threadId", "thread_id"))
                    ?.let { CodexThread.normalizeIdentifier(it) }
                ?: return
        val turnId =
            firstString(p, listOf("turnId", "turn_id"))
                ?: return
        val itemId =
            firstString(p, listOf("itemId", "item_id"))
                ?: return
        val delta = p["delta"]?.stringValue ?: return
        if (delta.isEmpty()) return
        recordTurnThread(turnId, threadId)
        scope.launch {
            messageTimeline.upsertPlanMessage(
                threadId = threadId,
                turnId = turnId,
                itemId = itemId,
                text = delta,
                isStreaming = true,
            )
        }
    }

    private fun handleTurnPlanUpdated(params: Map<String, JSONValue>?) {
        val p = params ?: return
        val threadId =
            resolveThreadId(p)
                ?: firstString(p, listOf("threadId", "thread_id"))
                    ?.let { CodexThread.normalizeIdentifier(it) }
                ?: return
        val turnId = firstString(p, listOf("turnId", "turn_id")) ?: return
        recordTurnThread(turnId, threadId)
        val explanation = firstString(p, listOf("explanation"))
        val steps = decodePlanSteps(p["plan"])
        scope.launch {
            messageTimeline.upsertPlanMessage(
                threadId = threadId,
                turnId = turnId,
                itemId = null,
                explanation = explanation,
                steps = steps,
                isStreaming = true,
            )
        }
    }

    private fun handleFileChangeDelta(params: Map<String, JSONValue>?) {
        val p = params ?: return
        val turnId = IncomingNotificationParsers.extractTurnId(p)
        val threadId = resolveThreadId(p) ?: return
        recordTurnThread(turnId, threadId)
        val itemId = IncomingNotificationParsers.extractItemId(p)
        val itemObj =
            IncomingNotificationParsers.extractIncomingItemObject(
                params = p,
                event = envelopeEventObject(p),
            )
        val rendered = itemObj?.let { FileChangeItemBodyRenderer.renderFromIncomingItem(it) }?.trim().orEmpty()
        val delta = IncomingNotificationParsers.extractTextDelta(p)?.trim().orEmpty()
        if (shouldIgnoreFileChangeDelta(rendered, delta)) return
        scope.launch {
            if (rendered.isNotEmpty()) {
                messageTimeline.upsertStreamingSystemItemSnapshot(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = itemId,
                    kind = CodexMessageKind.fileChange,
                    snapshot = rendered,
                )
                return@launch
            }
            if (delta.isEmpty() || delta.equals("[file change]", ignoreCase = true)) return@launch
            when {
                itemId != null ->
                    messageTimeline.appendStreamingSystemItemDelta(
                        threadId,
                        turnId,
                        itemId,
                        CodexMessageKind.fileChange,
                        delta,
                    )
                !turnId.isNullOrEmpty() ->
                    messageTimeline.appendStreamingSystemItemDelta(
                        threadId,
                        turnId,
                        null,
                        CodexMessageKind.fileChange,
                        delta,
                    )
                else ->
                    messageTimeline.appendSystemLine(
                        threadId,
                        null,
                        delta,
                        CodexMessageKind.fileChange,
                    )
            }
        }
    }

    private fun handleImageGenerationEnd(params: Map<String, JSONValue>?) {
        val p = params ?: return
        val event = envelopeEventObject(p)
        val imagePath =
            firstString(p, listOf("saved_path", "savedPath", "path", "url", "image_url"))
                ?: event?.let { firstString(it, listOf("saved_path", "savedPath", "path", "url", "image_url")) }
                ?: return
        val attachment =
            TurnAttachmentCodec.attachmentFromHistorySource(imagePath)
                ?: CodexImageAttachment(thumbnailBase64JPEG = "", sourceURL = imagePath)
        val turnId = IncomingNotificationParsers.extractTurnId(p)
        val threadId = resolveThreadId(p) ?: return
        recordTurnThread(turnId, threadId)
        val itemId =
            IncomingNotificationParsers.extractItemId(p)
                ?: firstString(p, listOf("call_id", "callId", "id"))
                ?: event?.let { firstString(it, listOf("call_id", "callId", "id")) }
        scope.launch {
            messageTimeline.completeAssistantMessage(
                threadId = threadId,
                turnId = turnId,
                itemId = itemId,
                text = "",
                attachments = listOf(attachment),
                assistantPhase = "final",
            )
        }
    }

    private fun shouldIgnoreFileChangeDelta(
        rendered: String,
        delta: String,
    ): Boolean {
        val candidate = listOf(rendered.trim(), delta.trim()).firstOrNull { it.isNotEmpty() } ?: return false
        if (candidate.contains("diff --git", ignoreCase = true)) return false
        if (candidate.contains("\nPath:", ignoreCase = true) || candidate.startsWith("Path:", ignoreCase = true)) return false
        if (candidate.contains("Totals:", ignoreCase = true)) return false
        if (candidate.contains("```diff", ignoreCase = true)) return false
        if (looksLikeTempPreviewError(candidate)) return true
        return candidate.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.all { line ->
            line.matches(Regex("""^[\w./\\:@\-]*[*?][\w./\\:@\-]*$"""))
        }
    }

    private fun looksLikeTempPreviewError(text: String): Boolean {
        val lower = text.lowercase()
        if (!(lower.contains("preview") || lower.contains("temp"))) return false
        return lower.contains("image preview") &&
            (
                lower.contains("timed out") ||
                    lower.contains("too long") ||
                    lower.contains("too large") ||
                    lower.contains("no longer exists") ||
                    lower.contains("not found") ||
                    lower.contains("could not be converted") ||
                    lower.contains("failed") ||
                    lower.contains("cannot")
            )
    }

    private fun handleToolCallOutputDelta(params: Map<String, JSONValue>?) {
        handleFileChangeDelta(params)
    }

    private fun handleCommandExecutionState(
        method: String,
        params: Map<String, JSONValue>?,
    ) {
        val p = params ?: return
        val event = envelopeEventObject(p)
        val state =
            CommandExecutionEventParser.parse(
                params = p,
                eventObject = event,
                method = method,
            )
        commandDetailsStore.upsertFromState(
            itemId = state.itemId,
            fullCommand = state.fullCommand,
            cwd = state.cwd,
            exitCode = state.exitCode,
            durationMs = state.durationMs,
        )

        val normalized = method.trim().lowercase()
        val isBegin = normalized.endsWith("exec_command_begin") || normalized.contains("commandexecution/started")
        val isEnd = normalized.endsWith("exec_command_end") || normalized.contains("commandexecution/completed")
        if (!isBegin && !isEnd) return

        val turnId = IncomingNotificationParsers.extractTurnId(p)
        val threadId = resolveThreadId(p) ?: return
        recordTurnThread(turnId, threadId)
        val itemId = state.itemId ?: IncomingNotificationParsers.extractItemId(p)
        val shortCommand =
            itemId?.let { id ->
                commandDetailsStore.detailsByItemId.value[id]?.fullCommand
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            } ?: state.fullCommand

        scope.launch {
            when {
                isBegin ->
                    messageTimeline.ensureStreamingSystemItem(
                        threadId = threadId,
                        turnId = turnId,
                        itemId = itemId,
                        kind = CodexMessageKind.commandExecution,
                        initialText =
                            commandExecutionTimelineLine(
                                phase = "running",
                                fullCommand = shortCommand,
                            ),
                    )
                isEnd ->
                    messageTimeline.completeSystemItem(
                        threadId = threadId,
                        turnId = turnId,
                        itemId = itemId,
                        kind = CodexMessageKind.commandExecution,
                        text =
                            commandExecutionTimelineLine(
                                phase = state.phase,
                                fullCommand = shortCommand,
                            ),
                    )
                else -> Unit
            }
        }
    }

    /**
     * Timeline text uses `phase> command` so [TurnTimelineRichContentParser.parseCommandExecution] keeps the
     * full invocation for [CommandHumanizer] (short previews used to truncate before `-Command`).
     */
    private fun commandExecutionTimelineLine(
        phase: String,
        fullCommand: String,
    ): String {
        val p =
            phase.trim().lowercase().ifBlank { "running" }.let { raw ->
                when (raw) {
                    "complete", "success", "succeeded" -> "completed"
                    "cancelled", "canceled" -> "stopped"
                    else -> raw
                }
            }
        val body =
            fullCommand.trim().ifBlank { "command" }.let { c ->
                val max = 8192
                if (c.length <= max) c
                else c.take(max - 1) + "…"
            }
        return "$p> $body"
    }

    private fun handleCommandExecutionDelta(
        method: String,
        params: Map<String, JSONValue>?,
    ) {
        val p = params ?: return
        val event = envelopeEventObject(p)
        val state =
            CommandExecutionEventParser.parse(
                params = p,
                eventObject = event,
                method = method,
            )
        commandDetailsStore.upsertFromState(
            itemId = state.itemId,
            fullCommand = state.fullCommand,
            cwd = state.cwd,
            exitCode = state.exitCode,
            durationMs = state.durationMs,
        )
        state.outputChunk?.let { commandDetailsStore.appendOutput(state.itemId, it) }

        val turnId = IncomingNotificationParsers.extractTurnId(p)
        val threadId = resolveThreadId(p) ?: return
        recordTurnThread(turnId, threadId)
        val itemId = state.itemId ?: IncomingNotificationParsers.extractItemId(p)
        scope.launch {
            if (itemId != null) {
                messageTimeline.ensureStreamingSystemItem(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = itemId,
                    kind = CodexMessageKind.commandExecution,
                    initialText =
                        commandExecutionTimelineLine(
                            phase = "running",
                            fullCommand = state.fullCommand,
                        ),
                )
                return@launch
            }

            val delta = IncomingNotificationParsers.extractTextDelta(p) ?: state.outputChunk ?: return@launch
            if (delta.isEmpty()) return@launch
            if (!turnId.isNullOrEmpty()) {
                messageTimeline.appendStreamingSystemItemDelta(
                    threadId,
                    turnId,
                    null,
                    CodexMessageKind.commandExecution,
                    delta,
                )
            } else {
                messageTimeline.appendSystemLine(
                    threadId,
                    null,
                    delta,
                    CodexMessageKind.commandExecution,
                )
            }
        }
    }

    private fun handleErrorNotification(params: Map<String, JSONValue>?) {
        val threadId = resolveThreadId(params) ?: return
        val turnId = IncomingNotificationParsers.extractTurnId(params)
        val text = IncomingNotificationParsers.extractErrorMessage(params) ?: return
        scope.launch {
            messageTimeline.appendSystemLine(threadId, turnId, "Error: $text")
        }
        onRunCompletionAttention(threadId, turnId, RunCompletionAttentionKind.Failed)
    }

    private fun upsertThread(
        list: List<CodexThread>,
        thread: CodexThread,
    ): List<CodexThread> {
        val i = list.indexOfFirst { it.id == thread.id }
        val merged =
            if (i >= 0) {
                val existing = list[i]
                val filled = thread.withMissingDisplayFieldsFrom(existing)
                list.mapIndexed { idx, t ->
                    if (idx == i) {
                        filled.copy(syncState = existing.syncState)
                    } else {
                        t
                    }
                }
            } else {
                list + thread
            }
        return sortThreadsForSidebar(merged)
    }

    private fun sortThreadsForSidebar(value: List<CodexThread>): List<CodexThread> {
        val past = java.time.Instant.EPOCH
        return value.sortedWith { lhs, rhs ->
            val l = lhs.updatedAt ?: lhs.createdAt ?: past
            val r = rhs.updatedAt ?: rhs.createdAt ?: past
            r.compareTo(l)
        }
    }

    private fun approvalDecisionResult(decision: String): JSONValue =
        JSONValue.Obj(mapOf("decision" to JSONValue.Str(decision)))

    private fun structuredUserInputResult(answersByQuestionId: Map<String, List<String>>): JSONValue =
        JSONValue.Obj(
            mapOf(
                "answers" to
                    JSONValue.Obj(
                        answersByQuestionId.mapValues { (_, answers) ->
                            JSONValue.Obj(
                                mapOf(
                                    "answers" to
                                        JSONValue.Arr(
                                            answers
                                                .map { it.trim() }
                                                .filter { it.isNotEmpty() }
                                                .map { JSONValue.Str(it) },
                                        ),
                                ),
                            )
                        },
                    ),
            ),
        )

    private suspend fun appendStructuredInputTimelineMarker(request: PendingStructuredInputRequest) {
        val threadId = request.threadId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        messageTimeline.appendStructuredInputPromptMarker(
            threadId = threadId,
            turnId = request.turnId?.trim()?.takeIf { it.isNotEmpty() },
            messageId = request.id,
            bodyText = StructuredInputTimelineFormatter.bodyText(request.questions),
        )
    }

    private suspend fun appendPendingApprovalTimelineMarker(request: PendingApprovalRequest) {
        val threadId = request.threadId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        messageTimeline.appendPendingApprovalMarker(
            threadId = threadId,
            turnId = request.turnId?.trim()?.takeIf { it.isNotEmpty() },
            itemId = request.itemId?.trim()?.takeIf { it.isNotEmpty() },
            messageId = request.id,
            bodyText =
                PendingApprovalTimelineFormatter.bodyText(
                    request.method,
                    request.command,
                    request.reason,
                ),
        )
    }

    private fun buildApprovalRequest(
        method: String,
        requestId: JSONValue,
        params: JSONValue?,
    ): PendingApprovalRequest {
        val obj = params?.objectValue
        return PendingApprovalRequest(
            id = requestKey(requestId),
            method = method,
            threadId = resolveThreadId(obj),
            turnId = IncomingNotificationParsers.extractTurnId(obj),
            itemId = IncomingNotificationParsers.extractItemId(obj),
            command = obj?.get("command")?.stringValue,
            reason = obj?.get("reason")?.stringValue,
        )
    }

    private fun buildStructuredInputRequest(
        requestId: JSONValue,
        params: JSONValue?,
    ): PendingStructuredInputRequest {
        val obj = params?.objectValue
        return PendingStructuredInputRequest(
            id = requestKey(requestId),
            threadId = resolveThreadId(obj),
            turnId = IncomingNotificationParsers.extractTurnId(obj),
            questions = parseStructuredInputQuestions(obj),
        )
    }

    private fun requestKey(requestId: JSONValue): String =
        JSONValue.toJsonElement(requestId).toString()

    private fun extractThreadId(params: Map<String, JSONValue>): String? {
        fun norm(s: String?) = CodexThread.normalizeIdentifier(s)
        norm(params["threadId"]?.stringValue)?.let { return it }
        norm(params["thread_id"]?.stringValue)?.let { return it }
        norm(params["conversationId"]?.stringValue)?.let { return it }
        norm(params["conversation_id"]?.stringValue)?.let { return it }
        norm(params["thread"]?.objectValue?.get("id")?.stringValue)?.let { return it }
        val event = envelopeEventObject(params) ?: return null
        norm(event["threadId"]?.stringValue)?.let { return it }
        norm(event["thread_id"]?.stringValue)?.let { return it }
        norm(event["thread"]?.objectValue?.get("id")?.stringValue)?.let { return it }
        return null
    }

    private fun decodePlanSteps(value: JSONValue?): List<CodexPlanStep> {
        val items = value?.arrayValue ?: return emptyList()
        return items.mapNotNull { item ->
            val objectValue = item.objectValue ?: return@mapNotNull null
            val step = firstString(objectValue, listOf("step")) ?: return@mapNotNull null
            val rawStatus = firstString(objectValue, listOf("status")) ?: return@mapNotNull null
            val normalizedStatus =
                rawStatus.lowercase().replace("_", "").replace("-", "")
            val status =
                when (normalizedStatus) {
                    "pending" -> CodexPlanStepStatus.pending
                    "inprogress" -> CodexPlanStepStatus.inProgress
                    "completed" -> CodexPlanStepStatus.completed
                    else -> null
                } ?: return@mapNotNull null
            CodexPlanStep(step = step, status = status)
        }
    }

    private fun envelopeEventObject(params: Map<String, JSONValue>): Map<String, JSONValue>? =
        params["msg"]?.objectValue ?: params["event"]?.objectValue

    private fun firstString(
        obj: Map<String, JSONValue>,
        keys: List<String>,
    ): String? {
        for (k in keys) {
            obj[k]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private fun hasAnyKey(
        obj: Map<String, JSONValue>?,
        keys: List<String>,
    ): Boolean {
        if (obj == null) return false
        return keys.any { obj.containsKey(it) }
    }

    private fun jsonObjectFromJsonValue(v: JSONValue): JsonObject? {
        if (v !is JSONValue.Obj) return null
        return buildJsonObject {
            v.map.forEach { (k, child) ->
                put(k, JSONValue.toJsonElement(child))
            }
        }
    }
}
