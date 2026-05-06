package com.remodex.mobile.core.model

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class CodexCollaborationModeKind {
    @SerialName("default")
    `default`,

    plan,
}

@Serializable
enum class CodexPlanStepStatus {
    pending,

    @SerialName("in_progress")
    inProgress,

    completed,
}

@Serializable
data class CodexPlanStep(
    val id: String = UUID.randomUUID().toString(),
    val step: String,
    val status: CodexPlanStepStatus,
)

@Serializable
data class CodexPlanState(
    val explanation: String? = null,
    val steps: List<CodexPlanStep> = emptyList(),
)

@Serializable
data class CodexStructuredUserInputOption(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val description: String,
)

@Serializable
data class CodexStructuredUserInputQuestion(
    val id: String,
    val header: String,
    val question: String,
    val isOther: Boolean,
    val isSecret: Boolean,
    val options: List<CodexStructuredUserInputOption>,
)

@Serializable
data class CodexStructuredUserInputRequest(
    @Serializable(JSONValueSerializer::class)
    val requestID: JSONValue,
    val questions: List<CodexStructuredUserInputQuestion>,
)

@Serializable
data class CodexSubagentRef(
    val threadId: String,
    val agentId: String? = null,
    val nickname: String? = null,
    val role: String? = null,
    val model: String? = null,
    val prompt: String? = null,
)

@Serializable
data class CodexSubagentState(
    val threadId: String,
    val status: String,
    val message: String? = null,
)

data class CodexSubagentThreadPresentation(
    val threadId: String,
    val agentId: String? = null,
    val nickname: String? = null,
    val role: String? = null,
    val model: String? = null,
    val modelIsRequestedHint: Boolean,
    val prompt: String? = null,
    val fallbackStatus: String? = null,
    val fallbackMessage: String? = null,
) {
    val id: String get() = threadId

    val displayLabel: String
        get() {
            val trimmedNickname = sanitizedAgentIdentity(nickname)
            val trimmedRole = sanitizedAgentIdentity(role)
            if (!trimmedNickname.isNullOrEmpty() && !trimmedRole.isNullOrEmpty()) {
                return "$trimmedNickname [$trimmedRole]"
            }
            if (!trimmedNickname.isNullOrEmpty()) return trimmedNickname
            if (!trimmedRole.isNullOrEmpty()) {
                return trimmedRole.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            val compactThreadId = threadId.trim()
            if (compactThreadId.length > 14) {
                return "Agent ${compactThreadId.takeLast(8)}"
            }
            return if (compactThreadId.isEmpty()) "Agent" else compactThreadId
        }

    private fun sanitizedAgentIdentity(value: String?): String? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val lowered = trimmed.lowercase()
        if (lowered == "collabagenttoolcall" || lowered == "collabtoolcall") return null
        return trimmed
    }
}

@Serializable
data class CodexSubagentAction(
    var tool: String,
    var status: String,
    var prompt: String? = null,
    var model: String? = null,
    var receiverThreadIds: List<String> = emptyList(),
    var receiverAgents: List<CodexSubagentRef> = emptyList(),
    var agentStates: Map<String, CodexSubagentState> = emptyMap(),
) {
    val agentRows: List<CodexSubagentThreadPresentation>
        get() {
            val ordered = LinkedHashSet<String>()
            receiverThreadIds.forEach { ordered.add(it) }
            receiverAgents.forEach { ordered.add(it.threadId) }
            agentStates.keys.sorted().forEach { ordered.add(it) }
            return ordered.map { threadId ->
                val matchingAgent = receiverAgents.firstOrNull { it.threadId == threadId }
                val matchingState = agentStates[threadId]
                CodexSubagentThreadPresentation(
                    threadId = threadId,
                    agentId = matchingAgent?.agentId,
                    nickname = matchingAgent?.nickname,
                    role = matchingAgent?.role,
                    model = matchingAgent?.model ?: model,
                    modelIsRequestedHint = matchingAgent?.model == null && model != null,
                    prompt = matchingAgent?.prompt,
                    fallbackStatus = matchingState?.status,
                    fallbackMessage = matchingState?.message,
                )
            }
        }

    val normalizedTool: String
        get() =
            tool.trim()
                .lowercase()
                .replace("_", "")
                .replace("-", "")

    val normalizedStatus: String
        get() =
            status.trim()
                .lowercase()
                .replace("_", "")
                .replace("-", "")

    val summaryText: String
        get() {
            val count = maxOf(1, agentRows.size, receiverThreadIds.size, receiverAgents.size)
            val noun = if (count == 1) "agent" else "agents"
            return when (normalizedTool) {
                "spawnagent" -> "Spawning $count $noun"
                "wait", "waitagent" -> "Waiting on $count $noun"
                "closeagent" -> "Closing $count $noun"
                "resumeagent" -> "Resuming $count $noun"
                "sendinput" -> if (count == 1) "Updating agent" else "Updating agents"
                else -> if (count == 1) "Agent activity" else "Agent activity ($count)"
            }
        }
}
