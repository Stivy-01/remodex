package com.remodex.mobile.core.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurnUsageSheetLogicTest {
    @Test
    fun isThreadTurnActive_considers_running_and_fallback_sets() {
        assertTrue(
            TurnUsageSheetLogic.isThreadTurnActive(
                threadId = "t1",
                runningTurnIdByThread = mapOf("t1" to "turn-1"),
                protectedRunningFallbackThreadIds = emptySet(),
            ),
        )
        assertTrue(
            TurnUsageSheetLogic.isThreadTurnActive(
                threadId = "t1",
                runningTurnIdByThread = emptyMap(),
                protectedRunningFallbackThreadIds = setOf("t1"),
            ),
        )
        assertFalse(
            TurnUsageSheetLogic.isThreadTurnActive(
                threadId = "t1",
                runningTurnIdByThread = emptyMap(),
                protectedRunningFallbackThreadIds = emptySet(),
            ),
        )
    }

    @Test
    fun recentChangeSetsForThread_filters_and_sorts_descending() {
        val now = Instant.now()
        val sample =
            listOf(
                fakeChangeSet(threadId = "other", createdAt = now.minusSeconds(30)),
                fakeChangeSet(threadId = "t1", createdAt = now.minusSeconds(20)),
                fakeChangeSet(threadId = "t1", createdAt = now.minusSeconds(5)),
            )
        val ids = TurnUsageSheetLogic.recentChangeSetsForThread("t1", sample).map { it.createdAt }
        assertEquals(listOf(now.minusSeconds(5), now.minusSeconds(20)), ids)
    }

    @Test
    fun revertPrimaryBlockReason_reflects_status_and_runtime_support() {
        val readyWithInverse = fakeChangeSet(status = AIChangeSetStatus.ready, inverseUnifiedPatch = "---\n+++")
        assertNull(TurnUsageSheetLogic.revertPrimaryBlockReason(readyWithInverse, runtimeRevertRpcAvailable = true))

        val readyWithCheckpoint =
            fakeChangeSet(
                status = AIChangeSetStatus.ready,
                workspaceCheckpoint =
                    AIWorkspaceCheckpointMetadata(
                        turnStartCheckpointRef = "refs/remodex/checkpoints/thread/turn-start",
                    ),
            )
        assertNull(TurnUsageSheetLogic.revertPrimaryBlockReason(readyWithCheckpoint, runtimeRevertRpcAvailable = true))

        val readyNoInverse = fakeChangeSet(status = AIChangeSetStatus.ready, inverseUnifiedPatch = null)
        assertEquals(
            AssistantRevertPrimaryBlockReason.MissingInversePatch,
            TurnUsageSheetLogic.revertPrimaryBlockReason(readyNoInverse, runtimeRevertRpcAvailable = true),
        )

        assertEquals(
            AssistantRevertPrimaryBlockReason.RuntimeEndpointUnavailable,
            TurnUsageSheetLogic.revertPrimaryBlockReason(readyWithInverse, runtimeRevertRpcAvailable = false),
        )
    }

    @Test
    fun markChangeSetReverted_updatesStatusAndClearsRevertError() {
        val now = Instant.parse("2026-05-03T10:00:00Z")
        val changeSet =
            fakeChangeSet(
                status = AIChangeSetStatus.ready,
                revertMetadata = AIRevertMetadata(lastRevertError = "conflict"),
            )

        val updated = TurnUsageSheetLogic.markChangeSetReverted(listOf(changeSet), changeSet.id, now).single()

        assertEquals(AIChangeSetStatus.reverted, updated.status)
        assertEquals(now, updated.revertMetadata.revertedAt)
        assertEquals(now, updated.revertMetadata.revertAttemptedAt)
        assertNull(updated.revertMetadata.lastRevertError)
    }

    @Test
    fun recordChangeSetRevertError_preservesReadyStatusForRetry() {
        val now = Instant.parse("2026-05-03T10:00:00Z")
        val changeSet = fakeChangeSet(status = AIChangeSetStatus.ready)

        val updated =
            TurnUsageSheetLogic
                .recordChangeSetRevertError(listOf(changeSet), changeSet.id, "Patch conflict.", now)
                .single()

        assertEquals(AIChangeSetStatus.ready, updated.status)
        assertNull(updated.revertMetadata.revertedAt)
        assertEquals(now, updated.revertMetadata.revertAttemptedAt)
        assertEquals("Patch conflict.", updated.revertMetadata.lastRevertError)
    }

    private fun fakeChangeSet(
        threadId: String = "t1",
        createdAt: Instant = Instant.now(),
        status: AIChangeSetStatus = AIChangeSetStatus.collecting,
        inverseUnifiedPatch: String? = null,
        workspaceCheckpoint: AIWorkspaceCheckpointMetadata? = null,
        revertMetadata: AIRevertMetadata = AIRevertMetadata(),
    ): AIChangeSet =
        AIChangeSet(
            threadId = threadId,
            turnId = "turn-1",
            createdAt = createdAt,
            status = status,
            source = AIChangeSetSource.turnDiff,
            inverseUnifiedPatch = inverseUnifiedPatch,
            workspaceCheckpoint = workspaceCheckpoint,
            revertMetadata = revertMetadata,
        )
}
