package com.remodex.mobile.data

import com.remodex.mobile.core.model.AIUnifiedPatchParser
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageKind

/** Rows for Git repo sheet "Last turn" scope ([com.remodex.mobile.ui.shell.GitRepoDiffBottomSheet]). */
data class RepoDiffLastTurnFileRow(
    val stableKey: String,
    /** Path shown in summary/review rows (parity timeline file rows). */
    val path: String,
    /** Unified fragment for exactly this row (typically one file). */
    val chunk: String,
)

/**
 * Collects unified patch text emitted during the chronologically latest assistant turn
 * that touched files ([CodexMessageKind.fileChange]). Used for the repo diff sheet scope
 * "last turn" without calling [git/diff].
 */
internal object RepoDiffLastTurnAggregator {
    /**
     * One row per file / patch fragment — same decomposition as chat ([TurnTimelineRichContentCache.parseFileChange]),
     * so the sheet does not merge multiple files into one blob that becomes `file_0` when split downstream.
     */
    fun fileRowsFromLastTurn(messages: List<CodexMessage>): List<RepoDiffLastTurnFileRow> {
        val segment = lastTurnSegment(messages) ?: return emptyList()
        val out = ArrayList<RepoDiffLastTurnFileRow>()

        for (msg in segment) {
            var rowSeq = 0
            fun nextKey(tag: String) = "${msg.id}:$tag:${rowSeq++}"
            val unified =
                TurnTimelineRichContentCache.unifiedPatchForFileChangeMessage(msg)
                    .trim()
            if (unified.isEmpty() || isTimelinePlaceholderOnly(unified)) continue

            val preview = TurnTimelineRichContentCache.parseFileChange(msg)

            when {
                preview.entries.isNotEmpty() -> {
                    val countBeforeEntries = out.size
                    preview.entries.forEachIndexed { ei, entry ->
                        val chunk =
                            entry.patchChunkText
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                                ?: if (preview.entries.size == 1) {
                                    preview.rawPatchText
                                        ?.trim()
                                        ?.takeIf { it.isNotEmpty() }
                                } else {
                                    null
                                }
                                ?: return@forEachIndexed

                        val inferredPath =
                            entry.path.trim().takeIf { it.isNotEmpty() }
                                ?: guessedPathFromChunk(chunk)
                        val synthSplit =
                            AIUnifiedPatchParser.splitUnifiedPatchIntoFileChunks(chunk)
                                .takeIf { sub ->
                                    sub.size > 1 && entriesLookLikeSyntheticFileNames(sub)
                                }

                        when {
                            synthSplit != null -> {
                                synthSplit.forEachIndexed { ix, pair ->
                                    val subPath =
                                        pair.first.takeUnless { looksSyntheticFileStem(it) }
                                            ?: guessedPathFromChunk(pair.second)
                                            ?: pair.first
                                    out.add(
                                        RepoDiffLastTurnFileRow(
                                            stableKey = nextKey("split-$ei-$ix"),
                                            path = subPath,
                                            chunk = pair.second,
                                        ),
                                    )
                                }
                            }

                            else -> {
                                out.add(
                                    RepoDiffLastTurnFileRow(
                                        stableKey = nextKey("entry-$ei"),
                                        path = resolveDisplayPath(inferredPath, chunk),
                                        chunk = chunk,
                                    ),
                                )
                            }
                        }
                    }

                    /**
                     * Summary-only parses can yield entries with counts but without patch chunks.
                     * Fall back to the same whole-body split as unstructured messages ([unified])
                     * so coarse diff lines (`+++ b/foo`) still contribute rows.
                     */
                    if (out.size == countBeforeEntries) {
                        emitFallbackRowsFromUnified(out, unified, ::nextKey)
                    }
                }

                else -> {
                    emitFallbackRowsFromUnified(out, unified, ::nextKey)
                }
            }
        }
        return out
    }

    fun unifiedPatchFromLastTurn(messages: List<CodexMessage>): String? {
        val rows = fileRowsFromLastTurn(messages)
        if (rows.isEmpty()) return null
        return rows.joinToString(separator = "\n\n") { it.chunk }
    }

    private fun emitFallbackRowsFromUnified(
        out: MutableList<RepoDiffLastTurnFileRow>,
        unified: String,
        nextKey: (tag: String) -> String,
    ) {
        val chunks = AIUnifiedPatchParser.splitUnifiedPatchIntoFileChunks(unified)
        if (chunks.isEmpty()) {
            out.add(
                RepoDiffLastTurnFileRow(
                    stableKey = nextKey("blob"),
                    path = resolveDisplayPath(null, unified),
                    chunk = unified,
                ),
            )
        } else {
            for ((i, pair) in chunks.withIndex()) {
                val guessed = guessedPathFromChunk(pair.second)
                val path =
                    pair.first.takeUnless { looksSyntheticFileStem(it) } ?: guessed ?: pair.first
                out.add(
                    RepoDiffLastTurnFileRow(
                        stableKey = nextKey("fallback-$i"),
                        path = path,
                        chunk = pair.second,
                    ),
                )
            }
        }
    }

    private fun lastTurnSegment(messages: List<CodexMessage>): List<CodexMessage>? {
        val sorted =
            messages
                .sortedBy { it.orderIndex }
                .filter { it.kind == CodexMessageKind.fileChange }
        if (sorted.isEmpty()) return null
        val lastTurnId = sorted.last().turnId
        val segment =
            if (lastTurnId.isNullOrBlank()) {
                sorted.asReversed().takeWhile { it.turnId.isNullOrBlank() }.reversed()
            } else {
                sorted.filter { it.turnId == lastTurnId }
            }
        return segment.ifEmpty { null }
    }

    private fun guessedPathFromChunk(chunk: String): String? =
        AIUnifiedPatchParser
            .analyze(chunk)
            .fileChanges
            .firstOrNull { it.path.isNotBlank() }
            ?.path
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun resolveDisplayPath(
        preferred: String?,
        chunk: String,
    ): String {
        val trimmed = preferred?.trim()?.takeIf { it.isNotEmpty() }
        val guessNonSynthetic =
            guessedPathFromChunk(chunk)?.takeUnless { looksSyntheticFileStem(it) }

        when {
            trimmed != null && !looksSyntheticFileStem(trimmed) -> return trimmed
            guessNonSynthetic != null -> return guessNonSynthetic
            trimmed != null -> return trimmed
        }
        return guessedPathFromChunk(chunk)?.takeUnless { looksSyntheticFileStem(it) }
            ?: "patch"
    }

    /** True for `file_0` style names emitted when the split found no ---/+++ path. */
    private fun looksSyntheticFileStem(path: String): Boolean {
        val seg = path.trim().substringAfterLast('/', path.trim())
        return seg.matches(Regex("file_\\d+", RegexOption.IGNORE_CASE))
    }

    private fun entriesLookLikeSyntheticFileNames(rows: List<Pair<String, String>>): Boolean =
        rows.isNotEmpty() &&
            rows.all { looksSyntheticFileStem(it.first) }

    /**
     * [ThreadHistoryDecoder.decodeFileChangePreview] uses `"[file change]"` when history items omit paths;
     * that is not a unified diff — exclude so "Last turn" does not synthesize bogus unified rows.
     */
    private fun isTimelinePlaceholderOnly(text: String): Boolean {
        val lines =
            text
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        if (lines.isEmpty()) return true
        return lines.all { it == "[file change]" }
    }
}
