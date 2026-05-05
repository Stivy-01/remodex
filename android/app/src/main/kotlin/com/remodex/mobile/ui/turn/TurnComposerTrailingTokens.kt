package com.remodex.mobile.ui.turn

/**
 * J11: Parses the composer “suffix” before the caret (last whitespace-bounded segment) into
 * `@file`, `$skill`, or `/slash` mention tokens for chips and send-payload normalization.
 *
 * Parsing is intentionally tolerant of partial typing (e.g. lone `@`, `$cod`, `/`).
 */
internal object TurnComposerTrailingTokens {

    fun parseTrailingToken(
        text: String,
        caret: Int = text.length,
    ): TrailingComposerMentionParse? {
        if (caret <= 0) return null
        val cappedCaret = caret.coerceIn(0, text.length)
        trailingFileToken(text, cappedCaret)?.let { return it }
        val endExclusive = trailingTokenEndExclusive(text, cappedCaret)
        val start = trailingLexemeStart(text, endExclusive)
        if (start >= endExclusive) return null
        val raw = text.substring(start, endExclusive)
        val range = start until endExclusive
        return when {
            raw.startsWith("@") -> fileMention(raw, range)
            raw.startsWith("$") -> skillMention(raw, range)
            raw.startsWith("/") -> slashCommand(raw, range)
            else -> null
        }
    }

    private fun trailingFileToken(
        text: String,
        caret: Int,
    ): TrailingComposerMentionParse? {
        if (text.isEmpty()) return null
        val cappedCaret = caret.coerceIn(0, text.length)
        if (cappedCaret <= 0 || text[cappedCaret - 1].isWhitespace()) return null

        val prefix = text.substring(0, cappedCaret)
        val triggerIndex = prefix.lastIndexOf('@')
        if (triggerIndex < 0) return null
        if (triggerIndex > 0 && !text[triggerIndex - 1].isWhitespace()) return null

        var endExclusive = cappedCaret
        while (endExclusive > triggerIndex + 1 && text[endExclusive - 1].isTrailingMentionPunctuation()) {
            endExclusive--
        }
        val rawQuery = text.substring(triggerIndex + 1, endExclusive)
        val query = rawQuery.trim()
        if (query.isEmpty() || query.any { it == '\n' || it == '\r' }) return null
        if (!rawQuery.all { it.isAllowedFileQueryChar() }) return null
        if (!isAllowedFileAutocompleteQuery(query)) return null
        if (query.any { it.isWhitespace() } &&
            !query.contains('/') &&
            !query.contains('\\') &&
            !query.contains('.')
        ) {
            return null
        }

        val raw = text.substring(triggerIndex, endExclusive)
        return fileMention(raw, triggerIndex until endExclusive)
    }

    /**
     * Returns text with the trailing mention segment replaced (default: removed) and caret at the segment start.
     */
    fun replaceTrailingSegment(
        text: String,
        caret: Int = text.length,
        replacement: String = "",
        parse: TrailingComposerMentionParse? = parseTrailingToken(text, caret),
    ): TrailingComposerReplaceResult {
        val p =
            parse
                ?: return TrailingComposerReplaceResult(
                    text = text,
                    caret = caret,
                    replaced = false,
                )
        val range = p.rangeInText
        val newText =
            buildString(text.length + replacement.length) {
                append(text, 0, range.first)
                append(replacement)
                append(text, range.last + 1, text.length)
            }
        val newCaret = (range.first + replacement.length).coerceIn(0, newText.length)
        return TrailingComposerReplaceResult(
            text = newText,
            caret = newCaret,
            replaced = true,
        )
    }

    /** Index just after the last mention-token character before [caret] (exclusive end of active lexeme). */
    internal fun trailingTokenEndExclusive(
        text: String,
        caret: Int,
    ): Int {
        val capped = caret.coerceIn(0, text.length)
        var hi = capped - 1
        while (hi >= 0 && text[hi].isWhitespace()) hi--
        while (hi >= 0 && !text[hi].isWhitespace() && !text[hi].isMentionTokenChar()) hi--
        return hi + 1
    }

    /** Start of whitespace-bounded lexeme that ends at [endExclusive]. */
    internal fun trailingLexemeStart(
        text: String,
        endExclusive: Int,
    ): Int {
        if (endExclusive <= 0) return 0
        var lo = endExclusive - 1
        while (lo >= 0 && !text[lo].isWhitespace()) lo--
        return lo + 1
    }

    private fun fileMention(
        raw: String,
        range: IntRange,
    ): TrailingComposerMentionParse? {
        val path = raw.drop(1)
        if (!path.all { it.isAllowedFileQueryChar() }) return null
        if (!isAllowedFileAutocompleteQuery(path)) return null
        val chipLabel =
            when {
                path.isEmpty() -> ""
                path.contains('/') -> path.substringAfterLast('/', path).ifBlank { path }
                else -> path
            }
        val payload =
            ComposerMentionChipPayload(
                kind = ComposerMentionKind.File,
                displayLabel =
                    chipLabel.takeIf { it.isNotBlank() },
                semanticValue =
                    path.trim('/').takeIf { it.isNotBlank() }.orEmpty(),
                rawSegment = raw,
            )
        return TrailingComposerMentionParse(raw = raw, rangeInText = range, payload = payload)
    }

    private fun skillMention(
        raw: String,
        range: IntRange,
    ): TrailingComposerMentionParse? {
        val idBody = raw.drop(1)
        if (!idBody.isValidSkillMentionBody()) return null
        if (!idBody.any { it.isLetter() }) return null
        val payload =
            ComposerMentionChipPayload(
                kind = ComposerMentionKind.Skill,
                displayLabel = skillIdToDisplayLabel(idBody).takeIf { it.isNotBlank() },
                semanticValue = idBody,
                rawSegment = raw,
            )
        return TrailingComposerMentionParse(raw = raw, rangeInText = range, payload = payload)
    }

    private fun String.isValidSkillMentionBody(): Boolean =
        isNotBlank() &&
            first().isLetterOrDigit() &&
            drop(1).all { ch -> ch.isLetterOrDigit() || ch == '/' || ch == '.' || ch == '_' || ch == '-' || ch == ':' }

    private fun Char.isMentionTokenChar(): Boolean =
        isLetterOrDigit() ||
            this == '/' ||
            this == '_' ||
            this == '-'

    private fun Char.isTrailingMentionPunctuation(): Boolean =
        this == ',' || this == '.' || this == ';' || this == '!' || this == '?' ||
            this == ')' || this == ']' || this == '}'

    private fun Char.isAllowedFileQueryChar(): Boolean =
        isLetterOrDigit() ||
            isWhitespace() ||
            this == '/' ||
            this == '\\' ||
            this == '.' ||
            this == '_' ||
            this == '-' ||
            this == ':'

    private fun slashCommand(
        raw: String,
        range: IntRange,
    ): TrailingComposerMentionParse? {
        val body = raw.drop(1)
        if (
            body.isNotEmpty() &&
                !(body.first().isLetterOrDigit() || body.first() == '_')
        ) return null
        val validRest =
            body.all { ch ->
                ch.isLetterOrDigit() || ch == '-' || ch == '_'
            }
        if (!validRest && body.isNotEmpty()) return null

        val pretty =
            body.replace('-', ' ')
                .replace('_', ' ')
                .split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { w ->
                    w.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecaseChar() else ch
                    }.toString()
                }
        val payload =
            ComposerMentionChipPayload(
                kind = ComposerMentionKind.SlashCommand,
                displayLabel =
                    when {
                        body.isEmpty() -> null
                        else -> "/$pretty"
                    },
                semanticValue = body,
                rawSegment = raw,
            )
        return TrailingComposerMentionParse(raw = raw, rangeInText = range, payload = payload)
    }

    private fun skillIdToDisplayLabel(skillIdBody: String): String {
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

    private fun isAllowedFileAutocompleteQuery(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        if (containsUnsupportedColonSyntax(trimmed)) return false
        if (looksPathLike(trimmed)) return true
        return allowedExtensionlessFileNames.contains(trimmed)
    }

    private fun looksPathLike(token: String): Boolean {
        val withoutLineColumn = token.replace(Regex(""":[0-9]+(?::[0-9]+)?$"""), "")
        return withoutLineColumn.contains('/') ||
            withoutLineColumn.contains('\\') ||
            withoutLineColumn.contains('.')
    }

    private fun containsUnsupportedColonSyntax(token: String): Boolean {
        val colonIndex = token.indexOf(':')
        if (colonIndex < 0) return false
        val suffix = token.substring(colonIndex + 1)
        return suffix.isEmpty() || suffix.any { !it.isDigit() }
    }

    private val allowedExtensionlessFileNames =
        setOf(
            ".env",
            ".env.example",
            ".gitignore",
            ".node-version",
            ".nvmrc",
            "Brewfile",
            "Cartfile",
            "Dangerfile",
            "Dockerfile",
            "Gemfile",
            "LICENSE",
            "Makefile",
            "Podfile",
            "Procfile",
            "README",
            "Rakefile",
        )
}

internal enum class ComposerMentionKind {
    File,
    Skill,
    Plugin,
    SlashCommand,
}

/**
 * Values suitable for a mention chip UI and outbound bridge payloads (`semanticValue` differs by kind).
 */
internal data class ComposerMentionChipPayload(
    val kind: ComposerMentionKind,
    /** Short label shown on a chip */
    val displayLabel: String?,
    /** File path slash skill id slash slash-command body (without leading `/` for slash) */
    val semanticValue: String,
    /** Verbatim whitespace-bounded composer segment (`@foo`, `\$sk`, `/cmd`) */
    val rawSegment: String,
    /** Optional structured metadata path, used for selected skills/plugins. */
    val sourcePath: String? = null,
)

internal data class TrailingComposerMentionParse(
    val raw: String,
    val rangeInText: IntRange,
    val payload: ComposerMentionChipPayload,
)

internal data class TrailingComposerReplaceResult(
    val text: String,
    val caret: Int,
    val replaced: Boolean,
)
