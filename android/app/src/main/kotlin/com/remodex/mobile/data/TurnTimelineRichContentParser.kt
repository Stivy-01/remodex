package com.remodex.mobile.data

import com.remodex.mobile.core.model.AIFileChange
import com.remodex.mobile.core.model.AIFileChangeKind
import com.remodex.mobile.core.model.AIUnifiedPatchParser
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexSubagentAction
import com.remodex.mobile.core.model.CommandExecutionDetails

internal data class TurnMarkdownSegment(
    val kind: TurnMarkdownSegmentKind,
    val text: String,
)

internal enum class TurnMarkdownSegmentKind {
    markdown,
    mermaid,
}

internal data class TurnFileChangeEntryPresentation(
    val path: String,
    val additions: Int,
    val deletions: Int,
    val label: String?,
    /** Unified diff fragment for this file (`diff --git` … slice), split from [TurnFileChangePresentation.rawPatchText]. */
    val patchChunkText: String? = null,
)

internal data class TurnFileChangePresentation(
    val headline: String,
    val summaryText: String,
    val entries: List<TurnFileChangeEntryPresentation>,
    val rawText: String,
    val rawPatchText: String?,
) {
    val hasDetails: Boolean
        get() = entries.isNotEmpty() || !rawPatchText.isNullOrBlank()

    val fileCount: Int
        get() = entries.size

    val totalAdditions: Int
        get() = entries.sumOf { it.additions }

    val totalDeletions: Int
        get() = entries.sumOf { it.deletions }

    /** True when the message body likely contains unified diff markup (fence or markers). */
    val likelyHasDiff: Boolean
        get() =
            !rawPatchText.isNullOrBlank() ||
                rawText.contains("diff --git") ||
                rawText.contains("+++ ")
}

internal data class TurnSubagentAgentPresentation(
    val threadId: String,
    val label: String,
    val role: String?,
    val model: String?,
    val prompt: String?,
    val status: String?,
    val message: String?,
)

internal data class TurnSubagentPresentation(
    val headline: String,
    val summaryText: String,
    val promptText: String?,
    val agents: List<TurnSubagentAgentPresentation>,
    val rawText: String,
    val normalizedTool: String? = null,
    val status: String? = null,
)

internal data class TurnCommandExecutionPresentation(
    val phase: String,
    val command: String,
    val outputText: String?,
    val rawText: String,
    val cwd: String? = null,
    val exitCode: Int? = null,
    val durationMs: Int? = null,
) {
    val isFailure: Boolean
        get() =
            phase.equals("failed", ignoreCase = true) ||
                phase.equals("error", ignoreCase = true) ||
                exitCode?.let { it != 0 } == true

    val isRunning: Boolean
        get() = phase.equals("running", ignoreCase = true)

    val isStopped: Boolean
        get() = phase.equals("stopped", ignoreCase = true)
}

internal object TurnCommandExecutionPreviewMerge {
    fun merge(
        parsed: TurnCommandExecutionPresentation,
        details: CommandExecutionDetails?,
    ): TurnCommandExecutionPresentation {
        if (details == null || !details.hasStructuredFields()) return parsed
        return parsed.copy(
            command = details.fullCommand.trim().takeIf { it.isNotEmpty() } ?: parsed.command,
            outputText = details.outputTail.takeIf { it.isNotBlank() } ?: parsed.outputText,
            cwd = details.cwd ?: parsed.cwd,
            exitCode = details.exitCode ?: parsed.exitCode,
            durationMs = details.durationMs ?: parsed.durationMs,
        )
    }

    fun hasUsefulFields(details: CommandExecutionDetails?): Boolean =
        details?.hasStructuredFields() == true

    private fun CommandExecutionDetails.hasStructuredFields(): Boolean =
        fullCommand.isNotBlank() ||
            !cwd.isNullOrBlank() ||
            exitCode != null ||
            durationMs != null ||
            outputTail.isNotBlank()
}

internal object TurnTimelineRichContentParser {
    private val summaryEntryRegex =
        Regex("""(?i)^(added|edited|deleted|renamed|updated)\s+(.+?)\s+\+(\d+)\s+-\s*(\d+)$""")
    private val pathLineRegex =
        Regex("""(?i)^(path|file|file path|filepath)\s*:\s*(.+)$""")
    private val kindLineRegex =
        Regex("""(?i)^kind\s*:\s*(.+)$""")
    private val totalsLineRegex =
        Regex("""(?i)^(totals?|changes?)\s*:\s*\+(\d+)\s*-\s*(\d+)$""")

    fun parseMermaidMarkdown(markdown: String): List<TurnMarkdownSegment>? {
        val source = markdown.trimEnd()
        if (source.isBlank()) return null

        val lines = source.split('\n')
        val segments = mutableListOf<TurnMarkdownSegment>()
        val markdownBuffer = mutableListOf<String>()
        val mermaidBuffer = mutableListOf<String>()
        var activeFence: MarkdownFence? = null
        var sawMermaid = false

        fun flushMarkdown() {
            if (markdownBuffer.isEmpty()) return
            segments +=
                TurnMarkdownSegment(
                    kind = TurnMarkdownSegmentKind.markdown,
                    text = markdownBuffer.joinToString("\n").trimEnd(),
                )
            markdownBuffer.clear()
        }

        fun flushMermaid() {
            segments +=
                TurnMarkdownSegment(
                    kind = TurnMarkdownSegmentKind.mermaid,
                    text = mermaidBuffer.joinToString("\n").trimEnd(),
                )
            mermaidBuffer.clear()
        }

        for (line in lines) {
            val trimmed = line.trimStart()
            val fence = activeFence
            if (fence == null) {
                val opening = parseOpeningFence(trimmed)
                if (opening != null && opening.language == "mermaid") {
                    flushMarkdown()
                    activeFence = opening
                    sawMermaid = true
                    continue
                }
                if (opening != null) {
                    activeFence = opening
                    markdownBuffer += line
                    continue
                }
                markdownBuffer += line
            } else {
                if (isClosingFence(trimmed, fence)) {
                    if (fence.language == "mermaid") {
                        flushMermaid()
                    } else {
                        markdownBuffer += line
                    }
                    activeFence = null
                } else {
                    if (fence.language == "mermaid") {
                        mermaidBuffer += line
                    } else {
                        markdownBuffer += line
                    }
                }
            }
        }

        val fence = activeFence
        if (fence?.language == "mermaid") {
            markdownBuffer += fence.openingLine
            markdownBuffer += mermaidBuffer
        }
        flushMarkdown()

        return if (sawMermaid) segments else null
    }

    /** Prefer fenced ```diff``` body; fallback to raw message text. */
    internal fun unifiedPatchForFileChangeMessage(message: CodexMessage): String {
        val rawText = message.text.trim()
        if (rawText.isEmpty()) return ""
        val fenced = extractFencedPatch(rawText)?.trim().orEmpty()
        if (fenced.isNotEmpty()) return fenced
        return rawText
    }

    fun parseFileChange(message: CodexMessage): TurnFileChangePresentation {
        val rawText = message.text.trim()
        val rawPatchText = extractFencedPatch(rawText)?.takeIf { it.isNotBlank() }
        val analysis = AIUnifiedPatchParser.analyze(rawPatchText ?: rawText)
        val patchEntries =
            if (analysis.fileChanges.isNotEmpty()) {
                analysis.fileChanges.map { change ->
                    TurnFileChangeEntryPresentation(
                        path = change.path,
                        additions = change.additions,
                        deletions = change.deletions,
                        label =
                            when (change.kind) {
                                AIFileChangeKind.create -> "Added"
                                AIFileChangeKind.delete -> "Deleted"
                                AIFileChangeKind.update ->
                                    if (change.isRenameOrModeOnly) "Renamed" else "Edited"
                            },
                    )
                }
            } else {
                emptyList()
            }
        val summaryEntries = parseSummaryEntries(rawText)
        val entriesMerged =
            when {
                patchEntries.isEmpty() -> summaryEntries
                patchEntries.all { it.additions == 0 && it.deletions == 0 } && summaryEntries.isNotEmpty() ->
                    summaryEntries
                summaryEntries.isNotEmpty() ->
                    enrichFileChangeEntriesFromSummary(patchEntries, summaryEntries)
                else -> patchEntries
            }
        val patchBody = rawPatchText?.trim().orEmpty()
        val entries =
            reconcileZeroCountsUsingFullPatchChunks(
                reconcileEntryCountsFromAttachedChunks(
                    attachPatchChunksToEntries(
                        fillZeroCountsFromPatch(entriesMerged, patchBody),
                        patchBody,
                    ),
                ),
                patchBody,
            )
        val summaryText =
            when {
                rawText.isNotEmpty() -> firstNonBlankLine(rawText) ?: rawText
                else -> "File change"
            }
        val headline =
            when {
                entries.isNotEmpty() -> {
                    val count = entries.size
                    val noun = if (count == 1) "file" else "files"
                    "$count $noun changed"
                }
                rawPatchText != null -> "Unified diff"
                rawText.isNotEmpty() -> "File change"
                else -> "File change"
            }
        return TurnFileChangePresentation(
            headline = headline,
            summaryText = summaryText,
            entries = entries,
            rawText = rawText,
            rawPatchText = rawPatchText,
        )
    }

    fun parseSubagent(message: CodexMessage): TurnSubagentPresentation {
        val action = message.subagentAction
        val rawText = message.text.trim()
        if (action != null) {
            return TurnSubagentPresentation(
                headline = action.summaryText,
                summaryText =
                    action.status.trim().takeIf { it.isNotEmpty() }
                        ?: action.summaryText,
                promptText = action.prompt?.trim()?.takeIf { it.isNotEmpty() },
                agents =
                    action.agentRows.map { agent ->
                        TurnSubagentAgentPresentation(
                            threadId = agent.threadId,
                            label = agent.displayLabel,
                            role = agent.role?.trim()?.takeIf { it.isNotEmpty() },
                            model = agent.model?.trim()?.takeIf { it.isNotEmpty() },
                            prompt = agent.prompt?.trim()?.takeIf { it.isNotEmpty() },
                            status = agent.fallbackStatus?.trim()?.takeIf { it.isNotEmpty() },
                            message = agent.fallbackMessage?.trim()?.takeIf { it.isNotEmpty() },
                        )
                    },
                rawText = rawText,
                normalizedTool = action.normalizedTool,
                status = action.status.trim().takeIf { it.isNotEmpty() },
            )
        }

        val summary = firstNonBlankLine(rawText) ?: "Subagent activity"
        return TurnSubagentPresentation(
            headline = summary,
            summaryText = rawText.ifBlank { summary },
            promptText = null,
            agents = emptyList(),
            rawText = rawText,
        )
    }

    fun parseCommandExecution(message: CodexMessage): TurnCommandExecutionPresentation {
        val rawText = message.text.trim()
        if (rawText.isEmpty()) {
            return TurnCommandExecutionPresentation(
                phase = if (message.isStreaming) "running" else "completed",
                command = "command",
                outputText = null,
                rawText = rawText,
            )
        }
        val lines = rawText.lines()
        val first = lines.firstOrNull()?.trim().orEmpty()
        val inlinedPhaseCommand =
            Regex(
                """^(?<phase>running|completed|failed|stopped|complete|success|error|cancelled|canceled)\s*[>»]+\s*(?<cmd>.*?)$""",
                RegexOption.IGNORE_CASE,
            ).matchEntire(first)
        if (inlinedPhaseCommand != null) {
            val parsedPhaseRaw = inlinedPhaseCommand.groups["phase"]!!.value.lowercase()
            val phase =
                when (parsedPhaseRaw) {
                    "complete", "success" -> "completed"
                    "cancelled", "canceled" -> "stopped"
                    "error" -> "failed"
                    else -> parsedPhaseRaw
                }
            var cmd = inlinedPhaseCommand.groups["cmd"]!!.value.trim()
            var skippedLines = 1
            if (cmd.isBlank()) {
                cmd = lines.drop(1).firstOrNull { it.trim().isNotBlank() }?.trim().orEmpty()
                skippedLines = 2
            }
            if (cmd.isBlank()) cmd = "command"
            val details = parseCommandMetadata(lines.drop(skippedLines))
            return TurnCommandExecutionPresentation(
                phase = phase,
                command = cmd,
                outputText = details.outputText,
                rawText = rawText,
                cwd = details.cwd,
                exitCode = details.exitCode,
                durationMs = details.durationMs,
            )
        }

        val firstParts = first.split(Regex("""\s+"""), limit = 2)
        val phaseCandidate = firstParts.firstOrNull().orEmpty().lowercase()
        val knownPhase =
            phaseCandidate in setOf("running", "completed", "complete", "success", "succeeded", "failed", "error", "stopped", "cancelled", "canceled")
        val phase =
            when {
                knownPhase && phaseCandidate == "complete" -> "completed"
                knownPhase && phaseCandidate == "success" -> "completed"
                knownPhase && phaseCandidate == "succeeded" -> "completed"
                knownPhase && phaseCandidate == "cancelled" -> "stopped"
                knownPhase && phaseCandidate == "canceled" -> "stopped"
                knownPhase -> phaseCandidate
                message.isStreaming -> "running"
                else -> "completed"
            }
        val firstContinuation = lines.drop(1).firstOrNull { it.trim().isNotBlank() }?.trim()
        val command =
            if (knownPhase) {
                val rest = firstParts.getOrNull(1)?.trim().orEmpty()
                if (rest.isNotBlank()) rest else firstContinuation ?: "command"
            } else {
                first.ifBlank { "command" }
            }
        val skippedLinesAfterCommand =
            if (knownPhase && firstParts.getOrNull(1)?.trim().orEmpty().isBlank() && firstContinuation != null &&
                command.trim() == firstContinuation
            ) {
                2
            } else {
                1
            }
        val details = parseCommandMetadata(lines.drop(skippedLinesAfterCommand))
        return TurnCommandExecutionPresentation(
            phase = phase,
            command = command,
            outputText = details.outputText,
            rawText = rawText,
            cwd = details.cwd,
            exitCode = details.exitCode,
            durationMs = details.durationMs,
        )
    }

    private data class ParsedCommandMetadata(
        val outputText: String?,
        val cwd: String?,
        val exitCode: Int?,
        val durationMs: Int?,
    )

    private fun parseCommandMetadata(lines: List<String>): ParsedCommandMetadata {
        var cwd: String? = null
        var exitCode: Int? = null
        var durationMs: Int? = null
        val outputLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            val kv = metadataKeyValue(trimmed)
            if (kv != null) {
                val (key, value) = kv
                when (key) {
                    "cwd",
                    "directory",
                    "workingdirectory",
                    "workingdir",
                    -> {
                        cwd = value.takeIf { it.isNotBlank() } ?: cwd
                        continue
                    }
                    "exitcode",
                    "exit",
                    "code",
                    -> {
                        exitCode = value.toIntOrNull() ?: exitCode
                        continue
                    }
                    "duration",
                    "durationms",
                    "elapsed",
                    "elapsedms",
                    -> {
                        durationMs = parseDurationMillis(value) ?: durationMs
                        continue
                    }
                }
            }
            outputLines += line
        }

        val output =
            outputLines
                .joinToString("\n")
                .trim()
                .takeIf { it.isNotEmpty() }
        return ParsedCommandMetadata(
            outputText = output,
            cwd = cwd,
            exitCode = exitCode,
            durationMs = durationMs,
        )
    }

    private fun metadataKeyValue(line: String): Pair<String, String>? {
        val separator = line.indexOf(':').takeIf { it >= 0 } ?: line.indexOf('=').takeIf { it >= 0 } ?: return null
        val key =
            line.take(separator)
                .trim()
                .lowercase()
                .replace(Regex("""[\s_-]+"""), "")
        val value = line.drop(separator + 1).trim()
        if (key.isEmpty() || value.isEmpty()) return null
        return key to value
    }

    private fun parseDurationMillis(raw: String): Int? {
        val normalized = raw.trim().lowercase()
        normalized.toIntOrNull()?.let { return it }
        Regex("""^(\d+(?:\.\d+)?)\s*(ms|s|sec|secs|second|seconds|m|min|mins|minute|minutes)?$""")
            .matchEntire(normalized)
            ?.let { match ->
                val amount = match.groupValues[1].toDoubleOrNull() ?: return null
                return when (match.groupValues[2]) {
                    "", "ms" -> amount.toInt()
                    "s", "sec", "secs", "second", "seconds" -> (amount * 1000).toInt()
                    "m", "min", "mins", "minute", "minutes" -> (amount * 60_000).toInt()
                    else -> null
                }
            }
        return null
    }

    private fun parseSummaryEntries(rawText: String): List<TurnFileChangeEntryPresentation> {
        val lines = rawText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return emptyList()
        val entries = mutableListOf<TurnFileChangeEntryPresentation>()
        var path: String? = null
        var additions: Int? = null
        var deletions: Int? = null
        var label: String? = null

        fun flush() {
            val resolvedPath = path?.trim()?.takeIf { it.isNotEmpty() } ?: return
            val resolvedAdditions = additions ?: 0
            val resolvedDeletions = deletions ?: 0
            entries +=
                TurnFileChangeEntryPresentation(
                    path = resolvedPath,
                    additions = resolvedAdditions,
                    deletions = resolvedDeletions,
                    label = label,
                )
            path = null
            additions = null
            deletions = null
            label = null
        }

        for (line in lines) {
            summaryEntryRegex.matchEntire(line)?.let { match ->
                flush()
                label = match.groupValues[1].replaceFirstChar { it.uppercase() }
                path = match.groupValues[2]
                additions = match.groupValues[3].toIntOrNull()
                deletions = match.groupValues[4].toIntOrNull()
                flush()
                continue
            }

            pathLineRegex.matchEntire(line)?.let { match ->
                flush()
                path = match.groupValues[2]
                continue
            }

            kindLineRegex.matchEntire(line)?.let { match ->
                label = match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
                continue
            }

            totalsLineRegex.matchEntire(line)?.let { match ->
                additions = match.groupValues[2].toIntOrNull()
                deletions = match.groupValues[3].toIntOrNull()
                flush()
                continue
            }

            if (path != null && additions == null && deletions == null) {
                // Continue collecting multi-line summaries until we find totals or another entry.
                continue
            }
            flush()
        }

        flush()
        return entries
    }

    private fun normalizePathKey(path: String): String =
        path.replace('\\', '/').trim().lowercase()

    private fun fileBaseName(path: String): String =
        path.replace('\\', '/').trim().substringAfterLast('/').ifBlank { path.trim() }

    private fun enrichFileChangeEntriesFromSummary(
        patchEntries: List<TurnFileChangeEntryPresentation>,
        summaryEntries: List<TurnFileChangeEntryPresentation>,
    ): List<TurnFileChangeEntryPresentation> {
        if (summaryEntries.isEmpty()) return patchEntries
        val byPath = summaryEntries.associateBy { normalizePathKey(it.path) }
        val byBase = summaryEntries.groupBy { fileBaseName(it.path).lowercase() }
        return patchEntries.map { p ->
            if (p.additions != 0 || p.deletions != 0) return@map p
            val base = fileBaseName(p.path).lowercase()
            val s =
                byPath[normalizePathKey(p.path)]
                    ?: byBase[base]?.singleOrNull()
                    ?: (if (patchEntries.size == 1) summaryEntries.singleOrNull() else null)
                    ?: byBase[base]?.firstOrNull()
            if (s != null && (s.additions > 0 || s.deletions > 0)) {
                p.copy(additions = s.additions, deletions = s.deletions, label = p.label ?: s.label)
            } else {
                p
            }
        }
    }

    private fun pathsLikelySameChunkToEntry(chunkPath: String, entryPath: String): Boolean {
        val a = normalizePathKey(chunkPath)
        val b = normalizePathKey(entryPath)
        if (a.isNotEmpty() && b.isNotEmpty()) {
            if (a == b) return true
            if (a.endsWith("/$b")) return true
            if (b.endsWith("/$a")) return true
            val bn = "/" + fileBaseName(entryPath).lowercase().trim()
            if (bn.length > 1 && (a.endsWith(bn) || b.endsWith(bn))) return true
        }
        val ba = fileBaseName(chunkPath).lowercase()
        val bb = fileBaseName(entryPath).lowercase()
        return ba.isNotBlank() && ba == bb
    }

    private fun attachPatchChunksToEntries(
        entries: List<TurnFileChangeEntryPresentation>,
        patchBody: String,
    ): List<TurnFileChangeEntryPresentation> {
        val body = patchBody.trim()
        if (body.isEmpty() || entries.isEmpty()) {
            return entries.map { it.copy(patchChunkText = null) }
        }
        val pool =
            AIUnifiedPatchParser.splitUnifiedPatchIntoFileChunks(body)
                .map { it.first to it.second }
                .toMutableList()
        if (pool.isEmpty()) return entries.map { it.copy(patchChunkText = null) }

        fun takeChunkAt(index: Int): String? {
            if (index !in pool.indices) return null
            return pool.removeAt(index).second
        }

        fun takeChunkWhere(predicate: (String) -> Boolean): String? {
            val ix = pool.indexOfFirst { (p, _) -> predicate(p) }
            if (ix < 0) return null
            return takeChunkAt(ix)
        }

        val assigned = MutableList<String?>(entries.size) { null }
        entries.forEachIndexed { i, entry ->
            val key = normalizePathKey(entry.path)
            takeChunkWhere { normalizePathKey(it) == key }?.let { assigned[i] = it }
                ?: run {
                    val base = fileBaseName(entry.path).lowercase()
                    val matches =
                        pool.withIndex().filter { fileBaseName(it.value.first).lowercase() == base }
                    if (matches.size == 1) {
                        val ix = matches.first().index
                        assigned[i] = takeChunkAt(ix)
                    }
                }
        }

        entries.forEachIndexed { i, entry ->
            if (assigned[i] != null) return@forEachIndexed
            val candidates =
                pool.withIndex().filter { pathsLikelySameChunkToEntry(it.value.first, entry.path) }
            if (candidates.size == 1) {
                assigned[i] = takeChunkAt(candidates.single().index)
            }
        }

        // Entries can stay unassigned while the pool still has chunks (e.g. extra files in the
        // diff, or an earlier pass skipped due to ambiguous basename). Drain when one remaining
        // chunk matches exactly one still-missing entry.
        while (pool.isNotEmpty()) {
            val missingIdx = assigned.withIndex().filter { it.value == null }.map { it.index }
            if (missingIdx.isEmpty()) break
            var paired: Pair<Int, Int>? = null // entry index, pool index
            for (pi in pool.indices) {
                val chunkPath = pool[pi].first
                val targets = missingIdx.filter { ei -> pathsLikelySameChunkToEntry(chunkPath, entries[ei].path) }
                if (targets.size == 1) {
                    paired = targets.single() to pi
                    break
                }
            }
            if (paired == null) break
            val (ei, pi) = paired
            assigned[ei] = pool.removeAt(pi).second
        }

        val missing = assigned.withIndex().filter { it.value == null }.map { it.index }
        if (missing.size == pool.size && pool.isNotEmpty()) {
            val bodies = pool.map { it.second }
            pool.clear()
            missing.zip(bodies).forEach { (idx, text) -> assigned[idx] = text }
        }

        return entries.mapIndexed { i, e -> e.copy(patchChunkText = assigned[i]) }
    }

    private fun diffLineCountsFromUnifiedChunk(chunk: String): Pair<Int, Int> {
        val trimmed = chunk.trim()
        if (trimmed.isEmpty()) return 0 to 0
        val analysis = AIUnifiedPatchParser.analyze(trimmed)
        val change =
            analysis.fileChanges.singleOrNull()
                ?: analysis.fileChanges.firstOrNull { it.additions > 0 || it.deletions > 0 }
        if (change != null && (change.additions > 0 || change.deletions > 0)) {
            return change.additions to change.deletions
        }
        return countDiffBodyAdditionsDeletions(trimmed)
    }

    /**
     * Fills +/- tallies once per-file fragments are known — [fillZeroCountsFromPatch] can miss when
     * summary paths mismatch diff headers until [attachPatchChunksToEntries] runs.
     */
    private fun reconcileEntryCountsFromAttachedChunks(
        entries: List<TurnFileChangeEntryPresentation>,
    ): List<TurnFileChangeEntryPresentation> =
        entries.map { entry ->
            if (entry.additions > 0 || entry.deletions > 0) return@map entry
            val chunk = entry.patchChunkText?.trim()?.takeIf { it.isNotBlank() } ?: return@map entry
            val (add, del) = diffLineCountsFromUnifiedChunk(chunk)
            if (add > 0 || del > 0) entry.copy(additions = add, deletions = del) else entry
        }

    /** When UI falls back to the full fenced patch for a row, still show +/- if one chunk uniquely matches the path. */
    private fun reconcileZeroCountsUsingFullPatchChunks(
        entries: List<TurnFileChangeEntryPresentation>,
        patchBody: String,
    ): List<TurnFileChangeEntryPresentation> {
        val body = patchBody.trim()
        if (body.isEmpty()) return entries
        val chunks = AIUnifiedPatchParser.splitUnifiedPatchIntoFileChunks(body)
        return entries.map { entry ->
            if (entry.additions > 0 || entry.deletions > 0) return@map entry
            val matching =
                chunks.filter { (p, _) -> pathsLikelySameChunkToEntry(p, entry.path) }.map { it.second }
            if (matching.size != 1) return@map entry
            val chunkText = matching.single()
            val (add, del) = diffLineCountsFromUnifiedChunk(chunkText)
            if (add > 0 || del > 0) entry.copy(additions = add, deletions = del) else entry
        }
    }

    private fun fillZeroCountsFromPatch(
        entries: List<TurnFileChangeEntryPresentation>,
        patchText: String,
    ): List<TurnFileChangeEntryPresentation> {
        if (patchText.isBlank() || entries.isEmpty()) return entries
        val chunks = AIUnifiedPatchParser.splitUnifiedPatchIntoFileChunks(patchText)
        if (chunks.isEmpty()) return entries

        fun lookupChange(entryPath: String): AIFileChange? {
            val key = normalizePathKey(entryPath)
            val base = fileBaseName(entryPath).lowercase()
            val bodies =
                chunks
                    .filter { (p, _) ->
                        normalizePathKey(p) == key ||
                            fileBaseName(p).lowercase() == base ||
                            pathsLikelySameChunkToEntry(p, entryPath)
                    }
                    .map { it.second }
            for (body in bodies) {
                val analysis = AIUnifiedPatchParser.analyze(body)
                val change =
                    analysis.fileChanges.singleOrNull()
                        ?: analysis.fileChanges.firstOrNull { it.additions > 0 || it.deletions > 0 }
                        ?: continue
                if (change.additions > 0 || change.deletions > 0) return change
            }
            if (chunks.size == 1 && entries.size == 1) {
                val body = chunks.first().second
                AIUnifiedPatchParser
                    .analyze(body)
                    .fileChanges
                    .singleOrNull()
                    ?.takeIf { it.additions > 0 || it.deletions > 0 }
                    ?.let { return it }
                val (add, del) = countDiffBodyAdditionsDeletions(body)
                if (add > 0 || del > 0) {
                    return AIFileChange(
                        path = entryPath,
                        kind = AIFileChangeKind.update,
                        additions = add,
                        deletions = del,
                        isBinary = false,
                        isRenameOrModeOnly = false,
                    )
                }
            }
            return null
        }

        return entries.map { e ->
            if (e.additions != 0 || e.deletions != 0) return@map e
            val change = lookupChange(e.path) ?: return@map e
            e.copy(additions = change.additions, deletions = change.deletions)
        }
    }

    private fun countDiffBodyAdditionsDeletions(body: String): Pair<Int, Int> {
        var additions = 0
        var deletions = 0
        for (line in body.split('\n')) {
            val first = line.firstOrNull() ?: continue
            if (first == '+' && !line.startsWith("+++")) additions++
            else if (first == '-' && !line.startsWith("---")) deletions++
        }
        return additions to deletions
    }

    private fun extractFencedPatch(rawText: String): String? {
        val lines = rawText.split('\n')
        var insideFence = false
        var fenceMarker: String? = null
        var fenceLanguage: String? = null
        var skippingNonDiffFence = false
        var skippingFenceMarker: String? = null
        val buffer = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trimStart()
            if (skippingNonDiffFence) {
                val sm = skippingFenceMarker
                if (sm != null && trimmed.startsWith(sm) && trimmed.drop(sm.length).isBlank()) {
                    skippingNonDiffFence = false
                    skippingFenceMarker = null
                }
                continue
            }
            if (!insideFence) {
                val marker =
                    when {
                        trimmed.startsWith("```") -> "```"
                        trimmed.startsWith("~~~") -> "~~~"
                        else -> null
                    }
                if (marker != null) {
                    val language =
                        trimmed
                            .drop(marker.length)
                            .trim()
                            .substringBefore(' ')
                            .trim()
                            .lowercase()
                    if (language.isNotEmpty() && language !in setOf("diff", "patch", "unifieddiff")) {
                        skippingNonDiffFence = true
                        skippingFenceMarker = marker
                        continue
                    }
                    insideFence = true
                    fenceMarker = marker
                    fenceLanguage = language
                    continue
                }
            } else {
                val currentMarker = fenceMarker
                if (currentMarker != null && trimmed.startsWith(currentMarker)) {
                    val joined = buffer.joinToString("\n").trimEnd()
                    if (joined.isNotBlank()) return joined
                    insideFence = false
                    fenceMarker = null
                    fenceLanguage = null
                    buffer.clear()
                    continue
                }
                buffer += line
            }
        }

        if (insideFence && fenceLanguage in setOf("diff", "patch", "unifieddiff")) {
            return buffer.joinToString("\n").trimEnd().takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun firstNonBlankLine(rawText: String): String? =
        rawText.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }

    private data class MarkdownFence(
        val markerChar: Char,
        val markerLength: Int,
        val language: String?,
        val openingLine: String,
    )

    private fun parseOpeningFence(trimmedLine: String): MarkdownFence? {
        val markerChar =
            when {
                trimmedLine.startsWith("```") -> '`'
                trimmedLine.startsWith("~~~") -> '~'
                else -> return null
            }
        val markerLength = trimmedLine.takeWhile { it == markerChar }.length
        if (markerLength < 3) return null
        val language =
            trimmedLine
                .drop(markerLength)
                .trim()
                .substringBefore(' ')
                .trim()
                .lowercase()
                .takeIf { it.isNotEmpty() }
        return MarkdownFence(
            markerChar = markerChar,
            markerLength = markerLength,
            language = language,
            openingLine = trimmedLine,
        )
    }

    private fun isClosingFence(
        trimmedLine: String,
        fence: MarkdownFence,
    ): Boolean {
        if (!trimmedLine.startsWith(fence.markerChar.toString().repeat(fence.markerLength))) return false
        val markerRun = trimmedLine.takeWhile { it == fence.markerChar }
        return markerRun.length >= fence.markerLength && trimmedLine.drop(markerRun.length).isBlank()
    }
}
