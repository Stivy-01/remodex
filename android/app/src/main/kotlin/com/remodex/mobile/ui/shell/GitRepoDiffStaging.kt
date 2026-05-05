package com.remodex.mobile.ui.shell

import com.remodex.mobile.core.model.GitChangedFile

internal data class GitPathStagingUi(
    val staged: Boolean,
    val unstaged: Boolean,
    val isUntracked: Boolean = false,
) {
    companion object {
        fun fromPorcelain(status: String): GitPathStagingUi {
            val t = status.trim().ifEmpty { "  " }.padEnd(2, ' ')
            if (t.length >= 2 && t[0] == '?' && t[1] == '?') {
                return GitPathStagingUi(staged = false, unstaged = true, isUntracked = true)
            }
            return GitPathStagingUi(
                staged = t[0] != ' ',
                unstaged = t[1] != ' ',
            )
        }
    }
}

internal fun findGitStatusForPatchPath(
    patchPath: String,
    files: List<GitChangedFile>,
): GitChangedFile? {
    val want = normalizePath(patchPath.trim())
    if (want.isEmpty()) return null
    files.firstOrNull { normalizePath(it.path) == want }?.let { return it }
    files.firstOrNull {
        val p = normalizePath(it.path)
        want == p ||
            want.endsWith("/$p") ||
            p.endsWith("/$want") ||
            want.substringAfterLast('/') == p.substringAfterLast('/')
    }?.let { return it }
    return null
}

private fun normalizePath(path: String) = path.trim().trimStart('/').replace('\\', '/')
