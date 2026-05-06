package com.remodex.mobile.ui.turn

/**
 * Formats skill references in visible prose while leaving code spans and fenced blocks untouched.
 *
 * The bridge/history decoder may surface skills as `$skill-name`; iOS displays those as readable
 * names in message prose.
 */
internal object SkillReferenceFormatter {
    private val knownSkillPathMarkers =
        listOf(
            "/.codex/skills/",
            "/.agents/skills/",
        )

    fun formatVisibleProse(markdown: String): String {
        if (markdown.isBlank()) return markdown

        val out = StringBuilder(markdown.length)
        var inFence = false
        markdown.lineSequence().forEachIndexed { index, line ->
            if (index > 0) out.append('\n')
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence
                out.append(line)
            } else if (inFence) {
                out.append(line)
            } else {
                out.append(formatLine(line))
            }
        }
        return out.toString()
    }

    private fun formatLine(line: String): String {
        val protectedRanges = inlineCodeRanges(line)
        val withSkillLinks = replaceSkillMarkdownLinks(line, protectedRanges)
        val withMentions = replaceMentionTokens(withSkillLinks, inlineCodeRanges(withSkillLinks))
        return replaceGenericSkillPaths(withMentions, inlineCodeRanges(withMentions))
    }

    private fun replaceSkillMarkdownLinks(
        line: String,
        protectedRanges: List<IntRange>,
    ): String {
        return markdownLinkRegex.replace(line) { match ->
            if (match.range.overlapsAny(protectedRanges)) return@replace match.value
            val label = match.groupValues[1]
            val destination = match.groupValues[2]
            val skillName = skillNameFromReference(destination) ?: skillNameFromReference(label)
            skillName?.toDisplayName() ?: match.value
        }
    }

    private fun replaceMentionTokens(
        line: String,
        protectedRanges: List<IntRange>,
    ): String {
        return mentionRegex.replace(line) { match ->
            if (match.range.overlapsAny(protectedRanges)) return@replace match.value
            match.groupValues[1].toDisplayName()
        }
    }

    private fun replaceGenericSkillPaths(
        line: String,
        protectedRanges: List<IntRange>,
    ): String {
        return genericSkillPathRegex.replace(line) { match ->
            if (match.range.overlapsAny(protectedRanges)) return@replace match.value
            val skillName = skillNameFromReference(match.value) ?: return@replace match.value
            skillName.toDisplayName()
        }
    }

    private fun inlineCodeRanges(line: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var index = 0
        while (index < line.length) {
            val start = line.indexOf('`', index)
            if (start < 0) break
            val end = line.indexOf('`', start + 1)
            if (end < 0) break
            ranges += start..end
            index = end + 1
        }
        return ranges
    }

    private fun skillNameFromReference(value: String): String? {
        val trimmed = value.trim().trim('`', '"', '\'')
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("$")) return trimmed.drop(1).takeIfSkillName()
        val marker = "skill:"
        val markerIndex = trimmed.indexOf(marker, ignoreCase = true)
        if (markerIndex >= 0) {
            return trimmed.substring(markerIndex + marker.length).trim('/').takeIfSkillName()
        }
        return skillNameFromSkillPath(trimmed)
    }

    private fun skillNameFromSkillPath(rawReference: String): String? {
        var candidate = rawReference.trim()
        if (candidate.isEmpty()) return null
        while (candidate.isNotEmpty() && ",.;)]}".contains(candidate.last())) {
            candidate = candidate.dropLast(1)
        }
        if (candidate.startsWith("(")) {
            candidate = candidate.drop(1)
        }

        val normalized = normalizeReferencePath(candidate)
        val lower = normalized.lowercase()
        if (!lower.endsWith("/skill.md")) return null
        if (knownSkillPathMarkers.none { lower.contains(it) }) return null

        val parts = normalized.replace('\\', '/').split('/').filter { it.isNotBlank() }
        val skillIndex = parts.indexOf("skills")
        if (skillIndex < 0 || skillIndex + 1 >= parts.size) return null
        return parts[skillIndex + 1].takeIfSkillName()
    }

    private fun normalizeReferencePath(reference: String): String {
        var candidate = reference
        val queryIndex = candidate.indexOf('?')
        if (queryIndex >= 0) candidate = candidate.substring(0, queryIndex)
        val fragmentIndex = candidate.indexOf('#')
        if (fragmentIndex >= 0) candidate = candidate.substring(0, fragmentIndex)

        if (candidate.startsWith("file://", ignoreCase = true)) {
            return runCatching {
                java.net.URI(candidate).path ?: candidate.removePrefix("file://")
            }.getOrDefault(candidate.removePrefix("file://"))
        }
        return candidate
    }

    private fun String.takeIfSkillName(): String? =
        trim().takeIf { candidate ->
            candidate.isNotEmpty() &&
                candidate.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' || it == '/' }
        }

    private fun String.toDisplayName(): String =
        trim()
            .trimStart('$')
            .substringAfterLast('/')
            .replace('-', ' ')
            .replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
            }
            .ifBlank { this }

    private fun IntRange.overlapsAny(ranges: List<IntRange>): Boolean =
        ranges.any { first <= it.last && it.first <= last }

    private val mentionRegex = Regex("""(?<![\w`])\$([A-Za-z0-9][A-Za-z0-9._/-]*)(?![\w`])""")
    private val markdownLinkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")
    private val genericSkillPathRegex = Regex("""(?<![\w`])(?:[A-Za-z]:)?[A-Za-z0-9._~:/\\-]+/Skill\.md(?:\?[^\s)]*)?(?:#[^\s)]*)?""")
}
