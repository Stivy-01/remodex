package com.remodex.mobile.core.model

/** Pure helpers for turn usage sheet runtime indicators (J19). */
object TurnUsageSheetLogic {
    fun isThreadTurnActive(
        threadId: String,
        runningTurnIdByThread: Map<String, String>,
        protectedRunningFallbackThreadIds: Set<String>,
    ): Boolean =
        runningTurnIdByThread.containsKey(threadId) || threadId in protectedRunningFallbackThreadIds

    fun recentChangeSetsForThread(
        threadId: String,
        all: List<AIChangeSet>,
        limit: Int = 8,
    ): List<AIChangeSet> =
        all.asSequence()
            .filter { it.threadId == threadId }
            .sortedByDescending { it.createdAt }
            .take(limit)
            .toList()

    /** Callers decide whether revert RPC is available in their current runtime context. */
    fun revertPrimaryBlockReason(
        cs: AIChangeSet,
        runtimeRevertRpcAvailable: Boolean,
    ): AssistantRevertPrimaryBlockReason? {
        if (!runtimeRevertRpcAvailable) return AssistantRevertPrimaryBlockReason.RuntimeEndpointUnavailable
        return when (cs.status) {
            AIChangeSetStatus.collecting -> AssistantRevertPrimaryBlockReason.StatusNotReady
            AIChangeSetStatus.ready ->
                if (!cs.hasRestorableChangeEvidence()) {
                    AssistantRevertPrimaryBlockReason.MissingInversePatch
                } else {
                    null
                }
            AIChangeSetStatus.reverted -> AssistantRevertPrimaryBlockReason.AlreadyReverted
            AIChangeSetStatus.failed -> AssistantRevertPrimaryBlockReason.ChangeSetFailed
            AIChangeSetStatus.notRevertable -> AssistantRevertPrimaryBlockReason.NotRevertableStatus
        }
    }

    fun revertPrimaryEnabled(
        cs: AIChangeSet,
        runtimeRevertRpcAvailable: Boolean,
    ): Boolean = revertPrimaryBlockReason(cs, runtimeRevertRpcAvailable) == null

    fun markChangeSetReverted(
        changeSets: List<AIChangeSet>,
        changeSetId: String,
        now: java.time.Instant,
    ): List<AIChangeSet> =
        changeSets.map { changeSet ->
            if (changeSet.id != changeSetId) {
                changeSet
            } else {
                val metadata = changeSet.revertMetadata
                changeSet.copy(
                    status = AIChangeSetStatus.reverted,
                    revertMetadata =
                        metadata.copy(
                            revertedAt = now,
                            revertAttemptedAt = now,
                            lastRevertError = null,
                        ),
                )
            }
        }

    fun recordChangeSetRevertError(
        changeSets: List<AIChangeSet>,
        changeSetId: String,
        message: String,
        now: java.time.Instant,
    ): List<AIChangeSet> =
        changeSets.map { changeSet ->
            if (changeSet.id != changeSetId) {
                changeSet
            } else {
                val metadata = changeSet.revertMetadata
                changeSet.copy(
                    revertMetadata =
                        metadata.copy(
                            revertAttemptedAt = now,
                            lastRevertError = message,
                        ),
                )
            }
        }

    private fun AIChangeSet.hasRestorableChangeEvidence(): Boolean =
        !inverseUnifiedPatch.isNullOrBlank() ||
            !workspaceCheckpoint?.restoreCheckpointRef.isNullOrBlank()
}

enum class AssistantRevertPrimaryBlockReason {
    RuntimeEndpointUnavailable,
    AlreadyReverted,
    StatusNotReady,
    MissingInversePatch,
    NotRevertableStatus,
    ChangeSetFailed,
}
