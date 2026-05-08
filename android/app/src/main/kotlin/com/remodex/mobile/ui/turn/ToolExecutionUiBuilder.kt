package com.remodex.mobile.ui.turn

import android.content.res.Resources
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CommandExecutionDetails
import com.remodex.mobile.core.model.ExecutionStatus
import com.remodex.mobile.core.model.ToolExecutionUi
import com.remodex.mobile.data.TurnCommandExecutionPresentation
import com.remodex.mobile.data.TurnCommandExecutionPreviewMerge

internal fun mapExecutionStatusFromPreview(preview: TurnCommandExecutionPresentation): ExecutionStatus =
    when {
        preview.isStopped -> ExecutionStatus.Cancelled
        preview.isRunning -> ExecutionStatus.Running
        preview.isFailure -> ExecutionStatus.Failed
        else -> ExecutionStatus.Completed
    }

/**
 * Builds [ToolExecutionUi] without Android string resolution — for unit tests.
 */
internal fun buildToolExecutionUiPreview(
    preview: TurnCommandExecutionPresentation,
    details: CommandExecutionDetails?,
    rawForHumanize: String,
    statusTitle: (ExecutionStatus) -> String,
): ToolExecutionUi {
    val status = mapExecutionStatusFromPreview(preview)
    val label = TurnMarkdownRenderCache.humanizeCommand(rawForHumanize, preview.isRunning)
    val subtitle = ToolExecutionSubtitlePolicy.subtitle(rawForHumanize, label)
    val commandPreview = buildCommandPreview(rawForHumanize, preview.command)
    val canOpenDetails =
        preview.outputText?.isNotBlank() == true ||
            !preview.cwd.isNullOrBlank() ||
            preview.exitCode != null ||
            preview.durationMs != null ||
            TurnCommandExecutionPreviewMerge.hasUsefulFields(details)

    return ToolExecutionUi(
        status = status,
        title = statusTitle(status),
        subtitle = subtitle,
        commandPreview = commandPreview,
        durationMs = preview.durationMs?.toLong(),
        isExpandable = canOpenDetails,
    )
}

internal fun buildToolExecutionUi(
    preview: TurnCommandExecutionPresentation,
    details: CommandExecutionDetails?,
    rawForHumanize: String,
    resources: Resources,
): ToolExecutionUi =
    buildToolExecutionUiPreview(preview, details, rawForHumanize) { st ->
        when (st) {
            ExecutionStatus.Running -> resources.getString(R.string.turn_timeline_command_running)
            ExecutionStatus.Completed -> resources.getString(R.string.turn_timeline_command_completed)
            ExecutionStatus.Failed -> resources.getString(R.string.turn_timeline_command_failed)
            ExecutionStatus.Cancelled -> resources.getString(R.string.turn_timeline_command_stopped)
        }
    }

internal fun buildCommandPreview(
    rawForHumanize: String,
    mergedCommand: String,
): String? {
    val cleaned = CommandHumanizer.effectiveCommandLineForRules(rawForHumanize).trim()
    val merged = mergedCommand.trim()
    val base = cleaned.ifBlank { merged }.trim()
    if (base.isEmpty()) return null
    val singleLine =
        abbreviatedCommandPathsEmbedded(base.replace(Regex("""\s+"""), " ").trim())
    return singleLine.take(320).trimEnd()
}

/** Rewrites embedded Windows / long Unix paths to leaf names only (timeline + sheet preview). */
internal fun abbreviatedCommandPathsEmbedded(line: String): String {
    fun leafFromPathString(p: String): String =
        compactPath(p.trim().trim('"', '\'')).ifBlank { "…" }

    val quotedWindowsPathDouble = Regex(""""([a-zA-Z]:[^"]+)""""")
    val quotedWindowsPathSingle = Regex("""'([a-zA-Z]:[^']+)'""")
    val windowsPath = Regex("""[a-zA-Z]:\\(?:[^\\\s]+\\)*[^\\\s]+""")
    val unixLongPath = Regex("""/[a-zA-Z0-9._-]+(?:/[^/\s]+){3,}""")

    return line
        .replace(quotedWindowsPathDouble) { mr -> leafFromPathString(mr.groupValues[1]) }
        .replace(quotedWindowsPathSingle) { mr -> leafFromPathString(mr.groupValues[1]) }
        .replace(windowsPath) { leafFromPathString(it.value) }
        .replace(unixLongPath) { leafFromPathString(it.value) }
        .replace(Regex("""\s+"""), " ")
        .trim()
}

internal object ToolExecutionSubtitlePolicy {
    private const val MAX_LEN = 80

    fun subtitle(
        raw: String,
        @Suppress("UNUSED_PARAMETER") label: CommandHumanizedLabel,
    ): String {
        val effective = CommandHumanizer.effectiveCommandLineForRules(raw)
        val norm = effective.lowercase()

        if (gradleInvocation(norm)) {
            return when {
                Regex("""\b(test|connectedcheck|instrumenttest|check)\b""").containsMatchIn(norm) -> "Gradle tests"
                Regex("""\b(assemble|bundle|install|package|build)\b""").containsMatchIn(norm) -> "Gradle build"
                else -> "Gradle"
            }
        }

        if (Regex("""\bgit\s+""").containsMatchIn(norm)) {
            return when {
                Regex("""\bgit\s+status\b""").containsMatchIn(norm) -> "Git status"
                Regex("""\bgit\s+diff\b""").containsMatchIn(norm) -> "Git diff"
                Regex("""\bgit\s+log\b""").containsMatchIn(norm) -> "Git log"
                Regex("""\bgit\s+(pull|fetch)\b""").containsMatchIn(norm) -> "Remote update"
                Regex("""\bgit\s+push\b""").containsMatchIn(norm) -> "Remote push"
                Regex("""\bgit\s+(commit|add)\b""").containsMatchIn(norm) -> "Staging or commit"
                else -> "Git"
            }
        }

        if (Regex("""\b(npm|pnpm|yarn)\b""").containsMatchIn(norm)) {
            return when {
                Regex("""\b(npm|pnpm)\s+install\b|\byarn(\s+install)?\b|\b(npm|pnpm|yarn)\s+add\b""").containsMatchIn(norm) ->
                    "Installazione dipendenze"
                Regex("""\b(npm|pnpm|yarn)\s+run\s+build\b|\b(npm|pnpm|yarn)\s+build\b""").containsMatchIn(norm) ->
                    "Build pacchetti"
                else -> "Script npm/yarn"
            }
        }

        return shortFallback(effective)
    }

    private fun gradleInvocation(norm: String): Boolean =
        Regex("""(^|[\\/\s])(\.?[/\\])?gradlew(\.bat)?\b""").containsMatchIn(norm) ||
            Regex("""\bgradle\s+""").containsMatchIn(norm) ||
            Regex("""\bcmd\s+/c\s+.*gradlew""").containsMatchIn(norm)

    private fun shortFallback(displayLine: String): String {
        var s = abbreviatedCommandPathsEmbedded(displayLine)
        if (s.length > MAX_LEN) {
            s = s.take(MAX_LEN - 1).trimEnd() + "…"
        }
        return s.ifBlank { "Shell" }
    }
}
