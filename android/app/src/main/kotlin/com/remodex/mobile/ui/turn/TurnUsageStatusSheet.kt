package com.remodex.mobile.ui.turn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remodex.mobile.R
import com.remodex.mobile.core.model.AIChangeSet
import com.remodex.mobile.core.model.AIChangeSetStatus
import com.remodex.mobile.core.model.AssistantRevertPrimaryBlockReason
import com.remodex.mobile.core.model.CodexRateLimitBucket
import com.remodex.mobile.core.model.CodexRateLimitDisplayRow
import com.remodex.mobile.core.model.ContextWindowUsage
import com.remodex.mobile.core.model.TurnUsageSheetLogic
import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.data.QueuedTurnDraftPreview
import com.remodex.mobile.services.AiChangeSetRevertService
import com.remodex.mobile.ui.LocalAIChangeSetPersistence
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * J.7d: detailed usage + account rate limits for the current thread, backed by
 * [CodexRepository] flows (same as Settings and [TurnComposerUsageStrip]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadUsageStatusBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    threadId: String,
    repository: CodexRepository,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        ThreadUsageStatusSheetContent(
            threadId = threadId,
            repository = repository,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ThreadUsageStatusSheetContent(
    threadId: String,
    repository: CodexRepository,
    onDismiss: () -> Unit,
) {
    val ready by repository.isSessionReady.collectAsStateWithLifecycle()
    val conn by repository.connectionState.collectAsStateWithLifecycle()
    val hasResolved by repository.hasResolvedRateLimitsSnapshot.collectAsStateWithLifecycle()
    val isLoadingRL by repository.isLoadingRateLimits.collectAsStateWithLifecycle()
    val rlErr by repository.rateLimitsErrorMessage.collectAsStateWithLifecycle()
    val buckets by repository.rateLimitBuckets.collectAsStateWithLifecycle()
    val contextUsageMap by repository.contextWindowUsageByThread.collectAsStateWithLifecycle()
    val contextLoading by repository.contextWindowUsageLoadingThreads.collectAsStateWithLifecycle()
    val contextErrors by repository.contextWindowUsageErrorByThread.collectAsStateWithLifecycle()
    val runningTurnByThread by repository.runningTurnIdByThread.collectAsStateWithLifecycle()
    val protectedRunningFallback by repository.protectedRunningFallbackThreadIds.collectAsStateWithLifecycle()
    val queuedDepthByThread by repository.turnDraftQueueDepthByThread.collectAsStateWithLifecycle()
    val queuedPreviewByThread by repository.turnDraftQueuePreviewByThread.collectAsStateWithLifecycle()
    val threads by repository.threads.collectAsStateWithLifecycle()
    val aiChangeSetPersistence = LocalAIChangeSetPersistence.current

    val displayRows = remember(buckets) { CodexRateLimitBucket.visibleDisplayRows(buckets) }
    val usage: ContextWindowUsage? = contextUsageMap[threadId]
    val ctxLoading = contextLoading.contains(threadId)
    val ctxErr = contextErrors[threadId]
    val connected = conn is ConnectionState.Connected
    val anyLoading = ctxLoading || isLoadingRL
    val scope = rememberCoroutineScope()
    val isThreadRunning =
        remember(threadId, runningTurnByThread, protectedRunningFallback) {
            TurnUsageSheetLogic.isThreadTurnActive(threadId, runningTurnByThread, protectedRunningFallback)
        }
    val queuedDraftCount =
        remember(threadId, queuedDepthByThread) {
            queuedDepthByThread[threadId] ?: 0
        }
    val queuedPreviews =
        remember(threadId, queuedPreviewByThread) {
            queuedPreviewByThread[threadId].orEmpty()
        }
    var recentChangeSets by remember(threadId) {
        mutableStateOf(TurnUsageSheetLogic.recentChangeSetsForThread(threadId, aiChangeSetPersistence.load()))
    }
    val threadCwd =
        remember(threadId, threads) {
            threads.firstOrNull { it.id == threadId }?.cwd
        }

    val refreshAllCd = stringResource(R.string.cd_turn_usage_sheet_refresh_all)
    val doneCd = stringResource(R.string.cd_turn_usage_sheet_close)
    val combinedLoadingCd = stringResource(R.string.cd_usage_combined_usage_loading)

    LaunchedEffect(threadId) {
        recentChangeSets = TurnUsageSheetLogic.recentChangeSetsForThread(threadId, aiChangeSetPersistence.load())
        runCatching { repository.refreshUsageStatus(threadId) }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.turn_usage_sheet_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.turn_usage_sheet_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ThreadRuntimeStatusBlock(
            connected = connected,
            sessionReady = ready,
            isThreadRunning = isThreadRunning,
            queuedDraftCount = queuedDraftCount,
            queuedPreviews = queuedPreviews,
            onRemoveQueuedDraft = { draftId ->
                scope.launch {
                    runCatching { repository.removeQueuedTurnDraft(threadId, draftId) }
                }
            },
        )
        if (!ready || !connected) {
            Text(
                text = stringResource(R.string.usage_rate_limits_offline),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = stringResource(R.string.usage_context_window_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.usage_context_window_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ContextWindowStatusBlock(usage = usage, loading = ctxLoading, error = ctxErr)

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.usage_account_limits_title),
                style = MaterialTheme.typography.titleSmall,
            )
            RateLimitsStatusBlock(
                displayRows = displayRows,
                isLoading = isLoadingRL,
                error = rlErr,
                hasResolvedSnapshot = hasResolved,
            )
            AssistantRevertStatusBlock(
                changeSets = recentChangeSets,
                repository = repository,
                threadCwd = threadCwd,
                onChangeSetsUpdated = {
                    recentChangeSets = TurnUsageSheetLogic.recentChangeSetsForThread(threadId, aiChangeSetPersistence.load())
                },
                markChangeSetReverted = { changeSetId ->
                    val now = Instant.now()
                    aiChangeSetPersistence.save(
                        TurnUsageSheetLogic.markChangeSetReverted(
                            changeSets = aiChangeSetPersistence.load(),
                            changeSetId = changeSetId,
                            now = now,
                        ),
                    )
                },
                recordChangeSetRevertError = { changeSetId, errorMessage ->
                    val now = Instant.now()
                    aiChangeSetPersistence.save(
                        TurnUsageSheetLogic.recordChangeSetRevertError(
                            changeSets = aiChangeSetPersistence.load(),
                            changeSetId = changeSetId,
                            message = errorMessage,
                            now = now,
                        ),
                    )
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            TextButton(
                onClick = {
                    scope.launch {
                        runCatching { repository.refreshUsageStatus(threadId) }
                    }
                },
                enabled = ready && connected && !anyLoading,
                modifier = Modifier.semantics { contentDescription = refreshAllCd },
            ) {
                Text(stringResource(R.string.turn_usage_sheet_refresh_all))
            }
            if (anyLoading) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .size(22.dp)
                            .semantics { contentDescription = combinedLoadingCd },
                    strokeWidth = 2.dp,
                )
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.semantics { contentDescription = doneCd },
        ) {
            Text(stringResource(R.string.turn_usage_sheet_done))
        }
    }
}

@Composable
private fun ContextWindowStatusBlock(
    usage: ContextWindowUsage?,
    loading: Boolean,
    error: String?,
) {
    when {
        loading -> {
            Text(
                text = stringResource(R.string.turn_usage_strip_context_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error != null -> {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        usage == null -> {
            Text(
                text = stringResource(R.string.usage_context_window_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        else -> {
            Text(
                text =
                    stringResource(
                        R.string.usage_context_window_tokens,
                        usage.tokensUsedFormatted,
                        usage.tokenLimitFormatted,
                        usage.percentUsed,
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { usage.fractionUsed.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ThreadRuntimeStatusBlock(
    connected: Boolean,
    sessionReady: Boolean,
    isThreadRunning: Boolean,
    queuedDraftCount: Int,
    queuedPreviews: List<QueuedTurnDraftPreview>,
    onRemoveQueuedDraft: (String) -> Unit,
) {
    val queueItemEmptyLabel = stringResource(R.string.turn_queue_item_empty)
    Text(
        text = stringResource(R.string.turn_usage_runtime_title),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        text = stringResource(if (connected) R.string.turn_usage_runtime_connected else R.string.turn_usage_runtime_disconnected),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = stringResource(if (sessionReady) R.string.turn_usage_runtime_ready else R.string.turn_usage_runtime_not_ready),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = stringResource(if (isThreadRunning) R.string.turn_usage_runtime_running else R.string.turn_usage_runtime_idle),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (queuedDraftCount > 0) {
        Text(
            text = stringResource(R.string.turn_queue_pending_count, queuedDraftCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        queuedPreviews.take(3).forEach { draft ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = draft.text.lines().firstOrNull()?.trim().orEmpty().ifBlank { queueItemEmptyLabel },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val meta =
                        buildList {
                            if (draft.attachmentCount > 0) {
                                add(stringResource(R.string.turn_queue_item_attachments, draft.attachmentCount))
                            }
                            if (draft.collaborationMode == com.remodex.mobile.core.model.CodexCollaborationModeKind.plan) {
                                add(stringResource(R.string.turn_plan_mode_chip))
                            }
                        }.joinToString(" / ")
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { onRemoveQueuedDraft(draft.id) }) {
                    Text(stringResource(R.string.turn_queue_remove))
                }
            }
        }
        if (queuedPreviews.size > 3) {
            Text(
                text = stringResource(R.string.turn_queue_more_items, queuedPreviews.size - 3),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssistantRevertStatusBlock(
    changeSets: List<AIChangeSet>,
    repository: CodexRepository,
    threadCwd: String?,
    onChangeSetsUpdated: () -> Unit,
    markChangeSetReverted: (changeSetId: String) -> Unit,
    recordChangeSetRevertError: (changeSetId: String, errorMessage: String) -> Unit,
) {
    Text(
        text = stringResource(R.string.turn_usage_revert_title),
        style = MaterialTheme.typography.titleSmall,
    )
    if (changeSets.isEmpty()) {
        Text(
            text = stringResource(R.string.turn_usage_revert_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val runtimeRevertRpcAvailable = true
    val revertService = remember(repository) { AiChangeSetRevertService(repository) }
    val scope = rememberCoroutineScope()
    var expandedIds by remember(changeSets) { mutableStateOf(emptySet<String>()) }
    var applyingIds by remember(changeSets) { mutableStateOf(emptySet<String>()) }
    var actionErrors by remember(changeSets) { mutableStateOf<Map<String, String>>(emptyMap()) }
    changeSets.take(6).forEach { changeSet ->
        val statusLabel =
            when (changeSet.status) {
                AIChangeSetStatus.collecting -> stringResource(R.string.turn_usage_revert_status_collecting)
                AIChangeSetStatus.ready -> stringResource(R.string.turn_usage_revert_status_ready)
                AIChangeSetStatus.reverted -> stringResource(R.string.turn_usage_revert_status_reverted)
                AIChangeSetStatus.failed -> stringResource(R.string.turn_usage_revert_status_failed)
                AIChangeSetStatus.notRevertable -> stringResource(R.string.turn_usage_revert_status_not_revertable)
            }
        val blockReason = TurnUsageSheetLogic.revertPrimaryBlockReason(changeSet, runtimeRevertRpcAvailable)
        val blockReasonMessage =
            when (blockReason) {
                AssistantRevertPrimaryBlockReason.RuntimeEndpointUnavailable ->
                    stringResource(R.string.turn_usage_revert_reason_runtime_unavailable)
                AssistantRevertPrimaryBlockReason.AlreadyReverted ->
                    stringResource(R.string.turn_usage_revert_reason_already_reverted)
                AssistantRevertPrimaryBlockReason.StatusNotReady ->
                    stringResource(R.string.turn_usage_revert_reason_not_ready)
                AssistantRevertPrimaryBlockReason.MissingInversePatch ->
                    stringResource(R.string.turn_usage_revert_reason_missing_inverse)
                AssistantRevertPrimaryBlockReason.NotRevertableStatus ->
                    stringResource(R.string.turn_usage_revert_reason_not_revertable)
                AssistantRevertPrimaryBlockReason.ChangeSetFailed ->
                    stringResource(R.string.turn_usage_revert_reason_failed)
                null -> null
            }
        val isExpanded = changeSet.id in expandedIds
        val isApplying = changeSet.id in applyingIds
        val actionError = actionErrors[changeSet.id]
        val workingDirectory = changeSet.repoRoot ?: threadCwd
        val blockReasonOverride =
            when {
                workingDirectory.isNullOrBlank() ->
                    stringResource(R.string.turn_usage_revert_reason_missing_cwd)
                else -> null
            }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = MaterialTheme.shapes.small,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.turn_usage_revert_item_title, changeSet.turnId.takeLast(8)),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text =
                        stringResource(
                            R.string.turn_usage_revert_item_metrics,
                            changeSet.fileChanges.size,
                            changeSet.fileChanges.sumOf { it.additions },
                            changeSet.fileChanges.sumOf { it.deletions },
                        ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            expandedIds =
                                if (isExpanded) {
                                    expandedIds - changeSet.id
                                } else {
                                    expandedIds + changeSet.id
                                }
                        },
                    ) {
                        Text(
                            if (isExpanded) {
                                stringResource(R.string.turn_usage_revert_hide_details)
                            } else {
                                stringResource(R.string.turn_usage_revert_show_details)
                            },
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            if (workingDirectory.isNullOrBlank() || isApplying) return@TextButton
                            scope.launch {
                                applyingIds = applyingIds + changeSet.id
                                actionErrors = actionErrors - changeSet.id
                                val result =
                                    runCatching {
                                        revertService.apply(changeSet = changeSet, workingDirectory = workingDirectory)
                                    }
                                result
                                    .onSuccess { applyResult ->
                                        if (applyResult.success) {
                                            markChangeSetReverted(changeSet.id)
                                        } else {
                                            val message =
                                                applyResult.unsupportedReasons.firstOrNull()
                                                    ?: applyResult.conflicts.firstOrNull()?.message
                                                    ?: "Patch revert failed."
                                            recordChangeSetRevertError(changeSet.id, message)
                                            actionErrors = actionErrors + (changeSet.id to message)
                                        }
                                        onChangeSetsUpdated()
                                    }.onFailure { error ->
                                        val message = error.message ?: "Patch revert failed."
                                        recordChangeSetRevertError(changeSet.id, message)
                                        actionErrors = actionErrors + (changeSet.id to message)
                                        onChangeSetsUpdated()
                                    }
                                applyingIds = applyingIds - changeSet.id
                            }
                        },
                        enabled =
                            !isApplying &&
                                !workingDirectory.isNullOrBlank() &&
                                TurnUsageSheetLogic.revertPrimaryEnabled(changeSet, runtimeRevertRpcAvailable),
                    ) {
                        Text(stringResource(R.string.turn_usage_revert_action))
                    }
                }
                if (blockReasonOverride != null || blockReasonMessage != null) {
                    Text(
                        text = blockReasonOverride ?: blockReasonMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (actionError != null) {
                    Text(
                        text = actionError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isExpanded) {
                    changeSet.unsupportedReasons.take(3).forEach { reason ->
                        Text(
                            text = "- $reason",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    changeSet.fileChanges.take(5).forEach { fileChange ->
                        Text(
                            text =
                                stringResource(
                                    R.string.turn_usage_revert_item_file_line,
                                    fileChange.path,
                                    fileChange.additions,
                                    fileChange.deletions,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
    if (changeSets.size > 6) {
        Text(
            text = stringResource(R.string.turn_usage_revert_more, changeSets.size - 6),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RateLimitsStatusBlock(
    displayRows: List<CodexRateLimitDisplayRow>,
    isLoading: Boolean,
    error: String?,
    hasResolvedSnapshot: Boolean,
) {
    when {
        error != null -> {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        displayRows.isNotEmpty() -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                displayRows.forEach { row -> UsageRateLimitRowDetail(row) }
            }
        }
        isLoading -> {
            Text(
                text = stringResource(R.string.turn_usage_strip_limits_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        hasResolvedSnapshot -> {
            Text(
                text = stringResource(R.string.usage_rate_limits_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        else -> {
            Text(
                text = stringResource(R.string.turn_usage_strip_limits_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UsageRateLimitRowDetail(row: CodexRateLimitDisplayRow) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { row.window.clampedUsedPercent / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.usage_rate_limits_percent, row.window.clampedUsedPercent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
