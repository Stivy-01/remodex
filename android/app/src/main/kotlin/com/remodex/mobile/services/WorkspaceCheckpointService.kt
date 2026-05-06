package com.remodex.mobile.services

import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.RPCObject
import com.remodex.mobile.core.model.WorkspaceCheckpointCaptureResult
import com.remodex.mobile.core.model.WorkspaceCheckpointCopyResult
import com.remodex.mobile.core.model.WorkspaceCheckpointDiffResult
import com.remodex.mobile.core.model.WorkspaceCheckpointRestoreApplyResult
import com.remodex.mobile.core.model.WorkspaceCheckpointRestorePreviewResult
import com.remodex.mobile.data.CodexRepository

class WorkspaceCheckpointService(
    private val repository: CodexRepository,
) {
    suspend fun capture(params: RPCObject): WorkspaceCheckpointCaptureResult =
        WorkspaceCheckpointCaptureResult.fromJson(send("workspace/checkpointCapture", params))

    suspend fun copy(params: RPCObject): WorkspaceCheckpointCopyResult =
        WorkspaceCheckpointCopyResult.fromJson(send("workspace/checkpointCopy", params))

    suspend fun diff(params: RPCObject): WorkspaceCheckpointDiffResult =
        WorkspaceCheckpointDiffResult.fromJson(send("workspace/checkpointDiff", params))

    suspend fun restorePreview(params: RPCObject): WorkspaceCheckpointRestorePreviewResult =
        WorkspaceCheckpointRestorePreviewResult.fromJson(send("workspace/checkpointRestorePreview", params))

    suspend fun restoreApply(params: RPCObject): WorkspaceCheckpointRestoreApplyResult =
        WorkspaceCheckpointRestoreApplyResult.fromJson(send("workspace/checkpointRestoreApply", params))

    private suspend fun send(
        method: String,
        params: RPCObject,
    ): RPCObject {
        val response =
            repository.sendRequest(
                method = method,
                params = JSONValue.Obj(params),
            )
        return response.result?.objectValue
            ?: error("Invalid response from bridge.")
    }
}
