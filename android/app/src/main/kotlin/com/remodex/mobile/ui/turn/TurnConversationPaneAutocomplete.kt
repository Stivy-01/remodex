package com.remodex.mobile.ui.turn

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexFuzzyFileMatch
import com.remodex.mobile.core.model.CodexSkillMetadata
import com.remodex.mobile.core.model.CodexTurnMention
import com.remodex.mobile.core.model.CodexTurnSkillMention
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.services.CodexLookupService

internal data class SkillAutocompleteSuggestion(
    val id: String,
    val label: String,
    val path: String?,
    val description: String? = null,
    val marketplace: String? = null,
    val isPlugin: Boolean = false,
    val searchBlob: String = listOfNotNull(id, label, path, description, marketplace).joinToString(" ").lowercase(),
)

internal suspend fun loadSkillAutocompleteSuggestions(
    repository: CodexRepository,
    cwd: String?,
): List<SkillAutocompleteSuggestion> {
    val lookupService = CodexLookupService(repository)
    val cwds = listOfNotNull(cwd?.trim()?.takeIf(String::isNotEmpty))
    val structured: List<CodexSkillMetadata>? =
        if (cwds.isNotEmpty()) runCatching { lookupService.listSkills(cwds) }.getOrNull() else null
    val results: List<SkillAutocompleteSuggestion> =
        structured?.map {
            SkillAutocompleteSuggestion(
                id = it.id,
                label = it.displayName?.trim()?.takeIf { value -> value.isNotEmpty() } ?: it.name,
                path = it.mentionPath,
                description = it.description,
                marketplace = it.marketplace,
                isPlugin = it.isPlugin,
                searchBlob = it.searchBlob,
            )
        } ?: runCatching {
            val preferredParams =
                cwd?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    JSONValue.Obj(
                        mapOf(
                            "paths" to JSONValue.Arr(listOf(JSONValue.Str(it))),
                        ),
                    )
                }
            val response =
                runCatching { repository.sendRequest("skills/list", preferredParams) }
                    .recoverCatching { repository.sendRequest("skills/list", JSONValue.Obj(emptyMap())) }
                    .recoverCatching { repository.sendRequest("skills/list", null) }
                    .getOrNull()
                    ?: return@runCatching emptyList()
            decodeSkillAutocompleteSuggestions(response.result)
        }.getOrDefault(emptyList())
    return results
}

internal fun decodeSkillAutocompleteSuggestions(result: JSONValue?): List<SkillAutocompleteSuggestion> {
    val out = linkedMapOf<String, SkillAutocompleteSuggestion>()

    fun collect(value: JSONValue?) {
        when (value) {
            is JSONValue.Arr -> value.elements.forEach(::collect)
            is JSONValue.Obj -> {
                val map = value.map
                val name = map["name"]?.stringValue?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    val path = map["path"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }
                    val key = name.lowercase()
                    out.putIfAbsent(
                        key,
                        SkillAutocompleteSuggestion(
                            id = name,
                            label =
                                map["displayName"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }
                                    ?: map["display_name"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() }
                                    ?: name,
                            path = path,
                            description = map["description"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                            marketplace = map["marketplace"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                            isPlugin =
                                map["kind"]?.stringValue.equals("plugin", ignoreCase = true) ||
                                    map["type"]?.stringValue.equals("plugin", ignoreCase = true) ||
                                    map["scope"]?.stringValue.equals("plugin", ignoreCase = true) ||
                                    path.orEmpty().startsWith("plugin://"),
                        ),
                    )
                }
                map["skills"]?.let(::collect)
                map["data"]?.let(::collect)
                map.values.forEach(::collect)
            }
            else -> Unit
        }
    }

    collect(result)
    return out.values.sortedBy { it.label.lowercase() }
}

internal fun extractThreadFileAutocompleteCandidates(messages: List<CodexMessage>): List<String> {
    val tokenRegex = Regex("""(?:^|[\s(])([A-Za-z0-9._/\-]+(?:\:[0-9]+)?)""")
    val out = linkedSetOf<String>()
    messages
        .filter { it.kind == CodexMessageKind.fileChange || it.kind == CodexMessageKind.commandExecution }
        .forEach { message ->
            tokenRegex.findAll(message.text).forEach { match ->
                val token = match.groupValues[1].trim()
                if (token.contains('/') || token.contains('.')) {
                    out += token
                }
            }
        }
    return out.toList()
}

internal fun buildComposerAutocompleteState(
    parse: TrailingComposerMentionParse?,
    skillSuggestions: List<SkillAutocompleteSuggestion>,
    fileCandidates: List<String>,
    fileMatches: List<CodexFuzzyFileMatch> = emptyList(),
    isThreadRunning: Boolean,
): TurnComposerAutocompleteState? {
    parse ?: return null
    val query = parse.payload.semanticValue.trim()
    return when (parse.payload.kind) {
        ComposerMentionKind.SlashCommand -> {
            val slashCommands =
                listOf(
                    "review" to "Review current changes",
                    "review-base" to "Review against default branch",
                    "compact" to "Compact conversation context",
                    "feedback" to "Report a bug or product feedback",
                    "status" to "Summarize runtime status",
                    "subagents" to "Delegate using subagents",
                    "fork" to "Fork current thread",
                ).filter { (cmd, _) ->
                    (query.isBlank() || cmd.startsWith(query, ignoreCase = true)) &&
                        !(isThreadRunning && (cmd == "fork" || cmd.startsWith("review")))
                }
                    .take(6)
                    .map { (cmd, subtitle) ->
                        TurnComposerAutocompleteItem(
                            id = "slash:$cmd",
                            title = "/$cmd",
                            subtitle = subtitle,
                            payload =
                                ComposerMentionChipPayload(
                                    kind = ComposerMentionKind.SlashCommand,
                                    displayLabel = cmd,
                                    semanticValue = cmd,
                                    rawSegment = "/$cmd",
                                ),
                            replacementText = "/$cmd ",
                        )
                    }
            if (slashCommands.isEmpty()) null else TurnComposerAutocompleteState("Commands", slashCommands)
        }
        ComposerMentionKind.Skill -> {
            val skills =
                skillSuggestions
                    .asSequence()
                    .filter {
                        query.isBlank() ||
                            it.label.contains(query, ignoreCase = true) ||
                            it.id.contains(query, ignoreCase = true) ||
                            it.searchBlob.contains(query, ignoreCase = true)
                    }
                    .take(6)
                    .map {
                        val kind = if (it.isPlugin) ComposerMentionKind.Plugin else ComposerMentionKind.Skill
                        val path = it.path?.trim()?.takeIf { value -> value.isNotEmpty() }
                        TurnComposerAutocompleteItem(
                            id = if (it.isPlugin) "plugin:${it.id}" else "skill:${it.id}",
                            title = if (it.isPlugin) it.label else "\$${it.label}",
                            subtitle = it.description ?: it.marketplace ?: path,
                            payload =
                                ComposerMentionChipPayload(
                                    kind = kind,
                                    displayLabel = it.label,
                                    semanticValue = if (it.isPlugin) path ?: "plugin://${it.id}" else it.id,
                                    rawSegment = "\$${it.id}",
                                    sourcePath = path,
                                ),
                            replacementText = "",
                        )
                    }.toList()
            if (skills.isEmpty()) null else TurnComposerAutocompleteState("Skills", skills)
        }
        ComposerMentionKind.Plugin -> null
        ComposerMentionKind.File -> {
            val candidates =
                buildList {
                    addAll(
                        fileMatches.map { match ->
                            TurnComposerAutocompleteItem(
                                id = "fuzzy:${match.id}",
                                title = "@${match.path}",
                                subtitle = match.root,
                                payload =
                                    ComposerMentionChipPayload(
                                        kind = ComposerMentionKind.File,
                                        displayLabel = match.fileName,
                                        semanticValue = match.path,
                                        rawSegment = "@${match.path}",
                                    ),
                                replacementText = "",
                            )
                        },
                    )
                    addAll(
                        fileCandidates.map { path ->
                            TurnComposerAutocompleteItem(
                                id = "file:$path",
                                title = "@$path",
                                payload =
                                    ComposerMentionChipPayload(
                                        kind = ComposerMentionKind.File,
                                        displayLabel = path.substringAfterLast('/', path),
                                        semanticValue = path,
                                        rawSegment = "@$path",
                                    ),
                                replacementText = "",
                            )
                        },
                    )
                }
            val files =
                candidates
                    .asSequence()
                    .filter {
                        query.isBlank() ||
                            it.title.contains(query, ignoreCase = true) ||
                            it.subtitle?.contains(query, ignoreCase = true) == true ||
                            it.payload.semanticValue.contains(query, ignoreCase = true) ||
                            (it.payload.displayLabel?.contains(query, ignoreCase = true) == true)
                    }
                    .distinctBy { it.payload.semanticValue.lowercase() }
                    .take(6)
                    .toList()
            if (files.isEmpty()) null else TurnComposerAutocompleteState("Files", files)
        }
    }
}

internal fun mergeMentionChipsIntoDraft(
    draft: String,
    chips: List<ComposerMentionChipPayload>,
): String {
    val tokenLines =
        chips
            .distinctBy { it.kind.name + ":" + it.semanticValue.lowercase() }
            .mapNotNull { chip ->
                when (chip.kind) {
                    ComposerMentionKind.File ->
                        chip.semanticValue.trim().takeIf { it.isNotEmpty() }?.let { "@$it" }
                    ComposerMentionKind.Skill ->
                        chip.semanticValue.trim().takeIf { it.isNotEmpty() }?.let { "\$$it" }
                    ComposerMentionKind.Plugin ->
                        chip.displayLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { "\$$it" }
                    ComposerMentionKind.SlashCommand -> null
                }
            }
    val trimmedDraft = draft.trim()
    if (tokenLines.isEmpty()) return trimmedDraft
    return if (trimmedDraft.isEmpty()) {
        tokenLines.joinToString("\n")
    } else {
        tokenLines.joinToString("\n") + "\n\n" + trimmedDraft
    }
}

internal fun mentionChipsToSkillMentions(chips: List<ComposerMentionChipPayload>): List<CodexTurnSkillMention> =
    chips.asSequence()
        .filter { it.kind == ComposerMentionKind.Skill }
        .mapNotNull { chip ->
            val id = chip.semanticValue.trim()
            if (id.isEmpty()) return@mapNotNull null
            CodexTurnSkillMention(
                id = id,
                name = chip.displayLabel?.trim()?.takeIf { it.isNotEmpty() },
                path = chip.sourcePath?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        .distinctBy { it.id.lowercase() + "|" + it.name.orEmpty().lowercase() + "|" + it.path.orEmpty().lowercase() }
        .toList()

internal fun mentionChipsToFileMentions(chips: List<ComposerMentionChipPayload>): List<CodexTurnMention> =
    chips.asSequence()
        .filter { it.kind == ComposerMentionKind.File || it.kind == ComposerMentionKind.Plugin }
        .mapNotNull { chip ->
            val path = chip.semanticValue.trim()
            if (path.isEmpty()) return@mapNotNull null
            CodexTurnMention(
                name = chip.displayLabel?.trim()?.takeIf { it.isNotEmpty() } ?: path.substringAfterLast('/', path),
                path = path,
            )
        }
        .distinctBy { it.name.lowercase() + "|" + it.path.lowercase() }
        .toList()

internal fun restoreMentionChips(
    skillMentions: List<CodexTurnSkillMention>,
    fileMentions: List<CodexTurnMention>,
): List<ComposerMentionChipPayload> =
    buildList {
        skillMentions.forEach { mention ->
            val id = mention.id.trim()
            if (id.isEmpty()) return@forEach
            add(
                ComposerMentionChipPayload(
                    kind = ComposerMentionKind.Skill,
                    displayLabel = mention.name?.trim()?.takeIf { it.isNotEmpty() } ?: skillIdToDisplayLabelLocal(id),
                    semanticValue = id,
                    rawSegment = "\$$id",
                ),
            )
        }
        fileMentions.forEach { mention ->
            val path = mention.path.trim()
            if (path.isEmpty()) return@forEach
            val isPlugin = path.startsWith("plugin://")
            add(
                ComposerMentionChipPayload(
                    kind = if (isPlugin) ComposerMentionKind.Plugin else ComposerMentionKind.File,
                    displayLabel = mention.name.trim().takeIf { it.isNotEmpty() } ?: path.substringAfterLast('/', path),
                    semanticValue = path,
                    rawSegment = if (isPlugin) "\$${mention.name}" else "@$path",
                    sourcePath = if (isPlugin) path else null,
                ),
            )
        }
    }

private fun skillIdToDisplayLabelLocal(skillIdBody: String): String {
    val tail = skillIdBody.substringAfterLast('/').substringAfterLast('.')
    return tail.replace('-', ' ')
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecaseChar() else ch
            }.toString()
        }
}

internal fun stripMergedMentionPrefix(
    text: String,
    skillMentions: List<CodexTurnSkillMention>,
    fileMentions: List<CodexTurnMention>,
): String {
    val chips = restoreMentionChips(skillMentions, fileMentions)
    if (chips.isEmpty()) return text
    val prefix = mergeMentionChipsIntoDraft("", chips)
    if (text == prefix) return ""
    val separator = "$prefix\n\n"
    return when {
        text.startsWith(separator) -> text.removePrefix(separator)
        text.startsWith(prefix) -> text.removePrefix(prefix).trimStart()
        else -> text
    }
}
