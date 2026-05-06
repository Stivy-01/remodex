package com.remodex.mobile.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceArtifactsModelsTest {
    @Test
    fun checkpointRestorePreviewResultParsesListsAndBooleans() {
        val parsed =
            WorkspaceCheckpointRestorePreviewResult.fromJson(
                mapOf(
                    "canRestore" to JSONValue.Bool(true),
                    "repoRoot" to JSONValue.Str("/repo"),
                    "checkpointRef" to JSONValue.Str("refs/remodex/checkpoints/thread/turn"),
                    "commit" to JSONValue.Str("abc123"),
                    "affectedFiles" to JSONValue.Arr(listOf(JSONValue.Str("src/App.kt"))),
                    "stagedFiles" to JSONValue.Arr(listOf(JSONValue.Str("README.md"))),
                    "untrackedFiles" to JSONValue.Arr(listOf(JSONValue.Str("tmp.txt"))),
                ),
            )

        assertTrue(parsed.canRestore)
        assertEquals("/repo", parsed.repoRoot)
        assertEquals("refs/remodex/checkpoints/thread/turn", parsed.checkpointRef)
        assertEquals("abc123", parsed.commit)
        assertEquals(listOf("src/App.kt"), parsed.affectedFiles)
        assertEquals(listOf("README.md"), parsed.stagedFiles)
        assertEquals(listOf("tmp.txt"), parsed.untrackedFiles)
    }

    @Test
    fun checkpointRestoreApplyResultParsesRepoSyncStatus() {
        val parsed =
            WorkspaceCheckpointRestoreApplyResult.fromJson(
                mapOf(
                    "success" to JSONValue.Bool(true),
                    "repoRoot" to JSONValue.Str("/repo"),
                    "checkpointRef" to JSONValue.Str("refs/remodex/checkpoints/thread/turn"),
                    "backupCheckpointRef" to JSONValue.Str("refs/remodex/checkpoints/thread/backup"),
                    "backupCommit" to JSONValue.Str("backup-commit"),
                    "restoredFiles" to JSONValue.Arr(listOf(JSONValue.Str("src/App.kt"))),
                    "status" to
                        JSONValue.Obj(
                            mapOf(
                                "repoRoot" to JSONValue.Str("/repo"),
                                "branch" to JSONValue.Str("main"),
                                "dirty" to JSONValue.Bool(false),
                                "ahead" to JSONValue.NumLong(0),
                                "behind" to JSONValue.NumLong(0),
                                "localOnlyCommitCount" to JSONValue.NumLong(0),
                                "state" to JSONValue.Str("up_to_date"),
                                "canPush" to JSONValue.Bool(true),
                                "publishedToRemote" to JSONValue.Bool(true),
                            ),
                        ),
                ),
            )

        assertTrue(parsed.success)
        assertEquals("/repo", parsed.repoRoot)
        assertEquals("refs/remodex/checkpoints/thread/turn", parsed.checkpointRef)
        assertEquals("refs/remodex/checkpoints/thread/backup", parsed.backupCheckpointRef)
        assertEquals(listOf("src/App.kt"), parsed.restoredFiles)
        assertTrue(parsed.status?.canPush == true)
    }

    @Test
    fun imageReadResultParsesMetadataAndDataFlags() {
        val parsed =
            WorkspaceImageReadResult.fromJson(
                mapOf(
                    "path" to JSONValue.Str("/tmp/preview.png"),
                    "fileName" to JSONValue.Str("preview.png"),
                    "mimeType" to JSONValue.Str("image/png"),
                    "byteLength" to JSONValue.NumLong(42),
                    "mtimeMs" to JSONValue.NumDouble(123.5),
                    "previewMaxPixelDimension" to JSONValue.NumLong(1600),
                    "dataByteLength" to JSONValue.NumLong(24),
                    "dataBase64" to JSONValue.Str("QUJD"),
                    "notModified" to JSONValue.Bool(false),
                ),
            )

        assertEquals("/tmp/preview.png", parsed.path)
        assertEquals("preview.png", parsed.fileName)
        assertEquals("image/png", parsed.mimeType)
        assertEquals(42L, parsed.byteLength)
        assertEquals(123.5, parsed.mtimeMs)
        assertEquals(1600, parsed.previewMaxPixelDimension)
        assertEquals(24, parsed.dataByteLength)
        assertEquals("QUJD", parsed.dataBase64)
        assertFalse(parsed.notModified)
    }
}
