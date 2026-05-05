package com.remodex.mobile.core.model

/**
 * Shared parsing helpers for timeline text that may include `::code-comment{...}` directives.
 */
internal object TurnCodeCommentDirectiveParsing {

    private const val directivePrefix = "::code-comment{"
    private val priorityPrefixRegex = Regex("""^\[P([0-3])]\s*""", RegexOption.IGNORE_CASE)

    fun parseCodeCommentDirectives(input: String): TurnCodeCommentDirectiveParseOutcome {
        if (!input.contains(directivePrefix)) {
            return TurnCodeCommentDirectiveParseOutcome(emptyList(), cleanedText = input)
        }

        val findings = mutableListOf<TurnCodeCommentDirectiveFinding>()
        val removedRanges = mutableListOf<IntRange>()

        for (directive in scanDirectives(input)) {
            parseFinding(directive.payload)?.let { findings += it }
            removedRanges += directive.range
        }

        val cleaned = buildString(input.length) {
            var cursor = 0
            removedRanges.sortBy { it.first }
            for (range in removedRanges) {
                if (cursor < range.first) append(input, cursor, range.first)
                cursor = range.last + 1
            }
            if (cursor < input.length) append(input, cursor, input.length)
        }

        return TurnCodeCommentDirectiveParseOutcome(
            findings = findings,
            cleanedText = collapseSurroundingWhitespace(cleaned),
        )
    }

    internal fun parseKvPairs(inner: String): Map<String, String> {
        return parseAttributes(inner) ?: emptyMap()
    }

    private fun parseFinding(payload: String): TurnCodeCommentDirectiveFinding? {
        val attrs = parseAttributes(payload) ?: return null
        val rawTitle = attrs["title"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val body = attrs["body"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val file = attrs["file"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val priorityPrefix = priorityPrefixRegex.find(rawTitle)
        val title = priorityPrefixRegex.replace(rawTitle, "").trim().takeIf { it.isNotEmpty() } ?: return null
        val inferredPriority = priorityPrefix?.groupValues?.getOrNull(1)?.toIntOrNull()
        val explicitPriority =
            attrs["priority"]?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
                ?: if (attrs.containsKey("priority")) return null else null
        val startLine =
            attrs["start"]?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
                ?: if (attrs.containsKey("start")) return null else null
        val endLine =
            attrs["end"]?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
                ?: if (attrs.containsKey("end")) return null else null
        val confidence =
            attrs["confidence"]?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                ?: if (attrs.containsKey("confidence")) return null else null
        val priority = explicitPriority ?: inferredPriority

        return TurnCodeCommentDirectiveFinding(
            id = "$file|${startLine ?: -1}|${endLine ?: -1}|$title",
            title = title,
            body = body,
            file = file,
            startLine = startLine,
            endLine = endLine,
            priority = priority,
            confidence = confidence,
        )
    }

    private data class DirectiveMatch(
        val range: IntRange,
        val payload: String,
    )

    private fun scanDirectives(input: String): List<DirectiveMatch> {
        val out = mutableListOf<DirectiveMatch>()
        var cursor = 0
        while (cursor < input.length) {
            val start = input.indexOf(directivePrefix, cursor)
            if (start < 0) break
            val payloadStart = start + directivePrefix.length
            var i = payloadStart
            var inQuoted = false
            var escaped = false
            var end: Int? = null

            while (i < input.length) {
                val ch = input[i]
                when {
                    escaped -> escaped = false
                    inQuoted && ch == '\\' -> escaped = true
                    ch == '"' -> inQuoted = !inQuoted
                    !inQuoted && ch == '}' -> {
                        end = i
                        break
                    }
                }
                i++
            }

            val close = end
            if (close == null) {
                out += DirectiveMatch(start until input.length, input.substring(payloadStart))
                break
            } else {
                out += DirectiveMatch(start..close, input.substring(payloadStart, close))
                cursor = close + 1
            }
        }
        return out
    }

    private fun parseAttributes(inner: String): Map<String, String>? {
        if (inner.isBlank()) return emptyMap()
        val out = linkedMapOf<String, String>()
        var i = 0

        fun skipSeparators() {
            while (i < inner.length && (inner[i].isWhitespace() || inner[i] == ',')) i++
        }

        while (i < inner.length) {
            skipSeparators()
            if (i >= inner.length) break

            val keyStart = i
            while (i < inner.length && inner[i] != '=' && inner[i] != ',' && !inner[i].isWhitespace()) i++
            val key = inner.substring(keyStart, i).trim()
            if (key.isEmpty()) return null

            while (i < inner.length && inner[i].isWhitespace()) i++
            if (i >= inner.length || inner[i] != '=') {
                return null
            }
            i++
            while (i < inner.length && inner[i].isWhitespace()) i++

            val value =
                if (i < inner.length && inner[i] == '"') {
                    i++
                    val sb = StringBuilder()
                    var escaped = false
                    var closed = false
                    while (i < inner.length) {
                        val ch = inner[i]
                        when {
                            escaped -> {
                                sb.append(
                                    when (ch) {
                                        '\\', '"' -> ch
                                        else -> ch
                                    },
                                )
                                escaped = false
                            }
                            ch == '\\' -> escaped = true
                            ch == '"' -> {
                                closed = true
                                i++
                                break
                            }
                            else -> sb.append(ch)
                        }
                        i++
                    }
                    if (!closed || escaped) return null
                    sb.toString()
                } else {
                    val valueStart = i
                    while (i < inner.length && inner[i] != ',' && !inner[i].isWhitespace()) i++
                    inner.substring(valueStart, i).trim()
                }

            if (key.isNotEmpty()) out[key] = value

            while (i < inner.length && inner[i].isWhitespace()) i++
            if (i < inner.length && inner[i] != ',') {
                val nextEquals = inner.indexOf('=', i)
                val nextComma = inner.indexOf(',', i).let { if (it < 0) inner.length else it }
                if (nextEquals < 0 || nextEquals > nextComma) return null
            }
        }
        return out
    }

    private fun collapseSurroundingWhitespace(s: String): String {
        return s
            .replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }
}

internal data class TurnCodeCommentDirectiveFinding(
    val id: String,
    val title: String,
    val body: String,
    val file: String,
    val startLine: Int?,
    val endLine: Int?,
    val priority: Int?,
    val confidence: Double?,
)

internal data class TurnCodeCommentDirectiveParseOutcome(
    val findings: List<TurnCodeCommentDirectiveFinding>,
    val cleanedText: String,
) {
    val hasFindings: Boolean
        get() = findings.isNotEmpty()
}

internal object TurnCodeCommentDirectiveFormatter {
    fun format(finding: TurnCodeCommentDirectiveFinding): String {
        val parts = mutableListOf(
            "title=\"${escapeQuoted(finding.title)}\"",
            "body=\"${escapeQuoted(finding.body)}\"",
            "file=\"${escapeQuoted(finding.file)}\"",
        )
        finding.startLine?.let { parts += "start=$it" }
        finding.endLine?.let { parts += "end=$it" }
        finding.priority?.let { parts += "priority=$it" }
        finding.confidence?.let { parts += "confidence=$it" }
        return "::code-comment{${parts.joinToString(" ")}}"
    }

    private fun escapeQuoted(value: String): String =
        buildString(value.length) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    else -> append(ch)
                }
            }
        }
}

/** Lightweight thinking-body cleanup (no heavy XML/tree parsing). */
internal object TurnThinkingDisclosureHints {
    private val thinkingTag =
        Regex(
            """(?is)<\s*(/?)\s*thinking\b[^>]*>""",
        )

    /**
     * Removes simple `<thinking>...</thinking>` fences (case-insensitive) for preview/summary.
     * Unbalanced tags are left unchanged.
     */
    fun stripSimpleThinkingTags(text: String): String {
        if (!text.contains('<')) return text
        val open = thinkingTag.find(text, 0) ?: return text
        val first = open.groupValues[1]
        if (first == "/") return text
        val afterOpen = open.range.last + 1
        val close = thinkingTag.find(text, afterOpen) ?: return text
        if (close.groupValues[1] != "/") return text
        val inner = text.substring(afterOpen, close.range.first)
        return buildString {
            append(text, 0, open.range.first)
            append(inner.trim())
            append(text, close.range.last + 1, text.length)
        }.let { collapseBlankRuns(it) }
    }

    /** User-visible excerpt for optional disclosure rows (trimmed, whitespace collapsed, bounded). */
    fun disclosureExcerpt(
        text: String,
        maxChars: Int = 400,
    ): String {
        val base = stripSimpleThinkingTags(text).trim()
        if (base.length <= maxChars) return base
        return base.take(maxChars).trimEnd() + "…"
    }

    private fun collapseBlankRuns(s: String): String =
        s.replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n")
}
