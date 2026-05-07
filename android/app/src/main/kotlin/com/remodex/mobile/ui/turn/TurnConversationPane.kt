package com.remodex.mobile.ui.turn

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remodex.mobile.R
import com.remodex.mobile.BuildConfig
import com.remodex.mobile.core.model.AIChangeSet
import com.remodex.mobile.core.model.CodexAccessMode
import com.remodex.mobile.core.model.CodexCollaborationModeKind
import com.remodex.mobile.core.model.CodexFileAttachment
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.GitBranchesWithStatusResult
import com.remodex.mobile.core.model.CodexModelOption
import com.remodex.mobile.core.model.CodexPluginMetadata
import com.remodex.mobile.core.model.CodexReviewTarget
import com.remodex.mobile.core.model.CodexServiceTier
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.TurnUsageSheetLogic
import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.data.GitBranchDisplayMapper
import com.remodex.mobile.data.GitBranchPickerRules
import com.remodex.mobile.data.QueuedTurnDraftPreview
import com.remodex.mobile.data.TurnWorktreePathRouting
import com.remodex.mobile.data.WorktreeFlowCoordinator
import com.remodex.mobile.data.WorktreeFlowHandoffOutcome
import com.remodex.mobile.data.gitWorkingDirectoryForGitActions
import com.remodex.mobile.data.loadGitBranchesWithStatus
import com.remodex.mobile.services.AiChangeSetRevertService
import com.remodex.mobile.services.GitActionsService
import com.remodex.mobile.data.TurnAttachmentCodec
import com.remodex.mobile.data.TurnFileAttachmentCodec
import com.remodex.mobile.ui.LocalAIChangeSetPersistence
import com.remodex.mobile.ui.agent.MessageList
import com.remodex.mobile.ui.home.RootReconnectRecoveryAction
import com.remodex.mobile.ui.home.RootReconnectUiState
import com.remodex.mobile.services.CodexLookupService
import com.remodex.mobile.services.isPluginListUnsupported
import com.remodex.mobile.core.voice.BridgeVoiceRecorder
import com.remodex.mobile.core.voice.VoiceDraftAppend
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.rememberModalBottomSheetState

private const val MAX_COMPOSER_ATTACHMENTS = 4
private const val MAX_NON_IMAGE_ATTACHMENT_BYTES = 256 * 1024
private const val MAX_NON_IMAGE_ATTACHMENT_TEXT_CHARS = 8_000
private const val TIMELINE_INITIAL_RENDER_TAIL = 48
private const val TIMELINE_LOAD_EARLIER_PAGE = 80
private const val TIMELINE_STAGING_THRESHOLD = 72
private const val STARTUP_TRACE_TAG = "RemodexStartup"
private const val SMART_SCROLL_CTA_SCROLLING_DWELL_MS = 1_000L
private const val SMART_SCROLL_CTA_STOP_HIDE_DELAY_MS = 1_000L
private const val SMART_SCROLL_FADE_JUMP_DISTANCE_ITEMS = 24
/** Shaves a few dp off IME bottom padding so the composer sits slightly closer to the keyboard. */
private val TurnConversationImeBottomTrim = 12.dp

/**
 * Guscio conversazione: timeline + composer (testo, invio, allegati immagine).
 */
@Composable
fun TurnConversationPane(
    threadId: String,
    repository: CodexRepository,
    reconnectUiState: RootReconnectUiState = RootReconnectUiState(),
    onReconnectSavedPairing: () -> Unit = {},
    onWakeSavedComputer: () -> Unit = {},
    onOpenPairingScanner: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ready by repository.isSessionReady.collectAsStateWithLifecycle()
    val availableModels by repository.availableModels.collectAsStateWithLifecycle()
    val isLoadingModels by repository.isLoadingModels.collectAsStateWithLifecycle()
    val selectedModelId by repository.selectedModelId.collectAsStateWithLifecycle()
    val selectedReasoningEffort by repository.selectedReasoningEffort.collectAsStateWithLifecycle()
    val selectedAccessMode by repository.selectedAccessMode.collectAsStateWithLifecycle()
    val selectedServiceTier by repository.selectedServiceTier.collectAsStateWithLifecycle()
    val messagesByThread by repository.messagesByThread.collectAsStateWithLifecycle()
    val commandExecutionDetailsByItemId by repository.commandExecutionDetailsByItemId.collectAsStateWithLifecycle()
    val historyPaginationByThread by repository.threadHistoryPaginationByThread.collectAsStateWithLifecycle()
    val loadingOlderHistoryThreadIds by repository.loadingOlderHistoryThreadIds.collectAsStateWithLifecycle()
    val olderHistoryErrorByThread by repository.olderHistoryErrorByThread.collectAsStateWithLifecycle()
    val threads by repository.threads.collectAsStateWithLifecycle()
    val connectionState by repository.connectionState.collectAsStateWithLifecycle()
    val hasResolvedRateLimits by repository.hasResolvedRateLimitsSnapshot.collectAsStateWithLifecycle()
    val isLoadingRateLimits by repository.isLoadingRateLimits.collectAsStateWithLifecycle()
    val rateLimitsError by repository.rateLimitsErrorMessage.collectAsStateWithLifecycle()
    val runningTurnByThread by repository.runningTurnIdByThread.collectAsStateWithLifecycle()
    val protectedRunningFallback by repository.protectedRunningFallbackThreadIds.collectAsStateWithLifecycle()
    val queuedDraftDepthByThread by repository.turnDraftQueueDepthByThread.collectAsStateWithLifecycle()
    val queuedDraftPreviewByThread by repository.turnDraftQueuePreviewByThread.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val aiChangeSetPersistence = LocalAIChangeSetPersistence.current
    var sending by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var isPlanModeEnabled by rememberSaveable(threadId) { mutableStateOf(false) }
    var planAccessoryExpanded by rememberSaveable(threadId) { mutableStateOf(false) }
    var composerAttachments by remember { mutableStateOf<List<TurnComposerAttachment>>(emptyList()) }
    var mentionChips by remember(threadId) { mutableStateOf<List<ComposerMentionChipPayload>>(emptyList()) }
    var availableSkills by remember(threadId) { mutableStateOf<List<SkillAutocompleteSuggestion>>(emptyList()) }
    var availablePlugins by remember(threadId) { mutableStateOf<List<CodexPluginMetadata>>(emptyList()) }
    var pluginAutocompleteLoading by remember(threadId) { mutableStateOf(false) }
    var cachedPluginSearchIndexByRoot by remember(repository) { mutableStateOf<Map<String, List<CodexPluginMetadata>>>(emptyMap()) }
    var unsupportedPluginAutocompleteRoots by remember(repository) { mutableStateOf<Set<String>>(emptySet()) }
    var availableFileMatches by remember(threadId) { mutableStateOf<List<com.remodex.mobile.core.model.CodexFuzzyFileMatch>>(emptyList()) }
    var voicePhase by remember(threadId) { mutableStateOf(TurnVoicePhase.Idle) }
    var voiceAudioLevels by remember(threadId) { mutableStateOf<List<Float>>(emptyList()) }
    var voiceRecordingDurationSeconds by remember(threadId) { mutableStateOf(0.0) }
    var showForkThreadSheet by remember(threadId) { mutableStateOf(false) }
    var showFeedbackDialog by remember(threadId) { mutableStateOf(false) }
    var showWorktreeHandoffSheet by remember(threadId) { mutableStateOf(false) }
    var forkingThread by remember(threadId) { mutableStateOf(false) }
    var showPlanDetailsSheet by remember(threadId) { mutableStateOf(false) }
    var reviewTargetName by rememberSaveable(threadId) { mutableStateOf<String?>(null) }
    var reviewBaseBranch by rememberSaveable(threadId) { mutableStateOf<String?>(null) }
    val voiceRecorder = remember(threadId) { BridgeVoiceRecorder() }
    val lookupService = remember(repository) { CodexLookupService(repository) }
    var transcribeJob by remember(threadId) { mutableStateOf<Job?>(null) }
    var threadChangeSets by remember(threadId) {
        mutableStateOf(TurnUsageSheetLogic.recentChangeSetsForThread(threadId, aiChangeSetPersistence.load(), limit = 50))
    }
    var applyingUndoChangeSetIds by remember(threadId) { mutableStateOf(emptySet<String>()) }
    var inlineUndoError by remember(threadId) { mutableStateOf<String?>(null) }

    fun appendVoiceAudioLevel(level: Float) {
        scope.launch {
            if (voicePhase != TurnVoicePhase.Recording) return@launch
            voiceAudioLevels = (voiceAudioLevels + level.coerceIn(0f, 1f)).takeLast(240)
        }
    }

    fun resetVoiceMeteringState() {
        voiceAudioLevels = emptyList()
        voiceRecordingDurationSeconds = 0.0
    }

    val activeThread =
        remember(threadId, threads) {
            threads.firstOrNull { it.id == threadId }
        }
    val gitCwd =
        remember(activeThread) {
            activeThread.gitWorkingDirectoryForGitActions()
        }
    var gitBranchPaneState by remember(threadId) { mutableStateOf<GitBranchPaneState>(GitBranchPaneState.UnavailableNoProject) }
    var gitBranchReloadNonce by remember(threadId) { mutableIntStateOf(0) }
    var isSwitchingGitBranch by remember(threadId) { mutableStateOf(false) }
    var isHandingOffWorktree by remember(threadId) { mutableStateOf(false) }
    var gitBranchCheckoutError by remember(threadId) { mutableStateOf<String?>(null) }
    var worktreeHandoffError by remember(threadId) { mutableStateOf<String?>(null) }
    val reviewTarget =
        remember(reviewTargetName) {
            reviewTargetName?.let { raw ->
                runCatching { CodexReviewTarget.valueOf(raw) }.getOrNull()
            }
        }
    val loadedGitBranchSummary =
        (gitBranchPaneState as? GitBranchPaneState.Loaded)?.summary
    val defaultReviewBaseBranch =
        remember(loadedGitBranchSummary) {
            reviewSelectableDefaultBranch(
                defaultBranch = loadedGitBranchSummary?.defaultBranch,
                availableBranches = loadedGitBranchSummary?.branches.orEmpty(),
            )
        }
    val resolvedReviewBaseBranch =
        remember(reviewBaseBranch, defaultReviewBaseBranch, loadedGitBranchSummary) {
            resolveReviewBaseBranch(
                selectedBaseBranch = reviewBaseBranch,
                defaultBranch = defaultReviewBaseBranch,
                availableBranches = loadedGitBranchSummary?.branches.orEmpty(),
            )
        }
    val localWorktreeHandoffTargetPath =
        remember(gitBranchPaneState) {
            val summary = (gitBranchPaneState as? GitBranchPaneState.Loaded)?.summary ?: return@remember null
            val preferredBranch = summary.defaultBranch ?: summary.currentBranch
            preferredBranch
                ?.let { summary.worktreePathByBranch[it]?.trim()?.takeIf { path -> path.isNotEmpty() } }
                ?: summary.worktreePathByBranch.values.firstOrNull { it.isNotBlank() }?.trim()
        }

    val isThreadRunning =
        remember(threadId, runningTurnByThread, protectedRunningFallback) {
            runningTurnByThread.containsKey(threadId) || protectedRunningFallback.contains(threadId)
        }
    val queuedDraftCount =
        remember(threadId, queuedDraftDepthByThread) {
            queuedDraftDepthByThread[threadId] ?: 0
        }
    val queuedDraftPreviews =
        remember(threadId, queuedDraftPreviewByThread) {
            queuedDraftPreviewByThread[threadId].orEmpty()
        }
    val hasComposerDraftContent =
        remember(draft, composerAttachments, mentionChips, isPlanModeEnabled, reviewTarget) {
            draft.trim().isNotEmpty() ||
                composerAttachments.isNotEmpty() ||
                mentionChips.isNotEmpty() ||
                isPlanModeEnabled ||
                reviewTarget != null
        }
    val canRestoreQueuedDrafts =
        remember(isThreadRunning, sending, hasComposerDraftContent) {
            !isThreadRunning && !sending && !hasComposerDraftContent
        }
    val branchPickerEnabled =
        remember(
            gitCwd,
            connectionState,
            ready,
            isThreadRunning,
            sending,
            isSwitchingGitBranch,
            isHandingOffWorktree,
            gitBranchPaneState,
        ) {
            gitCwd != null &&
                connectionState is ConnectionState.Connected &&
                ready &&
                !isThreadRunning &&
                !sending &&
                !isSwitchingGitBranch &&
                !isHandingOffWorktree &&
                gitBranchPaneState is GitBranchPaneState.Loaded
        }
    val connectionRecoverySnapshot =
        remember(connectionState, reconnectUiState) {
            TurnConnectionRecoverySnapshotBuilder.makeSnapshot(
                hasReconnectCandidate = true,
                connectionState = connectionState,
                reconnectUiState = reconnectUiState,
            )
        }

    val attachmentLimitMessage = stringResource(R.string.turn_attachment_limit, MAX_COMPOSER_ATTACHMENTS)
    val attachmentOverflowMessage = stringResource(R.string.turn_attachment_overflow, MAX_COMPOSER_ATTACHMENTS)
    val attachmentLoadFailedMessage = stringResource(R.string.turn_attachment_load_failed)
    val attachmentFileTooLargeMessage = stringResource(R.string.turn_attachment_file_too_large, MAX_NON_IMAGE_ATTACHMENT_BYTES / 1024)
    val attachmentFileBinarySummary = stringResource(R.string.turn_attachment_file_binary_summary)
    val attachmentCameraUnavailableMessage = stringResource(R.string.turn_attachment_camera_unavailable)
    val attachmentCameraPermissionDeniedMessage = stringResource(R.string.turn_attachment_camera_permission_denied)
    val voiceMicDeniedMessage = stringResource(R.string.turn_voice_mic_permission_denied)
    val voiceRecorderFailedMessage = stringResource(R.string.turn_voice_recorder_failed)
    val voiceNoAudioMessage = stringResource(R.string.turn_voice_no_audio)
    val voiceTranscriptionFailedMessage = stringResource(R.string.turn_voice_transcription_failed)
    val queuedDraftSendFailedMessage = stringResource(R.string.turn_queue_send_failed)
    val queuedDraftRestoreBlockedMessage = stringResource(R.string.turn_queue_restore_requires_empty)
    val planApplyRequiresEmptyMessage = stringResource(R.string.turn_plan_apply_requires_empty)
    val reviewRunningUnavailableMessage = stringResource(R.string.turn_review_unavailable_running)
    val reviewRequiresEmptyMessage = stringResource(R.string.turn_review_requires_empty)
    val reviewNoDefaultBranchMessage = stringResource(R.string.turn_review_no_default_branch)
    val reviewNoBaseBranchAvailableMessage = stringResource(R.string.turn_review_no_base_branch_available)
    val reviewNoAttachmentsMessage = stringResource(R.string.turn_review_no_attachments)
    val handoffMissingBaseMessage = stringResource(R.string.turn_worktree_handoff_missing_base)
    val handoffMissingLocalMessage = stringResource(R.string.turn_worktree_handoff_missing_local)

    fun reviewDraftText(
        target: CodexReviewTarget,
        baseBranch: String?,
    ): String =
        when (target) {
            CodexReviewTarget.uncommittedChanges -> "Review current changes"
            CodexReviewTarget.baseBranch -> "Review against base branch ${baseBranch.orEmpty()}"
        }

    fun clearReviewTarget() {
        reviewTargetName = null
        reviewBaseBranch = null
    }

    fun selectReviewTarget(
        target: CodexReviewTarget,
        baseBranch: String? = null,
    ) {
        if (
            TurnComposerReviewModeRules.hasComposerContentConflictingWithReview(
                draftText = draft,
                mentionChipCount = mentionChips.size,
                readyAttachmentCount =
                    composerAttachments.count {
                        it.state is TurnComposerAttachmentState.ReadyImage ||
                            it.state is TurnComposerAttachmentState.ReadyFile
                    },
                hasBlockingAttachments =
                    composerAttachments.any {
                        it.state == TurnComposerAttachmentState.Loading ||
                            it.state is TurnComposerAttachmentState.Failed
                    },
                isPlanModeEnabled = isPlanModeEnabled,
            )
        ) {
            lastError = if (composerAttachments.isNotEmpty()) reviewNoAttachmentsMessage else reviewRequiresEmptyMessage
            return
        }
        reviewTargetName = target.name
        reviewBaseBranch =
            resolveReviewBaseBranch(
                selectedBaseBranch = baseBranch,
                defaultBranch = defaultReviewBaseBranch,
                availableBranches = loadedGitBranchSummary?.branches.orEmpty(),
            )
        draft = reviewDraftText(target, reviewBaseBranch)
        mentionChips = emptyList()
        isPlanModeEnabled = false
        lastError = null
    }

    fun handoffCurrentThread(selectedBaseBranch: String? = null) {
        val cwd = gitCwd
        if (cwd == null || isThreadRunning || sending || isHandingOffWorktree) return
        val isWorktreeProject = activeThread?.isManagedWorktreeProject == true
        val baseBranch = selectedBaseBranch?.trim()?.takeIf { it.isNotEmpty() } ?: defaultReviewBaseBranch
        val localTargetPath = localWorktreeHandoffTargetPath
        val associatedWorktreePath = repository.associatedManagedWorktreePathFor(threadId)
        if (isWorktreeProject && localTargetPath == null) {
            worktreeHandoffError = handoffMissingLocalMessage
            return
        }
        if (!isWorktreeProject && baseBranch == null && associatedWorktreePath == null) {
            worktreeHandoffError = handoffMissingBaseMessage
            showWorktreeHandoffSheet = true
            return
        }
        scope.launch {
            isHandingOffWorktree = true
            worktreeHandoffError = null
            try {
                showWorktreeHandoffSheet = false
                val outcome =
                    runCatching {
                        val coordinator = WorktreeFlowCoordinator(repository)
                        if (isWorktreeProject) {
                            coordinator.handoffThreadToProjectPath(
                                threadId = threadId,
                                sourceProjectPath = cwd,
                                targetProjectPath = localTargetPath ?: error(handoffMissingLocalMessage),
                            )
                        } else {
                            coordinator.handoffThreadToWorktree(
                                threadId = threadId,
                                sourceProjectPath = cwd,
                                associatedWorktreePath = associatedWorktreePath,
                                baseBranchForNewWorktree = baseBranch,
                            )
                        }
                    }.getOrElse { e ->
                        worktreeHandoffError = GitBranchDisplayMapper.userVisibleMessage(e)
                        return@launch
                    }
                when (outcome) {
                    is WorktreeFlowHandoffOutcome.Moved -> {
                        repository.setActiveThreadId(outcome.move.thread.id)
                        gitBranchReloadNonce++
                    }
                    WorktreeFlowHandoffOutcome.MissingAssociatedWorktree -> {
                        worktreeHandoffError =
                            "The associated worktree is no longer available. Create a new managed worktree to continue."
                        showWorktreeHandoffSheet = true
                    }
                }
            } finally {
                isHandingOffWorktree = false
            }
        }
    }

    fun applyPlanToComposer() {
        if (hasComposerDraftContent) {
            lastError = planApplyRequiresEmptyMessage
        } else {
            draft = "Implement plan."
            isPlanModeEnabled = false
            mentionChips = emptyList()
        }
    }

    val voiceInteractionEnabled =
        remember(ready, connectionState, sending) {
            ready && connectionState is ConnectionState.Connected && !sending
        }

    fun remainingAttachmentSlots(): Int = (MAX_COMPOSER_ATTACHMENTS - composerAttachments.size).coerceAtLeast(0)

    fun appendLoadingAttachment(): String =
        UUID.randomUUID().toString().also { attachmentId ->
            composerAttachments = composerAttachments.withLoadingAttachment(attachmentId)
        }

    fun updateImageAttachmentResult(
        attachmentId: String,
        attachment: CodexImageAttachment?,
    ) {
        composerAttachments =
            composerAttachments.withImageAttachmentResult(
                attachmentId = attachmentId,
                attachment = attachment,
                failedMessage = attachmentLoadFailedMessage,
            )
        if (attachment == null) {
            lastError = attachmentLoadFailedMessage
        }
    }

    fun updateFileAttachmentResult(
        attachmentId: String,
        attachment: CodexFileAttachment?,
        errorMessage: String?,
    ) {
        val resolvedErrorMessage = errorMessage ?: attachmentLoadFailedMessage
        composerAttachments =
            composerAttachments.withFileAttachmentResult(
                attachmentId = attachmentId,
                attachment = attachment,
                failedMessage = resolvedErrorMessage,
            )
        if (attachment == null) {
            lastError = resolvedErrorMessage
        }
    }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            val remainingSlots = remainingAttachmentSlots()
            if (remainingSlots <= 0) {
                lastError = attachmentLimitMessage
                return@rememberLauncherForActivityResult
            }
            val acceptedUris = uris.take(remainingSlots)
            if (acceptedUris.size < uris.size) {
                lastError = attachmentOverflowMessage
            }
            acceptedUris.forEach { uri ->
                val attachmentId = appendLoadingAttachment()
                scope.launch {
                    val attachment =
                        withContext(Dispatchers.IO) {
                            TurnAttachmentCodec.makeAttachment(context, uri)
                        }
                    updateImageAttachmentResult(attachmentId, attachment)
                }
            }
        }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            val remainingSlots = remainingAttachmentSlots()
            if (remainingSlots <= 0) {
                lastError = attachmentLimitMessage
                return@rememberLauncherForActivityResult
            }
            val acceptedUris = uris.take(remainingSlots)
            if (acceptedUris.size < uris.size) {
                lastError = attachmentOverflowMessage
            }
            acceptedUris.forEach { uri ->
                val attachmentId = appendLoadingAttachment()
                scope.launch {
                    val contentType = context.contentResolver.getType(uri).orEmpty()
                    if (contentType.startsWith("image/")) {
                        val attachment =
                            withContext(Dispatchers.IO) {
                                TurnAttachmentCodec.makeAttachment(context, uri)
                            }
                        updateImageAttachmentResult(attachmentId, attachment)
                        return@launch
                    }
                    val decoded =
                        withContext(Dispatchers.IO) {
                            TurnFileAttachmentCodec.makeAttachment(
                                context = context,
                                uri = uri,
                                maxBytes = MAX_NON_IMAGE_ATTACHMENT_BYTES,
                                maxTextChars = MAX_NON_IMAGE_ATTACHMENT_TEXT_CHARS,
                                tooLargeMessage = { fileName, _ ->
                                    "$fileName: $attachmentFileTooLargeMessage"
                                },
                                loadFailedMessage = { fileName ->
                                    "$fileName: $attachmentLoadFailedMessage"
                                },
                            )
                        }
                    updateFileAttachmentResult(
                        attachmentId = attachmentId,
                        attachment = decoded.attachment,
                        errorMessage = decoded.errorMessage,
                    )
                }
            }
        }

    val cameraPreviewLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap == null) return@rememberLauncherForActivityResult
            if (remainingAttachmentSlots() <= 0) {
                lastError = attachmentLimitMessage
                return@rememberLauncherForActivityResult
            }
            val attachmentId = appendLoadingAttachment()
            scope.launch {
                val attachment =
                    withContext(Dispatchers.IO) {
                        TurnAttachmentCodec.makeAttachment(bitmap.toJpegByteArray() ?: return@withContext null)
                    }
                updateImageAttachmentResult(attachmentId, attachment)
            }
        }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                cameraPreviewLauncher.launch(null)
            } else {
                lastError = attachmentCameraPermissionDeniedMessage
            }
        }

    val audioPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                scope.launch {
                    if (voicePhase != TurnVoicePhase.Idle) return@launch
                    resetVoiceMeteringState()
                    val ok =
                        withContext(Dispatchers.IO) {
                            voiceRecorder.start(::appendVoiceAudioLevel)
                        }
                    if (ok) {
                        voicePhase = TurnVoicePhase.Recording
                    } else {
                        resetVoiceMeteringState()
                        lastError = voiceRecorderFailedMessage
                    }
                }
            } else {
                lastError = voiceMicDeniedMessage
            }
        }

    LaunchedEffect(threadId, ready) {
        if (ready) {
            runCatching { repository.syncThreadHistory(threadId) }
            if (availableModels.isEmpty()) {
                runCatching { repository.refreshModels() }
            }
        }
    }

    LaunchedEffect(threadId, ready, connectionState, hasResolvedRateLimits, rateLimitsError, isLoadingRateLimits) {
        if (ready &&
            connectionState is ConnectionState.Connected &&
            rateLimitsError == null &&
            !isLoadingRateLimits &&
            repository.shouldAutoRefreshUsageStatus(threadId)
        ) {
            runCatching { repository.refreshUsageStatus(threadId) }
        }
    }

    LaunchedEffect(threadId, ready, connectionState, activeThread?.cwd) {
        if (!ready || connectionState !is ConnectionState.Connected) {
            availableSkills = emptyList()
            return@LaunchedEffect
        }
        availableSkills =
            runCatching {
                com.remodex.mobile.ui.turn.loadSkillAutocompleteSuggestions(repository, activeThread?.cwd)
            }.getOrDefault(emptyList())
    }

    LaunchedEffect(threadId) {
        transcribeJob?.cancel()
        transcribeJob = null
        voiceRecorder.cancel()
        voicePhase = TurnVoicePhase.Idle
        resetVoiceMeteringState()
        lastError = null
        draft = ""
        composerAttachments = emptyList()
        mentionChips = emptyList()
        planAccessoryExpanded = false
    }

    LaunchedEffect(threadId, voicePhase) {
        if (voicePhase != TurnVoicePhase.Recording) return@LaunchedEffect
        val startedAtNanos = System.nanoTime()
        while (isActive && voicePhase == TurnVoicePhase.Recording) {
            voiceRecordingDurationSeconds = (System.nanoTime() - startedAtNanos) / 1_000_000_000.0
            delay(100)
        }
    }

    LaunchedEffect(threadId, gitCwd, connectionState, ready, gitBranchReloadNonce) {
        when {
            gitCwd == null -> {
                gitBranchPaneState = GitBranchPaneState.UnavailableNoProject
                return@LaunchedEffect
            }
            connectionState !is ConnectionState.Connected -> {
                gitBranchPaneState = GitBranchPaneState.AwaitingBridge
                return@LaunchedEffect
            }
            !ready -> {
                gitBranchPaneState = GitBranchPaneState.Loading
                return@LaunchedEffect
            }
            else -> {
                gitBranchPaneState = GitBranchPaneState.Loading
                val result = loadGitBranchesWithStatus(repository, gitCwd)
                gitBranchPaneState =
                    result.fold(
                        onSuccess = {
                            GitBranchPaneState.Loaded(GitBranchDisplayMapper.summaryFrom(it))
                        },
                        onFailure = {
                            GitBranchPaneState.Failed(GitBranchDisplayMapper.userVisibleMessage(it))
                        },
                    )
            }
        }
    }

    val messages =
        remember(threadId, messagesByThread) {
            messagesByThread[threadId].orEmpty().sortedWith(compareBy({ it.orderIndex }, { it.createdAt }))
        }
    LaunchedEffect(threadId, messages.size) {
        threadChangeSets =
            TurnUsageSheetLogic.recentChangeSetsForThread(threadId, aiChangeSetPersistence.load(), limit = 50)
    }
    val assistantUndoChangeSetsByMessageId =
        remember(messages, threadChangeSets) {
            assistantUndoChangeSetsByMessageId(messages = messages, changeSets = threadChangeSets)
        }
    var visibleTailCount by rememberSaveable(threadId) { mutableIntStateOf(TIMELINE_INITIAL_RENDER_TAIL) }
    LaunchedEffect(threadId, messages.size) {
        if (messages.size <= TIMELINE_STAGING_THRESHOLD) {
            visibleTailCount = messages.size.coerceAtLeast(TIMELINE_INITIAL_RENDER_TAIL)
        } else if (visibleTailCount < TIMELINE_INITIAL_RENDER_TAIL) {
            visibleTailCount = TIMELINE_INITIAL_RENDER_TAIL
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                STARTUP_TRACE_TAG,
                "timeline source thread=$threadId messages=${messages.size} visibleTail=$visibleTailCount",
            )
        }
    }
    val visibleMessages =
        remember(messages, visibleTailCount) {
            if (messages.size <= TIMELINE_STAGING_THRESHOLD) {
                messages
            } else {
                messages.takeLast(visibleTailCount.coerceAtMost(messages.size))
            }
        }
    val hiddenEarlierCount = (messages.size - visibleMessages.size).coerceAtLeast(0)
    val historyPaginationState = historyPaginationByThread[threadId]
    val canLoadOlderRemoteHistory = historyPaginationState?.canLoadOlder == true
    val isLoadingOlderHistory = threadId in loadingOlderHistoryThreadIds
    val olderHistoryError = olderHistoryErrorByThread[threadId]
    val pinnedPlanAccessoryMessage =
        remember(messages) {
            selectPinnedPlanAccessoryMessage(messages)
        }
    val fileAutocompleteCandidates =
        remember(messages) { com.remodex.mobile.ui.turn.extractThreadFileAutocompleteCandidates(messages) }
    val trailingToken =
        remember(draft) { TurnComposerTrailingTokens.parseTrailingToken(draft) }
    LaunchedEffect(threadId, ready, connectionState, activeThread?.cwd, trailingToken?.payload?.kind, trailingToken?.payload?.semanticValue) {
        val parse = trailingToken
        if (!ready || connectionState !is ConnectionState.Connected || parse?.payload?.kind != ComposerMentionKind.File) {
            availableFileMatches = emptyList()
            return@LaunchedEffect
        }
        val query = parse.payload.semanticValue.trim()
        val cwd = activeThread?.cwd?.trim()?.takeIf { it.isNotEmpty() }
        if (cwd.isNullOrEmpty() || query.isEmpty()) {
            availableFileMatches = emptyList()
            return@LaunchedEffect
        }
        availableFileMatches =
            runCatching {
                lookupService.fuzzyFileSearch(query = query, roots = listOf(cwd))
            }.getOrDefault(emptyList())
    }
    LaunchedEffect(threadId, ready, connectionState, activeThread?.cwd, trailingToken?.payload?.kind, trailingToken?.payload?.semanticValue) {
        val parse = trailingToken
        val shouldLoadPlugins =
            parse?.payload?.kind == ComposerMentionKind.Plugin ||
                (parse?.payload?.kind == ComposerMentionKind.File && isPluginAutocompleteQuery(parse.payload.semanticValue))
        if (!ready || connectionState !is ConnectionState.Connected || !shouldLoadPlugins) {
            availablePlugins = emptyList()
            pluginAutocompleteLoading = false
            return@LaunchedEffect
        }
        val cwd = activeThread?.cwd?.trim()?.takeIf { it.isNotEmpty() }
        if (cwd.isNullOrEmpty()) {
            availablePlugins = emptyList()
            pluginAutocompleteLoading = false
            return@LaunchedEffect
        }
        cachedPluginSearchIndexByRoot[cwd]?.let { cached ->
            availablePlugins = cached
            pluginAutocompleteLoading = false
            return@LaunchedEffect
        }
        if (unsupportedPluginAutocompleteRoots.contains(cwd)) {
            availablePlugins = emptyList()
            pluginAutocompleteLoading = false
            return@LaunchedEffect
        }
        pluginAutocompleteLoading = true
        runCatching {
            lookupService.listPlugins(cwds = listOf(cwd), forceReload = false)
        }.onSuccess { plugins ->
            cachedPluginSearchIndexByRoot = cachedPluginSearchIndexByRoot + (cwd to plugins)
            availablePlugins = plugins
        }.onFailure { error ->
            if (isPluginListUnsupported(error)) {
                unsupportedPluginAutocompleteRoots = unsupportedPluginAutocompleteRoots + cwd
            }
            availablePlugins = emptyList()
        }
        pluginAutocompleteLoading = false
    }
    val autocompleteState =
        remember(
            trailingToken,
            availableSkills,
            availablePlugins,
            pluginAutocompleteLoading,
            fileAutocompleteCandidates,
            availableFileMatches,
            isThreadRunning,
        ) {
            com.remodex.mobile.ui.turn.buildComposerAutocompleteState(
                parse = trailingToken,
                skillSuggestions = availableSkills,
                pluginSuggestions = availablePlugins,
                fileCandidates = fileAutocompleteCandidates,
                fileMatches = availableFileMatches,
                isThreadRunning = isThreadRunning,
                isPluginLoading = pluginAutocompleteLoading,
            )
        }
    val listState = rememberLazyListState()
    val latestMessageId = visibleMessages.lastOrNull()?.id
    var lastAutoScrollThreadId by remember { mutableStateOf<String?>(null) }
    var latestSeenAtBottomMessageId by rememberSaveable(threadId) { mutableStateOf(latestMessageId) }
    var previousFirstVisibleItemIndex by remember(threadId) { mutableIntStateOf(0) }
    var scrollDirection by remember(threadId) { mutableIntStateOf(0) }
    var scrollDirectionWindowStartedAtMs by remember(threadId) { mutableStateOf(System.currentTimeMillis()) }
    var scrollDirectionChangeCount by remember(threadId) { mutableIntStateOf(0) }
    var showSmartScrollCtaForSustainedScroll by remember(threadId) { mutableStateOf(false) }
    var timelineContentVisible by remember(threadId) { mutableStateOf(true) }
    val timelineContentAlpha by animateFloatAsState(
        targetValue = if (timelineContentVisible) 1f else 0.08f,
        label = "timeline-content-alpha",
    )
    val shouldFollowBottom by remember {
        derivedStateOf {
            shouldFollowTimelineBottom(
                totalItemsCount = listState.layoutInfo.totalItemsCount,
                lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
            )
        }
    }
    val firstVisibleListItemIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    val lastVisibleListItemIndex by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
    }
    val totalListItemCount by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount }
    }
    val timelineListItemOffset =
        if (hiddenEarlierCount > 0 || canLoadOlderRemoteHistory) {
            1
        } else {
            0
        }
    val chatAnchors =
        remember(visibleMessages, timelineListItemOffset) {
            buildChatAnchors(
                messages = visibleMessages,
                listItemOffset = timelineListItemOffset,
            )
        }
    val smartScrollNavigationState =
        remember(
            totalListItemCount,
            firstVisibleListItemIndex,
            lastVisibleListItemIndex,
            chatAnchors,
            shouldFollowBottom,
            latestSeenAtBottomMessageId,
            latestMessageId,
            scrollDirectionChangeCount,
        ) {
            buildSmartScrollNavigationState(
                totalItemsCount = totalListItemCount,
                firstVisibleItemIndex = firstVisibleListItemIndex,
                lastVisibleItemIndex = lastVisibleListItemIndex,
                anchors = chatAnchors,
                isNearTop = firstVisibleListItemIndex <= timelineListItemOffset + 1,
                isNearBottom = shouldFollowBottom,
                hasNewMessagesBelow =
                    latestMessageId != null &&
                        latestSeenAtBottomMessageId != latestMessageId &&
                        !shouldFollowBottom,
                directionChangeCount = scrollDirectionChangeCount,
            )
        }
    LaunchedEffect(threadId) {
        lastAutoScrollThreadId = threadId
        latestSeenAtBottomMessageId = latestMessageId
        if (visibleMessages.isNotEmpty()) {
            listState.scrollToItem(visibleMessages.lastIndex)
            if (BuildConfig.DEBUG) {
                Log.d(
                    STARTUP_TRACE_TAG,
                    "timeline scrolled thread=$threadId visible=${visibleMessages.size} hiddenEarlier=$hiddenEarlierCount reason=thread",
                )
            }
        }
    }
    LaunchedEffect(threadId, listState) {
        previousFirstVisibleItemIndex = listState.firstVisibleItemIndex
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisible ->
                val now = System.currentTimeMillis()
                if (now - scrollDirectionWindowStartedAtMs > 10_000L) {
                    scrollDirectionWindowStartedAtMs = now
                    scrollDirectionChangeCount = 0
                }
                val nextDirection =
                    when {
                        firstVisible > previousFirstVisibleItemIndex -> 1
                        firstVisible < previousFirstVisibleItemIndex -> -1
                        else -> scrollDirection
                    }
                if (nextDirection != 0 && scrollDirection != 0 && nextDirection != scrollDirection) {
                    scrollDirectionChangeCount++
                }
                if (nextDirection != 0) {
                    scrollDirection = nextDirection
                }
                previousFirstVisibleItemIndex = firstVisible
            }
    }
    LaunchedEffect(threadId, listState) {
        showSmartScrollCtaForSustainedScroll = false
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (isScrolling) {
                    delay(SMART_SCROLL_CTA_SCROLLING_DWELL_MS)
                    showSmartScrollCtaForSustainedScroll = listState.isScrollInProgress
                } else {
                    delay(SMART_SCROLL_CTA_STOP_HIDE_DELAY_MS)
                    showSmartScrollCtaForSustainedScroll = listState.isScrollInProgress
                }
            }
    }
    LaunchedEffect(latestMessageId, shouldFollowBottom) {
        if (shouldFollowBottom) {
            latestSeenAtBottomMessageId = latestMessageId
        }
    }
    LaunchedEffect(latestMessageId, visibleMessages.size, shouldFollowBottom) {
        if (visibleMessages.isNotEmpty() && shouldFollowBottom && lastAutoScrollThreadId == threadId) {
            listState.scrollToItem(visibleMessages.lastIndex)
            if (BuildConfig.DEBUG) {
                Log.d(
                    STARTUP_TRACE_TAG,
                    "timeline scrolled thread=$threadId visible=${visibleMessages.size} hiddenEarlier=$hiddenEarlierCount reason=followBottom",
                )
            }
        }
    }
    val readyComposerImageAttachments =
        remember(composerAttachments) {
            composerAttachments.mapNotNull { attachment ->
                (attachment.state as? TurnComposerAttachmentState.ReadyImage)?.attachment
            }
        }
    val readyComposerFileAttachments =
        remember(composerAttachments) {
            composerAttachments.mapNotNull { attachment ->
                (attachment.state as? TurnComposerAttachmentState.ReadyFile)?.attachment
            }
        }
    val runtimeLoadingLabel = stringResource(R.string.turn_runtime_loading)
    val runtimeModelFallbackLabel = stringResource(R.string.turn_runtime_model_fallback)
    val runtimeNoModelsLabel = stringResource(R.string.turn_runtime_no_models)
    val runtimeAutoLabel = stringResource(R.string.turn_runtime_auto)
    val runtimeNormalLabel = stringResource(R.string.turn_runtime_normal)
    val reasoningEffortTitles =
        ReasoningEffortTitleStrings(
            low = stringResource(R.string.turn_runtime_reasoning_title_low),
            medium = stringResource(R.string.turn_runtime_reasoning_title_medium),
            high = stringResource(R.string.turn_runtime_reasoning_title_high),
            xhigh = stringResource(R.string.turn_runtime_reasoning_title_xhigh),
        )
    val runtimeControls =
        remember(
            availableModels,
            isLoadingModels,
            selectedModelId,
            selectedReasoningEffort,
            selectedAccessMode,
            selectedServiceTier,
            runtimeLoadingLabel,
            runtimeModelFallbackLabel,
            runtimeNoModelsLabel,
            runtimeAutoLabel,
            runtimeNormalLabel,
            reasoningEffortTitles,
        ) {
            buildRuntimeControlsState(
                models = availableModels,
                isLoadingModels = isLoadingModels,
                selectedModelId = selectedModelId,
                selectedReasoningEffort = selectedReasoningEffort,
                selectedAccessMode = selectedAccessMode,
                selectedServiceTier = selectedServiceTier,
                loadingLabel = runtimeLoadingLabel,
                modelFallbackLabel = runtimeModelFallbackLabel,
                noModelsLabel = runtimeNoModelsLabel,
                autoLabel = runtimeAutoLabel,
                normalTierLabel = runtimeNormalLabel,
                reasoningEffortTitles = reasoningEffortTitles,
            )
        }
    val draftWithMentions =
        remember(draft, mentionChips) {
            com.remodex.mobile.ui.turn.mergeMentionChipsIntoDraft(draft, mentionChips)
        }
    val structuredSkillMentions =
        remember(mentionChips) {
            com.remodex.mobile.ui.turn.mentionChipsToSkillMentions(mentionChips)
        }
    val structuredFileMentions =
        remember(mentionChips) {
            com.remodex.mobile.ui.turn.mentionChipsToFileMentions(mentionChips)
        }
    val composerModel =
        remember(
            ready,
            sending,
            draftWithMentions,
            composerAttachments,
            mentionChips,
            voicePhase,
            isThreadRunning,
            transcribeJob,
        ) {
            listOf<TurnComposerEvent>(
                    TurnComposerEvent.SetEnabled(ready),
                    TurnComposerEvent.SetSending(sending),
                    TurnComposerEvent.SetDraftText(draftWithMentions),
                    TurnComposerEvent.SetReadyAttachmentCount(
                        composerAttachments.count {
                            it.state is TurnComposerAttachmentState.ReadyImage ||
                                it.state is TurnComposerAttachmentState.ReadyFile
                        },
                    ),
                    TurnComposerEvent.SetHasBlockingAttachments(
                        composerAttachments.any {
                            it.state == TurnComposerAttachmentState.Loading ||
                                it.state is TurnComposerAttachmentState.Failed
                        },
                    ),
                    TurnComposerEvent.SetVoicePhase(voicePhase),
                    TurnComposerEvent.SetThreadRunning(isThreadRunning),
                    TurnComposerEvent.SetTranscribing(
                        voicePhase == TurnVoicePhase.Transcribing || transcribeJob != null,
                    ),
                )
                .fold(TurnComposerModel()) { state, evt ->
                    TurnComposerReducer.reduce(state, evt)
                }
        }
    val composerLocks = remember(composerModel) { composerModel.deriveInteractionLocks() }

    fun dispatchTurn(
        text: String,
        attachments: List<com.remodex.mobile.core.model.CodexImageAttachment>,
        skillMentions: List<com.remodex.mobile.core.model.CodexTurnSkillMention>,
        fileMentions: List<com.remodex.mobile.core.model.CodexTurnMention>,
        collaborationMode: CodexCollaborationModeKind?,
        fromQueue: Boolean,
    ) {
        if (text.trim().isEmpty() &&
            attachments.isEmpty() &&
            skillMentions.isEmpty() &&
            fileMentions.isEmpty()
        ) return
        if (!fromQueue && isThreadRunning) {
            scope.launch {
                runCatching {
                    repository.enqueueTurnDraft(
                        threadId = threadId,
                        text = text,
                        attachments = attachments,
                        skillMentions = skillMentions,
                        fileMentions = fileMentions,
                        collaborationMode = collaborationMode,
                    )
                }.onSuccess {
                    draft = ""
                    composerAttachments = emptyList()
                    mentionChips = emptyList()
                }.onFailure { e ->
                    lastError = com.remodex.mobile.ui.turn.formatTurnSendError(e)
                }
            }
            return
        }

        sending = true
        scope.launch {
            runCatching {
                repository.startTurn(
                    threadId = threadId,
                    text = text,
                    attachments = attachments,
                    skillMentions = skillMentions,
                    fileMentions = fileMentions,
                    collaborationMode = collaborationMode,
                )
            }
                .onSuccess {
                    sending = false
                    if (!fromQueue) {
                        draft = ""
                        composerAttachments = emptyList()
                        mentionChips = emptyList()
                    }
                }
                .onFailure { e ->
                    sending = false
                    if (fromQueue) {
                        runCatching {
                            repository.enqueueTurnDraft(
                                threadId = threadId,
                                text = text,
                                attachments = attachments,
                                skillMentions = skillMentions,
                                fileMentions = fileMentions,
                                collaborationMode = collaborationMode,
                                prepend = true,
                            )
                        }
                        lastError = "${queuedDraftSendFailedMessage}: ${com.remodex.mobile.ui.turn.formatTurnSendError(e)}"
                    } else {
                        lastError = com.remodex.mobile.ui.turn.formatTurnSendError(e)
                    }
                }
        }
    }

    LaunchedEffect(threadId, isThreadRunning, sending, queuedDraftCount, hasComposerDraftContent) {
        if (isThreadRunning || sending || queuedDraftCount <= 0 || hasComposerDraftContent) return@LaunchedEffect
        val queued = runCatching { repository.pollTurnDraft(threadId) }.getOrNull() ?: return@LaunchedEffect
        dispatchTurn(
            text = queued.text,
            attachments = queued.attachments,
            skillMentions = queued.skillMentions,
            fileMentions = queued.fileMentions,
            collaborationMode = queued.collaborationMode,
            fromQueue = true,
        )
    }

    val imeBottomPad =
        (WindowInsets.ime.asPaddingValues().calculateBottomPadding() - TurnConversationImeBottomTrim)
            .coerceAtLeast(0.dp)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = imeBottomPad),
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            MessageList(
                messages = visibleMessages,
                listState = listState,
                commandExecutionDetailsByItemId = commandExecutionDetailsByItemId,
                hiddenEarlierCount = hiddenEarlierCount,
                canLoadOlderRemoteHistory = canLoadOlderRemoteHistory,
                isLoadingOlderHistory = isLoadingOlderHistory,
                olderHistoryError = olderHistoryError,
                onLoadEarlierMessages =
                    if (hiddenEarlierCount > 0 || canLoadOlderRemoteHistory) {
                        {
                            if (canLoadOlderRemoteHistory && hiddenEarlierCount <= TIMELINE_LOAD_EARLIER_PAGE) {
                                scope.launch { runCatching { repository.loadOlderThreadHistory(threadId) } }
                            } else {
                                visibleTailCount =
                                    (visibleTailCount + TIMELINE_LOAD_EARLIER_PAGE)
                                        .coerceAtMost(messages.size)
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        STARTUP_TRACE_TAG,
                                        "timeline loadEarlier thread=$threadId visibleTail=$visibleTailCount total=${messages.size}",
                                    )
                                }
                            }
                        }
                    } else {
                        null
                    },
                assistantUndoChangeSetsByMessageId = assistantUndoChangeSetsByMessageId,
                applyingUndoChangeSetIds = applyingUndoChangeSetIds,
                onUndoAssistantChanges = { changeSet ->
                    val workingDirectory = changeSet.repoRoot ?: activeThread?.cwd
                    if (workingDirectory.isNullOrBlank()) {
                        inlineUndoError = context.getString(R.string.turn_usage_revert_reason_missing_cwd)
                        return@MessageList
                    }
                    scope.launch {
                        applyingUndoChangeSetIds = applyingUndoChangeSetIds + changeSet.id
                        inlineUndoError = null
                        runCatching {
                            AiChangeSetRevertService(repository).apply(
                                changeSet = changeSet,
                                workingDirectory = workingDirectory,
                            )
                        }.onSuccess { applyResult ->
                            if (applyResult.success) {
                                aiChangeSetPersistence.save(
                                    TurnUsageSheetLogic.markChangeSetReverted(
                                        changeSets = aiChangeSetPersistence.load(),
                                        changeSetId = changeSet.id,
                                        now = Instant.now(),
                                    ),
                                )
                            } else {
                                val message =
                                    applyResult.unsupportedReasons.firstOrNull()
                                        ?: applyResult.conflicts.firstOrNull()?.message
                                        ?: context.getString(R.string.turn_message_action_undo_failed)
                                inlineUndoError = message
                                aiChangeSetPersistence.save(
                                    TurnUsageSheetLogic.recordChangeSetRevertError(
                                        changeSets = aiChangeSetPersistence.load(),
                                        changeSetId = changeSet.id,
                                        message = message,
                                        now = Instant.now(),
                                    ),
                                )
                            }
                        }.onFailure { error ->
                            val message =
                                error.message?.ifBlank { null }
                                    ?: context.getString(R.string.turn_message_action_undo_failed)
                            inlineUndoError = message
                            aiChangeSetPersistence.save(
                                TurnUsageSheetLogic.recordChangeSetRevertError(
                                    changeSets = aiChangeSetPersistence.load(),
                                    changeSetId = changeSet.id,
                                    message = message,
                                    now = Instant.now(),
                                ),
                            )
                        }
                        threadChangeSets =
                            TurnUsageSheetLogic.recentChangeSetsForThread(
                                threadId,
                                aiChangeSetPersistence.load(),
                                limit = 50,
                            )
                        applyingUndoChangeSetIds = applyingUndoChangeSetIds - changeSet.id
                    }
                },
                onForkThread = {
                    showForkThreadSheet = true
                    lastError = null
                },
                forkThreadEnabled =
                    ready &&
                        connectionState is ConnectionState.Connected &&
                        !isThreadRunning &&
                        !forkingThread,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .alpha(timelineContentAlpha),
            )
            SmartScrollNavigationCta(
                state =
                    if (showSmartScrollCtaForSustainedScroll) {
                        smartScrollNavigationState
                    } else {
                        SmartScrollNavigationState()
                    },
                onNavigate = { requestedIndex ->
                    val targetIndex =
                        requestedIndex.coerceIn(
                            minimumValue = 0,
                            maximumValue = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
                    )
                    scope.launch {
                        val distanceItems = kotlin.math.abs(targetIndex - listState.firstVisibleItemIndex)
                        if (distanceItems >= SMART_SCROLL_FADE_JUMP_DISTANCE_ITEMS) {
                            timelineContentVisible = false
                            delay(90L)
                            listState.scrollToItem(targetIndex)
                            delay(120L)
                            timelineContentVisible = true
                        } else {
                            listState.animateScrollToItemRelaxed(targetIndex)
                        }
                        if (targetIndex >= (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) {
                            latestSeenAtBottomMessageId = latestMessageId
                        }
                    }
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp),
            )
        }
        lastError?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        gitBranchCheckoutError?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        worktreeHandoffError?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        inlineUndoError?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        connectionRecoverySnapshot?.let { snapshot ->
            TurnConnectionRecoveryCard(
                snapshot = snapshot,
                onTap = {
                    when {
                        reconnectUiState.recoveryAction == RootReconnectRecoveryAction.ScanNewQr -> onOpenPairingScanner()
                        reconnectUiState.wakeDisplayAvailable -> onWakeSavedComputer()
                        else -> onReconnectSavedPairing()
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (queuedDraftCount > 0) {
            com.remodex.mobile.ui.turn.QueuedDraftsCard(
                previews = queuedDraftPreviews,
                totalCount = queuedDraftCount,
                canRestore = canRestoreQueuedDrafts,
                onRestore = { draftId ->
                    scope.launch {
                        if (!canRestoreQueuedDrafts) {
                            lastError = queuedDraftRestoreBlockedMessage
                            return@launch
                        }
                        val restored = runCatching { repository.removeQueuedTurnDraft(threadId, draftId) }.getOrNull()
                        if (restored == null) return@launch
                        mentionChips =
                            com.remodex.mobile.ui.turn.restoreMentionChips(
                                skillMentions = restored.skillMentions,
                                fileMentions = restored.fileMentions,
                            )
                        draft =
                            com.remodex.mobile.ui.turn.stripMergedMentionPrefix(
                                text = restored.text,
                                skillMentions = restored.skillMentions,
                                fileMentions = restored.fileMentions,
                            )
                        composerAttachments =
                            restored.attachments.take(MAX_COMPOSER_ATTACHMENTS).map {
                                TurnComposerAttachment(
                                    state = TurnComposerAttachmentState.ReadyImage(it),
                                )
                            }
                        if (restored.attachments.size > MAX_COMPOSER_ATTACHMENTS) {
                            lastError = attachmentOverflowMessage
                        }
                    }
                },
                onRemove = { draftId ->
                    scope.launch {
                        runCatching { repository.removeQueuedTurnDraft(threadId, draftId) }
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        reviewTarget?.let { target ->
            com.remodex.mobile.ui.turn.TurnReviewAccessoryCard(
                target = target,
                selectedBaseBranch = resolvedReviewBaseBranch,
                availableBranches = loadedGitBranchSummary?.branches.orEmpty(),
                currentBranch = loadedGitBranchSummary?.currentBranch,
                defaultBranch = defaultReviewBaseBranch,
                onSelectCurrentChanges = {
                    selectReviewTarget(CodexReviewTarget.uncommittedChanges)
                },
                onSelectBaseBranch = { branch ->
                    selectReviewTarget(CodexReviewTarget.baseBranch, branch)
                },
                onDismiss = {
                    clearReviewTarget()
                    draft = ""
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        pinnedPlanAccessoryMessage?.let { planMessage ->
            TurnPlanAccessoryCard(
                message = planMessage,
                expanded = planAccessoryExpanded,
                onToggleExpanded = { planAccessoryExpanded = !planAccessoryExpanded },
                canApplyPlan = !isThreadRunning && !sending,
                onApplyPlan = { applyPlanToComposer() },
                onOpenDetailsSheet = {
                    showPlanDetailsSheet = true
                    lastError = null
                },
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        val onGitCheckout: (String) -> Unit =
            checkout@{ selectedBranch ->
            val cwd = gitCwd
            val loaded = gitBranchPaneState as? GitBranchPaneState.Loaded
            if (cwd == null || loaded == null) return@checkout
            val summary = loaded.summary
            val elsewhereSet = summary.branchesCheckedOutElsewhere.toSet()
            val elsewherePathRaw = summary.worktreePathByBranch[selectedBranch]?.trim()
            val selectedElsewhereUnresolved =
                elsewhereSet.contains(selectedBranch) &&
                    elsewherePathRaw.isNullOrEmpty()
            if (selectedElsewhereUnresolved) {
                gitBranchCheckoutError =
                    context.getString(R.string.git_branch_checkout_elsewhere_blocked)
                return@checkout
            }
            if (
                elsewhereSet.contains(selectedBranch) &&
                    !elsewherePathRaw.isNullOrEmpty()
            ) {
                val targetComparable =
                    TurnWorktreePathRouting.comparableGitProjectPath(
                        CodexThread.normalizeProjectPath(elsewherePathRaw) ?: elsewherePathRaw,
                    )
                        ?: return@checkout
                val cwdComparable =
                    TurnWorktreePathRouting.comparableGitProjectPath(activeThread?.cwd ?: cwd)
                if (cwdComparable != null && targetComparable == cwdComparable) {
                    gitBranchCheckoutError = null
                    return@checkout
                }
                val siblingElsewhere =
                    TurnWorktreePathRouting.liveThreadAtProjectPath(
                        elsewherePathRaw,
                        threads,
                        threadId,
                    )
                scope.launch {
                    isSwitchingGitBranch = true
                    gitBranchCheckoutError = null
                    runCatching {
                        val targetNormalized =
                            CodexThread.normalizeProjectPath(elsewherePathRaw) ?: elsewherePathRaw
                        if (siblingElsewhere != null) {
                            repository.setActiveThreadId(siblingElsewhere.id)
                        } else {
                            repository.moveThreadToProjectPath(threadId, targetNormalized)
                        }
                        gitBranchReloadNonce++
                    }.onFailure { e ->
                        gitBranchCheckoutError = GitBranchDisplayMapper.userVisibleMessage(e)
                    }
                    isSwitchingGitBranch = false
                }
                return@checkout
            }
            scope.launch {
                isSwitchingGitBranch = true
                gitBranchCheckoutError = null
                runCatching { GitActionsService(repository, cwd).checkout(selectedBranch) }
                    .onSuccess { gitBranchReloadNonce++ }
                    .onFailure { e ->
                        gitBranchCheckoutError = GitBranchDisplayMapper.userVisibleMessage(e)
                    }
                isSwitchingGitBranch = false
            }
        }
        val onGitCreateBranch: (String) -> Unit =
            createBranch@{ branchName ->
                val cwd = gitCwd ?: return@createBranch
                val loaded = gitBranchPaneState as? GitBranchPaneState.Loaded ?: return@createBranch
                if (branchName.isBlank()) return@createBranch
                scope.launch {
                    isSwitchingGitBranch = true
                    gitBranchCheckoutError = null
                    runCatching {
                        GitActionsService(repository, cwd).createBranch(branchName.trim())
                    }.onSuccess {
                        val createdBranch = it.branch.trim()
                        val previous = loaded.summary
                        val nextSummary =
                            GitBranchDisplayMapper.summaryFrom(
                                GitBranchesWithStatusResult(
                                    branches = (previous.branches + createdBranch).filter { branch -> branch.isNotBlank() }.distinct(),
                                    branchesCheckedOutElsewhere = previous.branchesCheckedOutElsewhere.toSet(),
                                    worktreePathByBranch = previous.worktreePathByBranch,
                                    localCheckoutPath = null,
                                    currentBranch = createdBranch.ifEmpty { previous.currentBranch },
                                    defaultBranch = previous.defaultBranch,
                                    status = it.status,
                                ),
                            )
                        gitBranchPaneState = GitBranchPaneState.Loaded(nextSummary)
                        gitBranchReloadNonce++
                    }.onFailure { e ->
                        gitBranchCheckoutError = GitBranchDisplayMapper.userVisibleMessage(e)
                    }
                    isSwitchingGitBranch = false
                }
            }
        TurnComposerBar(
            draft = draft,
            attachments = composerAttachments,
            model = composerModel,
            isPlanModeEnabled = isPlanModeEnabled,
            runtimeControls = runtimeControls,
            mentionChips = mentionChips,
            autocomplete = autocompleteState,
            onDraftChange = { next ->
                draft = next
                val target = reviewTarget
                if (target != null && next.trim() != reviewDraftText(target, reviewBaseBranch)) {
                    clearReviewTarget()
                }
            },
            onPickImages = {
                if (reviewTarget != null) {
                    lastError = reviewNoAttachmentsMessage
                    return@TurnComposerBar
                }
                if (composerAttachments.size >= MAX_COMPOSER_ATTACHMENTS) {
                    lastError = attachmentLimitMessage
                } else {
                    photoPickerLauncher.launch("image/*")
                }
            },
            onPickFiles = {
                if (reviewTarget != null) {
                    lastError = reviewNoAttachmentsMessage
                    return@TurnComposerBar
                }
                if (composerAttachments.size >= MAX_COMPOSER_ATTACHMENTS) {
                    lastError = attachmentLimitMessage
                } else {
                    filePickerLauncher.launch(arrayOf("*/*"))
                }
            },
            onTakePhoto = {
                if (reviewTarget != null) {
                    lastError = reviewNoAttachmentsMessage
                    return@TurnComposerBar
                }
                if (composerAttachments.size >= MAX_COMPOSER_ATTACHMENTS) {
                    lastError = attachmentLimitMessage
                    return@TurnComposerBar
                }
                val hasCamera =
                    context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
                if (!hasCamera) {
                    lastError = attachmentCameraUnavailableMessage
                    return@TurnComposerBar
                }
                val hasPermission =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    cameraPreviewLauncher.launch(null)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onSetPlanModeEnabled = { isPlanModeEnabled = it },
            onSelectModel = { option ->
                scope.launch { runCatching { repository.setSelectedModelId(option.id) } }
            },
            onSelectReasoningEffort = { option ->
                scope.launch {
                    runCatching {
                        repository.setSelectedReasoningEffort(option.id.takeUnless { it == TURN_COMPOSER_RUNTIME_AUTO_ID })
                    }
                }
            },
            onSelectAccessMode = { option ->
                CodexAccessMode.entries.firstOrNull { it.name == option.id }?.let { mode ->
                    scope.launch { runCatching { repository.setSelectedAccessMode(mode) } }
                }
            },
            onSelectServiceTier = { option ->
                val tier = CodexServiceTier.entries.firstOrNull { it.name == option.id }
                scope.launch { runCatching { repository.setSelectedServiceTier(tier) } }
            },
            onRemoveAttachment = { attachmentId ->
                composerAttachments = composerAttachments.filterNot { it.id == attachmentId }
            },
            onRemoveMentionChip = { chip ->
                mentionChips = mentionChips.filterNot { it == chip }
            },
            onSelectAutocomplete = { item ->
                val replaced =
                    TurnComposerTrailingTokens.replaceTrailingSegment(
                        text = draft,
                        replacement = item.replacementText,
                        parse = trailingToken,
                    )
                draft = replaced.text
                when (item.payload.kind) {
                    ComposerMentionKind.File,
                    ComposerMentionKind.Skill,
                    ComposerMentionKind.Plugin,
                    -> {
                        if (mentionChips.none { it.kind == item.payload.kind && it.semanticValue == item.payload.semanticValue }) {
                            mentionChips = mentionChips + item.payload
                        }
                    }
                    ComposerMentionKind.SlashCommand -> {
                        when {
                            item.payload.semanticValue.equals("fork", ignoreCase = true) -> {
                                draft = replaced.text.removeSuffix("/fork ").trimEnd()
                                showForkThreadSheet = true
                                lastError = null
                            }
                            item.payload.semanticValue.equals("feedback", ignoreCase = true) -> {
                                draft = replaced.text.removeSuffix("/feedback ").trimEnd()
                                showFeedbackDialog = true
                                lastError = null
                            }
                            item.payload.semanticValue.equals("compact", ignoreCase = true) -> {
                                draft = (replaced.text.trimEnd() + " /compact").trim()
                                mentionChips = mentionChips.filterNot { chip ->
                                    chip.kind == ComposerMentionKind.SlashCommand &&
                                        chip.semanticValue.equals("compact", ignoreCase = true)
                                }
                                lastError = null
                            }
                            item.payload.semanticValue.equals("review", ignoreCase = true) -> {
                                selectReviewTarget(CodexReviewTarget.uncommittedChanges)
                            }
                            item.payload.semanticValue.equals("review-base", ignoreCase = true) -> {
                                val branch = defaultReviewBaseBranch
                                if (branch == null) {
                                    draft = replaced.text.removeSuffix("/review-base ").trimEnd()
                                    lastError = reviewNoDefaultBranchMessage
                                } else {
                                    selectReviewTarget(CodexReviewTarget.baseBranch, branch)
                                }
                            }
                        }
                    }
                }
            },
            voiceUiEnabled = voiceInteractionEnabled,
            voiceAudioLevels = voiceAudioLevels,
            voiceRecordingDurationSeconds = voiceRecordingDurationSeconds,
            onVoiceClick = {
                when (voicePhase) {
                    TurnVoicePhase.Idle -> {
                        if (voiceInteractionEnabled) {
                            val hasAudioPermission =
                                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                            if (!hasAudioPermission) {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                scope.launch {
                                    if (voicePhase != TurnVoicePhase.Idle) return@launch
                                    resetVoiceMeteringState()
                                    val ok =
                                        withContext(Dispatchers.IO) {
                                            voiceRecorder.start(::appendVoiceAudioLevel)
                                        }
                                    if (ok) {
                                        voicePhase = TurnVoicePhase.Recording
                                    } else {
                                        resetVoiceMeteringState()
                                        lastError = voiceRecorderFailedMessage
                                    }
                                }
                            }
                        }
                    }
                    TurnVoicePhase.Recording -> {
                        scope.launch {
                            if (voicePhase != TurnVoicePhase.Recording) return@launch
                            val encoded =
                                withContext(Dispatchers.IO) {
                                    voiceRecorder.stopAndEncodeWav()
                                }
                            val pair =
                                encoded.getOrElse { err ->
                                    voicePhase = TurnVoicePhase.Idle
                                    resetVoiceMeteringState()
                                    if (err.message != "not_recording") {
                                        lastError =
                                            when (err.message) {
                                                "empty_audio" -> voiceNoAudioMessage
                                                else -> voiceRecorderFailedMessage
                                            }
                                    }
                                    return@launch
                                }
                            voicePhase = TurnVoicePhase.Transcribing
                            resetVoiceMeteringState()
                            transcribeJob =
                                scope.launch {
                                    try {
                                        val text =
                                            repository.transcribeBridgeVoiceWav(
                                                pair.first,
                                                pair.second,
                                            )
                                        if (isActive) {
                                            draft = VoiceDraftAppend.append(draft, text)
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        if (isActive) {
                                            lastError =
                                                when (e) {
                                                    is CodexServiceError ->
                                                        e.message ?: voiceTranscriptionFailedMessage
                                                    else -> e.message ?: voiceTranscriptionFailedMessage
                                                }
                                        }
                                    } finally {
                                        transcribeJob = null
                                        if (isActive) {
                                            voicePhase = TurnVoicePhase.Idle
                                        }
                                    }
                                }
                        }
                    }
                    TurnVoicePhase.Transcribing -> Unit
                }
            },
            onCancelVoiceRecording = {
                scope.launch {
                    if (voicePhase != TurnVoicePhase.Recording) return@launch
                    withContext(Dispatchers.IO) { voiceRecorder.cancel() }
                    voicePhase = TurnVoicePhase.Idle
                    resetVoiceMeteringState()
                }
            },
            composerEnvironment = {
                TurnComposerSecondaryBar(
                    threadId = threadId,
                    repository = repository,
                    isWorktreeProject = activeThread?.isManagedWorktreeProject == true,
                    worktreeHandoffEnabled =
                        gitCwd != null &&
                            gitBranchPaneState is GitBranchPaneState.Loaded &&
                            ready &&
                            connectionState is ConnectionState.Connected &&
                            !isThreadRunning &&
                            !sending &&
                            !isSwitchingGitBranch,
                    isHandingOffWorktree = isHandingOffWorktree,
                    onWorktreeHandoff = {
                        showWorktreeHandoffSheet = true
                        worktreeHandoffError = null
                    },
                    selectedAccessMode = selectedAccessMode,
                    accessPickerEnabled =
                        ready &&
                            !composerLocks.runtimeControlsLocked &&
                            runtimeControls.accessMode.enabled,
                    onSelectAccessMode = { mode ->
                        scope.launch { runCatching { repository.setSelectedAccessMode(mode) } }
                    },
                    gitBranchPaneState = gitBranchPaneState,
                    branchPickerEnabled = branchPickerEnabled,
                    isSwitchingGitBranch = isSwitchingGitBranch,
                    onRefreshGitBranches = { gitBranchReloadNonce++ },
                    onCheckoutGitBranch = onGitCheckout,
                    onCreateGitBranch = onGitCreateBranch,
                )
            },
            onSend = {
                transcribeJob?.cancel()
                transcribeJob = null
                voiceRecorder.cancel()
                voicePhase = TurnVoicePhase.Idle
                resetVoiceMeteringState()
                lastError = null
                val activeReviewTarget = reviewTarget
                if (activeReviewTarget != null) {
                    if (isThreadRunning) {
                        lastError = reviewRunningUnavailableMessage
                        return@TurnComposerBar
                    }
                    val activeReviewBaseBranch = resolvedReviewBaseBranch
                    if (activeReviewTarget.name == "baseBranch" && activeReviewBaseBranch == null) {
                        lastError = reviewNoBaseBranchAvailableMessage
                        return@TurnComposerBar
                    }
                    if (composerAttachments.isNotEmpty()) {
                        lastError = reviewNoAttachmentsMessage
                        return@TurnComposerBar
                    }
                    sending = true
                    scope.launch {
                        runCatching {
                            repository.startReview(
                                threadId = threadId,
                                target = activeReviewTarget,
                                baseBranch = activeReviewBaseBranch,
                            )
                        }.onSuccess {
                            sending = false
                            draft = ""
                            mentionChips = emptyList()
                            clearReviewTarget()
                        }.onFailure { e ->
                            sending = false
                            lastError = com.remodex.mobile.ui.turn.formatTurnSendError(e)
                        }
                    }
                    return@TurnComposerBar
                }
                val draftText =
                    com.remodex.mobile.ui.turn.appendFileAttachmentsToDraft(
                        baseText = draftWithMentions,
                        files = readyComposerFileAttachments,
                        binarySummary = attachmentFileBinarySummary,
                    )
                val attachmentsToSend = readyComposerImageAttachments
                val collaborationMode =
                    if (isPlanModeEnabled) {
                        CodexCollaborationModeKind.plan
                    } else {
                        null
                    }
                dispatchTurn(
                    text = draftText,
                    attachments = attachmentsToSend,
                    skillMentions = structuredSkillMentions,
                    fileMentions = structuredFileMentions,
                    collaborationMode = collaborationMode,
                    fromQueue = false,
                )
            },
        )
    }
    com.remodex.mobile.ui.turn.ForkThreadActionSheet(
        visible = showForkThreadSheet,
        projectPath = activeThread?.cwd,
        inProgress = forkingThread,
        onDismiss = {
            if (!forkingThread) showForkThreadSheet = false
        },
        onConfirm = {
            scope.launch {
                forkingThread = true
                runCatching {
                    val forked = repository.forkThread(threadId, targetProjectPath = activeThread?.cwd)
                    repository.setActiveThreadId(forked.id)
                    showForkThreadSheet = false
                    lastError = null
                }.onFailure { e ->
                    lastError = com.remodex.mobile.ui.turn.formatTurnSendError(e)
                }
                forkingThread = false
            }
        },
    )
    if (showFeedbackDialog) {
        com.remodex.mobile.ui.turn.TurnFeedbackDialog(
            onDismiss = { showFeedbackDialog = false },
            onSubmit = {
                showFeedbackDialog = false
            },
        )
    }
    com.remodex.mobile.ui.turn.WorktreeHandoffActionSheet(
        visible = showWorktreeHandoffSheet,
        isWorktreeProject = activeThread?.isManagedWorktreeProject == true,
        inProgress = isHandingOffWorktree,
        availableBaseBranches = loadedGitBranchSummary?.branches.orEmpty(),
        defaultBaseBranch = defaultReviewBaseBranch,
        currentBranch = loadedGitBranchSummary?.currentBranch,
        sourceProjectPath = gitCwd,
        localTargetPath = localWorktreeHandoffTargetPath,
        associatedWorktreePath = repository.associatedManagedWorktreePathFor(threadId),
        hasAssociatedWorktree = repository.associatedManagedWorktreePathFor(threadId) != null,
        errorMessage = worktreeHandoffError,
        onDismiss = {
            if (!isHandingOffWorktree) showWorktreeHandoffSheet = false
        },
        onConfirm = { selectedBaseBranch ->
            handoffCurrentThread(selectedBaseBranch)
        },
    )
    com.remodex.mobile.ui.turn.PlanDetailsActionSheet(
        visible = showPlanDetailsSheet,
        message = pinnedPlanAccessoryMessage,
        canApplyPlan = !isThreadRunning && !sending,
        onDismiss = { showPlanDetailsSheet = false },
        onApplyPlan = {
            applyPlanToComposer()
            if (!hasComposerDraftContent) {
                showPlanDetailsSheet = false
            }
        },
    )
}

private fun assistantUndoChangeSetsByMessageId(
    messages: List<com.remodex.mobile.core.model.CodexMessage>,
    changeSets: List<AIChangeSet>,
): Map<String, AIChangeSet> {
    if (messages.isEmpty() || changeSets.isEmpty()) return emptyMap()
    val readyChangeSets =
        changeSets.filter { TurnUsageSheetLogic.revertPrimaryEnabled(it, runtimeRevertRpcAvailable = true) }
    val byAssistantMessageId =
        readyChangeSets
            .mapNotNull { changeSet ->
                changeSet.assistantMessageId
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { it to changeSet }
            }
            .toMap()
    val byTurnId = readyChangeSets.associateBy { it.turnId }
    return messages
        .asSequence()
        .filter { it.role == com.remodex.mobile.core.model.CodexMessageRole.assistant }
        .mapNotNull { message ->
            val changeSet =
                byAssistantMessageId[message.id]
                    ?: message.turnId?.let { byTurnId[it] }
            changeSet?.let { message.id to it }
        }
        .toMap()
}
