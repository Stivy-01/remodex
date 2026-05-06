package com.remodex.mobile.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class CodexReasoningEffortOption(
    val reasoningEffort: String,
    val description: String,
) {
    val id: String get() = reasoningEffort

    companion object {
        fun fromJsonObject(obj: JsonObject): CodexReasoningEffortOption {
            val camel = obj["reasoningEffort"]?.jsonPrimitive?.content
            val snake = obj["reasoning_effort"]?.jsonPrimitive?.content
            val effort = (camel ?: snake ?: "").trim()
            val desc = obj["description"]?.jsonPrimitive?.content?.trim() ?: ""
            return CodexReasoningEffortOption(reasoningEffort = effort, description = desc)
        }
    }
}
