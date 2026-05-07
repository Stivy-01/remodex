package com.remodex.mobile.ui.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.GitWorktreeChangeTransferMode
import com.remodex.mobile.core.model.TurnGitPreflightOperation
import com.remodex.mobile.core.model.TurnGitPreflightPolicy
import com.remodex.mobile.core.model.TurnGitSyncAlert
import com.remodex.mobile.core.model.TurnGitSyncAlertAction
import com.remodex.mobile.core.model.TurnGitSyncAlertButtonRole
import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.data.GitBranchDisplayMapper
import com.remodex.mobile.data.WorktreeFlowCoordinator
import com.remodex.mobile.data.WorktreeNewChatDefaults
import com.remodex.mobile.data.loadGitBranchesWithStatus
import com.remodex.mobile.ui.shared.ThreadRenameDialog
import kotlinx.coroutines.launch

@Composable
fun SidebarScreen(
    repository: CodexRepository,
    onOpenArchivedChats: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val threads by repository.threads.collectAsStateWithLifecycle()
    val activeId by repository.activeThreadId.collectAsStateWithLifecycle()
    val conn by repository.connectionState.collectAsStateWithLifecycle()
    val ready by repository.isSessionReady.collectAsStateWithLifecycle()
    val runningTurnByThread by repository.runningTurnIdByThread.collectAsStateWithLifecycle()
    val protectedRunningFallback by repository.protectedRunningFallbackThreadIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var newChatBusy by remember { mutableStateOf(false) }
    var newChatError by remember { mutableStateOf<String?>(null) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var worktreeChatBusy by remember { mutableStateOf(false) }
    var worktreeChatError by remember { mutableStateOf<String?>(null) }
    var worktreeGitSyncAlert by remember { mutableStateOf<TurnGitSyncAlert?>(null) }
    var pendingWorktreeChat by remember { mutableStateOf<PendingSidebarWorktreeChat?>(null) }
    var renameTarget by remember { mutableStateOf<CodexThread?>(null) }
    var renameBusy by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var deleteLocalTarget by remember { mutableStateOf<CodexThread?>(null) }
    var deleteLocalBusy by remember { mutableStateOf(false) }
    var deleteLocalError by remember { mutableStateOf<String?>(null) }
    var archiveGroupTarget by remember { mutableStateOf<SidebarThreadGroup?>(null) }
    var archiveGroupBusy by remember { mutableStateOf(false) }
    var archiveGroupError by remember { mutableStateOf<String?>(null) }
    var deleteLocalGroupTarget by remember { mutableStateOf<SidebarThreadGroup?>(null) }
    var deleteLocalGroupBusy by remember { mutableStateOf(false) }
    var deleteLocalGroupError by remember { mutableStateOf<String?>(null) }
    var collapsedGroupIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filtered =
        remember(threads, query) {
            filterThreadsForSidebar(threads, query)
        }
    val groups =
        remember(filtered) {
            SidebarThreadGrouping.makeGroups(threads = filtered)
        }

    fun startManagedWorktreeChat(
        preselected: PendingSidebarWorktreeChat? = null,
        skipPreflight: Boolean = false,
    ) {
        worktreeChatError = null
        worktreeChatBusy = true
        scope.launch {
            try {
                val base =
                    preselected?.baseProjectPath
                        ?: WorktreeNewChatDefaults.baseProjectPath(activeId, threads)
                        ?: run {
                            worktreeChatError =
                                context.getString(R.string.sidebar_worktree_chat_no_local_project)
                            return@launch
                        }
                val branch =
                    preselected?.baseBranch
                        ?: run {
                            val loaded = loadGitBranchesWithStatus(repository, base)
                            val gitResult =
                                loaded.getOrNull() ?: run {
                                    worktreeChatError =
                                        loaded.exceptionOrNull()?.let { GitBranchDisplayMapper.userVisibleMessage(it) }
                                            ?: context.getString(R.string.sidebar_worktree_chat_no_base_branch)
                                    return@launch
                                }
                            val summary = GitBranchDisplayMapper.summaryFrom(gitResult)
                            val resolvedBranch =
                                WorktreeNewChatDefaults.baseBranch(summary) ?: run {
                                    worktreeChatError =
                                        context.getString(R.string.sidebar_worktree_chat_no_base_branch)
                                    return@launch
                                }
                            if (!skipPreflight) {
                                val alert =
                                    TurnGitPreflightPolicy.alertFor(
                                        status = gitResult.status,
                                        branches = gitResult,
                                        operation =
                                            TurnGitPreflightOperation.createManagedWorktree(
                                                baseBranch = resolvedBranch,
                                                changeTransfer = GitWorktreeChangeTransferMode.none,
                                            ),
                                    )
                                if (alert != null) {
                                    pendingWorktreeChat = PendingSidebarWorktreeChat(base, resolvedBranch)
                                    worktreeGitSyncAlert = alert
                                    return@launch
                                }
                            }
                            resolvedBranch
                        }
                WorktreeFlowCoordinator(repository).startNewManagedWorktreeChat(base, branch)
            } catch (e: Exception) {
                worktreeChatError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            } finally {
                worktreeChatBusy = false
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SidebarSearchField(query = query, onQueryChange = { query = it })
        val bridgeConnected = conn is ConnectionState.Connected
        val worktreeEntryEnabled = ready && bridgeConnected && !newChatBusy && !worktreeChatBusy

        SidebarCompactActionRow(
            label = stringResource(R.string.sidebar_new_chat),
            enabled = ready && !newChatBusy && !worktreeChatBusy,
            busy = newChatBusy,
            onClick = { showProjectPicker = true },
        )
        SidebarCompactActionRow(
            label = stringResource(R.string.sidebar_new_managed_worktree_chat),
            enabled = worktreeEntryEnabled,
            busy = worktreeChatBusy,
            onClick = { startManagedWorktreeChat() },
            leading = {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountTree,
                        contentDescription = stringResource(R.string.cd_sidebar_new_managed_worktree_chat),
                        modifier =
                            Modifier
                                .padding(horizontal = 7.dp, vertical = 7.dp)
                                .size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        newChatError?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        worktreeChatError?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            groups.forEach { group ->
                item(key = "hdr-${group.id}") {
                    val isCollapsed = group.id in collapsedGroupIds
                    SidebarGroupHeaderRow(
                        group = group,
                        newChatBusy = newChatBusy,
                        collapsed = isCollapsed,
                        onToggleCollapse = {
                            collapsedGroupIds =
                                if (isCollapsed) {
                                    collapsedGroupIds - group.id
                                } else {
                                    collapsedGroupIds + group.id
                                }
                        },
                        onNewChatInProject =
                            if (group.kind == SidebarThreadGroupKind.Project && group.projectPath != null) {
                                {
                                    newChatError = null
                                    newChatBusy = true
                                    val cwd = group.projectPath
                                    scope.launch {
                                        runCatching { repository.startThread(cwd = cwd) }
                                            .onFailure { e ->
                                                newChatError = e.message ?: e.javaClass.simpleName
                                            }
                                        newChatBusy = false
                                    }
                                }
                            } else {
                                null
                            },
                        onArchiveProjectGroup =
                            if (group.kind == SidebarThreadGroupKind.Project) {
                                {
                                    archiveGroupTarget = group
                                    archiveGroupError = null
                                }
                            } else {
                                null
                            },
                        onDeleteLocalGroup =
                            if (group.kind == SidebarThreadGroupKind.Project) {
                                {
                                    deleteLocalGroupTarget = group
                                    deleteLocalGroupError = null
                                }
                            } else {
                                null
                            },
                        onOpenArchivedChats =
                            if (group.kind == SidebarThreadGroupKind.Archived) {
                                onOpenArchivedChats
                            } else {
                                null
                            },
                    )
                }
                items(
                    items = if (group.id in collapsedGroupIds) emptyList() else group.threads,
                    key = { it.id },
                ) { thread ->
                    SidebarThreadRow(
                        thread = thread,
                        selected = thread.id == activeId,
                        isRunning =
                            runningTurnByThread.containsKey(thread.id) ||
                                protectedRunningFallback.contains(thread.id),
                        onSelect = {
                            scope.launch { repository.setActiveThreadId(thread.id) }
                        },
                        onRenameRequest = {
                            renameTarget = thread
                            renameError = null
                        },
                        onDeleteLocalRequest = {
                            deleteLocalTarget = thread
                            deleteLocalError = null
                        },
                    )
                }
            }
        }
        ThreadRenameDialog(
            visible = renameTarget != null,
            initialName = renameTarget?.displayTitle.orEmpty(),
            busy = renameBusy,
            error = renameError,
            onDismiss = {
                if (!renameBusy) {
                    renameTarget = null
                    renameError = null
                }
            },
            onConfirm = { newName ->
                val target = renameTarget ?: return@ThreadRenameDialog
                if (newName.isBlank()) {
                    renameError = context.getString(R.string.thread_rename_dialog_empty)
                    return@ThreadRenameDialog
                }
                renameBusy = true
                renameError = null
                scope.launch {
                    runCatching {
                        repository.renameThread(target.id, newName)
                    }.onSuccess {
                        renameTarget = null
                    }.onFailure { e ->
                        renameError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                    }
                    renameBusy = false
                }
            },
        )
        deleteLocalTarget?.let { target ->
            AlertDialog(
                onDismissRequest = {
                    if (!deleteLocalBusy) {
                        deleteLocalTarget = null
                        deleteLocalError = null
                    }
                },
                title = { Text(stringResource(R.string.sidebar_thread_delete_local_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sidebar_thread_delete_local_message, target.displayTitle))
                        deleteLocalError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !deleteLocalBusy,
                        onClick = {
                            deleteLocalBusy = true
                            deleteLocalError = null
                            scope.launch {
                                runCatching {
                                    repository.deleteThreadLocally(target.id)
                                }.onSuccess {
                                    deleteLocalTarget = null
                                }.onFailure { e ->
                                    deleteLocalError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                                }
                                deleteLocalBusy = false
                            }
                        },
                    ) {
                        Text(
                            text =
                                if (deleteLocalBusy) {
                                    stringResource(R.string.sidebar_thread_delete_local_deleting)
                                } else {
                                    stringResource(R.string.sidebar_thread_delete_local_confirm)
                                },
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !deleteLocalBusy,
                        onClick = {
                            deleteLocalTarget = null
                            deleteLocalError = null
                        },
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
        archiveGroupTarget?.let { group ->
            AlertDialog(
                onDismissRequest = {
                    if (!archiveGroupBusy) {
                        archiveGroupTarget = null
                        archiveGroupError = null
                    }
                },
                title = { Text(stringResource(R.string.sidebar_project_archive_title, group.label)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sidebar_project_archive_message))
                        archiveGroupError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !archiveGroupBusy,
                        onClick = {
                            archiveGroupBusy = true
                            archiveGroupError = null
                            scope.launch {
                                runCatching {
                                    val ids = SidebarThreadGrouping.liveThreadIdsForGroup(group, threads)
                                    repository.archiveThreadGroup(ids)
                                }.onSuccess {
                                    archiveGroupTarget = null
                                }.onFailure { e ->
                                    archiveGroupError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                                }
                                archiveGroupBusy = false
                            }
                        },
                    ) {
                        Text(
                            text =
                                if (archiveGroupBusy) {
                                    stringResource(R.string.sidebar_project_archive_busy)
                                } else {
                                    stringResource(R.string.sidebar_project_archive_confirm)
                                },
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !archiveGroupBusy,
                        onClick = {
                            archiveGroupTarget = null
                            archiveGroupError = null
                        },
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
        deleteLocalGroupTarget?.let { group ->
            AlertDialog(
                onDismissRequest = {
                    if (!deleteLocalGroupBusy) {
                        deleteLocalGroupTarget = null
                        deleteLocalGroupError = null
                    }
                },
                title = { Text(stringResource(R.string.sidebar_project_delete_local_title, group.label)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sidebar_project_delete_local_message))
                        deleteLocalGroupError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !deleteLocalGroupBusy,
                        onClick = {
                            deleteLocalGroupBusy = true
                            deleteLocalGroupError = null
                            scope.launch {
                                runCatching {
                                    val ids = SidebarThreadGrouping.liveThreadIdsForGroup(group, threads)
                                    repository.deleteLocalThreadGroup(ids)
                                }.onSuccess {
                                    deleteLocalGroupTarget = null
                                }.onFailure { e ->
                                    deleteLocalGroupError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                                }
                                deleteLocalGroupBusy = false
                            }
                        },
                    ) {
                        Text(
                            text =
                                if (deleteLocalGroupBusy) {
                                    stringResource(R.string.sidebar_project_delete_local_busy)
                                } else {
                                    stringResource(R.string.sidebar_project_delete_local_confirm)
                                },
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !deleteLocalGroupBusy,
                        onClick = {
                            deleteLocalGroupTarget = null
                            deleteLocalGroupError = null
                        },
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
        SidebarProjectPickerSheet(
            repository = repository,
            visible = showProjectPicker,
            initialPath = WorktreeNewChatDefaults.baseProjectPath(activeId, threads),
            onDismiss = { showProjectPicker = false },
            onStartBusyChange = { newChatBusy = it },
        )
        worktreeGitSyncAlert?.let { alert ->
            fun dismissWorktreeAlert() {
                worktreeGitSyncAlert = null
                pendingWorktreeChat = null
            }

            AlertDialog(
                onDismissRequest = { dismissWorktreeAlert() },
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
                                            TurnGitSyncAlertAction.continuePendingGitOperation,
                                            TurnGitSyncAlertAction.continueGitBranchOperation,
                                            -> {
                                                val pending = pendingWorktreeChat
                                                dismissWorktreeAlert()
                                                if (pending != null) {
                                                    startManagedWorktreeChat(pending, skipPreflight = true)
                                                }
                                            }
                                            TurnGitSyncAlertAction.pullRebase -> {
                                                val pending = pendingWorktreeChat
                                                dismissWorktreeAlert()
                                                if (pending != null) {
                                                    worktreeChatBusy = true
                                                    scope.launch {
                                                        runCatching {
                                                            com.remodex.mobile.services.GitActionsService(
                                                                repository,
                                                                pending.baseProjectPath,
                                                            ).pull()
                                                        }.onFailure { e ->
                                                            worktreeChatError =
                                                                GitBranchDisplayMapper.userVisibleMessage(e)
                                                        }
                                                        worktreeChatBusy = false
                                                    }
                                                }
                                            }
                                            else -> dismissWorktreeAlert()
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
                            TextButton(onClick = { dismissWorktreeAlert() }) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    }
                },
                dismissButton = {
                    val cancel = alert.buttons.firstOrNull { it.role == TurnGitSyncAlertButtonRole.cancel }
                    if (cancel != null && alert.buttons.size > 1) {
                        TextButton(onClick = { dismissWorktreeAlert() }) {
                            Text(cancel.title)
                        }
                    }
                },
            )
        }
    }
}

private data class PendingSidebarWorktreeChat(
    val baseProjectPath: String,
    val baseBranch: String,
)

@Composable
private fun SidebarGroupHeaderRow(
    group: SidebarThreadGroup,
    newChatBusy: Boolean,
    onNewChatInProject: (() -> Unit)?,
    onArchiveProjectGroup: (() -> Unit)? = null,
    onDeleteLocalGroup: (() -> Unit)? = null,
    onOpenArchivedChats: (() -> Unit)? = null,
    collapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
) {
    var showOverflow by remember { mutableStateOf(false) }
    val hasActions = onArchiveProjectGroup != null || onDeleteLocalGroup != null
    val canCollapse = group.kind == SidebarThreadGroupKind.Project
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .then(
                        if (onOpenArchivedChats != null) {
                            Modifier.clickable { onOpenArchivedChats() }
                        } else if (canCollapse) {
                            Modifier.clickable { onToggleCollapse?.invoke() }
                        } else {
                            Modifier
                        },
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (canCollapse) {
                Icon(
                    imageVector =
                        if (collapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight
                        else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Icon(
                imageVector = group.leadingIcon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = group.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (onNewChatInProject != null) {
                IconButton(
                    onClick = onNewChatInProject,
                    enabled = !newChatBusy,
                    modifier = Modifier.size(36.dp),
                ) {
                    if (newChatBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.sidebar_new_chat),
                        )
                    }
                }
            }
            if (hasActions) {
                Box {
                    IconButton(
                        onClick = { showOverflow = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.sidebar_thread_actions_cd),
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false },
                    ) {
                        onArchiveProjectGroup?.let { action ->
                            DropdownMenuItem(
                                text = { Text("Archive project") },
                                onClick = {
                                    showOverflow = false
                                    action()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Archive,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                        onDeleteLocalGroup?.let { action ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.sidebar_thread_delete_local),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showOverflow = false
                                    action()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun SidebarThreadGroup.leadingIcon(): ImageVector =
    when (kind) {
        SidebarThreadGroupKind.Archived -> Icons.Outlined.Archive
        SidebarThreadGroupKind.Project -> {
            val t = threads.firstOrNull()
            when {
                t == null -> Icons.Outlined.Cloud
                t.normalizedProjectPath == null -> Icons.Outlined.Cloud
                t.isManagedWorktreeProject -> Icons.Outlined.AccountTree
                else -> Icons.Outlined.Computer
            }
        }
    }

private fun filterThreadsForSidebar(
    threads: List<CodexThread>,
    query: String,
): List<CodexThread> {
    val t = query.trim().lowercase()
    if (t.isEmpty()) return threads
    return threads.filter { thread ->
        thread.displayTitle.lowercase().contains(t) ||
            thread.id.lowercase().contains(t) ||
            (thread.preview?.lowercase()?.contains(t) == true) ||
            (thread.cwd?.lowercase()?.contains(t) == true)
    }
}
