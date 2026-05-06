package com.remodex.mobile.services

import com.remodex.mobile.core.model.AIChangeSet
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.RevertApplyResult
import com.remodex.mobile.core.model.WorkspaceCheckpointRestoreApplyResult
import com.remodex.mobile.data.CodexRepository

class AiChangeSetRevertService(
    private val repository: CodexRepository,
) {
    suspend fun apply(
        changeSet: AIChangeSet,
        workingDirectory: String,
    ): RevertApplyResult {
        val checkpointRef = changeSet.workspaceCheckpoint?.restoreCheckpointRef
        if (!checkpointRef.isNullOrBlank()) {
            return applyWorkspaceCheckpointRestore(changeSet, workingDirectory, checkpointRef)
        }

        val patch = changeSet.forwardUnifiedPatch.trim()
        require(patch.isNotEmpty()) {
            "This response cannot be auto-reverted because no exact patch was captured."
        }
        val response =
            repository.sendRequest(
                method = "workspace/revertPatchApply",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "cwd" to JSONValue.Str(workingDirectory),
                            "forwardPatch" to JSONValue.Str(changeSet.forwardUnifiedPatch),
                        ),
                    ),
            )
        val result =
            response.result?.objectValue
                ?: error("Invalid response from bridge.")
        return RevertApplyResult.fromJson(result)
    }

    private suspend fun applyWorkspaceCheckpointRestore(
        changeSet: AIChangeSet,
        workingDirectory: String,
        checkpointRef: String,
    ): RevertApplyResult {
        val params = linkedMapOf<String, JSONValue>(
            "cwd" to JSONValue.Str(workingDirectory),
            "threadId" to JSONValue.Str(changeSet.threadId),
            "targetCheckpointRef" to JSONValue.Str(checkpointRef),
            "confirmDestructiveRestore" to JSONValue.Bool(true),
        )
        changeSet.turnId.trim().takeIf { it.isNotEmpty() }?.let { params["targetTurnId"] = JSONValue.Str(it) }
        changeSet.workspaceCheckpoint?.restoreExpectedCommit
            ?.let { params["expectedTargetCommit"] = JSONValue.Str(it) }

        val response =
            repository.sendRequest(
                method = "workspace/checkpointRestoreApply",
                params = JSONValue.Obj(params),
            )
        val result =
            response.result?.objectValue
                ?: error("Invalid response from bridge.")
        val restored =
            WorkspaceCheckpointRestoreApplyResult.fromJson(result)
        return RevertApplyResult(
            success = restored.success,
            revertedFiles = restored.restoredFiles,
            conflicts = restored.conflicts,
            unsupportedReasons = restored.unsupportedReasons,
            stagedFiles = restored.stagedFiles,
            status = restored.status,
        )
    }
}

