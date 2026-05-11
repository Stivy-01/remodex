package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexCollaborationModeKind
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexTurnMention
import com.remodex.mobile.core.model.CodexTurnSkillMention
import com.remodex.mobile.core.model.isExplicitServerThreadMissing
import com.remodex.mobile.core.model.CodexMessageDeliveryState
import com.remodex.mobile.core.model.CodexThreadSyncState
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.RPCMessage
import com.remodex.mobile.data.ThreadTurnInterruptSnapshot
import com.remodex.mobile.data.ThreadTurnSnapshot
import com.remodex.mobile.data.extractTurnIdFromRpcResult
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Invio turno utente via `turn/start` (parity con [CodexService.sendTurnStart] in
 * [CodexService+ThreadsTurns.swift](../../../../../../../../CodexMobile/CodexMobile/Services/CodexService+ThreadsTurns.swift)).
 * J.2/J.6/J.7a: testo + immagini; sandboxPolicy/sandbox/minimal + approvalPolicy candidates + effort key alias come iOS.
 */
internal suspend fun CodexService.startTurnInternal(
    threadId: String,
    userText: String,
    attachments: List<CodexImageAttachment> = emptyList(),
    skillMentions: List<CodexTurnSkillMention> = emptyList(),
    fileMentions: List<CodexTurnMention> = emptyList(),
    collaborationMode: CodexCollaborationModeKind? = null,
) {
    if (!sessionReady) throw CodexServiceError.Disconnected
    val tid = threadId.trim()
    val trimmed = userText.trim()
    val readyAttachments =
        attachments.filter { attachment ->
            !attachment.payloadDataURL.isNullOrBlank()
        }
    if (tid.isEmpty()) throw CodexServiceError.InvalidInput("Missing thread id")
    if (trimmed.isEmpty() && readyAttachments.isEmpty()) {
        throw CodexServiceError.InvalidInput("Message is empty")
    }

    var targetThreadId = tid
    var imageUrlKey = "url"

    suspend fun resumeTargetOrThrow() {
        val row = _threads.value.find { it.id == targetThreadId }
        try {
            ensureThreadResumedInternal(
                threadId = targetThreadId,
                force = false,
                preferredProjectPath = row?.gitWorkingDirectory,
                modelIdentifierOverride = row?.model,
            )
        } catch (e: Exception) {
            if (!shouldAllowProjectRebindWithoutResume(e)) throw e
        }
    }

    suspend fun continuationAfterExplicitMissing(archivedId: String) {
        val prior = _threads.value.find { it.id == archivedId }
        handleMissingThread(archivedId)
        val continuation =
            try {
                createContinuationThreadInternal(archivedId, prior)
            } catch (_: Throwable) {
                throw CodexServiceError.ThreadRemovedOnServer
            }
        targetThreadId = continuation.id
        try {
            ensureThreadResumedInternal(
                threadId = targetThreadId,
                force = false,
                preferredProjectPath = continuation.gitWorkingDirectory,
                modelIdentifierOverride = continuation.model,
            )
        } catch (e: CodexServiceError.RpcFailure) {
            if (e.rpcError.isExplicitServerThreadMissing()) {
                handleMissingThread(targetThreadId)
                throw CodexServiceError.ThreadRemovedOnServer
            }
            throw e
        }
    }

    try {
        resumeTargetOrThrow()
    } catch (e: CodexServiceError.RpcFailure) {
        if (!e.rpcError.isExplicitServerThreadMissing()) throw e
        continuationAfterExplicitMissing(targetThreadId)
    }

    val automaticTitleSeed = automaticThreadTitleSeedIfNeeded(trimmed, readyAttachments, targetThreadId)
    val pendingId = messageTimelineStore.appendPendingUserMessage(targetThreadId, trimmed, readyAttachments)

    suspend fun sendTurnStartWithImageFallback(threadId: String): RPCMessage {
        var effectiveCollaborationMode = collaborationMode?.takeIf { supportsTurnCollaborationMode }
        var includeStructuredSkillItems = supportsStructuredSkillInput && skillMentions.isNotEmpty()
        var includeStructuredMentionItems = supportsStructuredMentionInput && fileMentions.isNotEmpty()
        var includesServiceTier = shouldWireServiceTier(supportsServiceTier, _selectedServiceTier.value != null)
        var effortWireMode: TurnStartEffortWireMode? =
            selectedReasoningEffortForSelectedModel()?.let { TurnStartEffortWireMode.UseEffort }
        var didDowngradePlanModeForRuntime = false
        while (true) {
            val params =
                buildTurnStartRequestParams(
                    threadId = threadId,
                    userText = trimmed,
                    attachments = readyAttachments,
                    skillMentions = skillMentions,
                    fileMentions = fileMentions,
                    includeStructuredSkillItems = includeStructuredSkillItems,
                    includeStructuredMentionItems = includeStructuredMentionItems,
                    imageUrlKey = imageUrlKey,
                    collaborationMode = effectiveCollaborationMode,
                    includesServiceTier = includesServiceTier,
                    effortWireMode = effortWireMode,
                )
            try {
                val response = sendRequestWithSandboxAndApprovalFallback("turn/start", params)
                if (didDowngradePlanModeForRuntime) {
                    messageTimelineStore.appendSystemLine(
                        threadId = threadId,
                        turnId = null,
                        text = "Plan mode is not supported by this runtime. Sent as a normal turn instead.",
                    )
                }
                return response
            } catch (e: Throwable) {
                if (imageUrlKey == "url" &&
                    readyAttachments.isNotEmpty() &&
                    shouldRetryTurnStartWithImageURLField(e)
                ) {
                    imageUrlKey = "image_url"
                    continue
                }
                if (effectiveCollaborationMode != null &&
                    shouldRetryTurnStartWithoutCollaborationMode(e)
                ) {
                    supportsTurnCollaborationMode = false
                    effectiveCollaborationMode = null
                    didDowngradePlanModeForRuntime = true
                    continue
                }
                if (includeStructuredSkillItems && shouldRetryTurnStartWithoutSkillItems(e)) {
                    supportsStructuredSkillInput = false
                    includeStructuredSkillItems = false
                    continue
                }
                if (includeStructuredMentionItems && shouldRetryTurnStartWithoutMentionItems(e)) {
                    supportsStructuredMentionInput = false
                    includeStructuredMentionItems = false
                    continue
                }
                if (includesServiceTier && shouldRetryTurnStartWithoutServiceTier(e)) {
                    markServiceTierUnsupportedForCurrentBridge()
                    includesServiceTier = false
                    continue
                }
                if (effortWireMode == TurnStartEffortWireMode.UseEffort &&
                    shouldRetryTurnStartEffortKeyAlias(e, TurnStartEffortWireMode.UseEffort)
                ) {
                    effortWireMode = TurnStartEffortWireMode.UseReasoningEffort
                    continue
                }
                throw e
            }
        }
    }

    try {
        _activeThreadId.value = targetThreadId
        sessionPersistence.saveLastActiveThreadId(targetThreadId)
        noteProtectedRunningFallback(targetThreadId, true)
        val response = sendTurnStartWithImageFallback(targetThreadId)
        markTurnStartAccepted(targetThreadId, pendingId, response)
        scheduleAutomaticThreadTitleGenerationIfNeeded(automaticTitleSeed, targetThreadId, readyAttachments)
    } catch (e: Throwable) {
        noteTurnFinished(targetThreadId)
        if (e is CodexServiceError.RpcFailure && e.rpcError.isExplicitServerThreadMissing()) {
            continuationAfterExplicitMissing(targetThreadId)
            val newId = targetThreadId
            val retryAutomaticTitleSeed = automaticThreadTitleSeedIfNeeded(trimmed, readyAttachments, newId)
            val retryPendingId = messageTimelineStore.appendPendingUserMessage(newId, trimmed, readyAttachments)
            try {
                _activeThreadId.value = newId
                sessionPersistence.saveLastActiveThreadId(newId)
                noteProtectedRunningFallback(newId, true)
                val response = sendTurnStartWithImageFallback(newId)
                markTurnStartAccepted(newId, retryPendingId, response)
                scheduleAutomaticThreadTitleGenerationIfNeeded(retryAutomaticTitleSeed, newId, readyAttachments)
            } catch (e2: Throwable) {
                noteTurnFinished(newId)
                runCatching {
                    messageTimelineStore.markUserMessageOutcome(
                        threadId = newId,
                        messageId = retryPendingId,
                        deliveryState = CodexMessageDeliveryState.failed,
                        turnId = null,
                    )
                }
                if (e2 is CodexServiceError.RpcFailure && e2.rpcError.isExplicitServerThreadMissing()) {
                    handleMissingThread(newId)
                    throw CodexServiceError.ThreadRemovedOnServer
                }
                throw e2
            }
            return
        }
        runCatching {
            messageTimelineStore.markUserMessageOutcome(
                threadId = targetThreadId,
                messageId = pendingId,
                deliveryState = CodexMessageDeliveryState.failed,
                turnId = null,
            )
        }
        throw e
    }
}

private suspend fun CodexService.markTurnStartAccepted(
    threadId: String,
    pendingMessageId: String,
    response: RPCMessage,
) {
    val turnId = extractTurnIdFromRpcResult(response.result)
    messageTimelineStore.markUserMessageOutcome(
        threadId = threadId,
        messageId = pendingMessageId,
        deliveryState =
            if (turnId != null) {
                CodexMessageDeliveryState.confirmed
            } else {
                CodexMessageDeliveryState.pending
            },
        turnId = turnId,
    )
    if (turnId != null) {
        noteTurnStarted(threadId, turnId)
    }
    bumpThreadActivityAfterTurn(threadId)
}

private fun CodexService.bumpThreadActivityAfterTurn(threadId: String) {
    val list = _threads.value
    if (list.none { it.id == threadId }) return
    publishThreads(
        sortThreadsForBridge(
            list.map {
                if (it.id == threadId) {
                    it.copy(
                        updatedAt = Instant.now(),
                        syncState = CodexThreadSyncState.live,
                    )
                } else {
                    it
                }
            },
        ),
    )
}

suspend fun CodexService.startTurnForRepository(
    threadId: String,
    userText: String,
    attachments: List<CodexImageAttachment> = emptyList(),
    skillMentions: List<CodexTurnSkillMention> = emptyList(),
    fileMentions: List<CodexTurnMention> = emptyList(),
    collaborationMode: CodexCollaborationModeKind? = null,
) = withContext(Dispatchers.IO) {
    startTurnInternal(threadId, userText, attachments, skillMentions, fileMentions, collaborationMode)
}

private fun CodexService.buildTurnStartRequestParams(
    threadId: String,
    userText: String,
    attachments: List<CodexImageAttachment>,
    skillMentions: List<CodexTurnSkillMention>,
    fileMentions: List<CodexTurnMention>,
    includeStructuredSkillItems: Boolean,
    includeStructuredMentionItems: Boolean,
    imageUrlKey: String,
    collaborationMode: CodexCollaborationModeKind?,
    includesServiceTier: Boolean,
    effortWireMode: TurnStartEffortWireMode?,
): JSONValue.Obj {
    val params = linkedMapOf<String, JSONValue>()
    params["threadId"] = JSONValue.Str(threadId)
    params["input"] =
        JSONValue.Arr(
            makeTurnInputPayload(
                userText = userText,
                attachments = attachments,
                imageUrlKey = imageUrlKey,
                skillMentions = skillMentions,
                fileMentions = fileMentions,
                includeStructuredSkillItems = includeStructuredSkillItems,
                includeStructuredMentionItems = includeStructuredMentionItems,
            ),
        )

    val threadModel = runtimeModelIdentifierForTurn(threadId)
    if (threadModel != null) {
        params["model"] = JSONValue.Str(threadModel)
    }
    val reasoningEffort = selectedReasoningEffortForSelectedModel()
    when (effortWireMode) {
        TurnStartEffortWireMode.UseEffort ->
            reasoningEffort?.let { params["effort"] = JSONValue.Str(it) }
        TurnStartEffortWireMode.UseReasoningEffort ->
            reasoningEffort?.let { params["reasoningEffort"] = JSONValue.Str(it) }
        null -> Unit
    }
    if (includesServiceTier) {
        _selectedServiceTier.value?.let { params["serviceTier"] = JSONValue.Str(it.name) }
    }
    if (collaborationMode != null) {
        params["collaborationMode"] =
            buildCollaborationModePayload(
                collaborationMode,
                threadModel,
                selectedReasoningEffortForSelectedModel(),
            )
    }

    return JSONValue.Obj(params)
}

private fun buildCollaborationModePayload(
    mode: CodexCollaborationModeKind,
    threadModel: String?,
    reasoningEffort: String?,
): JSONValue.Obj {
    if (mode == CodexCollaborationModeKind.plan && threadModel.isNullOrBlank()) {
        throw CodexServiceError.InvalidInput("Plan mode requires an available model before starting a plan turn.")
    }

    val settings = linkedMapOf<String, JSONValue>()
    if (!threadModel.isNullOrBlank()) {
        settings["model"] = JSONValue.Str(threadModel)
    }
    settings["reasoning_effort"] = reasoningEffort?.let { JSONValue.Str(it) } ?: JSONValue.Null
    settings["developer_instructions"] = JSONValue.Null

    return JSONValue.Obj(
        mapOf(
            "mode" to JSONValue.Str(mode.name),
            "settings" to JSONValue.Obj(settings),
        ),
    )
}

internal fun makeTurnInputPayload(
    userText: String,
    attachments: List<CodexImageAttachment>,
    imageUrlKey: String,
    skillMentions: List<CodexTurnSkillMention> = emptyList(),
    fileMentions: List<CodexTurnMention> = emptyList(),
    includeStructuredSkillItems: Boolean = true,
    includeStructuredMentionItems: Boolean = true,
): List<JSONValue> {
    val inputItems = ArrayList<JSONValue>()
    attachments.forEach { attachment ->
        val payloadDataUrl = attachment.payloadDataURL?.trim().orEmpty()
        if (payloadDataUrl.isEmpty()) return@forEach
        inputItems +=
            JSONValue.Obj(
                mapOf(
                    "type" to JSONValue.Str("image"),
                    imageUrlKey to JSONValue.Str(payloadDataUrl),
                ),
            )
    }
    val trimmedText = userText.trim()
    if (trimmedText.isNotEmpty()) {
        inputItems +=
            JSONValue.Obj(
                mapOf(
                    "type" to JSONValue.Str("text"),
                    "text" to JSONValue.Str(trimmedText),
                ),
            )
    }
    if (includeStructuredSkillItems) {
        skillMentions.forEach { mention ->
            val id = mention.id.trim()
            if (id.isEmpty()) return@forEach
            val payload = linkedMapOf<String, JSONValue>("type" to JSONValue.Str("skill"), "id" to JSONValue.Str(id))
            mention.name?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["name"] = JSONValue.Str(it) }
            mention.path?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["path"] = JSONValue.Str(it) }
            inputItems += JSONValue.Obj(payload)
        }
    }
    if (includeStructuredMentionItems) {
        fileMentions.forEach { mention ->
            val name = mention.name.trim()
            val path = mention.path.trim()
            if (name.isEmpty() || path.isEmpty()) return@forEach
            inputItems +=
                JSONValue.Obj(
                    mapOf(
                        "type" to JSONValue.Str("mention"),
                        "name" to JSONValue.Str(name),
                        "path" to JSONValue.Str(path),
                    ),
                )
        }
    }
    return inputItems
}

internal fun shouldRetryTurnStartWithoutSkillItems(error: Throwable): Boolean {
    val rpcFailure = error as? CodexServiceError.RpcFailure ?: return false
    val code = rpcFailure.rpcError.code
    if (code != -32600 && code != -32602) return false
    val message = rpcFailure.rpcError.message.lowercase()
    if (!message.contains("skill")) return false
    return message.contains("unknown") ||
        message.contains("unsupported") ||
        message.contains("invalid") ||
        message.contains("unexpected") ||
        message.contains("unrecognized") ||
        message.contains("failed to parse") ||
        message.contains("missing field") ||
        message.contains("expected")
}

internal fun shouldRetryTurnStartWithoutMentionItems(error: Throwable): Boolean {
    val rpcFailure = error as? CodexServiceError.RpcFailure ?: return false
    val code = rpcFailure.rpcError.code
    if (code != -32600 && code != -32602) return false
    val message = rpcFailure.rpcError.message.lowercase()
    if (!message.contains("mention")) return false
    return message.contains("unknown") ||
        message.contains("unsupported") ||
        message.contains("invalid") ||
        message.contains("unexpected") ||
        message.contains("unrecognized") ||
        message.contains("failed to parse") ||
        message.contains("missing field") ||
        message.contains("expected")
}

private fun shouldRetryTurnStartWithImageURLField(error: Throwable): Boolean {
    val rpcFailure = error as? CodexServiceError.RpcFailure ?: return false
    val message = rpcFailure.rpcError.message.lowercase()
    if (!message.contains("image_url")) return false
    return message.contains("missing") ||
        message.contains("unknown field") ||
        message.contains("expected") ||
        message.contains("invalid")
}

private fun shouldRetryTurnStartWithoutCollaborationMode(error: Throwable): Boolean {
    val rpcFailure = error as? CodexServiceError.RpcFailure ?: return false
    val code = rpcFailure.rpcError.code
    if (code != -32600 && code != -32602) return false
    val message = rpcFailure.rpcError.message.lowercase()
    if (!message.contains("collaboration")) return false
    return message.contains("missing") ||
        message.contains("unknown field") ||
        message.contains("unexpected") ||
        message.contains("unrecognized") ||
        message.contains("unsupported") ||
        message.contains("invalid")
}

internal suspend fun CodexService.interruptTurnInternal(
    threadId: String,
    hintTurnId: String?,
) {
    if (!sessionReady) throw CodexServiceError.Disconnected
    val tid = threadId.trim()
    if (tid.isEmpty()) throw CodexServiceError.InvalidInput("Missing thread id")

    var turnId =
        hintTurnId?.trim()?.takeIf { it.isNotEmpty() }
            ?: _runningTurnIdByThread.value[tid]
    if (turnId == null) {
        val snap = resolveInFlightTurnSnapshotForInterrupt(tid)
        turnId = snap.interruptibleTurnId
        if (turnId == null) {
            if (snap.hasInterruptibleTurnWithoutId) {
                throw CodexServiceError.InvalidInput(
                    "The active run has not published an interruptible turn ID yet. Please try again in a moment.",
                )
            }
            throw CodexServiceError.InvalidInput("No active turn to interrupt")
        }
    }

    val resolvedTurnId = turnId
    try {
        sendInterruptRpc(resolvedTurnId, tid, snakeCase = false)
        return
    } catch (e: CodexServiceError.RpcFailure) {
        if (shouldRetryInterruptSnakeCase(e)) {
            try {
                sendInterruptRpc(resolvedTurnId, tid, snakeCase = true)
                return
            } catch (e2: CodexServiceError.RpcFailure) {
                if (shouldRetryInterruptRefreshTurn(e2)) {
                    tryRefreshAndInterrupt(tid, resolvedTurnId)
                    return
                }
                throw e2
            }
        }
        if (shouldRetryInterruptRefreshTurn(e)) {
            tryRefreshAndInterrupt(tid, resolvedTurnId)
            return
        }
        throw e
    }
}

private suspend fun CodexService.tryRefreshAndInterrupt(
    threadId: String,
    previousTurnId: String,
) {
    val snap = fetchThreadTurnInterruptSnapshotImpl(threadId)
    val refreshed =
        snap.interruptibleTurnId?.takeIf { it != previousTurnId }
            ?: throw CodexServiceError.InvalidInput("Could not resolve an interruptible turn id")
    try {
        sendInterruptRpc(refreshed, threadId, snakeCase = false)
    } catch (e: CodexServiceError.RpcFailure) {
        if (shouldRetryInterruptSnakeCase(e)) {
            sendInterruptRpc(refreshed, threadId, snakeCase = true)
        } else {
            throw e
        }
    }
    noteTurnStarted(threadId, refreshed)
}

private suspend fun CodexService.resolveInFlightTurnSnapshotForInterrupt(threadId: String): ThreadTurnInterruptSnapshot {
    val maxAttempts = 3
    var latest = fetchThreadTurnInterruptSnapshotImpl(threadId)
    repeat(maxAttempts - 1) {
        if (latest.interruptibleTurnId != null || !latest.hasInterruptibleTurnWithoutId) {
            return latest
        }
        delay(200)
        latest = fetchThreadTurnInterruptSnapshotImpl(threadId)
    }
    return latest
}

private suspend fun CodexService.sendInterruptRpc(
    turnId: String,
    threadId: String,
    snakeCase: Boolean,
) {
    val params =
        if (snakeCase) {
            mapOf(
                "turn_id" to JSONValue.Str(turnId),
                "thread_id" to JSONValue.Str(threadId),
            )
        } else {
            mapOf(
                "turnId" to JSONValue.Str(turnId),
                "threadId" to JSONValue.Str(threadId),
            )
        }
    sendRequestImpl("turn/interrupt", JSONValue.Obj(params))
}

internal suspend fun CodexService.fetchThreadTurnInterruptSnapshotImpl(threadId: String): ThreadTurnInterruptSnapshot {
    val camel =
        JSONValue.Obj(
            mapOf(
                "threadId" to JSONValue.Str(threadId),
                "includeTurns" to JSONValue.Bool(true),
            ),
        )
    val response =
        try {
            sendRequestImpl("thread/read", camel)
        } catch (e: CodexServiceError.RpcFailure) {
            if (shouldRetryThreadReadSnakeCase(e)) {
                sendRequestImpl(
                    "thread/read",
                    JSONValue.Obj(
                        mapOf(
                            "thread_id" to JSONValue.Str(threadId),
                            "include_turns" to JSONValue.Bool(true),
                        ),
                    ),
                )
            } else {
                throw e
            }
        }
    val threadEl = (response.result as? JSONValue.Obj)?.map?.get("thread") as? JSONValue.Obj
    return ThreadTurnSnapshot.fromThreadObject(threadEl?.map ?: emptyMap())
}

private fun shouldRetryThreadReadSnakeCase(e: CodexServiceError.RpcFailure): Boolean {
    val c = e.rpcError.code
    if (c != -32600 && c != -32602) return false
    val m = e.rpcError.message.lowercase()
    val hints =
        listOf("threadid", "includeturns", "thread_id", "include_turns", "unknown field", "missing field", "invalid")
    return hints.any { m.contains(it) }
}

private fun shouldRetryInterruptSnakeCase(e: CodexServiceError.RpcFailure): Boolean {
    val c = e.rpcError.code
    if (c != -32600 && c != -32602) return false
    val m = e.rpcError.message.lowercase()
    val hints = listOf("turnid", "threadid", "turn_id", "thread_id", "unknown field", "missing field", "invalid")
    return hints.any { m.contains(it) }
}

private fun shouldRetryInterruptRefreshTurn(e: CodexServiceError.RpcFailure): Boolean {
    val m = e.rpcError.message.lowercase()
    val hints =
        listOf(
            "turn not found",
            "no active turn",
            "not in progress",
            "not running",
            "already completed",
            "already finished",
            "invalid turn",
            "no such turn",
            "not active",
            "does not exist",
            "cannot interrupt",
        )
    return hints.any { m.contains(it) }
}

suspend fun CodexService.interruptTurnForRepository(
    threadId: String,
    turnId: String?,
) = withContext(Dispatchers.IO) {
    interruptTurnInternal(threadId, turnId)
}
