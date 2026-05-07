package com.remodex.mobile.ui.shell

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.remodex.mobile.R
import com.remodex.mobile.core.model.GitBranchesWithStatusResult
import com.remodex.mobile.core.model.GitDiffTotals
import com.remodex.mobile.core.model.GitRepoSyncResult
import com.remodex.mobile.core.model.GitWorktreeChangeTransferMode
import com.remodex.mobile.core.model.PendingApprovalDecision
import com.remodex.mobile.core.model.TurnGitActionKind
import com.remodex.mobile.core.model.TurnGitPreflightOperation
import com.remodex.mobile.core.model.TurnGitPreflightPolicy
import com.remodex.mobile.core.model.TurnGitSyncAlert
import com.remodex.mobile.core.model.TurnGitSyncAlertAction
import com.remodex.mobile.core.model.TurnGitSyncAlertButtonRole
import com.remodex.mobile.data.gitWorkingDirectoryForGitActions
import com.remodex.mobile.data.RepoDiffLastTurnAggregator
import com.remodex.mobile.data.RepoDiffLastTurnFileRow
import com.remodex.mobile.data.WorktreeFlowCoordinator
import com.remodex.mobile.data.WorktreeFlowHandoffOutcome
import com.remodex.mobile.data.remodexBuildPullRequestUrl
import com.remodex.mobile.data.remodexResolveCommitMessage
import com.remodex.mobile.services.DesktopHandoffService
import com.remodex.mobile.services.GitActionsError
import com.remodex.mobile.services.GitActionsService
import com.remodex.mobile.ui.LocalCodexRepository
import com.remodex.mobile.ui.agent.ConversationHeader
import com.remodex.mobile.ui.agent.SidebarDrawerContent
import com.remodex.mobile.ui.agent.truncatePathMiddle
import com.remodex.mobile.ui.home.BridgeUpdateSheet
import com.remodex.mobile.ui.home.GitActionProgressBannerState
import com.remodex.mobile.ui.home.GitActionProgressPhase
import com.remodex.mobile.ui.home.RootViewModel
import com.remodex.mobile.ui.home.ThreadCompletionBanner
import com.remodex.mobile.ui.navigation.AppNavHost
import com.remodex.mobile.ui.navigation.AppRoutes
import com.remodex.mobile.ui.turn.LocalOpenRepoDiffForMarkdownLink
import com.remodex.mobile.ui.turn.RepoMarkdownFileLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val GIT_OPERATION_TIMEOUT_MS = 150_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    viewModel: RootViewModel,
    onOpenPairingScanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = LocalCodexRepository.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val showShellHeader = currentRoute == AppRoutes.Home
    val lifecycleOwner = LocalLifecycleOwner.current

    val ready by repository.isSessionReady.collectAsStateWithLifecycle()
    val activeThreadId by repository.activeThreadId.collectAsStateWithLifecycle()
    val threads by repository.threads.collectAsStateWithLifecycle()
    val runningTurnByThread by repository.runningTurnIdByThread.collectAsStateWithLifecycle()
    val protectedRunningFallback by repository.protectedRunningFallbackThreadIds.collectAsStateWithLifecycle()
    val pendingApprovalRequest by repository.pendingApprovalRequest.collectAsStateWithLifecycle()
    val pendingStructuredInputRequest by repository.pendingStructuredInputRequest.collectAsStateWithLifecycle()
    val bridgeUpdatePrompt by repository.bridgeUpdatePrompt.collectAsStateWithLifecycle()
    val connectionState by repository.connectionState.collectAsStateWithLifecycle()
    val reconnectUiState by viewModel.reconnectUiState.collectAsStateWithLifecycle()
    val messagesByThread by repository.messagesByThread.collectAsStateWithLifecycle()
    val handoffFallbackMessage = stringResource(R.string.turn_open_desktop_failed_fallback)
    val appName = stringResource(R.string.app_name)
    val gitStatusLoadingToast = stringResource(R.string.git_status_loading_toast)
    val context = LocalContext.current
    var desktopHandoffError by remember { mutableStateOf<String?>(null) }
    var handingOffToDesktop by remember { mutableStateOf(false) }
    var handingOffWorktree by remember { mutableStateOf(false) }
    var worktreeHandoffError by remember { mutableStateOf<String?>(null) }
    var repoStatusSnapshot by remember { mutableStateOf<GitRepoSyncResult?>(null) }
    var branchesWithStatusSnapshot by remember { mutableStateOf<GitBranchesWithStatusResult?>(null) }
    var repoDiffTotals by remember { mutableStateOf<GitDiffTotals?>(null) }
    var defaultGitBaseBranch by remember { mutableStateOf<String?>(null) }
    var isLoadingRepoDiff by remember { mutableStateOf(false) }
    var gitToolbarRefreshNonce by remember { mutableStateOf(0) }
    var showRepoDiffSheet by remember { mutableStateOf(false) }
    var repoDiffSheetScope by remember { mutableStateOf(GitRepoDiffScope.LastTurn) }
    /** threadId → full working-tree patch from bridge (invalidated via [gitToolbarRefreshNonce]). */
    var cachedFullWorkingTreeDiff by remember { mutableStateOf<Pair<String, String>?>(null) }
    var repoDiffSheetLastTurnRows by remember { mutableStateOf<List<RepoDiffLastTurnFileRow>>(emptyList()) }
    var repoDiffSheetFullPatch by remember { mutableStateOf("") }
    var repoDiffSheetFullLoading by remember { mutableStateOf(false) }
    var repoDiffSheetFullError by remember { mutableStateOf<String?>(null) }
    var repoDiffMarkdownFocusQuery by remember { mutableStateOf<String?>(null) }
    var gitActionBusy by remember { mutableStateOf(false) }
    var gitActionError by remember { mutableStateOf<String?>(null) }
    var gitActionProgressMessage by remember { mutableStateOf<String?>(null) }
    var gitActionProgressPhase by remember { mutableStateOf<GitActionProgressPhase?>(null) }
    var gitActionProgressIncludesPush by remember { mutableStateOf(true) }
    var gitActionProgressIncludesPullRequest by remember { mutableStateOf(false) }
    var gitActionSheetMode by remember { mutableStateOf<GitActionSheetMode?>(null) }
    var gitActionSheetInitialNextStep by remember { mutableStateOf<GitActionNextStep?>(null) }
    var showGitInitPrompt by remember { mutableStateOf(false) }
    var gitInitError by remember { mutableStateOf<String?>(null) }
    var gitSyncAlert by remember { mutableStateOf<TurnGitSyncAlert?>(null) }
    var pendingGitOperation by remember { mutableStateOf<PendingGitOperation?>(null) }
    var showNothingToCommit by remember { mutableStateOf(false) }
    var showPathDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val showTurnStop =
        ready &&
            activeThreadId?.let { tid ->
                runningTurnByThread.containsKey(tid) || protectedRunningFallback.contains(tid)
            } == true
    val showDesktopHandoff = ready && !activeThreadId.isNullOrBlank()
    val activeThread =
        remember(activeThreadId, threads) {
            activeThreadId?.let { id -> threads.firstOrNull { it.id == id } }
        }
    val activeThreadTitle =
        remember(activeThreadId, threads, appName) {
            activeThreadId
                ?.let { id -> threads.firstOrNull { it.id == id }?.displayTitle }
                ?.takeIf { it.isNotBlank() }
                ?: appName
        }
    val threadPathFull =
        remember(activeThread) {
            activeThread?.cwd?.trim()?.takeIf { it.isNotEmpty() }
        }
    val pathSubtitle =
        remember(threadPathFull) {
            threadPathFull?.let { truncatePathMiddle(it) }
        }
    val threadMessages =
        remember(activeThreadId, messagesByThread) {
            activeThreadId?.let { tid -> messagesByThread[tid] }.orEmpty()
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.onAppForegrounded()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.onAppLaunched()
    }

    LaunchedEffect(ready) {
        if (ready) {
            viewModel.restoreActiveThreadIfNeeded()
        }
    }

    LaunchedEffect(activeThreadId) {
        val id = activeThreadId
        if (!id.isNullOrBlank()) {
            viewModel.persistActiveThreadId(id)
        }
    }

    val gitCwd = remember(activeThread) { activeThread?.gitWorkingDirectoryForGitActions() }
    val showGitControls = ready && !gitCwd.isNullOrBlank()
    val isWorktreeProject = activeThread?.isManagedWorktreeProject == true
    LaunchedEffect(repoStatusSnapshot?.state, gitCwd, showGitControls) {
        val needsInit = repoStatusSnapshot?.state in setOf("not_initialized", "missing_local_repo")
        if (showGitControls && gitCwd != null && needsInit) {
            showGitInitPrompt = true
        } else {
            showGitInitPrompt = false
            gitInitError = null
        }
    }
    val associatedWorktreePath =
        remember(activeThreadId, threads) {
            activeThreadId?.let { repository.associatedManagedWorktreePathFor(it) }
        }
    val localWorktreeHandoffTargetPath =
        remember(branchesWithStatusSnapshot) {
            val branches = branchesWithStatusSnapshot ?: return@remember null
            val preferredBranch = branches.defaultBranch ?: branches.currentBranch
            preferredBranch
                ?.let { branches.worktreePathByBranch[it]?.trim()?.takeIf { path -> path.isNotEmpty() } }
                ?: branches.worktreePathByBranch.values.firstOrNull { it.isNotBlank() }?.trim()
        }
    val showWorktreeHandoff =
        showGitControls &&
            ready &&
            activeThreadId != null &&
            !showTurnStop &&
            !handingOffWorktree &&
            (
                (isWorktreeProject && localWorktreeHandoffTargetPath != null) ||
                    (!isWorktreeProject && (associatedWorktreePath != null || defaultGitBaseBranch != null))
            )
    val gitToastMessage =
        if (isLoadingRepoDiff && showGitControls && gitActionProgressPhase == null) {
                gitStatusLoadingToast
            } else {
                null
            }
    val gitProgressToast =
        gitActionProgressPhase?.let {
            GitActionProgressBannerState(
                phase = it,
                includesPush = gitActionProgressIncludesPush,
                includesPullRequest = gitActionProgressIncludesPullRequest,
            )
        }

    fun enqueueRepoDiffFullTreePrefetch() {
        val tid = activeThreadId
        val cwd = gitCwd
        if (tid != null && !cwd.isNullOrBlank()) {
            val cached = cachedFullWorkingTreeDiff?.takeIf { it.first == tid }
            repoDiffSheetFullPatch = cached?.second.orEmpty()
            repoDiffSheetFullLoading = cached == null || cached.second.isBlank()
            scope.launch {
                runCatching { GitActionsService(repository, cwd).diff() }
                    .onSuccess {
                        cachedFullWorkingTreeDiff = tid to it.patch
                        repoDiffSheetFullPatch = it.patch
                    }
                    .onFailure {
                        val rawMessage = it.message.orEmpty()
                        val userVisibleMessage = rawMessage.withoutGitLineEndingWarnings().ifBlank { null }
                        repoDiffSheetFullError =
                            if (rawMessage.isNotBlank() && userVisibleMessage == null) {
                                null
                            } else {
                                userVisibleMessage ?: context.getString(R.string.git_repo_diff_load_error)
                            }
                    }
                repoDiffSheetFullLoading = false
            }
        } else {
            repoDiffSheetFullPatch = ""
            repoDiffSheetFullLoading = false
        }
    }

    fun openRepoDiffSheetFromHeader() {
        repoDiffMarkdownFocusQuery = null
        showRepoDiffSheet = true
        repoDiffSheetScope = GitRepoDiffScope.LastTurn
        repoDiffSheetFullError = null
        repoDiffSheetLastTurnRows =
            RepoDiffLastTurnAggregator.fileRowsFromLastTurn(threadMessages)
        enqueueRepoDiffFullTreePrefetch()
    }

    fun openRepoDiffSheetFromMarkdown(link: String) {
        if (repoDiffTotals?.hasChanges != true || !showGitControls || activeThreadId == null) return
        val q = RepoMarkdownFileLink.canonicalFilenameQuery(link)
        repoDiffMarkdownFocusQuery = q
        val lastRows = RepoDiffLastTurnAggregator.fileRowsFromLastTurn(threadMessages)
        repoDiffSheetLastTurnRows = lastRows
        repoDiffSheetScope =
            if (lastRows.any { RepoMarkdownFileLink.rowMatchesQuery(it.path, q) }) {
                GitRepoDiffScope.LastTurn
            } else {
                GitRepoDiffScope.FullWorkingTree
            }
        repoDiffSheetFullError = null
        enqueueRepoDiffFullTreePrefetch()
        showRepoDiffSheet = true
    }

    LaunchedEffect(activeThreadId, gitToolbarRefreshNonce) {
        cachedFullWorkingTreeDiff = null
    }

    fun handleGitActionFailure(
        e: Throwable,
        onNothingToCommit: () -> Unit,
    ) {
        gitActionProgressMessage = null
        gitActionProgressPhase = null
        gitActionProgressIncludesPush = true
        gitActionProgressIncludesPullRequest = false
        when {
            e is GitActionsError.BridgeFailure && e.errorCode == "nothing_to_commit" -> onNothingToCommit()
            e is GitActionsError.BridgeFailure &&
                (e.errorCode == "branch_is_main" || e.errorCode == "protected_branch") -> {
                gitSyncAlert =
                    TurnGitSyncAlert.withDefaultButtons(
                        title = "Protected branch",
                        message = e.message?.ifBlank { null } ?: "This branch is protected.",
                        action = TurnGitSyncAlertAction.dismissOnly,
                    )
                pendingGitOperation = null
            }
            e is TimeoutCancellationException -> {
                gitActionError = "Git operation timed out. Check the desktop bridge and try again."
            }
            else -> {
                gitActionError = e.message?.ifBlank { null } ?: e.toString()
            }
        }
    }

    suspend fun resolveCommitMessage(
        git: GitActionsService,
        rawMessage: String,
    ): String? {
        return remodexResolveCommitMessage(rawMessage) { git.generateCommitMessage().fullMessage }
    }

    suspend fun openPullRequestUrl(
        git: GitActionsService,
        submission: GitActionSheetSubmission,
    ) {
        val st = git.status()
        val bw = git.branchesWithStatus()
        val branch = st.currentBranch?.trim().orEmpty()
        val base =
            submission.baseBranch.trim()
                .ifEmpty { bw.defaultBranch?.trim().orEmpty() }
        if (branch.isEmpty() || base.isEmpty()) {
            throw IllegalStateException("Could not determine branch or default for PR.")
        }
        val draft =
            if (submission.pullRequestTitle.isBlank() || submission.pullRequestBody.isBlank()) {
                runCatching { git.generatePullRequestDraft(baseBranch = base) }.getOrNull()
            } else {
                null
            }
        val title =
            submission.pullRequestTitle.trim()
                .ifEmpty { draft?.title?.trim().orEmpty() }
        val body =
            submission.pullRequestBody.trim()
                .ifEmpty { draft?.body?.trim().orEmpty() }
        val remote = git.remoteUrl()
        val ownerRepo =
            remote.ownerRepo?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("Could not read Git remote (GitHub PR link needs origin).")
        val url = remodexBuildPullRequestUrl(ownerRepo, branch, base, title, body)
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun operationForSubmission(submission: GitActionSheetSubmission): TurnGitPreflightOperation =
        when (submission.nextStep) {
            GitActionNextStep.commit -> TurnGitPreflightOperation.commit
            GitActionNextStep.commitAndPush,
            GitActionNextStep.push,
            -> TurnGitPreflightOperation.push
            GitActionNextStep.commitPushAndPullRequest,
            GitActionNextStep.pushAndPullRequest,
            GitActionNextStep.createPullRequest,
            -> TurnGitPreflightOperation.createPullRequest
        }

    fun enqueueGitPreflightIfNeeded(
        cwd: String,
        operation: TurnGitPreflightOperation,
        submission: GitActionSheetSubmission? = null,
        status: GitRepoSyncResult? = repoStatusSnapshot,
        branches: GitBranchesWithStatusResult? = branchesWithStatusSnapshot,
    ): Boolean {
        val alert = TurnGitPreflightPolicy.alertFor(status, branches, operation) ?: return false
        pendingGitOperation =
            PendingGitOperation(
                operation = operation,
                cwd = cwd,
                submission = submission,
            )
        gitSyncAlert = alert
        return true
    }

    suspend fun executeGitActionSheetNow(
        submission: GitActionSheetSubmission,
        cwd: String,
    ) {
        gitActionBusy = true
        gitActionError = null
        try {
            withTimeout(GIT_OPERATION_TIMEOUT_MS) {
                val git = GitActionsService(repository, cwd)
                when (submission.nextStep) {
                    GitActionNextStep.commit -> {
                        gitActionProgressMessage = null
                        gitActionProgressIncludesPush = false
                        gitActionProgressIncludesPullRequest = false
                        gitActionProgressPhase = GitActionProgressPhase.resolvingCommitMessage
                        val commitMessage = resolveCommitMessage(git, submission.commitMessage)
                        gitActionProgressPhase = GitActionProgressPhase.committing
                        git.commit(commitMessage)
                        gitActionProgressPhase = GitActionProgressPhase.done
                    }
                    GitActionNextStep.commitAndPush -> {
                        gitActionProgressMessage = null
                        gitActionProgressIncludesPush = true
                        gitActionProgressIncludesPullRequest = false
                        gitActionProgressPhase = GitActionProgressPhase.resolvingCommitMessage
                        val commitMessage = resolveCommitMessage(git, submission.commitMessage)
                        gitActionProgressPhase = GitActionProgressPhase.committing
                        git.commit(commitMessage)
                        gitActionProgressPhase = GitActionProgressPhase.pushing
                        git.push()
                        gitActionProgressPhase = GitActionProgressPhase.done
                    }
                    GitActionNextStep.commitPushAndPullRequest -> {
                        gitActionProgressMessage = null
                        gitActionProgressIncludesPush = true
                        gitActionProgressIncludesPullRequest = true
                        gitActionProgressPhase = GitActionProgressPhase.resolvingCommitMessage
                        val commitMessage = resolveCommitMessage(git, submission.commitMessage)
                        gitActionProgressPhase = GitActionProgressPhase.committing
                        git.commit(commitMessage)
                        gitActionProgressPhase = GitActionProgressPhase.pushing
                        git.push()
                        gitActionProgressPhase = GitActionProgressPhase.preparingPullRequest
                        openPullRequestUrl(git, submission)
                        gitActionProgressPhase = GitActionProgressPhase.done
                    }
                    GitActionNextStep.push -> {
                        gitActionProgressMessage = "Pushing branch..."
                        git.push()
                        gitActionProgressMessage = "Pushed branch."
                    }
                    GitActionNextStep.pushAndPullRequest -> {
                        gitActionProgressMessage = "Pushing branch..."
                        git.push()
                        gitActionProgressMessage = "Preparing pull request..."
                        openPullRequestUrl(git, submission)
                        gitActionProgressMessage = "Pull request draft opened."
                    }
                    GitActionNextStep.createPullRequest -> {
                        gitActionProgressMessage = "Preparing pull request..."
                        openPullRequestUrl(git, submission)
                        gitActionProgressMessage = "Pull request draft opened."
                    }
                }
            }
            gitActionError = null
            gitActionSheetMode = null
            gitActionSheetInitialNextStep = null
        } catch (e: Throwable) {
            handleGitActionFailure(e) { showNothingToCommit = true }
        } finally {
            gitActionBusy = false
            gitToolbarRefreshNonce++
        }
    }

    LaunchedEffect(gitActionProgressMessage, gitActionProgressPhase, gitActionBusy) {
        val message = gitActionProgressMessage
        val phase = gitActionProgressPhase
        if (!gitActionBusy && (!message.isNullOrBlank() || phase == GitActionProgressPhase.done)) {
            delay(3_500)
            if (gitActionProgressMessage == message && gitActionProgressPhase == phase) {
                gitActionProgressMessage = null
                gitActionProgressPhase = null
            }
        }
    }

    fun continuePendingGitOperation(commitFirst: Boolean = false) {
        val pending = pendingGitOperation ?: return
        gitSyncAlert = null
        pendingGitOperation = null
        scope.launch {
            gitActionBusy = true
            gitActionError = null
            try {
                withTimeout(GIT_OPERATION_TIMEOUT_MS) {
                    val git = GitActionsService(repository, pending.cwd)
                    if (commitFirst) {
                        git.commit("WIP before continuing")
                    }
                    val submission = pending.submission
                    if (submission != null) {
                        gitActionBusy = false
                        executeGitActionSheetNow(submission, pending.cwd)
                    } else if (pending.operation is TurnGitPreflightOperation.CreateManagedWorktree) {
                        gitActionBusy = false
                        val tid = activeThreadId ?: return@withTimeout
                        val baseBranch = pending.operation.baseBranch
                        handingOffWorktree = true
                        worktreeHandoffError = null
                        try {
                            val outcome =
                                WorktreeFlowCoordinator(repository).handoffThreadToWorktree(
                                    threadId = tid,
                                    sourceProjectPath = pending.cwd,
                                    associatedWorktreePath = associatedWorktreePath,
                                    baseBranchForNewWorktree = baseBranch,
                                )
                            when (outcome) {
                                is WorktreeFlowHandoffOutcome.Moved -> repository.setActiveThreadId(outcome.move.thread.id)
                                WorktreeFlowHandoffOutcome.MissingAssociatedWorktree ->
                                    worktreeHandoffError =
                                        "The associated worktree is no longer available. Open the thread to create a new managed worktree."
                            }
                        } catch (e: Throwable) {
                            worktreeHandoffError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                        } finally {
                            handingOffWorktree = false
                        }
                    }
                }
            } catch (e: Throwable) {
                handleGitActionFailure(e) { showNothingToCommit = true }
            } finally {
                if (gitActionBusy) {
                    gitActionBusy = false
                    gitToolbarRefreshNonce++
                }
            }
        }
    }

    fun initializeRepositoryForCurrentThread() {
        val cwd = gitCwd ?: return
        if (repoStatusSnapshot?.isRepo == true) return
        if (!showGitControls || gitActionBusy) return
        scope.launch {
            gitActionBusy = true
            gitActionProgressMessage = context.getString(R.string.git_init_initializing)
            gitInitError = null
            try {
                withTimeout(GIT_OPERATION_TIMEOUT_MS) {
                    val git = GitActionsService(repository, cwd)
                    val result = git.initializeRepository()
                    val refreshedStatus = result.status ?: runCatching { git.status() }.getOrNull()
                    repoStatusSnapshot = refreshedStatus
                    repoDiffTotals = refreshedStatus?.workingTreeDiffTotals
                    branchesWithStatusSnapshot = runCatching { git.branchesWithStatus() }.getOrNull()
                    defaultGitBaseBranch = branchesWithStatusSnapshot?.defaultBranch
                    showGitInitPrompt = false
                    gitActionProgressMessage = context.getString(R.string.git_init_initialized)
                }
            } catch (e: Throwable) {
                gitActionProgressMessage = null
                gitInitError = e.message?.ifBlank { null } ?: e.toString()
            } finally {
                gitActionBusy = false
                gitToolbarRefreshNonce++
            }
        }
    }

    fun pullRebaseForPendingGitOperation() {
        val cwd = pendingGitOperation?.cwd ?: gitCwd ?: return
        gitSyncAlert = null
        pendingGitOperation = null
        scope.launch {
            gitActionBusy = true
            gitActionError = null
            runCatching {
                withTimeout(GIT_OPERATION_TIMEOUT_MS) {
                    val result = GitActionsService(repository, cwd).pull()
                    result.status?.let {
                        repoStatusSnapshot = it
                        repoDiffTotals = it.workingTreeDiffTotals
                    }
                }
            }.onFailure { e ->
                handleGitActionFailure(e) { showNothingToCommit = true }
            }
            gitActionBusy = false
            gitToolbarRefreshNonce++
        }
    }

    fun discardRuntimeChangesForPendingGitOperation() {
        val cwd = pendingGitOperation?.cwd ?: gitCwd ?: return
        gitSyncAlert = null
        pendingGitOperation = null
        scope.launch {
            gitActionBusy = true
            gitActionError = null
            runCatching {
                withTimeout(GIT_OPERATION_TIMEOUT_MS) {
                    val result = GitActionsService(repository, cwd).resetToRemoteDiscardRuntime()
                    result.status?.let {
                        repoStatusSnapshot = it
                        repoDiffTotals = it.workingTreeDiffTotals
                    }
                }
            }.onFailure { e ->
                handleGitActionFailure(e) { showNothingToCommit = true }
            }
            gitActionBusy = false
            gitToolbarRefreshNonce++
        }
    }

    fun handoffCurrentThreadWorktree(skipPreflight: Boolean = false) {
        val tid = activeThreadId ?: return
        val cwd = gitCwd ?: return
        if (!showGitControls || showTurnStop || handingOffWorktree) return
        val baseBranch =
            defaultGitBaseBranch?.trim()?.takeIf { it.isNotEmpty() }
                ?: branchesWithStatusSnapshot?.currentBranch?.trim()?.takeIf { it.isNotEmpty() }
        val localTarget = localWorktreeHandoffTargetPath
        if (isWorktreeProject && localTarget == null) {
            gitSyncAlert =
                TurnGitSyncAlert.withDefaultButtons(
                    title = "Worktree handoff unavailable",
                    message = "Could not resolve the paired Local checkout for this worktree.",
                    action = TurnGitSyncAlertAction.dismissOnly,
                )
            return
        }
        if (!isWorktreeProject && associatedWorktreePath == null && baseBranch == null) {
            gitSyncAlert =
                TurnGitSyncAlert.withDefaultButtons(
                    title = "Worktree handoff unavailable",
                    message = "Could not determine a base branch for the managed worktree.",
                    action = TurnGitSyncAlertAction.dismissOnly,
                )
            return
        }

        val preflightBranches = branchesWithStatusSnapshot
        if (
            !skipPreflight &&
            !isWorktreeProject &&
            associatedWorktreePath == null &&
            preflightBranches != null &&
            baseBranch != null
        ) {
            val alert =
                TurnGitPreflightPolicy.alertFor(
                    status = repoStatusSnapshot ?: preflightBranches.status,
                    branches = preflightBranches,
                    operation =
                        TurnGitPreflightOperation.createManagedWorktree(
                            baseBranch = baseBranch,
                            changeTransfer = GitWorktreeChangeTransferMode.move,
                        ),
                )
            if (alert != null) {
                pendingGitOperation =
                    PendingGitOperation(
                        operation =
                            TurnGitPreflightOperation.createManagedWorktree(
                                baseBranch = baseBranch,
                                changeTransfer = GitWorktreeChangeTransferMode.move,
                            ),
                        cwd = cwd,
                    )
                gitSyncAlert = alert
                return
            }
        }

        scope.launch {
            handingOffWorktree = true
            worktreeHandoffError = null
            try {
                val coordinator = WorktreeFlowCoordinator(repository)
                val outcome =
                    if (isWorktreeProject) {
                        coordinator.handoffThreadToProjectPath(
                            threadId = tid,
                            sourceProjectPath = cwd,
                            targetProjectPath = localTarget ?: error("Missing local checkout path."),
                        )
                    } else {
                        coordinator.handoffThreadToWorktree(
                            threadId = tid,
                            sourceProjectPath = cwd,
                            associatedWorktreePath = associatedWorktreePath,
                            baseBranchForNewWorktree = baseBranch,
                        )
                    }
                when (outcome) {
                    is WorktreeFlowHandoffOutcome.Moved -> {
                        repository.setActiveThreadId(outcome.move.thread.id)
                        gitToolbarRefreshNonce++
                    }
                    WorktreeFlowHandoffOutcome.MissingAssociatedWorktree ->
                        worktreeHandoffError =
                            "The associated worktree is no longer available. Open the thread to create a new managed worktree."
                }
            } catch (e: Throwable) {
                worktreeHandoffError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            } finally {
                handingOffWorktree = false
            }
        }
    }

    fun handleGitAction(action: TurnGitActionKind) {
        val cwd = gitCwd ?: return
        if (action == TurnGitActionKind.discardRuntimeChangesAndSync) {
            enqueueGitPreflightIfNeeded(cwd, TurnGitPreflightOperation.discardRuntimeChanges)
            return
        }
        if (!showGitControls || showTurnStop) return
        if (action == TurnGitActionKind.initialize) {
            initializeRepositoryForCurrentThread()
            return
        }
        when (action) {
            TurnGitActionKind.commit -> {
                gitActionSheetMode = GitActionSheetMode.commit
                gitActionSheetInitialNextStep = GitActionNextStep.commit
                return
            }
            TurnGitActionKind.push -> {
                gitActionSheetMode = GitActionSheetMode.push
                gitActionSheetInitialNextStep = GitActionNextStep.push
                return
            }
            TurnGitActionKind.commitAndPush -> {
                gitActionSheetMode = GitActionSheetMode.commit
                gitActionSheetInitialNextStep = GitActionNextStep.commitAndPush
                return
            }
            TurnGitActionKind.createPR -> {
                gitActionSheetMode = GitActionSheetMode.createPullRequest
                gitActionSheetInitialNextStep = GitActionNextStep.createPullRequest
                return
            }
            TurnGitActionKind.previewCommitPushToast -> {
                if (gitActionBusy) return
                scope.launch {
                    gitActionBusy = true
                    gitActionError = null
                    gitActionProgressMessage = null
                    gitActionProgressIncludesPush = true
                    gitActionProgressIncludesPullRequest = false
                    gitActionProgressPhase = GitActionProgressPhase.resolvingCommitMessage
                    delay(900)
                    gitActionProgressPhase = GitActionProgressPhase.committing
                    delay(900)
                    gitActionProgressPhase = GitActionProgressPhase.pushing
                    delay(900)
                    gitActionProgressPhase = GitActionProgressPhase.done
                    gitActionBusy = false
                }
                return
            }
            else -> Unit
        }
        scope.launch {
            gitActionBusy = true
            gitActionError = null
            try {
                withTimeout(GIT_OPERATION_TIMEOUT_MS) {
                    val git = GitActionsService(repository, cwd)
                    when (action) {
                        TurnGitActionKind.syncNow -> {
                            val s = git.status()
                            repoStatusSnapshot = s
                            repoDiffTotals = s.workingTreeDiffTotals
                            if (
                                enqueueGitPreflightIfNeeded(
                                    cwd = cwd,
                                    operation = TurnGitPreflightOperation.syncUpdate,
                                    status = s,
                                )
                            ) {
                                return@withTimeout
                            }
                            when (s.state) {
                                "behind_only" -> git.pull()
                                else -> Unit
                            }
                        }
                        else -> Unit
                    }
                }
            } catch (e: Throwable) {
                handleGitActionFailure(e) { showNothingToCommit = true }
            } finally {
                gitActionBusy = false
                gitToolbarRefreshNonce++
            }
        }
    }

    fun executeGitActionSheet(submission: GitActionSheetSubmission) {
        val cwd = gitCwd ?: return
        if (!showGitControls || showTurnStop || gitActionBusy) return
        if (enqueueGitPreflightIfNeeded(cwd, operationForSubmission(submission), submission)) return
        scope.launch {
            executeGitActionSheetNow(submission, cwd)
        }
    }

    LaunchedEffect(activeThreadId, ready, threads, gitToolbarRefreshNonce) {
        if (!ready) {
            repoStatusSnapshot = null
            branchesWithStatusSnapshot = null
            repoDiffTotals = null
            defaultGitBaseBranch = null
            isLoadingRepoDiff = false
            return@LaunchedEffect
        }
        val tid = activeThreadId
        if (tid.isNullOrBlank()) {
            repoStatusSnapshot = null
            branchesWithStatusSnapshot = null
            repoDiffTotals = null
            defaultGitBaseBranch = null
            isLoadingRepoDiff = false
            return@LaunchedEffect
        }
        val thread = threads.firstOrNull { it.id == tid }
        val cwd = thread?.gitWorkingDirectoryForGitActions()
        if (cwd == null) {
            repoStatusSnapshot = null
            branchesWithStatusSnapshot = null
            repoDiffTotals = null
            defaultGitBaseBranch = null
            isLoadingRepoDiff = false
            return@LaunchedEffect
        }
        repoStatusSnapshot = null
        isLoadingRepoDiff = true
        val git = GitActionsService(repository, cwd)
        val status = runCatching { git.status() }.getOrNull()
        val branches = runCatching { git.branchesWithStatus() }.getOrNull()
        repoStatusSnapshot = status
        branchesWithStatusSnapshot = branches
        repoDiffTotals = status?.workingTreeDiffTotals
        defaultGitBaseBranch = branches?.defaultBranch
        isLoadingRepoDiff = false
    }

    val hasSidebarSnapshot = threads.isNotEmpty()
    LaunchedEffect(drawerState, ready, hasSidebarSnapshot) {
        snapshotFlow { drawerState.isOpen }
            .collect { open ->
                if (open && ready && !hasSidebarSnapshot) {
                    runCatching { repository.refreshThreads() }
                }
            }
    }

    DisposableEffect(navController, drawerState, scope) {
        val listener =
            androidx.navigation.NavController.OnDestinationChangedListener { _, _, _ ->
                if (drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }
            }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                SidebarDrawerContent(
                    repository = repository,
                    navController = navController,
                    drawerScope = scope,
                    onOpenPairingScanner = onOpenPairingScanner,
                    onReconnectSavedPairing = viewModel::reconnectSavedPairingManually,
                    onWakeSavedComputer = viewModel::wakeSavedComputerDisplay,
                    closeDrawer = { drawerState.close() },
                    sessionReady = ready,
                    connectionState = connectionState,
                    reconnectUiState = reconnectUiState,
                )
            }
        },
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            // Home: ConversationHeader spans under the status bar. Other routes nest their own TopAppBar
            // — avoid stacking root safeDrawing TOP padding with the child's status-bar insets (double gap).
            contentWindowInsets =
                if (showShellHeader) {
                    ScaffoldDefaults.contentWindowInsets
                } else {
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    )
                },
            topBar = {
                if (showShellHeader) {
                    ConversationHeader(
                        title = activeThreadTitle,
                        pathSubtitle = pathSubtitle,
                        onPathClick =
                            threadPathFull?.let {
                                { showPathDialog = true }
                            },
                        showRunningPill = showTurnStop,
                        repoDiffTotals = repoDiffTotals,
                        isLoadingRepoDiff = isLoadingRepoDiff,
                        onTapRepoDiff =
                            if (repoDiffTotals?.hasChanges == true && showGitControls) {
                                {
                                    openRepoDiffSheetFromHeader()
                                }
                            } else {
                                null
                            },
                        showGitActions = showGitControls,
                        onGitAction = { handleGitAction(it) },
                        gitActionsBusy = gitActionBusy,
                        showsDiscardRuntimeRecovery = repoStatusSnapshot?.isDirty == true,
                        isGitActionEnabled = showGitControls && !showTurnStop,
                        isGitInitialized = repoStatusSnapshot?.isRepo == true,
                        showDesktopHandoff = showDesktopHandoff,
                        handingOffToDesktop = handingOffToDesktop,
                        showWorktreeHandoff = showWorktreeHandoff,
                        handingOffWorktree = handingOffWorktree,
                        isWorktreeProject = isWorktreeProject,
                        showTurnStop = showTurnStop,
                        onOpenDrawer = {
                            scope.launch { drawerState.open() }
                        },
                        onContinueDesktop = {
                            val tid = activeThreadId
                            if (tid != null) {
                                handingOffToDesktop = true
                                desktopHandoffError = null
                                scope.launch {
                                    runCatching {
                                        DesktopHandoffService(repository).continueOnDesktop(tid)
                                    }.onFailure { error ->
                                        desktopHandoffError =
                                            error.message ?: handoffFallbackMessage
                                    }
                                    handingOffToDesktop = false
                                }
                            }
                        },
                        onWorktreeHandoff = { handoffCurrentThreadWorktree() },
                        onStopTurn = {
                            val tid = activeThreadId
                            if (tid != null) {
                                scope.launch {
                                    runCatching { repository.interruptTurn(threadId = tid) }
                                }
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                CompositionLocalProvider(
                    LocalOpenRepoDiffForMarkdownLink provides ::openRepoDiffSheetFromMarkdown,
                ) {
                    AppNavHost(
                        navController = navController,
                        repository = repository,
                        reconnectUiState = reconnectUiState,
                        onReconnectSavedPairing = viewModel::reconnectSavedPairingManually,
                        onWakeSavedComputer = viewModel::wakeSavedComputerDisplay,
                        onOpenPairingScanner = onOpenPairingScanner,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (showShellHeader) {
                    ThreadCompletionBanner(
                        bannerMessage = gitToastMessage,
                        gitProgress = gitProgressToast,
                        onTap = { },
                        onDismiss = {
                            if (gitActionProgressMessage != null) {
                                gitActionProgressMessage = null
                            }
                            if (gitActionProgressPhase != null) {
                                gitActionProgressPhase = null
                            }
                        },
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter),
                    )
                }
            }
        }
    }

    if (showPathDialog) {
        val fullPath = threadPathFull
        if (fullPath != null) {
            AlertDialog(
                onDismissRequest = { showPathDialog = false },
                title = { Text(stringResource(R.string.turn_thread_path_dialog_title)) },
                text = {
                    SelectionContainer {
                        Text(
                            text = fullPath,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipData.newPlainText("thread-path", fullPath).toClipEntry(),
                                )
                                showPathDialog = false
                            }
                        },
                    ) {
                        Text(stringResource(R.string.turn_thread_path_copy))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPathDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }

    GitRepoDiffBottomSheet(
        visible = showRepoDiffSheet,
        scope = repoDiffSheetScope,
        onScopeChange = { repoDiffSheetScope = it },
        lastTurnRows = repoDiffSheetLastTurnRows,
        fullTreePatch = repoDiffSheetFullPatch,
        isFullTreeLoading = repoDiffSheetFullLoading,
        fullTreeError = repoDiffSheetFullError,
        gitStatus = repoStatusSnapshot,
        focusPathQuery = repoDiffMarkdownFocusQuery,
        onFocusPathQueryConsumed = { repoDiffMarkdownFocusQuery = null },
        onDismiss = {
            showRepoDiffSheet = false
            repoDiffMarkdownFocusQuery = null
        },
    )

    GitActionBottomSheet(
        visible = gitActionSheetMode != null,
        mode = gitActionSheetMode,
        initialNextStep = gitActionSheetInitialNextStep,
        status = repoStatusSnapshot,
        defaultBaseBranch = defaultGitBaseBranch,
        isBusy = gitActionBusy,
        onDismiss = {
            gitActionSheetMode = null
            gitActionSheetInitialNextStep = null
        },
        onSubmit = { executeGitActionSheet(it) },
    )

    if (showGitInitPrompt) {
        AlertDialog(
            onDismissRequest = {
                if (!gitActionBusy) {
                    showGitInitPrompt = false
                    gitInitError = null
                }
            },
            title = { Text(stringResource(R.string.git_init_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.git_init_message))
                    gitInitError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { initializeRepositoryForCurrentThread() },
                    enabled = !gitActionBusy,
                ) {
                    Text(
                        text =
                            if (gitActionBusy) {
                                stringResource(R.string.git_init_initializing)
                            } else {
                                stringResource(R.string.git_init_initialize)
                            },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!gitActionBusy) {
                            showGitInitPrompt = false
                            gitInitError = null
                        }
                    },
                    enabled = !gitActionBusy,
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showNothingToCommit) {
        AlertDialog(
            onDismissRequest = { showNothingToCommit = false },
            title = { Text(stringResource(R.string.git_action_section_write)) },
            text = { Text(stringResource(R.string.git_nothing_to_commit)) },
            confirmButton = {
                TextButton(onClick = { showNothingToCommit = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    gitSyncAlert?.let { alert ->
        fun dismissGitAlert() {
            gitSyncAlert = null
            pendingGitOperation = null
        }

        AlertDialog(
            onDismissRequest = { dismissGitAlert() },
            title = { Text(alert.title) },
            text = { Text(alert.message) },
            confirmButton = {
                Column {
                    alert.buttons
                        .filter { it.role != TurnGitSyncAlertButtonRole.cancel }
                        .forEach { button ->
                            TextButton(
                                onClick = {
                                    when (button.action) {
                                        TurnGitSyncAlertAction.dismissOnly -> dismissGitAlert()
                                        TurnGitSyncAlertAction.pullRebase -> pullRebaseForPendingGitOperation()
                                        TurnGitSyncAlertAction.continuePendingGitOperation,
                                        TurnGitSyncAlertAction.continueGitBranchOperation,
                                        -> continuePendingGitOperation()
                                        TurnGitSyncAlertAction.commitAndContinuePendingGitOperation,
                                        TurnGitSyncAlertAction.commitAndContinueGitBranchOperation,
                                        -> continuePendingGitOperation(commitFirst = true)
                                        TurnGitSyncAlertAction.discardRuntimeChanges ->
                                            discardRuntimeChangesForPendingGitOperation()
                                    }
                                },
                            ) {
                                Text(
                                    text = button.title,
                                    color =
                                        if (button.role == TurnGitSyncAlertButtonRole.destructive) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                )
                            }
                        }
                    if (alert.buttons.none { it.role != TurnGitSyncAlertButtonRole.cancel }) {
                        TextButton(onClick = { dismissGitAlert() }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                }
            },
            dismissButton = {
                val cancel = alert.buttons.firstOrNull { it.role == TurnGitSyncAlertButtonRole.cancel }
                if (cancel != null && alert.buttons.size > 1) {
                    TextButton(onClick = { dismissGitAlert() }) {
                        Text(cancel.title)
                    }
                }
            },
        )
    }

    gitActionError?.let { err ->
        AlertDialog(
            onDismissRequest = { gitActionError = null },
            title = { Text(stringResource(R.string.git_error_title)) },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { gitActionError = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (desktopHandoffError != null) {
        AlertDialog(
            onDismissRequest = { desktopHandoffError = null },
            title = { Text(stringResource(R.string.turn_open_desktop_error_title)) },
            text = { Text(desktopHandoffError ?: "") },
            confirmButton = {
                TextButton(onClick = { desktopHandoffError = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (worktreeHandoffError != null) {
        AlertDialog(
            onDismissRequest = { worktreeHandoffError = null },
            title = { Text("Worktree handoff failed") },
            text = { Text(worktreeHandoffError ?: "") },
            confirmButton = {
                TextButton(onClick = { worktreeHandoffError = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    pendingApprovalRequest?.let { request ->
        val supportsSession = PendingRequestPresentation.supportsAcceptForSession(request.method)
        fun resolve(decision: PendingApprovalDecision) {
            scope.launch { runCatching { repository.resolvePendingApproval(request.id, decision) } }
        }
        AlertDialog(
            onDismissRequest = { resolve(PendingApprovalDecision.Decline) },
            title = { Text(stringResource(R.string.approval_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val cmdLine =
                        request.command
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { c -> stringResource(R.string.approval_command_line, c) }
                    val message =
                        PendingRequestPresentation.approvalMessageOrNull(
                            request.reason,
                            cmdLine,
                        )
                    Text(
                        text = message ?: stringResource(R.string.approval_default_body),
                    )
                    Text(
                        text = stringResource(PendingRequestPresentation.approvalKindTitleRes(request.method)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                if (supportsSession) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(
                            onClick = { resolve(PendingApprovalDecision.Accept) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.approval_approve))
                        }
                        TextButton(
                            onClick = { resolve(PendingApprovalDecision.AcceptForSession) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.approval_approve_for_session))
                        }
                        TextButton(
                            onClick = { resolve(PendingApprovalDecision.Decline) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.approval_decline))
                        }
                    }
                } else {
                    TextButton(onClick = { resolve(PendingApprovalDecision.Accept) }) {
                        Text(stringResource(R.string.approval_approve))
                    }
                }
            },
            dismissButton = {
                if (!supportsSession) {
                    TextButton(onClick = { resolve(PendingApprovalDecision.Decline) }) {
                        Text(stringResource(R.string.approval_decline))
                    }
                }
            },
        )
    }

    pendingStructuredInputRequest?.let { request ->
        StructuredInputDialog(
            request = request,
            onSubmit = { answers ->
                repository.resolvePendingStructuredInput(request.id, answers)
            },
            onSkip = {
                repository.resolvePendingStructuredInput(request.id, emptyMap())
            },
        )
    }

    BridgeUpdateSheet(
        visible = bridgeUpdatePrompt != null,
        title = bridgeUpdatePrompt?.title.orEmpty(),
        message = bridgeUpdatePrompt?.message.orEmpty(),
        installCommand = bridgeUpdatePrompt?.command,
        onDismiss = { repository.dismissBridgeUpdatePrompt() },
        onRetry = { viewModel.retryBridgeConnectionAfterUpdate() },
        onScanNewQr = {
            repository.dismissBridgeUpdatePrompt()
            onOpenPairingScanner()
        },
    )
}

private data class PendingGitOperation(
    val operation: TurnGitPreflightOperation,
    val cwd: String,
    val submission: GitActionSheetSubmission? = null,
)

private fun String.withoutGitLineEndingWarnings(): String =
    lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filterNot { it.isGitLineEndingWarning() }
        .joinToString("\n")

private fun String.isGitLineEndingWarning(): Boolean =
    startsWith("warning: in the working copy of ") &&
        contains("LF will be replaced by CRLF the next time Git touches it")
