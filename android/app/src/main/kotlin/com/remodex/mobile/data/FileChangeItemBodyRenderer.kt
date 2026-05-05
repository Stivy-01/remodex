package com.remodex.mobile.data

import com.remodex.mobile.core.model.JSONValue

internal object FileChangeItemBodyRenderer {
    private data class DecodedFileChangeEntry(
        val path: String,
        val kind: String,
        val additions: Int?,
        val deletions: Int?,
        val diff: String?,
    )

    /**
     * Swift parity: CodexService+Incoming.decodeFileChangeItemBody renders a file-change body from `changes: [...]`
     * so the UI can parse paths/totals even when history doesn't store unified diff text.
     */
    fun renderFromIncomingItem(itemObject: Map<String, JSONValue>): String? {
        val changes = decodeFileChangeEntries(itemObject["changes"]) ?: emptyList()
        val diffFallback =
            firstNonBlankString(
                itemObject["diff"],
                itemObject["patch"],
                itemObject["unifiedDiff"],
                itemObject["unified_diff"],
                itemObject["unified_patch"],
                itemObject["result"]?.objectValue?.get("diff"),
                itemObject["result"]?.objectValue?.get("patch"),
            )

        val renderedChanges =
            changes.mapNotNull { entry ->
                if (entry.path.isBlank()) return@mapNotNull null
                if (looksLikeIgnoredImageReference(entry.path)) return@mapNotNull null
                if (looksLikeTempPreviewError(entry.diff.orEmpty())) return@mapNotNull null
                buildString {
                    val trimmedPath = entry.path.trim()
                    append("Path: ")
                    append(trimmedPath)
                    append("\nFilename: ")
                    append(fileNameFromPath(trimmedPath))
                    append("\nKind: ")
                    append(entry.kind.trim().ifBlank { "update" })
                    val adds = entry.additions
                    val dels = entry.deletions
                    if (adds != null || dels != null) {
                        append("\nTotals: +")
                        append(adds ?: 0)
                        append(" -")
                        append(dels ?: 0)
                    }
                    val diff = entry.diff?.trim().orEmpty()
                    if (diff.isNotEmpty()) {
                        append("\n\n```diff\n")
                        append(diff)
                        append("\n```")
                    }
                }
            }

        if (renderedChanges.isNotEmpty()) {
            return renderedChanges.joinToString(separator = "\n\n---\n\n")
        }

        diffFallback?.trim()?.takeIf {
            hasFileChangeEvidence(it) &&
                !looksLikeTempPreviewError(it) &&
                !looksLikeIgnoredImageReference(it)
        }?.let { fallback ->
            return "Status: completed\n\n```diff\n$fallback\n```"
        }

        return null
    }

    fun hasFileChangeEvidence(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.contains("\nPath:", ignoreCase = true) || trimmed.startsWith("Path:", ignoreCase = true)) return true
        if (trimmed.contains("diff --git ", ignoreCase = true)) return true
        val lines = trimmed.lineSequence().map { it.trimStart() }.toList()
        val hasOldPath = lines.any { it.startsWith("--- ") && !it.startsWith("--- /dev/null") }
        val hasNewPath = lines.any { it.startsWith("+++ ") && !it.startsWith("+++ /dev/null") }
        if (hasOldPath && hasNewPath) return true
        return lines.any { it.startsWith("rename from ") || it.startsWith("rename to ") || it.startsWith("Binary files ") }
    }

    private fun decodeFileChangeEntries(value: JSONValue?): List<DecodedFileChangeEntry>? {
        val arr = value?.arrayValue ?: return null
        return arr.mapNotNull { element ->
            val obj = element.objectValue ?: return@mapNotNull null
            val path =
                firstNonBlankString(
                    obj["path"],
                    obj["filePath"],
                    obj["file_path"],
                    obj["displayPath"],
                    obj["display_path"],
                )?.trim().orEmpty()
            val kind = firstNonBlankString(obj["kind"], obj["action"], obj["type"])?.trim().orEmpty()
            val totalsObj = obj["totals"]?.objectValue
            val additions =
                firstInt(
                    obj["additions"],
                    obj["added"],
                    totalsObj?.get("additions"),
                    totalsObj?.get("added"),
                )
            val deletions =
                firstInt(
                    obj["deletions"],
                    obj["deleted"],
                    totalsObj?.get("deletions"),
                    totalsObj?.get("deleted"),
                )
            val diff =
                firstNonBlankString(
                    obj["diff"],
                    obj["patch"],
                    obj["unifiedDiff"],
                    obj["unified_diff"],
                )
            if (path.isEmpty() && diff.isNullOrBlank()) return@mapNotNull null
            DecodedFileChangeEntry(
                path = path,
                kind = kind,
                additions = additions,
                deletions = deletions,
                diff = diff,
            )
        }
    }

    private fun firstNonBlankString(vararg values: JSONValue?): String? {
        for (value in values) {
            value?.stringValue?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private fun firstInt(vararg values: JSONValue?): Int? {
        for (value in values) {
            when (value) {
                is JSONValue.NumLong -> return value.value.toInt()
                is JSONValue.NumDouble -> return value.value.toInt()
                is JSONValue.Str -> value.value.trim().toIntOrNull()?.let { return it }
                else -> Unit
            }
        }
        return null
    }

    private fun fileNameFromPath(path: String): String =
        path
            .replace('\\', '/')
            .substringAfterLast('/')
            .ifBlank { path }

    private fun looksLikeIgnoredImageReference(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.contains("diff --git", ignoreCase = true)) return false
        if (trimmed.contains("\nPath:", ignoreCase = true) || trimmed.startsWith("Path:", ignoreCase = true)) return false
        if (trimmed.contains("```diff", ignoreCase = true)) return false
        if (trimmed.contains("Totals:", ignoreCase = true)) return false
        if (trimmed.contains("image preview", ignoreCase = true) || trimmed.contains("temp preview", ignoreCase = true)) {
            return looksLikeTempPreviewError(trimmed)
        }
        return trimmed.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.all { line ->
            line.matches(Regex("""^[\w./\\:@\-]*[*?][\w./\\:@\-]*$"""))
        }
    }

    private fun looksLikeTempPreviewError(text: String): Boolean {
        val lower = text.lowercase()
        if (!(lower.contains("preview") || lower.contains("temp"))) return false
        return lower.contains("image preview") &&
            (
                lower.contains("timed out") ||
                    lower.contains("too long") ||
                    lower.contains("too large") ||
                    lower.contains("no longer exists") ||
                    lower.contains("not found") ||
                    lower.contains("could not be converted") ||
                    lower.contains("failed") ||
                    lower.contains("cannot")
            )
    }
}

