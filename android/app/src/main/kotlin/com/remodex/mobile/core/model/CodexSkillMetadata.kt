package com.remodex.mobile.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class CodexSkillMetadata(
    val name: String,
    val description: String? = null,
    val path: String? = null,
    val scope: String? = null,
    val enabled: Boolean = true,
    val displayName: String? = null,
    val marketplace: String? = null,
    val kind: String? = null,
) {
    val id: String get() = normalizedName

    val normalizedName: String get() = name.trim().lowercase()

    val isPlugin: Boolean
        get() =
            kind.equals("plugin", ignoreCase = true) ||
                scope.equals("plugin", ignoreCase = true) ||
                path.orEmpty().startsWith("plugin://")

    val mentionPath: String?
        get() =
            path?.trim()?.takeIf { it.isNotEmpty() }
                ?: if (isPlugin) {
                    val pluginName = name.trim().takeIf { it.isNotEmpty() } ?: return null
                    val market = marketplace?.trim()?.takeIf { it.isNotEmpty() }
                    "plugin://$pluginName${market?.let { "@$it" }.orEmpty()}"
                } else {
                    null
                }

    val searchBlob: String
        get() =
            listOfNotNull(name, displayName, description, marketplace, path)
                .joinToString(" ")
                .lowercase()

    companion object {
        fun fromJsonObject(obj: JsonObject): CodexSkillMetadata =
            CodexSkillMetadata(
                name = obj["name"]!!.jsonPrimitive.content,
                description = obj["description"]?.jsonPrimitive?.contentOrNull,
                path = obj["path"]?.jsonPrimitive?.contentOrNull,
                scope = obj["scope"]?.jsonPrimitive?.contentOrNull,
                enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                displayName =
                    obj["displayName"]?.jsonPrimitive?.contentOrNull
                        ?: obj["display_name"]?.jsonPrimitive?.contentOrNull,
                marketplace = obj["marketplace"]?.jsonPrimitive?.contentOrNull,
                kind =
                    obj["kind"]?.jsonPrimitive?.contentOrNull
                        ?: obj["type"]?.jsonPrimitive?.contentOrNull,
            )
    }
}

data class CodexTurnSkillMention(
    val id: String,
    val name: String? = null,
    val path: String? = null,
)

data class CodexTurnMention(
    val name: String,
    val path: String,
)
