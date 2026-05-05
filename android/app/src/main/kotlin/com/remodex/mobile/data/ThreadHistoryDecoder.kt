package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.core.model.CodexPlanState
import com.remodex.mobile.core.model.CodexPlanStep
import com.remodex.mobile.core.model.CodexPlanStepStatus
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
                    else -> Unit
                }
            }
        }
        return out
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
            else -> null
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
        assistantPhase: String? = null,
    ) {
        val t = sanitizeTextForKind(kind, text)
        if (t.isEmpty() && attachments.isEmpty() && kind != CodexMessageKind.plan) return
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
            ),
        )
    }

    private fun normalizedItemType(raw: String): String =
        raw.replace("_", "").replace("-", "").lowercase()

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
