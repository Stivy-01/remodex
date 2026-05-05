package com.remodex.mobile.ui.turn

import androidx.compose.runtime.staticCompositionLocalOf
import java.io.File

/**
 * When set (e.g. from [TurnConversationPane]), assistant markdown resolves relative `./path` links
 * against the active thread repo root when opening the repo diff sheet.
 */
val LocalThreadGitProjectRoot = staticCompositionLocalOf<String?> { null }

/**
 * Main shell installs this to open **Repository diff** from assistant markdown taps on repo paths.
 */
val LocalOpenRepoDiffForMarkdownLink = staticCompositionLocalOf<(String) -> Unit> { { } }

/**
 * Decide when a tapped markdown URL should route to repo diff vs [androidx.compose.ui.platform.LocalUriHandler].
 */
internal object RepoMarkdownFileLink {

    fun looksLikeLinkToLocalRepoFile(raw: String): Boolean {
        val s = raw.trim()
        if (s.isEmpty()) return false
        val lower = s.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return false
        if (lower.startsWith("mailto:") || lower.startsWith("tel:")) return false
        if ("://" in s && !lower.startsWith("file:")) return false
        val path = stripFileSchemeAndDecode(s)

        fun hasSlash() = '/' in path || '\\' in path
        fun nameFrom(p: String): String =
            normalizeSlashes(File(p.trim()).name).substringBefore('#').substringBefore('?').trim()

        val nm = nameFrom(path)
        val extOk = hasKnownSourceExtension(nm)

        val looksRel = path.startsWith("./") || path.startsWith("../")
        val looksAbsolute = Regex("^[A-Za-z]:\\\\").containsMatchIn(path) || path.startsWith("/")
        val looksLikePath = looksRel || looksAbsolute || hasSlash()

        return (looksLikePath && extOk) || ((!hasSlash()) && nm.isNotBlank() && extOk)
    }

    /** Last path segment (filename) — used for matching [GitRepoDiffRenderableRow.displayPath]. */
    fun canonicalFilenameQuery(decodedLink: String): String {
        val path = normalizeSlashes(stripFileSchemeAndDecode(decodedLink.trim()))
        val seg =
            (
                path
                    .substringAfterLast('/')
                    .trim()
                    .ifBlank { path }
                    .substringAfterLast('\\')
                    .trim()
            )
            .substringBefore('#')
            .substringBefore('?')
            .trim()
        return seg.ifBlank { path }.trim()
    }

    fun normalizePath(raw: String): String = normalizeSlashes(stripFileSchemeAndDecode(raw.trim()))

    /** Row label may be truncated or absolute; matching is path-segment tolerant. */
    fun rowMatchesQuery(displayPath: String, canonicalQuery: String): Boolean {
        val q =
            canonicalQuery.trim().substringAfterLast('/').substringAfterLast('\\').ifBlank {
                canonicalQuery.trim()
            }
        if (q.isEmpty()) return false
        val row = normalizeSlashes(displayPath.trim())
        if (row.equals(q, ignoreCase = true)) return true
        if (row.endsWith("/$q", ignoreCase = true)) return true
        if (row.endsWith("\\$q", ignoreCase = true)) return true

        val rowBase = row.substringAfterLast('/').substringAfterLast('\\').ifBlank { row }
        return rowBase.equals(q, ignoreCase = true)
    }

    private fun stripFileSchemeAndDecode(raw: String): String {
        var s =
            raw
                .removePrefix("file:")
                .removePrefix("File:")
                .trimStart()
                .trim()
        while (s.startsWith("/")) s = s.removePrefix("/").trimStart()
        // Windows file:/// C:/...
        try {
            if (s.contains('%')) {
                s = java.net.URLDecoder.decode(s, Charsets.UTF_8.name())
            }
        } catch (_: Exception) {
        }
        return s
    }

    private fun normalizeSlashes(value: String): String = value.trim().replace('\\', '/')

    private fun hasKnownSourceExtension(filename: String): Boolean {
        val l = filename.lowercase().trim()
        if (l.isEmpty()) return false
        return KNOWN_SUFFIXES.any { suf -> l.endsWith(suf, ignoreCase = true) }
    }

    private val KNOWN_SUFFIXES =
        listOf(
            ".gradle.kts",
            ".kt",
            ".kts",
            ".java",
            ".xml",
            ".gradle",
            ".toml",
            ".properties",
            ".md",
            ".txt",
            ".json",
            ".yaml",
            ".yml",
            ".swift",
            ".rs",
            ".hpp",
            ".cpp",
            ".cc",
            ".cxx",
            ".c",
            ".h",
            ".mjs",
            ".js",
            ".jsx",
            ".tsx",
            ".css",
            ".scss",
            ".patch",
            ".sql",
            ".lock",
            ".pro",
            ".webp",
            ".png",
            ".jpg",
            ".jpeg",
            ".gif",
            ".svg",
            ".ico",
            ".py",
            ".go",
            ".rb",
            ".php",
            ".html",
            ".htm",
            ".sh",
            ".bat",
            ".ps1",
        )
}
