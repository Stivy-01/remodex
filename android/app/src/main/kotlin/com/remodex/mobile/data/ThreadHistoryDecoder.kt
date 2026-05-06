package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.core.model.CodexPlanState
import com.remodex.mobile.core.model.CodexPlanStep
import com.remodex.mobile.core.model.CodexPlanStepStatus
import com.remodex.mobile.core.model.CodexSubagentAction
import com.remodex.mobile.core.model.CodexSubagentRef
import com.remodex.mobile.core.model.CodexSubagentState
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.TurnThinkingDisclosureHints
import java.time.Instant
import java.time.format.DateTimeFormatter

internal data class DecodedCompletedItem(
    val role: CodexMessageRole,
    val kind: CodexMessageKind,
    val text: String,
    val attachments: List<CodexImageAttachment> = emptyList(),
    val planState: CodexPlanState? = null,
    val subagentAction: CodexSubagentAction? = null,
    val assistantPhase: String? = null,
)

/**
 * Decodes [thread/read] with includeTurns=true (parity with [CodexService.decodeMessagesFromThreadRead]).
 */
internal object ThreadHistoryDecoder {
    fun decodeFromThreadRead(
        threadId: String,
        threadObject: Map<String, JSONValue>,
    ): List<CodexMessage> {
        val base = decodeBaseInstant(threadObject)
        val turns = threadObject["turns"]?.arrayValue ?: return emptyList()
        var offset = 0.0
        val out = ArrayList<CodexMessage>()
        for (turnValue in turns) {
            val turnObject = turnValue.objectValue ?: continue
            val turnId = turnObject["id"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }
            val turnTs = decodeInstant(turnObject) ?: base
            val turnCompleted = isCompletedHistoryTurn(turnObject)
            val items = turnObject["items"]?.arrayValue ?: continue
            for (itemValue in items) {
                val itemObject = itemValue.objectValue ?: continue
                val itemType = itemObject["type"]?.stringValue ?: continue
                val synthetic = turnTs.plusMillis((offset * 1000).toLong())
                offset += 0.001
                val ts = decodeInstant(itemObject) ?: synthetic
                val itemId = itemObject["id"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }
                val text = decodeItemText(itemObject)
                val attachments = decodeImageAttachments(itemObject)
                val norm = normalizedItemType(itemType)
                when (norm) {
                    "usermessage" ->
                        append(out, threadId, CodexMessageRole.user, CodexMessageKind.chat, text, turnId, itemId, ts, attachments)
                    "agentmessage", "assistantmessage" ->
                        append(
                            out,
                            threadId,
                            CodexMessageRole.assistant,
                            CodexMessageKind.chat,
                            text,
                            turnId,
                            itemId,
                            ts,
                            attachments,
                            assistantPhase = IncomingNotificationParsers.extractAssistantPhase(null, itemObject),
                        )
                    "message" -> {
                        val roleRaw = itemObject["role"]?.stringValue?.lowercase().orEmpty()
                        val role =
                            if (roleRaw.contains("user")) CodexMessageRole.user else CodexMessageRole.assistant
                        append(
                            out,
                            threadId,
                            role,
                            CodexMessageKind.chat,
                            text,
                            turnId,
                            itemId,
                            ts,
                            attachments,
                            assistantPhase =
                                if (role == CodexMessageRole.assistant) {
                                    IncomingNotificationParsers.extractAssistantPhase(null, itemObject)
                                } else {
                                    null
                                },
                        )
                    }
                    "reasoning" ->
                        append(
                            out,
                            threadId,
                            CodexMessageRole.system,
                            CodexMessageKind.thinking,
                            decodeReasoningText(itemObject),
                            turnId,
                            itemId,
                            ts,
                        )
                    "filechange" ->
                        append(
                            out,
                            threadId,
                            CodexMessageRole.system,
                            CodexMessageKind.fileChange,
                            decodeFileChangeBody(itemObject),
                            turnId,
                            itemId,
                            ts,
                        )
                    "toolcall", "diff" -> {
                        val t = decodeToolOrDiffPreview(itemObject)
                        if (t.isNotEmpty()) {
                            append(
                                out,
                                threadId,
                                CodexMessageRole.system,
                                CodexMessageKind.fileChange,
                                t,
                                turnId,
                                itemId,
                                ts,
                            )
                        }
                    }
                    "commandexecution" ->
                        append(
                            out,
                            threadId,
                            CodexMessageRole.system,
                            CodexMessageKind.commandExecution,
                            decodeCommandPreview(itemObject),
                            turnId,
                            itemId,
                            ts,
                        )
                    "imagegeneration", "imagegenerationcall", "imagegenerationend", "imageview" -> {
                        val imageAttachments = decodeGeneratedImageAttachments(itemObject)
                        if (imageAttachments.isNotEmpty()) {
                            append(
                                out,
                                threadId,
                                CodexMessageRole.assistant,
                                CodexMessageKind.chat,
                                "",
                                turnId,
                                itemId,
                                ts,
                                imageAttachments,
                                assistantPhase = "final_answer",
                            )
                        }
                    }
                    "plan" ->
                        append(
                            out,
                            threadId,
                            CodexMessageRole.system,
                            CodexMessageKind.plan,
                            text.ifEmpty { "[plan]" },
                            turnId,
                            itemId,
                            ts,
                            planState = finalizedHistoryPlanState(decodeHistoryPlanState(itemObject), turnCompleted),
                        )
                    "contextcompaction" ->
                        append(
                            out,
                            threadId,
                            CodexMessageRole.system,
                            CodexMessageKind.commandExecution,
                            "Context compacted",
                            turnId,
                            itemId,
                            ts,
                        )
                    else -> {
                        if (isSubagentItemType(norm)) {
                            decodeSubagentActionItem(itemObject)?.let { action ->
                                append(
                                    out,
                                    threadId,
                                    CodexMessageRole.system,
                                    CodexMessageKind.subagentAction,
                                    action.summaryText,
                                    turnId,
                                    itemId,
                                    ts,
                                    subagentAction = action,
                                )
                            }
                        }
                    }
                }
            }
        }
        return foldSubagentAssistantSummaries(out)
    }

    /** `item/completed` payload (parity iOS `handleStructuredItemLifecycle` testi principali). */
    fun decodeCompletedItem(itemObject: Map<String, JSONValue>): DecodedCompletedItem? {
        val itemType = itemObject["type"]?.stringValue ?: return null
        val norm = normalizedItemType(itemType)
        return when (norm) {
            "usermessage" ->
                DecodedCompletedItem(
                    CodexMessageRole.user,
                    CodexMessageKind.chat,
                    sanitizeTextForKind(CodexMessageKind.chat, decodeItemText(itemObject)),
                    decodeImageAttachments(itemObject),
                )
            "agentmessage", "assistantmessage" ->
                DecodedCompletedItem(
                    CodexMessageRole.assistant,
                    CodexMessageKind.chat,
                    sanitizeTextForKind(CodexMessageKind.chat, decodeItemText(itemObject)),
                    decodeImageAttachments(itemObject),
                    assistantPhase = IncomingNotificationParsers.extractAssistantPhase(null, itemObject),
                )
            "message" -> {
                val roleRaw = itemObject["role"]?.stringValue?.lowercase().orEmpty()
                val role =
                    if (roleRaw.contains("user")) CodexMessageRole.user else CodexMessageRole.assistant
                DecodedCompletedItem(
                    role,
                    CodexMessageKind.chat,
                    sanitizeTextForKind(CodexMessageKind.chat, decodeItemText(itemObject)),
                    decodeImageAttachments(itemObject),
                    assistantPhase =
                        if (role == CodexMessageRole.assistant) {
                            IncomingNotificationParsers.extractAssistantPhase(null, itemObject)
                        } else {
                            null
                        },
                )
            }
            "reasoning" ->
                DecodedCompletedItem(
                    CodexMessageRole.system,
                    CodexMessageKind.thinking,
                    sanitizeTextForKind(CodexMessageKind.thinking, decodeReasoningText(itemObject)),
                )
            "filechange" -> {
                val body =
                    FileChangeItemBodyRenderer.renderFromIncomingItem(itemObject)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: decodeItemText(itemObject).trim().takeIf { it.isNotEmpty() }
                        ?: decodeFileChangePreview(itemObject)
                DecodedCompletedItem(
                    CodexMessageRole.system,
                    CodexMessageKind.fileChange,
                    sanitizeTextForKind(CodexMessageKind.fileChange, body),
                )
            }
            "toolcall", "diff" -> {
                val t = sanitizeTextForKind(CodexMessageKind.fileChange, decodeToolOrDiffPreview(itemObject))
                if (t.isEmpty()) {
                    null
                } else {
                    DecodedCompletedItem(CodexMessageRole.system, CodexMessageKind.fileChange, t)
                }
            }
            "commandexecution" ->
                DecodedCompletedItem(
                    CodexMessageRole.system,
                    CodexMessageKind.commandExecution,
                    sanitizeTextForKind(CodexMessageKind.commandExecution, decodeCommandPreview(itemObject)),
                )
            "imagegeneration", "imagegenerationcall", "imagegenerationend", "imageview" -> {
                val imageAttachments = decodeGeneratedImageAttachments(itemObject)
                if (imageAttachments.isEmpty()) {
                    null
                } else {
                    DecodedCompletedItem(
                        CodexMessageRole.assistant,
                        CodexMessageKind.chat,
                        "",
                        imageAttachments,
                        assistantPhase = "final_answer",
                    )
                }
            }
            "plan" -> {
                val body =
                    decodeItemText(itemObject).ifEmpty {
                        itemObject["summary"]?.stringValue?.trim().orEmpty()
                    }.ifEmpty { "[plan]" }
                DecodedCompletedItem(
                    CodexMessageRole.system,
                    CodexMessageKind.plan,
                    sanitizeTextForKind(CodexMessageKind.plan, body),
                    planState = decodeHistoryPlanState(itemObject),
                )
            }
            else ->
                if (isSubagentItemType(norm)) {
                    decodeSubagentActionItem(itemObject)?.let { action ->
                        DecodedCompletedItem(
                            CodexMessageRole.system,
                            CodexMessageKind.subagentAction,
                            action.summaryText,
                            subagentAction = action,
                        )
                    }
                } else {
                    null
                }
        }
    }

    private fun append(
        out: MutableList<CodexMessage>,
        threadId: String,
        role: CodexMessageRole,
        kind: CodexMessageKind,
        text: String,
        turnId: String?,
        itemId: String?,
        createdAt: Instant,
        attachments: List<CodexImageAttachment> = emptyList(),
        planState: CodexPlanState? = null,
        subagentAction: CodexSubagentAction? = null,
        assistantPhase: String? = null,
    ) {
        val t = sanitizeTextForKind(kind, text)
        if (t.isEmpty() && attachments.isEmpty() && kind != CodexMessageKind.plan && subagentAction == null) return
        out.add(
            CodexMessage(
                threadId = threadId,
                role = role,
                kind = kind,
                assistantPhase = if (role == CodexMessageRole.assistant) assistantPhase else null,
                text = t,
                createdAt = createdAt,
                turnId = turnId,
                itemId = itemId,
                isStreaming = false,
                attachments = attachments,
                planState = planState,
                subagentAction = subagentAction,
            ),
        )
    }

    private fun normalizedItemType(raw: String): String =
        raw.replace("_", "").replace("-", "").lowercase()

    private fun isSubagentItemType(norm: String): Boolean =
        norm == "collabagenttoolcall" ||
            norm == "collabtoolcall" ||
            norm.startsWith("collabagentspawn") ||
            norm.startsWith("collabwaiting") ||
            norm.startsWith("collabclose") ||
            norm.startsWith("collabresume") ||
            norm.startsWith("collabagentinteraction")

    internal fun decodeSubagentActionItem(itemObject: Map<String, JSONValue>): CodexSubagentAction? {
        val receiverThreadIds = decodeSubagentReceiverThreadIds(itemObject)
        val receiverAgents = decodeSubagentReceiverAgents(itemObject, receiverThreadIds)
        val agentStates = decodeSubagentAgentStates(itemObject)
        val tool = firstStringValue(itemObject, "tool", "name") ?: inferSubagentToolFromType(itemObject) ?: "spawnAgent"
        val status = firstStringValue(itemObject, "status") ?: "in_progress"
        val prompt = firstStringValue(itemObject, "prompt", "task", "message")
        val model =
            normalizedIdentifier(
                firstStringValue(
                    itemObject,
                    "model",
                    "modelName",
                    "model_name",
                    "requestedModel",
                    "requested_model",
                ),
            )

        if (receiverThreadIds.isEmpty() && receiverAgents.isEmpty() && agentStates.isEmpty() && prompt == null && model == null) {
            return null
        }

        return CodexSubagentAction(
            tool = tool,
            status = status,
            prompt = prompt,
            model = model,
            receiverThreadIds = receiverThreadIds,
            receiverAgents = receiverAgents,
            agentStates = agentStates,
        )
    }

    private fun decodeSubagentReceiverThreadIds(itemObject: Map<String, JSONValue>): List<String> {
        val plural =
            firstValue(itemObject, "receiverThreadIds", "receiver_thread_ids", "threadIds", "thread_ids")
                ?.arrayValue
                .orEmpty()
                .mapNotNull { normalizedIdentifier(it.stringValue) }
                .distinct()
        if (plural.isNotEmpty()) return plural

        return listOfNotNull(
            normalizedIdentifier(
                firstStringValue(
                    itemObject,
                    "receiverThreadId",
                    "receiver_thread_id",
                    "threadId",
                    "thread_id",
                    "newThreadId",
                    "new_thread_id",
                ),
            ),
        )
    }

    private fun decodeSubagentReceiverAgents(
        itemObject: Map<String, JSONValue>,
        fallbackThreadIds: List<String>,
    ): List<CodexSubagentRef> {
        val values = firstValue(itemObject, "receiverAgents", "receiver_agents", "agents")?.arrayValue
        if (values.isNullOrEmpty()) return buildSyntheticAgentRefs(itemObject, fallbackThreadIds)

        return values.mapIndexedNotNull { index, value ->
            val obj = value.objectValue ?: return@mapIndexedNotNull null
            val threadId =
                normalizedIdentifier(
                    firstStringValue(
                        obj,
                        "threadId",
                        "thread_id",
                        "receiverThreadId",
                        "receiver_thread_id",
                        "newThreadId",
                        "new_thread_id",
                    ) ?: fallbackThreadIds.getOrNull(index),
                ) ?: return@mapIndexedNotNull null
            CodexSubagentRef(
                threadId = threadId,
                agentId = normalizedIdentifier(firstStringValue(obj, "agentId", "agent_id", "receiverAgentId", "receiver_agent_id", "newAgentId", "new_agent_id", "id")),
                nickname = normalizedIdentifier(firstStringValue(obj, "agentNickname", "agent_nickname", "receiverAgentNickname", "receiver_agent_nickname", "newAgentNickname", "new_agent_nickname", "nickname", "name")),
                role = normalizedIdentifier(firstStringValue(obj, "agentRole", "agent_role", "receiverAgentRole", "receiver_agent_role", "newAgentRole", "new_agent_role", "agentType", "agent_type")),
                model = normalizedIdentifier(firstStringValue(obj, "modelProvider", "model_provider", "modelProviderId", "model_provider_id", "modelName", "model_name", "model")),
                prompt = normalizedIdentifier(firstStringValue(obj, "prompt", "instructions", "instruction", "task", "message")),
            )
        }
    }

    private fun decodeSubagentAgentStates(itemObject: Map<String, JSONValue>): Map<String, CodexSubagentState> {
        val candidate = firstValue(itemObject, "statuses", "agentsStates", "agents_states", "agentStates", "agent_states")
        candidate?.objectValue?.let { obj ->
            val decoded = LinkedHashMap<String, CodexSubagentState>()
            for ((rawThreadId, value) in obj) {
                val stateObject = value.objectValue
                val threadId =
                    normalizedIdentifier(rawThreadId)
                        ?: normalizedIdentifier(firstStringValue(stateObject, "threadId", "thread_id"))
                        ?: continue
                decoded[threadId] =
                    CodexSubagentState(
                        threadId = threadId,
                        status = firstStringValue(stateObject, "status") ?: "unknown",
                        message = firstStringValue(stateObject, "message", "text", "delta", "summary"),
                    )
            }
            return decoded
        }
        candidate?.arrayValue?.let { values ->
            val decoded = LinkedHashMap<String, CodexSubagentState>()
            for (value in values) {
                val obj = value.objectValue ?: continue
                val threadId = normalizedIdentifier(firstStringValue(obj, "threadId", "thread_id")) ?: continue
                decoded[threadId] =
                    CodexSubagentState(
                        threadId = threadId,
                        status = firstStringValue(obj, "status") ?: "unknown",
                        message = firstStringValue(obj, "message", "text", "delta", "summary"),
                    )
            }
            return decoded
        }
        return emptyMap()
    }

    private fun buildSyntheticAgentRefs(
        itemObject: Map<String, JSONValue>,
        fallbackThreadIds: List<String>,
    ): List<CodexSubagentRef> {
        val threadId =
            fallbackThreadIds.firstOrNull()
                ?: normalizedIdentifier(
                    firstStringValue(
                        itemObject,
                        "receiverThreadId",
                        "receiver_thread_id",
                        "threadId",
                        "thread_id",
                        "newThreadId",
                        "new_thread_id",
                    ),
                )
                ?: return emptyList()
        return listOf(
            CodexSubagentRef(
                threadId = threadId,
                agentId = normalizedIdentifier(firstStringValue(itemObject, "newAgentId", "new_agent_id", "agentId", "agent_id")),
                nickname = normalizedIdentifier(firstStringValue(itemObject, "newAgentNickname", "new_agent_nickname", "agentNickname", "agent_nickname", "receiverAgentNickname", "receiver_agent_nickname")),
                role = normalizedIdentifier(firstStringValue(itemObject, "receiverAgentRole", "receiver_agent_role", "newAgentRole", "new_agent_role", "agentRole", "agent_role", "agentType", "agent_type")),
                model = normalizedIdentifier(firstStringValue(itemObject, "modelProvider", "model_provider", "modelProviderId", "model_provider_id", "modelName", "model_name", "model")),
                prompt = normalizedIdentifier(firstStringValue(itemObject, "prompt", "instructions", "instruction", "task", "message")),
            ),
        )
    }

    private fun inferSubagentToolFromType(itemObject: Map<String, JSONValue>): String? {
        val normalized = firstStringValue(itemObject, "type")?.let(::normalizedItemType) ?: return null
        return when {
            "spawn" in normalized -> "spawnAgent"
            "waiting" in normalized || "wait" in normalized -> "wait"
            "close" in normalized -> "closeAgent"
            "resume" in normalized -> "resumeAgent"
            "sendinput" in normalized || "interaction" in normalized -> "sendInput"
            else -> null
        }
    }

    private fun firstValue(
        obj: Map<String, JSONValue>?,
        vararg keys: String,
    ): JSONValue? {
        if (obj == null) return null
        for (key in keys) {
            obj[key]?.let { return it }
        }
        return null
    }

    private fun firstStringValue(
        obj: Map<String, JSONValue>?,
        vararg keys: String,
    ): String? =
        firstValue(obj, *keys)
            ?.stringValue
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun normalizedIdentifier(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    private fun foldSubagentAssistantSummaries(messages: List<CodexMessage>): List<CodexMessage> {
        if (messages.none { it.kind == CodexMessageKind.subagentAction }) return messages
        val out = messages.toMutableList()
        val summaryByTurn = LinkedHashMap<String, List<CodexSubagentRef>>()
        val allRefs = ArrayList<CodexSubagentRef>()
        out.filter { it.role == CodexMessageRole.assistant && it.kind == CodexMessageKind.chat }
            .forEach { message ->
                val refs = parseAssistantSubagentSummaryRefs(message.text)
                if (refs.isEmpty()) return@forEach
                allRefs += refs
                message.turnId?.let { summaryByTurn[it] = refs }
            }
        if (summaryByTurn.isEmpty() && allRefs.isEmpty()) return messages

        for (index in out.indices) {
            val message = out[index]
            val action = message.subagentAction ?: continue
            val refs =
                summaryByTurn[message.turnId]
                    ?: allRefs.filter { ref ->
                        action.agentRows.any { it.threadId == ref.threadId || it.agentId == ref.agentId }
                    }
            if (refs.isEmpty()) continue
            val enriched = enrichSubagentAction(action, refs)
            out[index] = message.copy(text = enriched.summaryText, subagentAction = enriched)
        }
        return out.filterNot { message ->
            message.role == CodexMessageRole.assistant &&
                message.kind == CodexMessageKind.chat &&
                parseAssistantSubagentSummaryRefs(message.text).isNotEmpty()
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
        var pendingName: String? = null
        val bulletRegex = Regex("""^\s*[-*]\s*`?([^`(\n]+?)`?\s*$""")
        val inlineRegex = Regex("""^\s*[-*]\s*`?([^`(\n]+?)`?\s*\(?`?([0-9a-fA-F]{8}-[0-9a-fA-F-]{12,})`?\)?\s*$""")
        val idRegex = Regex("""`?([0-9a-fA-F]{8}-[0-9a-fA-F-]{12,})`?""")
        for (line in text.lines()) {
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
            val id = idRegex.find(line)?.groupValues?.getOrNull(1)?.trim()
            val name = pendingName
            if (name != null && !id.isNullOrBlank()) {
                refs += CodexSubagentRef(threadId = id, nickname = name)
                pendingName = null
            }
        }
        return refs.distinctBy { it.threadId }
    }

    private fun decodeBaseInstant(threadObject: Map<String, JSONValue>): Instant =
        decodeInstant(threadObject)
            ?: Instant.EPOCH

    private fun decodeInstant(obj: Map<String, JSONValue>): Instant? {
        for (key in listOf("createdAt", "created_at", "updatedAt", "updated_at")) {
            obj[key]?.let { v ->
                when (v) {
                    is JSONValue.Str -> parseIso(v.value)?.let { return it }
                    is JSONValue.NumLong -> return unixToInstant(v.value.toDouble())
                    is JSONValue.NumDouble -> return unixToInstant(v.value)
                    else -> Unit
                }
            }
        }
        return null
    }

    private fun unixToInstant(raw: Double): Instant {
        val sec = if (raw > 10_000_000_000.0) raw / 1000.0 else raw
        return Instant.ofEpochMilli((sec * 1000).toLong())
    }

    private fun parseIso(s: String): Instant? =
        runCatching { Instant.parse(s.trim()) }.getOrNull()
            ?: runCatching {
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s.trim(), Instant::from)
            }.getOrNull()

    private fun decodeItemText(itemObject: Map<String, JSONValue>): String {
        val contentItems = itemObject["content"]?.arrayValue.orEmpty()
        val parts = ArrayList<String>()
        for (value in contentItems) {
            val o = value.objectValue ?: continue
            val t = normalizedItemType(o["type"]?.stringValue ?: "")
            when {
                t == "text" -> o["text"]?.stringValue?.let { parts.add(it) }
                (t == "inputtext" || t == "outputtext" || t == "message") ->
                    o["text"]?.stringValue?.let { parts.add(it) }
                t == "skill" -> {
                    val id = o["id"]?.stringValue?.trim().orEmpty()
                    val name = o["name"]?.stringValue?.trim().orEmpty()
                    val r = id.ifEmpty { name }
                    if (r.isNotEmpty()) parts.add("$$r")
                }
            }
        }
        val joined = parts.joinToString("\n").trim()
        if (joined.isNotEmpty()) return joined
        itemObject["text"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        itemObject["message"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return ""
    }

    private fun decodeReasoningText(itemObject: Map<String, JSONValue>): String {
        listOf("summary", "text", "content").forEach { k ->
            itemObject[k]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return decodeItemText(itemObject).ifEmpty { "[reasoning]" }
    }

    private fun decodeHistoryPlanState(itemObject: Map<String, JSONValue>): CodexPlanState? {
        val explanation =
            decodeHistoryNormalizedPlanText(itemObject["explanation"])
                ?: decodeHistoryNormalizedPlanText(itemObject["summary"])
        val steps =
            (itemObject["plan"]?.arrayValue ?: emptyList()).mapNotNull { stepValue ->
                val stepObject = stepValue.objectValue ?: return@mapNotNull null
                val step = decodeHistoryNormalizedPlanText(stepObject["step"]) ?: return@mapNotNull null
                val rawStatus = decodeHistoryNormalizedPlanText(stepObject["status"]) ?: return@mapNotNull null
                val status =
                    when (rawStatus.lowercase().replace("_", "").replace("-", "")) {
                        "pending" -> CodexPlanStepStatus.pending
                        "inprogress" -> CodexPlanStepStatus.inProgress
                        "completed" -> CodexPlanStepStatus.completed
                        else -> null
                    } ?: return@mapNotNull null
                CodexPlanStep(step = step, status = status)
            }
        if (explanation == null && steps.isEmpty()) return null
        return CodexPlanState(explanation = explanation, steps = steps)
    }

    private fun finalizedHistoryPlanState(
        planState: CodexPlanState?,
        turnCompleted: Boolean,
    ): CodexPlanState? {
        val state = planState ?: return null
        if (!turnCompleted || state.steps.isEmpty() || state.steps.none { it.status != CodexPlanStepStatus.completed }) {
            return state
        }
        return state.copy(
            steps = state.steps.map { step -> step.copy(status = CodexPlanStepStatus.completed) },
        )
    }

    private fun isCompletedHistoryTurn(turnObject: Map<String, JSONValue>): Boolean {
        val status =
            turnObject["status"]?.stringValue
                ?: turnObject["state"]?.stringValue
                ?: (turnObject["terminalState"] as? JSONValue.Obj)?.map?.get("status")?.stringValue
                ?: (turnObject["terminal_state"] as? JSONValue.Obj)?.map?.get("status")?.stringValue
                ?: return false
        val normalized = status.lowercase().replace("_", "").replace("-", "")
        return normalized == "completed" || normalized == "success" || normalized == "succeeded"
    }

    private fun decodeHistoryNormalizedPlanText(value: JSONValue?): String? {
        val raw =
            when (value) {
                is JSONValue.Str -> value.value
                else -> null
            } ?: return null
        val trimmed = raw.trim()
        return trimmed.ifEmpty { null }
    }

    private fun decodeImageAttachments(itemObject: Map<String, JSONValue>): List<CodexImageAttachment> {
        val contentItems = itemObject["content"]?.arrayValue.orEmpty()
        val attachments = ArrayList<CodexImageAttachment>()
        for (value in contentItems) {
            val objectValue = value.objectValue ?: continue
            val normalizedType = normalizedItemType(objectValue["type"]?.stringValue ?: "")
            if (normalizedType != "image" && normalizedType != "localimage") continue
            val sourceUrl =
                objectValue["url"]?.stringValue
                    ?: objectValue["image_url"]?.stringValue
                    ?: objectValue["path"]?.stringValue
            TurnAttachmentCodec.attachmentFromHistorySource(sourceUrl)?.let { attachments.add(it) }
        }
        return attachments
    }

    private fun decodeGeneratedImageAttachments(itemObject: Map<String, JSONValue>): List<CodexImageAttachment> {
        val directSource =
            firstString(
                itemObject,
                listOf("saved_path", "savedPath", "file_path", "filePath", "path", "url", "image_url"),
            )
        val attachments =
            if (directSource != null) {
                listOfNotNull(TurnAttachmentCodec.attachmentFromHistorySource(directSource))
            } else {
                decodeImageAttachments(itemObject)
            }
        return attachments
    }

    private fun decodeFileChangePreview(itemObject: Map<String, JSONValue>): String {
        firstString(
            itemObject,
            listOf("path", "filePath", "file_path", "displayPath", "display_path"),
        )?.let { return it }
        return "[file change]"
    }

    private fun decodeFileChangeBody(itemObject: Map<String, JSONValue>): String =
        FileChangeItemBodyRenderer.renderFromIncomingItem(itemObject)?.trim()?.takeIf { it.isNotEmpty() }
            ?: decodeItemText(itemObject).trim().takeIf { it.isNotEmpty() }
            ?: decodeFileChangePreview(itemObject)

    private fun decodeToolOrDiffPreview(itemObject: Map<String, JSONValue>): String {
        FileChangeItemBodyRenderer.renderFromIncomingItem(itemObject)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return decodeItemText(itemObject)
            .trim()
            .takeIf { FileChangeItemBodyRenderer.hasFileChangeEvidence(it) }
            .orEmpty()
    }

    private fun decodeCommandPreview(itemObject: Map<String, JSONValue>): String {
        val cmd =
            firstString(
                itemObject,
                listOf("command", "cmd", "raw_command", "rawCommand", "input", "invocation"),
            ) ?: "command"
        val statusRaw = itemObject["status"]
        val status =
            when (statusRaw) {
                is JSONValue.Str -> statusRaw.value
                is JSONValue.Obj ->
                    statusRaw.map["type"]?.stringValue
                        ?: statusRaw.map["statusType"]?.stringValue
                        ?: statusRaw.map["status"]?.stringValue
                        ?: "completed"
                else -> statusRaw?.stringValue ?: "completed"
            }
        val phase =
            when {
                status.contains("fail", ignoreCase = true) || status.contains("error", ignoreCase = true) ->
                    "failed"
                status.contains("cancel", ignoreCase = true) ||
                    status.contains("abort", ignoreCase = true) -> "stopped"
                status.contains("complete", ignoreCase = true) ||
                    status.contains("success", ignoreCase = true) -> "completed"
                else -> "running"
            }
        val trimCmd = cmd.trim().ifBlank { "command" }.let { c ->
            val max = 8192
            if (c.length <= max) c else c.take(max - 1) + "…"
        }
        // `phase> cmd` matches parseCommandExecution's inlined phase header and preserves the full command.
        return "$phase> $trimCmd"
    }

    private fun firstString(
        obj: Map<String, JSONValue>,
        keys: List<String>,
    ): String? {
        for (k in keys) {
            obj[k]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private fun sanitizeTextForKind(
        kind: CodexMessageKind,
        rawText: String,
    ): String {
        val text = rawText.trim()
        return when (kind) {
            CodexMessageKind.thinking ->
                TurnThinkingDisclosureHints.stripSimpleThinkingTags(text).trim()
            else -> text
        }
    }
}
