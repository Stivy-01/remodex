package com.remodex.mobile.core.model

data class WorkspaceCheckpointCaptureResult(
    val repoRoot: String? = null,
    val checkpointRef: String = "",
    val checkpointKind: String = "",
    val commit: String = "",
    val threadId: String = "",
    val turnId: String? = null,
    val messageId: String? = null,
) {
    companion object {
        fun fromJson(json: RPCObject): WorkspaceCheckpointCaptureResult =
            WorkspaceCheckpointCaptureResult(
                repoRoot = json["repoRoot"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                checkpointRef = json["checkpointRef"]?.stringValue?.trim().orEmpty(),
                checkpointKind = json["checkpointKind"]?.stringValue?.trim().orEmpty(),
                commit = json["commit"]?.stringValue?.trim().orEmpty(),
                threadId = json["threadId"]?.stringValue?.trim().orEmpty(),
                turnId = json["turnId"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                messageId = json["messageId"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
            )
    }
}

data class WorkspaceCheckpointCopyResult(
    val copied: Boolean = false,
    val repoRoot: String? = null,
    val sourceCheckpointRef: String = "",
    val checkpointRef: String = "",
    val checkpointKind: String = "",
    val commit: String = "",
    val threadId: String = "",
    val turnId: String? = null,
    val messageId: String? = null,
) {
    companion object {
        fun fromJson(json: RPCObject): WorkspaceCheckpointCopyResult =
            WorkspaceCheckpointCopyResult(
                copied = json["copied"]?.boolValue ?: false,
                repoRoot = json["repoRoot"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                sourceCheckpointRef = json["sourceCheckpointRef"]?.stringValue?.trim().orEmpty(),
                checkpointRef = json["checkpointRef"]?.stringValue?.trim().orEmpty(),
                checkpointKind = json["checkpointKind"]?.stringValue?.trim().orEmpty(),
                commit = json["commit"]?.stringValue?.trim().orEmpty(),
                threadId = json["threadId"]?.stringValue?.trim().orEmpty(),
                turnId = json["turnId"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                messageId = json["messageId"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
            )
    }
}

data class WorkspaceCheckpointDiffResult(
    val repoRoot: String? = null,
    val fromCheckpointRef: String = "",
    val toCheckpointRef: String = "",
    val diff: String = "",
) {
    companion object {
        fun fromJson(json: RPCObject): WorkspaceCheckpointDiffResult =
            WorkspaceCheckpointDiffResult(
                repoRoot = json["repoRoot"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                fromCheckpointRef = json["fromCheckpointRef"]?.stringValue?.trim().orEmpty(),
                toCheckpointRef = json["toCheckpointRef"]?.stringValue?.trim().orEmpty(),
                diff = json["diff"]?.stringValue.orEmpty(),
            )
    }
}

data class WorkspaceCheckpointRestorePreviewResult(
    val canRestore: Boolean = false,
    val repoRoot: String? = null,
    val checkpointRef: String = "",
    val commit: String = "",
    val affectedFiles: List<String> = emptyList(),
    val stagedFiles: List<String> = emptyList(),
    val untrackedFiles: List<String> = emptyList(),
) {
    companion object {
        fun fromJson(json: RPCObject): WorkspaceCheckpointRestorePreviewResult =
            WorkspaceCheckpointRestorePreviewResult(
                canRestore = json["canRestore"]?.boolValue ?: false,
                repoRoot = json["repoRoot"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                checkpointRef = json["checkpointRef"]?.stringValue?.trim().orEmpty(),
                commit = json["commit"]?.stringValue?.trim().orEmpty(),
                affectedFiles = json.stringList("affectedFiles"),
                stagedFiles = json.stringList("stagedFiles"),
                untrackedFiles = json.stringList("untrackedFiles"),
            )
    }
}

data class WorkspaceCheckpointRestoreApplyResult(
    val success: Boolean = false,
    val repoRoot: String? = null,
    val checkpointRef: String = "",
    val backupCheckpointRef: String = "",
    val backupCommit: String = "",
    val restoredFiles: List<String> = emptyList(),
    val conflicts: List<RevertConflict> = emptyList(),
    val unsupportedReasons: List<String> = emptyList(),
    val stagedFiles: List<String> = emptyList(),
    val status: GitRepoSyncResult? = null,
) {
    companion object {
        fun fromJson(json: RPCObject): WorkspaceCheckpointRestoreApplyResult =
            WorkspaceCheckpointRestoreApplyResult(
                success = json["success"]?.boolValue ?: false,
                repoRoot = json["repoRoot"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() },
                checkpointRef = json["checkpointRef"]?.stringValue?.trim().orEmpty(),
                backupCheckpointRef = json["backupCheckpointRef"]?.stringValue?.trim().orEmpty(),
                backupCommit = json["backupCommit"]?.stringValue?.trim().orEmpty(),
                restoredFiles = json.stringList("restoredFiles"),
                conflicts = json["conflicts"]?.arrayValue?.mapNotNull { value ->
                    val obj = value.objectValue ?: return@mapNotNull null
                    RevertConflict(
                        path = obj["path"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() } ?: "unknown",
                        message = obj["message"]?.stringValue?.trim()?.takeIf { it.isNotEmpty() } ?: "Patch conflict.",
                    )
                } ?: emptyList(),
                unsupportedReasons = json.stringList("unsupportedReasons"),
                stagedFiles = json.stringList("stagedFiles"),
                status = json["status"]?.objectValue?.let { GitRepoSyncResult.fromJson(it) },
            )
    }
}

private fun RPCObject.stringList(key: String): List<String> =
    this[key]?.arrayValue?.mapNotNull { it.stringValue?.trim()?.takeIf { value -> value.isNotEmpty() } }
        ?: emptyList()
