package com.remodex.mobile.core.model

import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AIFileChangeKind {
    create,
    update,
    delete,
}

@Serializable
data class AIFileChange(
    val path: String,
    val kind: AIFileChangeKind,
    val additions: Int,
    val deletions: Int,
    val isBinary: Boolean,
    val isRenameOrModeOnly: Boolean,
    val beforeContentHash: String? = null,
    val afterContentHash: String? = null,
) {
    val id: String get() = path
}

@Serializable
enum class AIChangeSetStatus {
    collecting,
    ready,
    reverted,
    failed,
    @SerialName("not_revertable")
    notRevertable,
}

@Serializable
enum class AIChangeSetSource {
    @SerialName("turnDiff")
    turnDiff,

    @SerialName("fileChangeFallback")
    fileChangeFallback,
}

@Serializable
data class AIRevertMetadata(
    @Serializable(Iso8601InstantSerializer::class)
    var revertedAt: Instant? = null,
    @Serializable(Iso8601InstantSerializer::class)
    var revertAttemptedAt: Instant? = null,
    var lastRevertError: String? = null,
)

@Serializable
data class AIWorkspaceCheckpointMetadata(
    val targetCheckpointRef: String? = null,
    val targetCommit: String? = null,
    val turnStartCheckpointRef: String? = null,
    val turnStartCommit: String? = null,
    val turnEndCheckpointRef: String? = null,
    val turnEndCommit: String? = null,
) {
    val restoreCheckpointRef: String?
        get() =
            targetCheckpointRef?.trim()?.takeIf { it.isNotEmpty() }
                ?: turnStartCheckpointRef?.trim()?.takeIf { it.isNotEmpty() }

    val restoreExpectedCommit: String?
        get() =
            targetCommit?.trim()?.takeIf { it.isNotEmpty() }
                ?: turnStartCommit?.trim()?.takeIf { it.isNotEmpty() }
}

@Serializable
data class AIChangeSet(
    val id: String = UUID.randomUUID().toString(),
    val repoRoot: String? = null,
    val threadId: String,
    val turnId: String,
    val assistantMessageId: String? = null,
    @Serializable(Iso8601InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(Iso8601InstantSerializer::class)
    val finalizedAt: Instant? = null,
    val status: AIChangeSetStatus = AIChangeSetStatus.collecting,
    val source: AIChangeSetSource,
    val forwardUnifiedPatch: String = "",
    val inverseUnifiedPatch: String? = null,
    val patchHash: String = "",
    val fileChanges: List<AIFileChange> = emptyList(),
    val unsupportedReasons: List<String> = emptyList(),
    val revertMetadata: AIRevertMetadata = AIRevertMetadata(),
    val fallbackPatchCount: Int = 0,
    val workspaceCheckpoint: AIWorkspaceCheckpointMetadata? = null,
)

@Serializable
data class RevertConflict(
    val path: String,
    val message: String,
)

data class RevertPreviewResult(
    val canRevert: Boolean,
    val affectedFiles: List<String>,
    val conflicts: List<RevertConflict>,
    val unsupportedReasons: List<String>,
    val stagedFiles: List<String>,
) {
    companion object {
        fun fromJson(json: RPCObject): RevertPreviewResult =
            RevertPreviewResult(
                canRevert = json["canRevert"]?.boolValue ?: false,
                affectedFiles = json["affectedFiles"]?.arrayValue?.mapNotNull { it.stringValue } ?: emptyList(),
                conflicts =
                    json["conflicts"]?.arrayValue?.mapNotNull { v ->
                        v.objectValue?.let { o ->
                            RevertConflict(
                                path = o["path"]?.stringValue ?: "unknown",
                                message = o["message"]?.stringValue ?: "Patch conflict.",
                            )
                        }
                    } ?: emptyList(),
                unsupportedReasons =
                    json["unsupportedReasons"]?.arrayValue?.mapNotNull { it.stringValue } ?: emptyList(),
                stagedFiles = json["stagedFiles"]?.arrayValue?.mapNotNull { it.stringValue } ?: emptyList(),
            )
    }
}

data class RevertApplyResult(
    val success: Boolean,
    val revertedFiles: List<String>,
    val conflicts: List<RevertConflict>,
    val unsupportedReasons: List<String>,
    val stagedFiles: List<String>,
    val status: GitRepoSyncResult?,
) {
    companion object {
        fun fromJson(json: RPCObject): RevertApplyResult =
            RevertApplyResult(
                success = json["success"]?.boolValue ?: false,
                revertedFiles = json["revertedFiles"]?.arrayValue?.mapNotNull { it.stringValue } ?: emptyList(),
                conflicts =
                    json["conflicts"]?.arrayValue?.mapNotNull { v ->
                        v.objectValue?.let { o ->
                            RevertConflict(
                                path = o["path"]?.stringValue ?: "unknown",
                                message = o["message"]?.stringValue ?: "Patch conflict.",
                            )
                        }
                    } ?: emptyList(),
                unsupportedReasons =
                    json["unsupportedReasons"]?.arrayValue?.mapNotNull { it.stringValue } ?: emptyList(),
                stagedFiles = json["stagedFiles"]?.arrayValue?.mapNotNull { it.stringValue } ?: emptyList(),
                status = json["status"]?.objectValue?.let { GitRepoSyncResult.fromJson(it) },
            )
    }
}

enum class AssistantRevertRiskLevel {
    safe,
    warning,
    blocked,
}

data class AssistantRevertPresentation(
    val title: String,
    val isEnabled: Boolean,
    val helperText: String?,
    val riskLevel: AssistantRevertRiskLevel = AssistantRevertRiskLevel.safe,
    val warningText: String? = null,
    val overlappingFiles: List<String> = emptyList(),
)

data class AIUnifiedPatchAnalysis(
    val fileChanges: List<AIFileChange>,
    val unsupportedReasons: List<String>,
) {
    val affectedFiles: List<String> get() = fileChanges.map { it.path }

    val totalAdditions: Int get() = fileChanges.sumOf { it.additions }

    val totalDeletions: Int get() = fileChanges.sumOf { it.deletions }
}

object AIUnifiedPatchParser {
    fun analyze(rawPatch: String): AIUnifiedPatchAnalysis {
        val patch = rawPatch.trim()
        if (patch.isEmpty()) {
            return AIUnifiedPatchAnalysis(
                fileChanges = emptyList(),
                unsupportedReasons = listOf("No exact patch was captured."),
            )
        }
        val chunks = splitIntoChunks(patch)
        if (chunks.isEmpty()) {
            return AIUnifiedPatchAnalysis(
                fileChanges = emptyList(),
                unsupportedReasons = listOf("No exact patch was captured."),
            )
        }
        val fileChanges = mutableListOf<AIFileChange>()
        val unsupportedReasons = mutableSetOf<String>()
        for (chunk in chunks) {
            val analysis = analyzeChunk(chunk)
            analysis.fileChange?.let { fileChanges.add(it) }
            unsupportedReasons.addAll(analysis.unsupportedReasons)
        }
        if (fileChanges.isEmpty()) {
            unsupportedReasons.add("This response cannot be auto-reverted because no exact patch was captured.")
        }
        return AIUnifiedPatchAnalysis(
            fileChanges = fileChanges,
            unsupportedReasons = unsupportedReasons.sorted(),
        )
    }

    /**
     * Counts `+`/`-` body lines matching [analyzeChunk] rules, **without** requiring `+++`
     * or `diff --git` path headers (hunk-only fragments still count).
     */
    fun countUnifiedDiffBodyLineStats(patch: String): Pair<Int, Int> {
        val normalized = patch.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return 0 to 0
        var additions = 0
        var deletions = 0
        for (line in normalized.lineSequence()) {
            val first = line.firstOrNull() ?: continue
            if (first == '+' && !line.startsWith("+++")) additions++
            else if (first == '-' && !line.startsWith("---")) deletions++
        }
        return additions to deletions
    }

    /**
     * Additions/deletions for UI summaries. Uses structured [analyze] when it yields nonzero
     * tallies; otherwise falls back on raw body counting (parity timeline when path is omitted).
     */
    fun additionsDeletionsForDisplay(patch: String): Pair<Int, Int> {
        val analysis = analyze(patch)
        val structuredAdd = analysis.fileChanges.sumOf { it.additions }
        val structuredDel = analysis.fileChanges.sumOf { it.deletions }
        if (structuredAdd > 0 || structuredDel > 0) return structuredAdd to structuredDel
        return countUnifiedDiffBodyLineStats(patch)
    }

    fun hashFor(rawPatch: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawPatch.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Splits a unified diff into sequential file chunks (`diff --git …` boundaries).
     * First value is best-effort path from hunk headers; unnamed chunks become `file_0`, `file_1`, ….
     */
    fun splitUnifiedPatchIntoFileChunks(patch: String): List<Pair<String, String>> {
        val normalized = patch.replace("\r\n", "\n").trimEnd()
        if (normalized.isEmpty()) return emptyList()
        val chunks = splitIntoChunks(normalized)
        val out = ArrayList<Pair<String, String>>(chunks.size)
        var fallbackIndex = 0
        for (lines in chunks) {
            if (lines.isEmpty()) continue
            var path = extractPath(lines)
            if (path.isEmpty()) {
                path = "file_$fallbackIndex"
                fallbackIndex++
            }
            out.add(path to lines.joinToString("\n"))
        }
        return out
    }

    private fun splitIntoChunks(patch: String): List<List<String>> {
        val lines = patch.split("\n")
        if (lines.isEmpty()) return emptyList()
        val chunks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        fun flush() {
            if (current.isNotEmpty()) {
                chunks.add(current.toList())
                current = mutableListOf()
            }
        }
        for (line in lines) {
            if (line.startsWith("diff --git ") && current.isNotEmpty()) {
                flush()
            }
            current.add(line)
        }
        flush()
        return chunks
    }

    private data class ChunkAnalysis(
        val fileChange: AIFileChange?,
        val unsupportedReasons: Set<String>,
    )

    private fun analyzeChunk(lines: List<String>): ChunkAnalysis {
        if (lines.isEmpty()) return ChunkAnalysis(null, emptySet())
        val path = extractPath(lines)
        val isBinary =
            lines.any { it.startsWith("Binary files ") || it == "GIT binary patch" }
        val isRenameOrModeOnly =
            lines.any { line ->
                line.startsWith("rename from ") ||
                    line.startsWith("rename to ") ||
                    line.startsWith("copy from ") ||
                    line.startsWith("copy to ") ||
                    line.startsWith("old mode ") ||
                    line.startsWith("new mode ") ||
                    line.startsWith("new file mode 120") ||
                    line.startsWith("deleted file mode 120") ||
                    line.startsWith("similarity index ")
            }
        val isCreate =
            lines.contains("new file mode 100644") ||
                lines.contains("new file mode 100755") ||
                lines.contains("--- /dev/null")
        val isDelete =
            lines.contains("deleted file mode 100644") ||
                lines.contains("deleted file mode 100755") ||
                lines.contains("+++ /dev/null")
        var additions = 0
        var deletions = 0
        for (line in lines) {
            val first = line.firstOrNull() ?: continue
            if (first == '+' && !line.startsWith("+++")) additions++
            else if (first == '-' && !line.startsWith("---")) deletions++
        }
        val unsupportedReasons = mutableSetOf<String>()
        if (isBinary) unsupportedReasons.add("Binary changes are not auto-revertable in v1.")
        if (isRenameOrModeOnly) {
            unsupportedReasons.add("Rename, mode-only, or symlink changes are not auto-revertable in v1.")
        }
        val kind =
            when {
                isCreate -> AIFileChangeKind.create
                isDelete -> AIFileChangeKind.delete
                else -> AIFileChangeKind.update
            }
        val hasPatchBody = additions > 0 || deletions > 0 || isCreate || isDelete
        if (path.isEmpty() || !hasPatchBody) {
            if (!isBinary && !isRenameOrModeOnly) {
                unsupportedReasons.add("This response cannot be auto-reverted because no exact patch was captured.")
            }
            return ChunkAnalysis(null, unsupportedReasons)
        }
        return ChunkAnalysis(
            AIFileChange(
                path = path,
                kind = kind,
                additions = additions,
                deletions = deletions,
                isBinary = isBinary,
                isRenameOrModeOnly = isRenameOrModeOnly,
                beforeContentHash = null,
                afterContentHash = null,
            ),
            unsupportedReasons,
        )
    }

    private fun extractPath(lines: List<String>): String {
        for (line in lines) {
            if (line.startsWith("+++ ")) {
                val rawPath = line.drop(4).trim()
                val normalized = normalizeDiffPath(rawPath)
                if (normalized.isNotEmpty() && normalized != "/dev/null") return normalized
            }
        }
        for (line in lines) {
            if (line.startsWith("diff --git ")) {
                val parts = line.split(" ").filter { it.isNotEmpty() }
                if (parts.size >= 4) {
                    val normalized = normalizeDiffPath(parts[3])
                    if (normalized.isNotEmpty()) return normalized
                }
            }
        }
        return ""
    }

    private fun normalizeDiffPath(rawPath: String): String {
        var value = rawPath.trim()
        if (value.startsWith("a/") || value.startsWith("b/")) {
            value = value.drop(2)
        }
        return value
    }
}
