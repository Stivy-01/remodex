package com.remodex.mobile.core.model

import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

enum class CodexThreadSyncState {
    live,
    archivedLocal,
}

data class CodexThread(
    val id: String,
    val title: String? = null,
    val name: String? = null,
    val preview: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val cwd: String? = null,
    val metadata: Map<String, JSONValue>? = null,
    val forkedFromThreadId: String? = null,
    val parentThreadId: String? = null,
    val agentId: String? = null,
    val agentNickname: String? = null,
    val agentRole: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
    val syncState: CodexThreadSyncState = CodexThreadSyncState.live,
) {
    companion object {
        /** User-visible fallback when there is no name, title, or preview (parity [CodexThread.swift] `defaultDisplayTitle`). */
        const val DEFAULT_DISPLAY_TITLE = "New Thread"

        private val windowsDrivePathRegex = Regex("^[A-Za-z]:[\\\\/].*")
        private val adHocCodexCwdPatterns =
            listOf(
                Regex("/Documents/Codex/\\d{4}-\\d{2}-\\d{2}/"),
                Regex("/\\.codex/sessions/"),
            )

        fun fromJsonObject(obj: JsonObject): CodexThread {
            val id = obj.stringOrThrow("id")
            val name =
                obj.firstNonBlankString(
                    "name",
                    "threadName",
                    "thread_name",
                )
            val title =
                obj.firstNonBlankString(
                    "title",
                    "threadTitle",
                    "thread_title",
                    "displayName",
                    "display_name",
                    "heading",
                    "headline",
                )
            val preview =
                obj.firstNonBlankString(
                    "preview",
                    "summary",
                    "snippet",
                    "subtitle",
                    "lastMessagePreview",
                    "last_message_preview",
                )
            val createdAt = obj.decodeDate("createdAt", "created_at")
            val updatedAt = obj.decodeDate("updatedAt", "updated_at")
            val cwd =
                decodeCwd(
                    obj,
                )
            val metadata =
                obj["metadata"]?.let { el ->
                    when (val v = JSONValue.fromJsonElement(el)) {
                        is JSONValue.Obj -> v.map
                        else -> null
                    }
                }
            val forked =
                decodeThreadIdentity(
                    obj,
                    metadata,
                    listOf("forkedFromThreadId", "forkedFromId", "forked_from_thread_id", "forked_from_id"),
                )
            val parent =
                decodeThreadIdentity(
                    obj,
                    metadata,
                    listOf("parentThreadId", "parent_thread_id"),
                )
            val agent =
                decodeThreadIdentity(
                    obj,
                    metadata,
                    listOf("agentId", "agent_id"),
                )
            val agentNick =
                decodeThreadIdentity(
                    obj,
                    metadata,
                    listOf("agentNickname", "agent_nickname", "nickname", "name"),
                )
            val agentRoleVal =
                decodeThreadIdentity(
                    obj,
                    metadata,
                    listOf("agentRole", "agent_role", "agentType", "agent_type"),
                )
            val modelVal =
                decodeThreadIdentity(
                    obj,
                    metadata,
                    listOf("model", "modelName", "model_name"),
                )
            val modelProv =
                decodeThreadIdentity(
                    obj,
                    metadata,
                    listOf("modelProvider", "model_provider", "modelProviderId", "model_provider_id"),
                )
            val sync =
                obj.stringOrNull("syncState")?.let { raw ->
                    runCatching { CodexThreadSyncState.valueOf(raw) }.getOrNull()
                } ?: CodexThreadSyncState.live

            return CodexThread(
                id = id,
                title = title,
                name = name,
                preview = preview,
                createdAt = createdAt,
                updatedAt = updatedAt,
                cwd = normalizeProjectPath(cwd),
                metadata = metadata,
                forkedFromThreadId = normalizeIdentifier(forked),
                parentThreadId = normalizeIdentifier(parent),
                agentId = normalizeIdentifier(agent),
                agentNickname = normalizeIdentifier(agentNick),
                agentRole = normalizeIdentifier(agentRoleVal),
                model = normalizeIdentifier(modelVal),
                modelProvider = normalizeIdentifier(modelProv),
                syncState = sync,
            )
        }

        /**
         * Some bridges/clients stringify nulls (`"null"`, `"undefined"`). Treat as absent — parity resilient decoding.
         */
        fun isMalformedJsonSentinel(value: String?): Boolean {
            val t = value?.trim()?.takeIf { it.isNotEmpty() } ?: return false
            return when (t.lowercase()) {
                "null", "undefined", "(null)" -> true
                else -> false
            }
        }

        private fun JsonObject.firstNonBlankString(vararg keys: String): String? {
            for (k in keys) {
                stringOrNull(k)?.let { cand ->
                    val trimmed = cand.trim()
                    if (trimmed.isNotEmpty() && !isMalformedJsonSentinel(trimmed)) return trimmed
                }
            }
            return null
        }

        private fun decodeCwd(obj: JsonObject): String? {
            val keys = listOf("cwd", "current_working_directory", "working_directory")
            for (k in keys) {
                obj.stringOrNull(k)?.let { normalizeProjectPath(it)?.let { return it } }
            }
            return null
        }

        private fun decodeThreadIdentity(
            obj: JsonObject,
            metadata: Map<String, JSONValue>?,
            keys: List<String>,
        ): String? {
            for (k in keys) {
                obj.stringOrNull(k)?.let { normalizeIdentifier(it)?.let { return it } }
            }
            for (k in keys) {
                metadata?.get(k)?.stringValue?.let { normalizeIdentifier(it)?.let { return it } }
            }
            return null
        }

        private fun JsonObject.decodeDate(vararg keys: String): Instant? {
            for (k in keys) {
                this[k]?.let { el ->
                    when (el) {
                        is JsonPrimitive -> {
                            if (el.isString) {
                                parseIso8601(el.content)?.let { return it }
                            }
                            el.longOrNull?.let { return decodeUnixTimestamp(it.toDouble()) }
                            el.doubleOrNull?.let { return decodeUnixTimestamp(it) }
                        }
                        else -> {}
                    }
                }
            }
            return null
        }

        private fun parseIso8601(value: String): Instant? =
            runCatching { Instant.parse(value.trim()) }.getOrNull()
                ?: runCatching {
                    java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(
                        value.trim(),
                        Instant::from,
                    )
                }.getOrNull()

        private fun decodeUnixTimestamp(raw: Double): Instant {
            val seconds = if (raw > 10_000_000_000.0) raw / 1000.0 else raw
            return Instant.ofEpochMilli((seconds * 1000).toLong())
        }

        fun normalizeIdentifier(value: String?): String? {
            if (value == null) return null
            val t = value.trim()
            if (t.isEmpty()) return null
            if (isMalformedJsonSentinel(t)) return null
            return t
        }

        fun normalizeProjectPath(value: String?): String? {
            if (value == null) return null
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            if (!isLikelyFilesystemPath(trimmed)) return null
            if (isAdHocCodexCwd(trimmed)) return null
            if (trimmed == "/") return trimmed
            var normalized = trimmed.trimEnd('\\', '/')
            while ((normalized.endsWith("/") || normalized.endsWith("\\")) && normalized.length > 1) {
                normalized = normalized.dropLast(1)
            }
            return if (normalized.isEmpty()) "/" else normalized
        }

        private fun isLikelyFilesystemPath(value: String): Boolean =
            value == "/" ||
                value.startsWith("/") ||
                value.startsWith("~/") ||
                windowsDrivePathRegex.matches(value) ||
                value.startsWith("\\\\")

        private fun isAdHocCodexCwd(cwd: String): Boolean =
            adHocCodexCwdPatterns.any { it.containsMatchIn(cwd) }

        fun isGenericPlaceholderTitle(value: String?): Boolean {
            val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return false
            // Parity CodexThread.swift: legacy "Conversation" and `defaultDisplayTitle` ("New Thread") are placeholders.
            if (trimmed.equals("Conversation", ignoreCase = true)) return true
            if (trimmed.equals("New Thread", ignoreCase = true)) return true
            return false
        }
    }

    /**
     * Campi null/assenti dalla risposta server vengono presi dalla copia locale (parity iOS `mergedThread`).
     */
    fun withMissingDisplayFieldsFrom(existing: CodexThread): CodexThread =
        copy(
            title = title ?: existing.title,
            name = name ?: existing.name,
            preview = preview ?: existing.preview,
            createdAt = createdAt ?: existing.createdAt,
            updatedAt = updatedAt ?: existing.updatedAt,
            cwd = cwd ?: existing.cwd,
            metadata = metadata ?: existing.metadata,
            forkedFromThreadId = forkedFromThreadId ?: existing.forkedFromThreadId,
            parentThreadId = parentThreadId ?: existing.parentThreadId,
            agentId = agentId ?: existing.agentId,
            agentNickname = agentNickname ?: existing.agentNickname,
            agentRole = agentRole ?: existing.agentRole,
            model = model ?: existing.model,
            modelProvider = modelProvider ?: existing.modelProvider,
        )

    private val noProjectGroupKey = "__no_project__"

    val displayTitle: String
        get() {
            val cleanedTitle =
                title?.trim()?.takeUnless { Companion.isMalformedJsonSentinel(it) }
            val cleanedName =
                name?.trim()?.takeUnless { Companion.isMalformedJsonSentinel(it) }
            val cleanedAgentLabel = agentDisplayLabel?.trim()?.takeUnless { it.isEmpty() }
            val cleanedPreview =
                preview?.trim()?.takeUnless { Companion.isMalformedJsonSentinel(it) }
            val effectiveTitle =
                cleanedTitle?.takeUnless { Companion.isGenericPlaceholderTitle(it) }

            // Prefer explicit thread name (AI/user rename) over server title fallback.
            if (!cleanedName.isNullOrEmpty()) return cleanedName

            if (!cleanedAgentLabel.isNullOrEmpty()) {
                if (cleanedTitle.isNullOrEmpty() || Companion.isGenericPlaceholderTitle(cleanedTitle)) {
                    return cleanedAgentLabel
                }
            }

            // Server often mirrors the full first user prompt in `title` while `preview` holds the short headline.
            if (!cleanedTitle.isNullOrEmpty() &&
                !cleanedPreview.isNullOrEmpty() &&
                cleanedPreview.length < cleanedTitle.length
            ) {
                return cleanedPreview.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

            val resolved = effectiveTitle?.takeUnless { it.isEmpty() }
            if (resolved != null) return resolved

            if (!cleanedPreview.isNullOrEmpty()) {
                return cleanedPreview.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            return DEFAULT_DISPLAY_TITLE
        }

    val isSubagent: Boolean get() = parentThreadId != null

    val isForkedThread: Boolean get() = forkedFromThreadId != null

    val preferredSubagentLabel: String?
        get() {
            if (!isSubagent) return null
            agentDisplayLabel?.let { return it }
            for (candidate in listOf(name, title)) {
                val trimmed =
                    candidate?.trim()?.takeUnless { Companion.isMalformedJsonSentinel(it) } ?: continue
                if (trimmed.isEmpty() || Companion.isGenericPlaceholderTitle(trimmed)) continue
                return trimmed
            }
            return null
        }

    val derivedSubagentIdentity: Pair<String?, String?>?
        get() {
            val label = preferredSubagentLabel ?: return null
            val trimmed = label.trim()
            if (trimmed.isEmpty()) return null
            if (!trimmed.endsWith("]")) return Pair(trimmed, null)
            val open = trimmed.lastIndexOf('[')
            if (open < 0) return Pair(trimmed, null)
            val nickname = trimmed.substring(0, open).trim()
            val role = trimmed.substring(open + 1, trimmed.length - 1).trim()
            return Pair(
                nickname.ifEmpty { null },
                role.ifEmpty { null },
            )
        }

    val agentDisplayLabel: String?
        get() {
            val nickname = sanitizedAgentIdentity(agentNickname).orEmpty()
            val role = sanitizedAgentIdentity(agentRole).orEmpty()
            return when {
                nickname.isNotEmpty() && role.isNotEmpty() -> "$nickname [$role]"
                nickname.isNotEmpty() -> nickname
                role.isNotEmpty() -> role.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                else -> null
            }
        }

    val modelDisplayLabel: String?
        get() {
            val p = modelProvider?.trim()
            if (!p.isNullOrEmpty()) return p
            val m = model?.trim()
            if (!m.isNullOrEmpty()) return m
            return null
        }

    val normalizedProjectPath: String? get() = Companion.normalizeProjectPath(cwd)

    val gitWorkingDirectory: String?
        get() =
            normalizedProjectPath
                ?: cwd?.trim()?.takeIf { it.isNotEmpty() }

    val projectKey: String get() = normalizedProjectPath ?: noProjectGroupKey

    val projectDisplayName: String get() = projectDisplayLabelFor(normalizedProjectPath)

    val isManagedWorktreeProject: Boolean
        get() = projectIconSystemNameFor(normalizedProjectPath) == "arrow.triangle.branch"

    private fun sanitizedAgentIdentity(value: String?): String? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val lowered = trimmed.lowercase()
        if (lowered == "collabagenttoolcall" || lowered == "collabtoolcall") return null
        return trimmed
    }
}

fun projectDisplayLabelFor(normalizedProjectPath: String?): String {
    if (normalizedProjectPath == null) return "Cloud"
    val base = projectBaseDisplayName(normalizedProjectPath)
    val token = codexManagedWorktreeDisplayToken(normalizedProjectPath) ?: return base
    return "$base $token"
}

fun projectIconSystemNameFor(normalizedProjectPath: String?): String {
    if (normalizedProjectPath == null) return "cloud"
    return if (codexManagedWorktreeToken(normalizedProjectPath) == null) "laptopcomputer" else "arrow.triangle.branch"
}

private fun projectBaseDisplayName(normalizedProjectPath: String): String {
    val sanitized = normalizedProjectPath.trimEnd('\\', '/')
    val last =
        sanitized
            .substringAfterLast('/')
            .substringAfterLast('\\')
    if (last.isNotEmpty()) return last
    return sanitized.ifEmpty { normalizedProjectPath }
}

private fun codexManagedWorktreeToken(normalizedProjectPath: String): String? {
    val parts = normalizedProjectPath.replace('\\', '/').split('/').filter { it.isNotEmpty() }
    val idx = parts.indexOf("worktrees")
    if (idx <= 0 || parts[idx - 1] != ".codex") return null
    val tokenIdx = idx + 1
    if (tokenIdx >= parts.size) return null
    val token = parts[tokenIdx].trim()
    return token.ifEmpty { null }
}

private fun codexManagedWorktreeDisplayToken(normalizedProjectPath: String): String? {
    val token = codexManagedWorktreeToken(normalizedProjectPath) ?: return null
    return "[$token]"
}

private fun JsonObject.stringOrThrow(key: String): String =
    this[key]?.jsonPrimitive?.content
        ?: error("Missing key $key")

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
