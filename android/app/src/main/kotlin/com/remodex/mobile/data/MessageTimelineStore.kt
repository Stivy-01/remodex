package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageDeliveryState
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageOrderCounter
import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.core.model.CodexPlanState
import com.remodex.mobile.core.model.CodexPlanStep
import com.remodex.mobile.core.model.CodexSubagentAction
import com.remodex.mobile.core.model.CodexSubagentRef
import com.remodex.mobile.core.model.CodexSubagentState
import com.remodex.mobile.core.persistence.CodexMessagePersistence
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory timeline + encrypted persistence (iOS [messagesByThread] + persistMessages).
 */
internal class MessageTimelineStore(
    initialMessages: Map<String, List<CodexMessage>>,
    private val saveMessages: (Map<String, List<CodexMessage>>) -> Unit,
) {
    private data class SubagentIdentityEntry(
        val threadId: String? = null,
        val agentId: String? = null,
        val nickname: String? = null,
        val role: String? = null,
    ) {
        val hasMetadata: Boolean
            get() = threadId != null || agentId != null || nickname != null || role != null
    }

    private val mutex = Mutex()
    private val subagentIdentityByThreadId = mutableMapOf<String, SubagentIdentityEntry>()
    private val subagentIdentityByAgentId = mutableMapOf<String, SubagentIdentityEntry>()

    private val _messagesByThread = MutableStateFlow<Map<String, List<CodexMessage>>>(emptyMap())
    val messagesByThread: StateFlow<Map<String, List<CodexMessage>>> = _messagesByThread.asStateFlow()

    init {
        val normalizedInitialMessages =
            initialMessages.mapValues { (_, messages) ->
                HistoryMessageMerge.normalize(messages)
            }
        CodexMessageOrderCounter.seedFrom(normalizedInitialMessages)
        rebuildSubagentIdentityDirectory(normalizedInitialMessages.values.flatten())
        _messagesByThread.value = normalizedInitialMessages.mapValues { (_, messages) ->
            messages.map(::resolveSubagentMessageIdentities)
        }
    }

    constructor(
        persistence: CodexMessagePersistence,
        lastActiveThreadId: String? = null,
        initialTailLimit: Int = DEFAULT_INITIAL_TAIL_LIMIT,
    ) : this(
        initialMessages = persistence.loadInitialThreadTail(lastActiveThreadId, initialTailLimit),
        saveMessages = { map -> persistence.save(map) },
    )

    internal constructor(
        initialMessages: Map<String, List<CodexMessage>> = emptyMap(),
    ) : this(
        initialMessages = initialMessages,
        saveMessages = {},
    )

    private fun publishMessages(map: Map<String, List<CodexMessage>>) {
        _messagesByThread.value = map
        saveMessages(map)
    }

    private fun publishResolvedMessages(map: MutableMap<String, List<CodexMessage>>) {
        publishMessages(
            map.mapValues { (_, messages) ->
                messages.map(::resolveSubagentMessageIdentities)
            },
        )
    }

    suspend fun mergeThreadHistory(
        threadId: String,
        incoming: List<CodexMessage>,
    ) {
        if (incoming.isEmpty()) return
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val existing = map[threadId].orEmpty()
            rebuildSubagentIdentityDirectory(existing + incoming)
            map[threadId] =
                HistoryMessageMerge.merge(existing, incoming)
                    .map(::resolveSubagentMessageIdentities)
            publishMessages(map)
        }
    }

    /** Server reports thread gone — drop persisted rows (parity iOS `removeThreadTimelineState` + prune messages). */
    suspend fun removeThreadMessages(threadId: String) {
        val tid = threadId.trim()
        if (tid.isEmpty()) return
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            if (map.remove(tid) == null) return@withLock
            publishMessages(map)
        }
    }

    suspend fun appendThinkingDelta(
        threadId: String,
        turnId: String,
        itemId: String?,
        delta: String,
    ) {
        appendStreamingSystemItemDelta(
            threadId = threadId,
            turnId = turnId,
            itemId = itemId,
            kind = CodexMessageKind.thinking,
            delta = delta,
        )
    }

    /**
     * Delta streaming su righe system (thinking, file change, plan, output comando) — parity iOS `appendStreamingSystemItemDelta`.
     */
    suspend fun appendStreamingSystemItemDelta(
        threadId: String,
        turnId: String?,
        itemId: String?,
        kind: CodexMessageKind,
        delta: String,
    ) {
        if (delta.isEmpty()) return
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val fileChangePathKeys =
                if (kind == CodexMessageKind.fileChange) normalizedFileChangePathKeys(delta) else emptySet()
            val idx =
                findStreamingSystemItemIndex(list, kind, turnId, itemId, fileChangePathKeys)
            if (idx >= 0) {
                val m = list[idx]
                list[idx] =
                    m.copy(
                        text = m.text + delta,
                        isStreaming = true,
                        turnId = turnId ?: m.turnId,
                        itemId = itemId ?: m.itemId,
                    )
                pruneDuplicateFileChangeRows(list, idx, turnId, fileChangePathKeys, false)
            } else {
                list.add(
                    CodexMessage(
                        threadId = threadId,
                        role = CodexMessageRole.system,
                        kind = kind,
                        text = delta,
                        createdAt = Instant.now(),
                        turnId = turnId,
                        itemId = itemId,
                        isStreaming = true,
                    ),
                )
            }
            map[threadId] = list
            publishMessages(map)
        }
    }

    /**
     * Like [appendStreamingSystemItemDelta], but treats [snapshot] as an authoritative replacement when it looks like
     * structured file-change payload (Path/Kind/Totals or fenced diff). This matches iOS behavior where file-change
     * completed payloads overwrite streaming placeholders.
     */
    suspend fun upsertStreamingSystemItemSnapshot(
        threadId: String,
        turnId: String?,
        itemId: String?,
        kind: CodexMessageKind,
        snapshot: String,
    ) {
        val incoming = snapshot.trim()
        if (incoming.isEmpty()) return
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val fileChangePathKeys =
                if (kind == CodexMessageKind.fileChange) normalizedFileChangePathKeys(incoming) else emptySet()
            val idx =
                findStreamingSystemItemIndex(
                    list = list,
                    kind = kind,
                    turnId = turnId,
                    itemId = itemId,
                    fileChangePathKeys = fileChangePathKeys,
                    allowCompletedFileChange = true,
                )
            if (idx >= 0) {
                val m = list[idx]
                val existing = m.text.trim()
                val shouldReplace =
                    existing.isEmpty() ||
                        existing.equals("[file change]", ignoreCase = true) ||
                        existing.equals("file change", ignoreCase = true) ||
                        existing.equals("applying file changes...", ignoreCase = true) ||
                        isStructuredFileChangeSnapshot(incoming)
                if (shouldReplace) {
                    list[idx] =
                        m.copy(
                            text = incoming,
                            isStreaming = true,
                            turnId = turnId ?: m.turnId,
                            itemId = itemId ?: m.itemId,
                        )
                    pruneDuplicateFileChangeRows(
                        list,
                        idx,
                        turnId,
                        fileChangePathKeys,
                        isStructuredFileChangeSnapshot(incoming),
                    )
                }
            } else {
                list.add(
                    CodexMessage(
                        threadId = threadId,
                        role = CodexMessageRole.system,
                        kind = kind,
                        text = incoming,
                        createdAt = Instant.now(),
                        turnId = turnId,
                        itemId = itemId,
                        isStreaming = true,
                    ),
                )
            }
            map[threadId] = list
            publishMessages(map)
        }
    }

    private fun isStructuredFileChangeSnapshot(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.contains("\nPath:", ignoreCase = true) || t.startsWith("Path:", ignoreCase = true)) return true
        if (t.contains("\nTotals:", ignoreCase = true) || t.startsWith("Totals:", ignoreCase = true)) return true
        if (t.contains("```diff", ignoreCase = true)) return true
        if (t.contains("diff --git")) return true
        return false
    }

    private fun isFileChangePlaceholder(text: String): Boolean =
        text.trim().equals("[file change]", ignoreCase = true) ||
            text.trim().equals("file change", ignoreCase = true)

    /**
     * Ensures a streaming system item exists for the given identity, without appending output deltas into its `text`.
     * This is used for command execution previews where the live output is stored separately.
     */
    suspend fun ensureStreamingSystemItem(
        threadId: String,
        turnId: String?,
        itemId: String?,
        kind: CodexMessageKind,
        initialText: String,
    ) {
        val seed = initialText.trim()
        if (seed.isEmpty()) return
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val existing =
                list.any { m ->
                    m.role == CodexMessageRole.system &&
                        m.kind == kind &&
                        m.isStreaming &&
                        ((itemId != null && m.itemId == itemId) ||
                            (itemId == null && turnId != null && m.turnId == turnId))
                }
            if (!existing) {
                list.add(
                    CodexMessage(
                        threadId = threadId,
                        role = CodexMessageRole.system,
                        kind = kind,
                        text = seed,
                        createdAt = Instant.now(),
                        turnId = turnId,
                        itemId = itemId,
                        isStreaming = true,
                    ),
                )
                map[threadId] = list
                publishMessages(map)
            }
        }
    }

    /** Reasoning in arrivo dopo fine turno: merge nella riga thinking esistente (parity iOS `mergeLateReasoningDeltaIfPossible`). */
    suspend fun mergeLateReasoningDelta(
        threadId: String,
        turnId: String?,
        itemId: String?,
        delta: String,
    ): Boolean {
        if (delta.trim().isEmpty()) return false
        return mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val idx =
                list.indexOfLast { m ->
                    m.role == CodexMessageRole.system &&
                        m.kind == CodexMessageKind.thinking &&
                        ((itemId != null && m.itemId == itemId) ||
                            (itemId == null && turnId != null && m.turnId == turnId))
                }
            if (idx < 0) {
                return@withLock false
            }
            val m = list[idx]
            list[idx] =
                m.copy(
                    text = mergeSnapshot(m.text, delta),
                    isStreaming = false,
                    turnId = turnId ?: m.turnId,
                    itemId = itemId ?: m.itemId,
            )
            map[threadId] = list
            publishMessages(map)
            true
        }
    }

    suspend fun completeSystemItem(
        threadId: String,
        turnId: String?,
        itemId: String?,
        kind: CodexMessageKind,
        text: String,
    ) {
        val finalText = text.trim()
        if (finalText.isEmpty()) return
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val fileChangePathKeys =
                if (kind == CodexMessageKind.fileChange) normalizedFileChangePathKeys(finalText) else emptySet()
            val idx =
                findCompletedSystemItemIndex(
                    list = list,
                    kind = kind,
                    turnId = turnId,
                    itemId = itemId,
                    finalText = finalText,
                    fileChangePathKeys = fileChangePathKeys,
                    isAuthoritativeFileChangeSnapshot = isStructuredFileChangeSnapshot(finalText),
                )
            if (idx >= 0) {
                val m = list[idx]
                val merged =
                    when (kind) {
                        CodexMessageKind.commandExecution -> finalText
                        CodexMessageKind.fileChange ->
                            if (isFileChangePlaceholder(finalText) && isStructuredFileChangeSnapshot(m.text)) {
                                m.text
                            } else if (isFileChangePlaceholder(m.text) && !isFileChangePlaceholder(finalText)) {
                                finalText
                            } else if (m.text.isEmpty()) {
                                finalText
                            } else {
                                mergeSnapshot(m.text, finalText)
                            }
                        else ->
                            if (m.text.isEmpty()) {
                                finalText
                            } else {
                                mergeSnapshot(m.text, finalText)
                            }
                    }
                list[idx] =
                    m.copy(
                        text = merged,
                        isStreaming = false,
                        turnId = turnId ?: m.turnId,
                        itemId = itemId ?: m.itemId,
                    )
                pruneDuplicateFileChangeRows(
                    list,
                    idx,
                    turnId,
                    fileChangePathKeys,
                    isStructuredFileChangeSnapshot(finalText),
                )
                pruneDuplicateCommandExecutionRows(list, idx, turnId, finalText)
            } else {
                list.add(
                    CodexMessage(
                        threadId = threadId,
                        role = CodexMessageRole.system,
                        kind = kind,
                        text = finalText,
                        createdAt = Instant.now(),
                        turnId = turnId,
                        itemId = itemId,
                        isStreaming = false,
                    ),
                )
                pruneDuplicateCommandExecutionRows(list, list.lastIndex, turnId, finalText)
            }
            map[threadId] = list
            publishMessages(map)
        }
    }

    suspend fun upsertPlanMessage(
        threadId: String,
        turnId: String?,
        itemId: String?,
        text: String? = null,
        explanation: String? = null,
        steps: List<CodexPlanStep>? = null,
        isStreaming: Boolean,
    ) {
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val idx =
                list.indexOfLast { message ->
                    message.role == CodexMessageRole.system &&
                        message.kind == CodexMessageKind.plan &&
                        (
                            (itemId != null && message.itemId == itemId) ||
                                (itemId == null && turnId != null && message.turnId == turnId)
                        )
                }

            if (idx >= 0) {
                val current = list[idx]
                val mergedText =
                    text?.trim()?.takeIf { it.isNotEmpty() }?.let { incoming ->
                        if (current.text.isEmpty()) {
                            incoming
                        } else {
                            mergeSnapshot(current.text, incoming)
                        }
                    } ?: current.text
                val currentState = current.planState ?: CodexPlanState()
                val nextState =
                    currentState.copy(
                        explanation =
                            explanation?.trim()?.takeIf { it.isNotEmpty() }
                                ?: currentState.explanation,
                        steps = steps ?: currentState.steps,
                    )
                list[idx] =
                    current.copy(
                        text = mergedText,
                        isStreaming = isStreaming,
                        turnId = turnId ?: current.turnId,
                        itemId = itemId ?: current.itemId,
                        planState =
                            if (nextState.explanation != null || nextState.steps.isNotEmpty()) {
                                nextState
                            } else {
                                null
                            },
                    )
            } else {
                val trimmedText = text?.trim()?.takeIf { it.isNotEmpty() } ?: "Planning..."
                val nextState =
                    CodexPlanState(
                        explanation = explanation?.trim()?.takeIf { it.isNotEmpty() },
                        steps = steps ?: emptyList(),
                    )
                list.add(
                    CodexMessage(
                        threadId = threadId,
                        role = CodexMessageRole.system,
                        kind = CodexMessageKind.plan,
                        text = trimmedText,
                        createdAt = Instant.now(),
                        turnId = turnId,
                        itemId = itemId,
                        isStreaming = isStreaming,
                        planState =
                            if (nextState.explanation != null || nextState.steps.isNotEmpty()) {
                                nextState
                            } else {
                                null
                            },
                    ),
                )
            }

            map[threadId] = list
            publishMessages(map)
        }
    }

    suspend fun upsertSubagentActionMessage(
        threadId: String,
        turnId: String?,
        itemId: String?,
        action: CodexSubagentAction,
        isStreaming: Boolean,
    ) {
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val summaryRefs = subagentSummaryRefsForTurn(list, turnId)
            summaryRefs.forEach { ref ->
                upsertSubagentIdentity(ref.threadId, ref.agentId, ref.nickname, ref.role)
            }
            val incomingAction = resolveSubagentActionIdentities(enrichSubagentAction(action, summaryRefs))
            removeAssistantSubagentSummaries(list, turnId)
            val idx =
                list.indexOfLast { message ->
                    message.role == CodexMessageRole.system &&
                        message.kind == CodexMessageKind.subagentAction &&
                        (
                            (itemId != null && message.itemId == itemId) ||
                                (itemId == null && turnId != null && message.turnId == turnId &&
                                    message.subagentAction?.normalizedTool == incomingAction.normalizedTool)
                        )
                }

            if (idx >= 0) {
                val current = list[idx]
                val mergedAction = resolveSubagentActionIdentities(mergeSubagentActions(current.subagentAction, incomingAction))
                list[idx] =
                    current.copy(
                        text = mergedAction.summaryText,
                        isStreaming = isStreaming,
                        turnId = turnId ?: current.turnId,
                        itemId = itemId ?: current.itemId,
                        subagentAction = mergedAction,
                    )
            } else {
                list.add(
                    CodexMessage(
                        threadId = threadId,
                        role = CodexMessageRole.system,
                        kind = CodexMessageKind.subagentAction,
                        text = incomingAction.summaryText,
                        createdAt = Instant.now(),
                        turnId = turnId,
                        itemId = itemId,
                        isStreaming = isStreaming,
                        subagentAction = incomingAction,
                    ),
                )
            }
            map[threadId] = list
            publishMessages(map)
        }
    }

    suspend fun appendAssistantDelta(
        threadId: String,
        turnId: String,
        itemId: String?,
        delta: String,
        assistantPhase: String? = null,
    ) {
        if (delta.isEmpty()) return
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val idx =
                list.indexOfLast { m ->
                    m.role == CodexMessageRole.assistant &&
                        m.kind == CodexMessageKind.chat &&
                        m.isStreaming &&
                        (itemId != null && m.itemId == itemId ||
                            (itemId == null && m.turnId == turnId))
            }
            if (idx >= 0) {
                val m = list[idx]
                list[idx] =
                    m.copy(
                        text = m.text + delta,
                        assistantPhase = assistantPhase ?: m.assistantPhase,
                        isStreaming = true,
                        turnId = turnId,
                        itemId = itemId ?: m.itemId,
                    )
            } else {
                list.add(
                    CodexMessage(
                        threadId = threadId,
                        role = CodexMessageRole.assistant,
                        kind = CodexMessageKind.chat,
                        assistantPhase = assistantPhase,
                        text = delta,
                        createdAt = Instant.now(),
                        turnId = turnId,
                        itemId = itemId,
                        isStreaming = true,
                    ),
                )
            }
            map[threadId] = list
            publishMessages(map)
        }
    }

    suspend fun ensureStreamingAssistantPlaceholder(
        threadId: String,
        turnId: String?,
    ) {
        val resolvedTurnId = turnId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val existing =
                list.any { message ->
                    message.role == CodexMessageRole.assistant &&
                        message.kind == CodexMessageKind.chat &&
                        message.turnId == resolvedTurnId &&
                        message.isStreaming
                }
            if (!existing) {
                list.add(
                    CodexMessage(
                        threadId = threadId,
                        role = CodexMessageRole.assistant,
                        kind = CodexMessageKind.chat,
                        text = "",
                        createdAt = Instant.now(),
                        turnId = resolvedTurnId,
                        isStreaming = true,
                    ),
                )
                map[threadId] = list
                publishMessages(map)
            }
        }
    }

    suspend fun completeAssistantMessage(
        threadId: String,
        turnId: String?,
        itemId: String?,
        text: String,
        attachments: List<CodexImageAttachment> = emptyList(),
        assistantPhase: String? = null,
    ) {
        val finalText = text.trim()
        if (finalText.isEmpty() && attachments.isEmpty()) return
        mutex.withLock {
            val resolvedTurnId = turnId?.trim()?.takeIf { it.isNotEmpty() }
            val resolvedItemId = itemId?.trim()?.takeIf { it.isNotEmpty() }
            val normalizedFinalText = normalizedMessageText(finalText)
            val now = Instant.now()

            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            if (absorbAssistantSubagentSummary(list, resolvedTurnId, finalText)) {
                map[threadId] = list
                publishMessages(map)
                return@withLock
            }
            val idx =
                list.indexOfLast { m ->
                    m.role == CodexMessageRole.assistant &&
                        m.kind == CodexMessageKind.chat &&
                        matchesCompletedMessageCandidate(
                            candidate = m,
                            turnId = resolvedTurnId,
                            itemId = resolvedItemId,
                        )
                }
            if (idx >= 0) {
                val m = list[idx]
                list[idx] =
                    m.copy(
                        text = if (m.text.isEmpty()) finalText else mergeSnapshot(m.text, finalText),
                        assistantPhase = assistantPhase ?: m.assistantPhase,
                        isStreaming = false,
                        turnId = resolvedTurnId ?: m.turnId,
                        itemId = resolvedItemId ?: m.itemId,
                        attachments = if (attachments.isNotEmpty()) attachments else m.attachments,
                    )
            } else {
                val duplicateIdx =
                    list.indexOfLast { candidate ->
                        candidate.role == CodexMessageRole.assistant &&
                            candidate.kind == CodexMessageKind.chat &&
                            normalizedMessageText(candidate.text) == normalizedFinalText &&
                            (
                                candidate.isStreaming ||
                                    (resolvedTurnId != null && candidate.turnId == resolvedTurnId) ||
                                    (resolvedItemId != null && candidate.itemId == resolvedItemId)
                            )
                    }
                if (duplicateIdx >= 0) {
                    val candidate = list[duplicateIdx]
                    list[duplicateIdx] =
                        candidate.copy(
                            text = if (candidate.text.isEmpty()) finalText else mergeSnapshot(candidate.text, finalText),
                            assistantPhase = assistantPhase ?: candidate.assistantPhase,
                            isStreaming = false,
                            turnId = resolvedTurnId ?: candidate.turnId,
                            itemId = resolvedItemId ?: candidate.itemId,
                            attachments = if (attachments.isNotEmpty()) attachments else candidate.attachments,
                        )
                } else {
                    list.add(
                        CodexMessage(
                            threadId = threadId,
                            role = CodexMessageRole.assistant,
                            kind = CodexMessageKind.chat,
                            assistantPhase = assistantPhase,
                            text = finalText,
                            createdAt = now,
                            turnId = resolvedTurnId,
                            itemId = resolvedItemId,
                            isStreaming = false,
                            attachments = attachments,
                        ),
                    )
                }
            }
            map[threadId] = list
            publishMessages(map)
        }
    }

    suspend fun appendSystemLine(
        threadId: String,
        turnId: String?,
        text: String,
        kind: CodexMessageKind = CodexMessageKind.chat,
    ) {
        val t = text.trim()
        if (t.isEmpty()) return
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            list.add(
                CodexMessage(
                    threadId = threadId,
                    role = CodexMessageRole.system,
                    kind = kind,
                    text = t,
                    createdAt = Instant.now(),
                    turnId = turnId,
                    isStreaming = false,
                ),
            )
            map[threadId] = list
            publishMessages(map)
        }
    }

    /**
     * Item-scoped marker when the shell shows the global approval dialog (J.7b). Non-interactive here;
     * [messageId] matches [PendingApprovalRequest.id] for future removal/updates.
     */
    suspend fun appendPendingApprovalMarker(
        threadId: String,
        turnId: String?,
        itemId: String?,
        messageId: String,
        bodyText: String,
    ) {
        val t = bodyText.trim()
        if (t.isEmpty()) return
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val id = messageId.trim().ifEmpty { UUID.randomUUID().toString() }
            if (list.any { it.id == id }) return@withLock
            list.add(
                CodexMessage(
                    id = id,
                    threadId = threadId,
                    role = CodexMessageRole.system,
                    kind = CodexMessageKind.pendingApproval,
                    text = t,
                    createdAt = Instant.now(),
                    turnId = turnId,
                    itemId = itemId,
                    isStreaming = false,
                ),
            )
            map[threadId] = list
            publishMessages(map)
        }
    }

    /**
     * Item-scoped marker when the shell shows the structured input dialog (J.7b). Non-interactive;
     * [messageId] matches [PendingStructuredInputRequest.id] so it can be removed after response.
     * Uses [CodexMessageKind.userInputPrompt]; not persisted ([CodexMessagePersistence] filter).
     */
    suspend fun appendStructuredInputPromptMarker(
        threadId: String,
        turnId: String?,
        messageId: String,
        bodyText: String,
    ) {
        val t = bodyText.trim()
        if (t.isEmpty()) return
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val id = messageId.trim().ifEmpty { UUID.randomUUID().toString() }
            if (list.any { it.id == id }) return@withLock
            list.add(
                CodexMessage(
                    id = id,
                    threadId = threadId,
                    role = CodexMessageRole.system,
                    kind = CodexMessageKind.userInputPrompt,
                    text = t,
                    createdAt = Instant.now(),
                    turnId = turnId,
                    isStreaming = false,
                ),
            )
            map[threadId] = list
            publishMessages(map)
        }
    }

    /**
     * Removes ephemeral server-request timeline rows keyed by RPC id ([PendingApprovalRequest.id],
     * [PendingStructuredInputRequest.id]) across threads once the shell completes the RPC.
     */
    suspend fun removeEphemeralPendingServerMarker(messageId: String) {
        val mid = messageId.trim()
        if (mid.isEmpty()) return
        mutex.withLock {
            val ephemeralKinds =
                setOf(CodexMessageKind.pendingApproval, CodexMessageKind.userInputPrompt)
            val map = _messagesByThread.value.toMutableMap()
            var changed = false
            for ((tid, list) in map.entries.toList()) {
                val newList =
                    list.filterNot { it.id == mid && ephemeralKinds.contains(it.kind) }
                if (newList.size != list.size) {
                    map[tid] = newList
                    changed = true
                }
            }
            if (changed) {
                publishMessages(map)
            }
        }
    }

    suspend fun appendPendingUserMessage(
        threadId: String,
        text: String,
        attachments: List<CodexImageAttachment> = emptyList(),
    ): String {
        val trimmed = text.trim()
        val id = UUID.randomUUID().toString()
        if (trimmed.isEmpty() && attachments.isEmpty()) return id
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            list.add(
                CodexMessage(
                    id = id,
                    threadId = threadId,
                    role = CodexMessageRole.user,
                    kind = CodexMessageKind.chat,
                    text = trimmed,
                    createdAt = Instant.now(),
                    deliveryState = CodexMessageDeliveryState.pending,
                    isStreaming = false,
                    attachments = attachments,
                ),
            )
            map[threadId] = list
            publishMessages(map)
        }
        return id
    }

    suspend fun markUserMessageOutcome(
        threadId: String,
        messageId: String,
        deliveryState: CodexMessageDeliveryState,
        turnId: String?,
    ) {
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val idx = list.indexOfFirst { it.id == messageId }
            if (idx >= 0) {
                val m = list[idx]
                list[idx] =
                    m.copy(
                        deliveryState = deliveryState,
                        turnId = turnId ?: m.turnId,
                    )
            }
            map[threadId] = list
            publishMessages(map)
        }
    }

    suspend fun appendMirroredUser(
        threadId: String,
        turnId: String?,
        text: String,
        attachments: List<CodexImageAttachment> = emptyList(),
    ) {
        val t = text.trim()
        val normalizedText = normalizedMessageText(t)
        if (t.isEmpty() && attachments.isEmpty()) return
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val existingIdx =
                list.indexOfLast { m ->
                    m.role == CodexMessageRole.user &&
                        normalizedMessageText(m.text) == normalizedText &&
                        compatibleUserAttachments(m.attachments, attachments) &&
                        (
                            (turnId != null && (m.turnId == null || m.turnId == turnId)) ||
                                (turnId == null && m.turnId == null)
                        )
                }
            if (existingIdx >= 0) {
                val existing = list[existingIdx]
                val next =
                    existing.copy(
                        deliveryState = CodexMessageDeliveryState.confirmed,
                        turnId = turnId ?: existing.turnId,
                        attachments = UserChatAttachmentMatcher.merge(existing.attachments, attachments),
                    )
                if (next != existing) {
                    list[existingIdx] = next
                }
            } else {
                val insertionIndex =
                    turnId
                        ?.let { resolvedTurnId ->
                            list.indexOfFirst { message ->
                                message.turnId == resolvedTurnId && message.role != CodexMessageRole.user
                            }.takeIf { it >= 0 }
                        }
                        ?: list.size
                list.add(
                    insertionIndex,
                    CodexMessage(
                        threadId = threadId,
                        role = CodexMessageRole.user,
                        kind = CodexMessageKind.chat,
                        text = t,
                        createdAt = Instant.now(),
                        turnId = turnId,
                        deliveryState = CodexMessageDeliveryState.confirmed,
                        isStreaming = false,
                        attachments = attachments,
                    ),
                )
                if (insertionIndex < list.lastIndex) {
                    reassignOrderIndexesInCurrentOrder(list)
                }
            }
            map[threadId] = list
            publishMessages(map)
        }
    }

    suspend fun attachLatestTurnlessUserMessageToTurn(
        threadId: String,
        turnId: String,
    ) {
        val resolvedTurnId = turnId.trim().takeIf { it.isNotEmpty() } ?: return
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val idx =
                list.indices.reversed().firstOrNull { index ->
                    val candidate = list[index]
                    candidate.role == CodexMessageRole.user &&
                        candidate.kind == CodexMessageKind.chat &&
                        candidate.turnId == null &&
                        candidate.deliveryState == CodexMessageDeliveryState.confirmed
                } ?: return@withLock
            list[idx] = list[idx].copy(turnId = resolvedTurnId)
            map[threadId] = list
            publishMessages(map)
        }
    }

    suspend fun confirmLatestPendingUserMessage(
        threadId: String,
        turnId: String,
    ) {
        val resolvedTurnId = turnId.trim()
        if (resolvedTurnId.isEmpty()) return
        mutex.withLock {
            val map = _messagesByThread.value.toMutableMap()
            val list = map[threadId].orEmpty().toMutableList()
            val idx =
                list.indices.reversed().firstOrNull { index ->
                    val candidate = list[index]
                    candidate.role == CodexMessageRole.user &&
                        candidate.deliveryState == CodexMessageDeliveryState.pending &&
                        (candidate.turnId == null || candidate.turnId == resolvedTurnId)
                } ?: return@withLock
            val message = list[idx]
            list[idx] =
                message.copy(
                    deliveryState = CodexMessageDeliveryState.confirmed,
                    turnId = resolvedTurnId,
                )
            map[threadId] = list
            publishMessages(map)
        }
    }

    private fun mergeSnapshot(
        existing: String,
        incoming: String,
    ): String {
        if (existing.isEmpty()) return incoming
        if (incoming == existing) return existing
        if (existing.endsWith(incoming)) return existing
        if (incoming.length > existing.length && incoming.startsWith(existing)) return incoming
        if (existing.length > incoming.length && existing.startsWith(incoming)) return existing
        return incoming
    }

    private fun mergeSubagentActions(
        existing: CodexSubagentAction?,
        incoming: CodexSubagentAction,
    ): CodexSubagentAction {
        if (existing == null || existing.normalizedTool != incoming.normalizedTool) return incoming
        val receiverThreadIds = linkedSetOf<String>()
        receiverThreadIds.addAll(existing.receiverThreadIds)
        receiverThreadIds.addAll(incoming.receiverThreadIds)

        val receiverAgentsByThread = LinkedHashMap<String, CodexSubagentRef>()
        fun addAgent(agent: CodexSubagentRef) {
            val key = agent.threadId.trim()
            if (key.isEmpty()) return
            val current = receiverAgentsByThread[key]
            receiverAgentsByThread[key] =
                if (current == null) {
                    agent
                } else {
                    current.copy(
                        agentId = current.agentId ?: agent.agentId,
                        nickname = current.nickname ?: agent.nickname,
                        role = current.role ?: agent.role,
                        model = current.model ?: agent.model,
                        prompt = current.prompt ?: agent.prompt,
                    )
                }
        }
        existing.receiverAgents.forEach(::addAgent)
        incoming.receiverAgents.forEach(::addAgent)

        val agentStates = LinkedHashMap<String, CodexSubagentState>()
        agentStates.putAll(existing.agentStates)
        agentStates.putAll(incoming.agentStates)

        return incoming.copy(
            prompt = incoming.prompt ?: existing.prompt,
            model = incoming.model ?: existing.model,
            receiverThreadIds = receiverThreadIds.toList(),
            receiverAgents = receiverAgentsByThread.values.toList(),
            agentStates = agentStates,
        )
    }

    private fun rebuildSubagentIdentityDirectory(messages: List<CodexMessage>) {
        subagentIdentityByThreadId.clear()
        subagentIdentityByAgentId.clear()
        messages.forEach { message ->
            message.subagentAction?.let(::ingestSubagentIdentity)
            parseAssistantSubagentSummaryRefs(message.text).forEach { ref ->
                upsertSubagentIdentity(
                    threadId = ref.threadId,
                    agentId = ref.agentId,
                    nickname = ref.nickname,
                    role = ref.role,
                )
            }
        }
    }

    private fun ingestSubagentIdentity(action: CodexSubagentAction) {
        action.agentRows.forEach { agent ->
            upsertSubagentIdentity(
                threadId = agent.threadId,
                agentId = agent.agentId,
                nickname = agent.nickname,
                role = agent.role,
            )
        }
        action.receiverAgents.forEach { agent ->
            upsertSubagentIdentity(
                threadId = agent.threadId,
                agentId = agent.agentId,
                nickname = agent.nickname,
                role = agent.role,
            )
        }
        action.agentStates.values.forEach { state ->
            upsertSubagentIdentity(threadId = state.threadId, agentId = null, nickname = null, role = null)
        }
    }

    private fun upsertSubagentIdentity(
        threadId: String?,
        agentId: String?,
        nickname: String?,
        role: String?,
    ) {
        val normalizedThreadId = normalizedSubagentIdentifier(threadId)
        val normalizedAgentId = normalizedSubagentIdentifier(agentId)
        val normalizedNickname = normalizedSubagentIdentifier(nickname)
        val normalizedRole = normalizedSubagentIdentifier(role)
        if (normalizedThreadId == null && normalizedAgentId == null && normalizedNickname == null && normalizedRole == null) {
            return
        }
        val threadEntry = normalizedThreadId?.let { subagentIdentityByThreadId[it] }
        val agentEntry = normalizedAgentId?.let { subagentIdentityByAgentId[it] }
        val merged =
            SubagentIdentityEntry(
                threadId = normalizedThreadId ?: threadEntry?.threadId ?: agentEntry?.threadId,
                agentId = normalizedAgentId ?: threadEntry?.agentId ?: agentEntry?.agentId,
                nickname = normalizedNickname ?: threadEntry?.nickname ?: agentEntry?.nickname,
                role = normalizedRole ?: threadEntry?.role ?: agentEntry?.role,
            )
        if (!merged.hasMetadata) return
        normalizedThreadId?.let { subagentIdentityByThreadId[it] = merged }
        normalizedAgentId?.let { subagentIdentityByAgentId[it] = merged }
        merged.threadId?.let { tid ->
            merged.agentId?.let { aid ->
                subagentIdentityByThreadId[tid] = merged
                subagentIdentityByAgentId[aid] = merged
            }
        }
    }

    private fun resolveSubagentMessageIdentities(message: CodexMessage): CodexMessage {
        val action = message.subagentAction ?: return message
        val resolvedAction = resolveSubagentActionIdentities(action)
        return if (resolvedAction == action) message else message.copy(text = resolvedAction.summaryText, subagentAction = resolvedAction)
    }

    private fun resolveSubagentActionIdentities(action: CodexSubagentAction): CodexSubagentAction {
        ingestSubagentIdentity(action)
        val resolvedAgents =
            action.agentRows.map { agent ->
                val identity = resolvedSubagentIdentity(agent.threadId, agent.agentId)
                CodexSubagentRef(
                    threadId = identity?.threadId ?: agent.threadId,
                    agentId = identity?.agentId ?: agent.agentId,
                    nickname = identity?.nickname ?: agent.nickname,
                    role = identity?.role ?: agent.role,
                    model = agent.model,
                    prompt = agent.prompt,
                )
            }
        return action.copy(
            receiverThreadIds = (action.receiverThreadIds + resolvedAgents.map { it.threadId }).distinct(),
            receiverAgents = resolvedAgents,
        )
    }

    private fun resolvedSubagentIdentity(
        threadId: String?,
        agentId: String?,
    ): SubagentIdentityEntry? {
        val threadEntry = normalizedSubagentIdentifier(threadId)?.let { subagentIdentityByThreadId[it] }
        val agentEntry = normalizedSubagentIdentifier(agentId)?.let { subagentIdentityByAgentId[it] }
        val merged =
            SubagentIdentityEntry(
                threadId = threadEntry?.threadId ?: agentEntry?.threadId,
                agentId = threadEntry?.agentId ?: agentEntry?.agentId,
                nickname = threadEntry?.nickname ?: agentEntry?.nickname,
                role = threadEntry?.role ?: agentEntry?.role,
            )
        return merged.takeIf { it.hasMetadata }
    }

    private fun normalizedSubagentIdentifier(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    private fun absorbAssistantSubagentSummary(
        list: MutableList<CodexMessage>,
        turnId: String?,
        text: String,
    ): Boolean {
        val refs = parseAssistantSubagentSummaryRefs(text)
        if (refs.isEmpty()) return false
        refs.forEach { ref ->
            upsertSubagentIdentity(
                threadId = ref.threadId,
                agentId = ref.agentId,
                nickname = ref.nickname,
                role = ref.role,
            )
        }
        var changed = false
        for (index in list.indices) {
            val current = list[index]
            val action = current.subagentAction ?: continue
            val sameTurn = turnId != null && current.turnId == turnId
            val matchesThread =
                action.agentRows.any { agent -> refs.any { it.threadId == agent.threadId || it.agentId == agent.agentId } }
            if (!sameTurn && !matchesThread) continue
            val enriched = resolveSubagentActionIdentities(enrichSubagentAction(action, refs))
            list[index] =
                current.copy(
                    text = enriched.summaryText,
                    subagentAction = enriched,
                )
            changed = true
        }
        return changed
    }

    private fun subagentSummaryRefsForTurn(
        list: List<CodexMessage>,
        turnId: String?,
    ): List<CodexSubagentRef> {
        if (turnId.isNullOrBlank()) return emptyList()
        return list
            .asReversed()
            .firstOrNull { message ->
                message.role == CodexMessageRole.assistant &&
                    message.kind == CodexMessageKind.chat &&
                    message.turnId == turnId &&
                    parseAssistantSubagentSummaryRefs(message.text).isNotEmpty()
            }
            ?.let { parseAssistantSubagentSummaryRefs(it.text) }
            .orEmpty()
    }

    private fun removeAssistantSubagentSummaries(
        list: MutableList<CodexMessage>,
        turnId: String?,
    ) {
        if (turnId.isNullOrBlank()) return
        list.removeAll { message ->
            message.role == CodexMessageRole.assistant &&
                message.kind == CodexMessageKind.chat &&
                message.turnId == turnId &&
                parseAssistantSubagentSummaryRefs(message.text).isNotEmpty()
        }
    }

    private fun latestSubagentActionIndexForTurn(
        list: List<CodexMessage>,
        turnId: String?,
    ): Int {
        if (turnId.isNullOrBlank()) return -1
        return list.indexOfLast { message ->
            message.role == CodexMessageRole.system &&
                message.kind == CodexMessageKind.subagentAction &&
                message.turnId == turnId &&
                message.subagentAction != null
        }
    }

    private fun enrichSubagentAction(
        action: CodexSubagentAction,
        summaryRefs: List<CodexSubagentRef>,
    ): CodexSubagentAction {
        if (summaryRefs.isEmpty()) return action
        val refsByThread = summaryRefs.associateBy { it.threadId }
        val enrichedAgents =
            action.agentRows.map { row ->
                val summary = refsByThread[row.threadId]
                if (summary == null) {
                    CodexSubagentRef(
                        threadId = row.threadId,
                        agentId = row.agentId,
                        nickname = row.nickname,
                        role = row.role,
                        model = row.model,
                        prompt = row.prompt,
                    )
                } else {
                    CodexSubagentRef(
                        threadId = row.threadId,
                        agentId = row.agentId,
                        nickname = row.nickname ?: summary.nickname,
                        role = row.role ?: summary.role,
                        model = row.model,
                        prompt = row.prompt,
                    )
                }
            }
        val existingThreads = enrichedAgents.mapTo(linkedSetOf()) { it.threadId }
        val extraRefs = summaryRefs.filterNot { it.threadId in existingThreads }
        return action.copy(
            receiverThreadIds = (action.receiverThreadIds + summaryRefs.map { it.threadId }).distinct(),
            receiverAgents = enrichedAgents + extraRefs,
        )
    }

    private fun parseAssistantSubagentSummaryRefs(text: String): List<CodexSubagentRef> {
        if (!text.contains("subagent", ignoreCase = true)) return emptyList()
        val refs = ArrayList<CodexSubagentRef>()
        val inlinePairRegex =
            Regex("""[-*]?\s*`?([A-Za-z][A-Za-z0-9_-]{1,40})`?\s*\(\s*`?([0-9a-fA-F]{8}-[0-9a-fA-F-]{12,})`?\s*\)""")
        inlinePairRegex.findAll(text.replace('\n', ' ')).forEach { match ->
            refs +=
                CodexSubagentRef(
                    threadId = match.groupValues[2].trim(),
                    nickname = match.groupValues[1].trim(),
                )
        }
        val lines = text.lines()
        var pendingName: String? = null
        val bulletRegex = Regex("""^\s*[-*]\s*`?([^`(\n]+?)`?\s*$""")
        val inlineRegex = Regex("""^\s*[-*]\s*`?([^`(\n]+?)`?\s*\(?`?([0-9a-fA-F]{8}-[0-9a-fA-F-]{12,})`?\)?\s*$""")
        val idRegex = Regex("""`?([0-9a-fA-F]{8}-[0-9a-fA-F-]{12,})`?""")
        for (line in lines) {
            val inline = inlineRegex.find(line)
            if (inline != null) {
                val name = inline.groupValues[1].trim().takeIf { it.isNotEmpty() }
                val threadId = inline.groupValues[2].trim()
                if (name != null) refs += CodexSubagentRef(threadId = threadId, nickname = name)
                pendingName = null
                continue
            }
            val bullet = bulletRegex.find(line)
            if (bullet != null) {
                pendingName = bullet.groupValues[1].trim().takeIf { it.isNotEmpty() }
                continue
            }
            val name = pendingName
            val id = idRegex.find(line)?.groupValues?.getOrNull(1)?.trim()
            if (name != null && !id.isNullOrBlank()) {
                refs += CodexSubagentRef(threadId = id, nickname = name)
                pendingName = null
            }
        }
        return refs.distinctBy { it.threadId }
    }

    private fun matchesCompletedMessageCandidate(
        candidate: CodexMessage,
        turnId: String?,
        itemId: String?,
    ): Boolean {
        val candidateItemId = candidate.itemId?.trim()?.takeIf { it.isNotEmpty() }
        val candidateTurnId = candidate.turnId?.trim()?.takeIf { it.isNotEmpty() }
        if (itemId != null && candidateItemId == itemId) {
            return true
        }
        if (turnId != null && candidateTurnId == turnId) {
            return itemId == null || candidate.isStreaming || candidateItemId == null || candidateItemId == itemId
        }
        return false
    }

    private fun findStreamingSystemItemIndex(
        list: List<CodexMessage>,
        kind: CodexMessageKind,
        turnId: String?,
        itemId: String?,
        fileChangePathKeys: Set<String>,
        allowCompletedFileChange: Boolean = false,
    ): Int {
        val direct =
            list.indexOfLast { m ->
                m.role == CodexMessageRole.system &&
                    m.kind == kind &&
                    (m.isStreaming || (allowCompletedFileChange && kind == CodexMessageKind.fileChange)) &&
                    (itemId != null && m.itemId == itemId ||
                        (itemId == null && turnId != null && m.turnId == turnId))
            }
        if (direct >= 0) return direct

        if (kind != CodexMessageKind.fileChange || turnId.isNullOrBlank()) return -1
        if (fileChangePathKeys.isNotEmpty()) {
            val pathMatch =
                list.indexOfLast { m ->
                    m.role == CodexMessageRole.system &&
                        m.kind == CodexMessageKind.fileChange &&
                        (m.isStreaming || allowCompletedFileChange) &&
                        (m.turnId == turnId || m.turnId == null) &&
                        normalizedFileChangePathKeys(m.text).any { it in fileChangePathKeys }
                }
            if (pathMatch >= 0) return pathMatch
        }

        return -1
    }

    private fun findCompletedSystemItemIndex(
        list: List<CodexMessage>,
        kind: CodexMessageKind,
        turnId: String?,
        itemId: String?,
        finalText: String,
        fileChangePathKeys: Set<String>,
        isAuthoritativeFileChangeSnapshot: Boolean,
    ): Int {
        if (kind == CodexMessageKind.fileChange && !turnId.isNullOrBlank()) {
            if (fileChangePathKeys.isNotEmpty()) {
                val pathMatch =
                    list.indexOfLast { m ->
                        m.role == CodexMessageRole.system &&
                            m.kind == CodexMessageKind.fileChange &&
                            (m.turnId == turnId || m.turnId == null) &&
                            normalizedFileChangePathKeys(m.text).any { it in fileChangePathKeys }
                    }
                if (pathMatch >= 0) return pathMatch
                return itemId?.let { exactItemId ->
                    list.indexOfLast { m ->
                        m.role == CodexMessageRole.system &&
                            m.kind == CodexMessageKind.fileChange &&
                            m.itemId == exactItemId
                    }
                } ?: -1
            } else if (isAuthoritativeFileChangeSnapshot) {
                val unique = uniqueFileChangeIndexForTurn(list, turnId, allowsTurnlessFallback = true)
                if (unique >= 0) return unique
            }
        }

        if (kind == CodexMessageKind.commandExecution && !turnId.isNullOrBlank()) {
            val incomingCommandKey = normalizedCommandExecutionPreviewKey(finalText)
            if (incomingCommandKey != null) {
                val commandMatch =
                    list.indexOfLast { m ->
                        m.role == CodexMessageRole.system &&
                            m.kind == CodexMessageKind.commandExecution &&
                            m.turnId == turnId &&
                            normalizedCommandExecutionPreviewKey(m.text) == incomingCommandKey
                    }
                if (commandMatch >= 0) return commandMatch
            }
        }

        return list.indexOfLast { m ->
            m.role == CodexMessageRole.system &&
                m.kind == kind &&
                matchesCompletedMessageCandidate(
                    candidate = m,
                    turnId = turnId,
                    itemId = itemId,
                )
        }
    }

    private fun pruneDuplicateCommandExecutionRows(
        list: MutableList<CodexMessage>,
        keepIndex: Int,
        turnId: String?,
        finalText: String,
    ) {
        if (turnId.isNullOrBlank() || keepIndex !in list.indices) return
        val keepId = list[keepIndex].id
        val commandKey = normalizedCommandExecutionPreviewKey(finalText) ?: return
        list.removeAll { candidate ->
            candidate.id != keepId &&
                candidate.role == CodexMessageRole.system &&
                candidate.kind == CodexMessageKind.commandExecution &&
                candidate.turnId == turnId &&
                normalizedCommandExecutionPreviewKey(candidate.text) == commandKey
        }
    }

    private fun normalizedCommandExecutionPreviewKey(text: String): String? {
        val withoutPhase =
            text.trim().replaceFirst(
                Regex("""^(running|completed|failed|stopped)\s*>?\s*""", RegexOption.IGNORE_CASE),
                "",
            )
        val normalized =
            withoutPhase
                .split("\\s+".toRegex())
                .filter { it.isNotEmpty() }
                .joinToString(" ") { token -> token.trim().trim('"', '\'') }
                .replace("\\s+".toRegex(), " ")
                .lowercase()
        return normalized.takeIf { it.isNotEmpty() }
    }

    private fun uniqueFileChangeIndexForTurn(
        list: List<CodexMessage>,
        turnId: String,
        allowsTurnlessFallback: Boolean,
    ): Int {
        val candidates =
            list.withIndex().filter { (_, message) ->
                message.role == CodexMessageRole.system &&
                    message.kind == CodexMessageKind.fileChange &&
                    (message.turnId == turnId || (allowsTurnlessFallback && message.turnId == null))
            }
        return if (candidates.size == 1) candidates.single().index else -1
    }

    private fun pruneDuplicateFileChangeRows(
        list: MutableList<CodexMessage>,
        keepIndex: Int,
        turnId: String?,
        fileChangePathKeys: Set<String>,
        isAuthoritativeSnapshot: Boolean,
    ) {
        if (turnId.isNullOrBlank() || keepIndex !in list.indices) return
        val keepId = list[keepIndex].id
        val keepText = list[keepIndex].text.trim()
        list.removeAll { candidate ->
            if (candidate.id == keepId ||
                candidate.role != CodexMessageRole.system ||
                candidate.kind != CodexMessageKind.fileChange
            ) {
                return@removeAll false
            }
            val sameTurn = candidate.turnId == turnId
            val turnlessFallback = isAuthoritativeSnapshot && candidate.turnId == null
            if (!sameTurn && !turnlessFallback) return@removeAll false

            if (fileChangePathKeys.isNotEmpty()) {
                val candidateKeys = normalizedFileChangePathKeys(candidate.text)
                if (isAuthoritativeSnapshot) {
                    return@removeAll candidateKeys.isNotEmpty() && candidateKeys.all { it in fileChangePathKeys }
                }
                return@removeAll candidateKeys.any { it in fileChangePathKeys }
            }
            candidate.text.trim() == keepText
        }
    }

    private fun normalizedFileChangePathKeys(text: String): Set<String> {
        val keys = linkedSetOf<String>()
        val inlineTotals = Regex("""\s*[+\uFF0B]\s*\d+\s*[-\u2212\u2013\u2014\uFE63\uFF0D]\s*\d+\s*$""")
        val verbs = listOf("edited ", "updated ", "added ", "created ", "deleted ", "removed ", "renamed ", "moved ")
        for (rawLine in text.lineSequence()) {
            var line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("- ") || line.startsWith("* ")) {
                line = line.drop(2).trim()
            }
            val lower = line.lowercase()
            when {
                lower.startsWith("path:") ->
                    keys.addAll(normalizedFileChangePathAliases(line.drop("Path:".length)))
                line.startsWith("+++ ") || line.startsWith("--- ") ->
                    keys.addAll(normalizedFileChangePathAliases(line.drop(4)))
                line.startsWith("diff --git ") -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 4) keys.addAll(normalizedFileChangePathAliases(parts[3]))
                }
                else -> {
                    val verb = verbs.firstOrNull { lower.startsWith(it) }
                    if (verb != null) {
                        keys.addAll(normalizedFileChangePathAliases(line.drop(verb.length).replace(inlineTotals, "")))
                    }
                }
            }
        }
        return keys
    }

    private fun normalizedFileChangePathAliases(rawPath: String): Set<String> {
        val normalized = normalizeFileChangePathKey(rawPath) ?: return emptySet()
        val aliases = linkedSetOf(normalized)
        val parts = normalized.split('/').filter { it.isNotEmpty() }
        val workspaceIndex = parts.indexOf("workspace")
        if (workspaceIndex >= 0 && parts.size > workspaceIndex + 2) {
            aliases.add(parts.drop(workspaceIndex + 2).joinToString("/"))
        }
        return aliases
    }

    private fun normalizeFileChangePathKey(rawPath: String): String? {
        var normalized = rawPath.trim()
        if (normalized.isEmpty() || normalized == "/dev/null") return null
        normalized = normalized.replace("`", "").replace("\"", "").replace("'", "")
        if (normalized.startsWith("(") && normalized.endsWith(")") && normalized.length > 2) {
            normalized = normalized.drop(1).dropLast(1)
        }
        if (normalized.startsWith("a/") || normalized.startsWith("b/")) normalized = normalized.drop(2)
        if (normalized.startsWith("./")) normalized = normalized.drop(2)
        normalized = normalized.replace(Regex(""":\d+(?::\d+)?$"""), "")
        normalized = normalized.trim().trimEnd(',', '.', ';')
        return normalized.takeIf { it.isNotEmpty() }?.lowercase()
    }

    private fun normalizedMessageText(text: String): String =
        text.trim().replace("\\s+".toRegex(), " ")

    private fun compatibleUserAttachments(
        existing: List<CodexImageAttachment>,
        incoming: List<CodexImageAttachment>,
    ): Boolean =
        UserChatAttachmentMatcher.compatible(existing, incoming)

    private fun reassignOrderIndexesInCurrentOrder(list: MutableList<CodexMessage>) {
        for (index in list.indices) {
            list[index] = list[index].copy(orderIndex = CodexMessageOrderCounter.next())
        }
    }

    private companion object {
        const val DEFAULT_INITIAL_TAIL_LIMIT = 48
    }
}
