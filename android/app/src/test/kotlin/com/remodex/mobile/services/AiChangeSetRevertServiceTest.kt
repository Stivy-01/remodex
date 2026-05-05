package com.remodex.mobile.services

import com.remodex.mobile.core.model.AIChangeSet
import com.remodex.mobile.core.model.AIChangeSetSource
import com.remodex.mobile.core.model.AIWorkspaceCheckpointMetadata
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.RPCMessage
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AiChangeSetRevertServiceTest {
    @Test
    fun apply_prefersWorkspaceCheckpointRestoreWhenCheckpointMetadataExists() = runTest {
        var capturedMethod = ""
        var capturedParams: Map<String, JSONValue> = emptyMap()
        val service =
            AiChangeSetRevertService(
                MinimalSendRepository { method, params ->
                    capturedMethod = method
                    capturedParams = params?.objectValue.orEmpty()
                    RPCMessage.success(
                        id = JSONValue.NumLong(1),
                        result =
                            JSONValue.Obj(
                                mapOf(
                                    "success" to JSONValue.Bool(true),
                                    "restoredFiles" to JSONValue.Arr(listOf(JSONValue.Str("src/App.kt"))),
                                    "stagedFiles" to JSONValue.Arr(listOf(JSONValue.Str("README.md"))),
                                ),
                            ),
                    )
                },
            )

        val result =
            service.apply(
                changeSet =
                    sampleChangeSet(
                        workspaceCheckpoint =
                            AIWorkspaceCheckpointMetadata(
                                turnStartCheckpointRef = "refs/remodex/checkpoints/thread/turn-start",
                                turnStartCommit = "abc123",
                            ),
                    ),
                workingDirectory = "/repo",
            )

        assertEquals("workspace/checkpointRestoreApply", capturedMethod)
        assertEquals("/repo", capturedParams["cwd"]?.stringValue)
        assertEquals("thread-1", capturedParams["threadId"]?.stringValue)
        assertEquals("turn-1", capturedParams["targetTurnId"]?.stringValue)
        assertEquals("refs/remodex/checkpoints/thread/turn-start", capturedParams["targetCheckpointRef"]?.stringValue)
        assertEquals("abc123", capturedParams["expectedTargetCommit"]?.stringValue)
        assertEquals(true, capturedParams["confirmDestructiveRestore"]?.boolValue)
        assertTrue(result.success)
        assertEquals(listOf("src/App.kt"), result.revertedFiles)
        assertEquals(listOf("README.md"), result.stagedFiles)
    }

    @Test
    fun apply_fallsBackToPatchRevertForLegacyChangeSet() = runTest {
        var capturedMethod = ""
        var capturedParams: Map<String, JSONValue> = emptyMap()
        val service =
            AiChangeSetRevertService(
                MinimalSendRepository { method, params ->
                    capturedMethod = method
                    capturedParams = params?.objectValue.orEmpty()
                    RPCMessage.success(
                        id = JSONValue.NumLong(1),
                        result =
                            JSONValue.Obj(
                                mapOf(
                                    "success" to JSONValue.Bool(true),
                                    "revertedFiles" to JSONValue.Arr(listOf(JSONValue.Str("legacy.kt"))),
                                ),
                            ),
                    )
                },
            )

        val result =
            service.apply(
                changeSet = sampleChangeSet(forwardUnifiedPatch = "diff --git a/legacy.kt b/legacy.kt"),
                workingDirectory = "/repo",
            )

        assertEquals("workspace/revertPatchApply", capturedMethod)
        assertEquals("/repo", capturedParams["cwd"]?.stringValue)
        assertEquals("diff --git a/legacy.kt b/legacy.kt", capturedParams["forwardPatch"]?.stringValue)
        assertTrue(result.success)
        assertEquals(listOf("legacy.kt"), result.revertedFiles)
    }

    private fun sampleChangeSet(
        forwardUnifiedPatch: String = "",
        workspaceCheckpoint: AIWorkspaceCheckpointMetadata? = null,
    ): AIChangeSet =
        AIChangeSet(
            repoRoot = "/repo",
            threadId = "thread-1",
            turnId = "turn-1",
            createdAt = Instant.parse("2026-05-01T00:00:00Z"),
            source = AIChangeSetSource.turnDiff,
            forwardUnifiedPatch = forwardUnifiedPatch,
            workspaceCheckpoint = workspaceCheckpoint,
        )
}
