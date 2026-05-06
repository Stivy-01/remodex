package com.remodex.mobile.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class CodexModelOption(
    val id: String,
    val model: String,
    val displayName: String,
    val description: String,
    val isDefault: Boolean,
    val supportedReasoningEfforts: List<CodexReasoningEffortOption>,
    val defaultReasoningEffort: String?,
) {
    companion object {
        fun fromJsonObject(obj: JsonObject): CodexModelOption {
            val modelValue = obj["model"]?.jsonPrimitive?.content?.trim().orEmpty()
            val idValue = obj["id"]?.jsonPrimitive?.content?.trim().orEmpty()
            val rawModel = modelValue.ifEmpty { idValue }
            val normalizedModel = rawModel.trim()
            val rawId = idValue.ifEmpty { normalizedModel }
            val normalizedId = rawId.trim()
            val displayNameValue = obj["displayName"]?.jsonPrimitive?.content
            val displayNameSnake = obj["display_name"]?.jsonPrimitive?.content
            val rawDisplayName = (displayNameValue ?: displayNameSnake ?: normalizedModel).trim()
            val normalizedDisplayName = rawDisplayName.trim()
            val rawDescription = obj["description"]?.jsonPrimitive?.content ?: ""

            val effortsObj = obj["supportedReasoningEfforts"] ?: obj["supported_reasoning_efforts"]
            val efforts =
                when (effortsObj) {
                    is JsonArray ->
                        effortsObj.mapNotNull { el ->
                            runCatching { el.jsonObject }.getOrNull()?.let {
                                CodexReasoningEffortOption.fromJsonObject(it)
                            }
                        }.filter { it.reasoningEffort.isNotBlank() }
                    else -> emptyList()
                }

            val camelDefaultEffort = obj["defaultReasoningEffort"]?.jsonPrimitive?.content
            val snakeDefaultEffort = obj["default_reasoning_effort"]?.jsonPrimitive?.content
            val defaultEffort = (camelDefaultEffort ?: snakeDefaultEffort)?.trim()

            val camelDefaultFlag = obj["isDefault"]?.jsonPrimitive?.booleanOrNull
            val snakeDefaultFlag = obj["is_default"]?.jsonPrimitive?.booleanOrNull

            val resolvedId = if (normalizedId.isEmpty()) normalizedModel else normalizedId
            val resolvedDisplay =
                if (normalizedDisplayName.isEmpty()) normalizedModel else normalizedDisplayName

            return CodexModelOption(
                id = resolvedId,
                model = normalizedModel,
                displayName = resolvedDisplay,
                description = rawDescription.trim(),
                isDefault = camelDefaultFlag ?: snakeDefaultFlag ?: false,
                supportedReasoningEfforts = efforts,
                defaultReasoningEffort = defaultEffort?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
