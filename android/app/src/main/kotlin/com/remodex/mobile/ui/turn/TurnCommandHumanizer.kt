package com.remodex.mobile.ui.turn

internal data class CommandHumanizedLabel(
    val verb: String,
    val target: String,
)

internal object CommandHumanizer {
    fun humanize(
        raw: String,
        isRunning: Boolean,
    ): CommandHumanizedLabel {
        val command = unwrapShell(stripPhasePrefixes(raw))
        classifyFromFullInvocation(command, isRunning)?.let { return it }
        val tool = command.substringBefore(' ').substringAfterLast('/').lowercase()
        val args = command.substringAfter(' ', "").trim()
        return when (tool) {
            "cat", "nl", "head", "tail", "sed", "less", "more" ->
                CommandHumanizedLabel(if (isRunning) "Reading" else "Read", lastPath(args, "file"))
            "rg", "grep", "ag", "ack" ->
                CommandHumanizedLabel(if (isRunning) "Searching" else "Searched", searchTarget(args))
            "ls" ->
                CommandHumanizedLabel(if (isRunning) "Listing" else "Listed", lastPath(args, "directory"))
            "find", "fd" ->
                CommandHumanizedLabel(if (isRunning) "Finding" else "Found", lastPath(args, "files"))
            "mkdir" ->
                CommandHumanizedLabel(if (isRunning) "Creating" else "Created", lastPath(args, "directory"))
            "cp" ->
                CommandHumanizedLabel(if (isRunning) "Copying" else "Copied", lastPath(args, "file"))
            "mv" ->
                CommandHumanizedLabel(if (isRunning) "Moving" else "Moved", lastPath(args, "file"))
            "git" -> gitLabel(args, isRunning)
            "./gradlew", "gradlew", "gradlew.bat" ->
                CommandHumanizedLabel(if (isRunning) "Running" else "Ran", "Gradle")
            "npm", "pnpm", "yarn" ->
                CommandHumanizedLabel(if (isRunning) "Running" else "Ran", "$tool ${args.substringBefore(' ')}".trim())
            else ->
                CommandHumanizedLabel(if (isRunning) "Running" else "Ran", command)
        }
    }

    /**
     * Removes embedded phase tokens the bridge sometimes prefixes on the same line as the executable
     * (e.g. `completed> cat x`, `<running> rg foo`, `completed>ran "C:\...\powershell.exe" ...`).
     */
    internal fun stripPhasePrefixes(raw: String): String {
        var s = raw.trim()
        val noisePatterns =
            listOf(
                // `<running>` or `running >>` style
                Regex("""^(?:<)?(running|completed|failed|stopped|error)(?:>)?\s*[>»]+\s*""", RegexOption.IGNORE_CASE),
                // `completed >` or `completed>` (single chevron) before the real command
                Regex("""^(running|completed|failed|stopped|error|complete)\s*[>»]+\s*""", RegexOption.IGNORE_CASE),
                // Human-readable history: `ran ...`, `running ...`
                Regex("""^(running|completed|complete|ran|failed|stopped|error)\s+""", RegexOption.IGNORE_CASE),
            )
        repeat(8) {
            val before = s
            for (re in noisePatterns) {
                val next = s.replace(re, "").trim()
                if (next != s) {
                    s = next
                    break
                }
            }
            if (s == before) return s
        }
        return s
    }

    private fun classifyFromFullInvocation(
        line: String,
        isRunning: Boolean,
    ): CommandHumanizedLabel? {
        val l = line.lowercase()
        windowsPowerShellCommandLabel(line, isRunning)?.let { return it }

        fun pathFromQuotedOrNamed(): String {
            pathArgRegex.find(line)?.groupValues?.getOrNull(1)?.trim()?.let { return compactPath(it) }
            Regex(""""([^"]+\.\w+)"""").find(line)?.groupValues?.getOrNull(1)?.let { return compactPath(it) }
            Regex("'([^']+\\.[^']+)'").find(line)?.groupValues?.getOrNull(1)?.let { return compactPath(it) }
            return "file"
        }

        return when {
            Regex("""\bread_file\b|\bview_file\b|filesystem\.read|functions\.read\b""").containsMatchIn(l) ->
                CommandHumanizedLabel(if (isRunning) "Reading" else "Read", pathFromQuotedOrNamed())

            Regex("""\bworkspace_?read\b""").containsMatchIn(line) ->
                CommandHumanizedLabel(if (isRunning) "Reading" else "Read", pathFromQuotedOrNamed())

            Regex("""\b(workspace_?)?search\b|semantic_?search\b|code_?search\b""").containsMatchIn(l) ->
                CommandHumanizedLabel(if (isRunning) "Searching" else "Searched", "codebase")

            Regex("""(^|[\s/])(cat|nl|head|tail|less|more)(\s|$)""").containsMatchIn(l) ->
                CommandHumanizedLabel(
                    if (isRunning) "Reading" else "Read",
                    extractPathAfterTool(line, """(?:^|[\s/])(?:/[\w./-]+\/)?(cat|nl|head|tail|less|more)\s+"""),
                )

            Regex("""(^|[\s/])(rg|grep|ag|ack)(\s|$)""").containsMatchIn(l) ->
                CommandHumanizedLabel(if (isRunning) "Searching" else "Searched", searchTargetFromLine(line))

            else -> null
        }
    }

    /**
     * Normalized executable line for subtitle rules (unwraps shell prefixes, strips phase noise,
     * and extracts `-Command` payload from Windows PowerShell invocations when present).
     */
    internal fun effectiveCommandLineForRules(raw: String): String {
        val trimmed = unwrapShell(stripPhasePrefixes(raw)).trim()
        extractWindowsPowerShellCommandPayload(trimmed)?.let { return it.trim() }
        return trimmed
    }

    private fun extractWindowsPowerShellCommandPayload(trimmed: String): String? {
        val powershellPrefix =
            Regex(
                """^"?[a-zA-Z]:[/\\].*?[/\\](?:powershell|pwsh)(?:\.exe)?"?\s+""",
                RegexOption.IGNORE_CASE,
            )
        if (!powershellPrefix.containsMatchIn(trimmed)) return null
        return Regex(
            """(?i)(?:^|\s)-Command\s+(?:"([^"]*)"|'([^']*)'|([^\s].*))""",
        ).find(trimmed)
            ?.let { match ->
                match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun windowsPowerShellCommandLabel(
        line: String,
        isRunning: Boolean,
    ): CommandHumanizedLabel? {
        val trimmed = stripPhasePrefixes(line)
        val command =
            extractWindowsPowerShellCommandPayload(trimmed)
                ?: return null
        return CommandHumanizedLabel(
            verb = if (isRunning) "Esecuzione di" else "Esecuzione completata di",
            target = "'$command'",
        )
    }

    private val pathArgRegex =
        Regex("""(?:path|file|uri|target)\s*[:=]\s*["']?([^"'\s]+)["']?""", RegexOption.IGNORE_CASE)

    private fun extractPathAfterTool(
        line: String,
        toolPattern: String,
    ): String {
        Regex(toolPattern, RegexOption.IGNORE_CASE).find(line)?.let { match ->
            val rest = line.drop(match.range.last + 1).trim()
            return lastPath(rest, "file")
        }
        return "file"
    }

    private fun searchTargetFromLine(line: String): String {
        val afterTool =
            line.replaceFirst(Regex("""^.*?\b(rg|grep|ag|ack)\b\s*""", RegexOption.IGNORE_CASE), "").trim()
        return searchTarget(afterTool)
    }

    private fun unwrapShell(raw: String): String {
        var result = raw.trim()
        val lower = result.lowercase()
        val prefixes =
            listOf(
                "/usr/bin/bash -lc ",
                "/usr/bin/bash -c ",
                "/bin/bash -lc ",
                "/bin/bash -c ",
                "bash -lc ",
                "bash -c ",
                "/bin/sh -c ",
                "sh -c ",
                "cmd /c ",
                "powershell -command ",
            )
        prefixes.firstOrNull { lower.startsWith(it) }?.let { prefix ->
            result = result.drop(prefix.length).trim()
            if ((result.startsWith("\"") && result.endsWith("\"")) || (result.startsWith("'") && result.endsWith("'"))) {
                result = result.drop(1).dropLast(1)
            }
            result = result.substringAfter("&&", result).trim()
        }
        return result.substringBefore(" | ").trim()
    }

    private fun gitLabel(
        args: String,
        isRunning: Boolean,
    ): CommandHumanizedLabel {
        val sub = args.substringBefore(' ')
        val rest = args.substringAfter(' ', "").trim()
        return when (sub) {
            "status" -> CommandHumanizedLabel(if (isRunning) "Checking" else "Checked", "git status")
            "diff" -> CommandHumanizedLabel(if (isRunning) "Comparing" else "Compared", "changes")
            "log" -> CommandHumanizedLabel(if (isRunning) "Viewing" else "Viewed", "git log")
            "add" -> CommandHumanizedLabel(if (isRunning) "Staging" else "Staged", "changes")
            "commit" -> CommandHumanizedLabel(if (isRunning) "Committing" else "Committed", "changes")
            "push" -> CommandHumanizedLabel(if (isRunning) "Pushing" else "Pushed", "to remote")
            "pull" -> CommandHumanizedLabel(if (isRunning) "Pulling" else "Pulled", "from remote")
            "checkout", "switch" ->
                CommandHumanizedLabel(
                    if (isRunning) "Switching to" else "Switched to",
                    rest.split(' ').lastOrNull().orEmpty().ifBlank { "branch" },
                )
            else -> CommandHumanizedLabel(if (isRunning) "Running" else "Ran", "git $args")
        }
    }

    private fun searchTarget(args: String): String {
        val tokens = args.split(Regex("""\s+""")).filter { it.isNotBlank() && !it.startsWith("-") }
        val pattern = tokens.firstOrNull()?.trim('"', '\'')?.take(32) ?: "..."
        val path = tokens.drop(1).firstOrNull()?.let { " in ${compactPath(it)}" }.orEmpty()
        return "for $pattern$path"
    }

    private fun lastPath(
        args: String,
        fallback: String,
    ): String =
        args.split(Regex("""\s+"""))
            .asReversed()
            .firstOrNull { it.isNotBlank() && !it.startsWith("-") }
            ?.trim('"', '\'')
            ?.let { compactPath(it) }
            ?: fallback
}

/**
 * Timeline UI: prefer the leaf file/dir name only so long drives and trees do not widen rows.
 */
internal fun compactPath(path: String): String {
    val t = path.trim().trim('"', '\'')
    if (t.isEmpty()) return path.trim()
    val normalized = t.replace('\\', '/').trimEnd('/')
    return normalized.substringAfterLast('/').ifBlank { normalized }
}
