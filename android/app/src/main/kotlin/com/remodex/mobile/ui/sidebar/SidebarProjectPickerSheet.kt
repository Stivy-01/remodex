package com.remodex.mobile.ui.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexProjectDirectoryEntry
import com.remodex.mobile.core.model.CodexProjectDirectoryListing
import com.remodex.mobile.core.model.CodexProjectLocation
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.services.ProjectFolderService
import com.remodex.mobile.ui.theme.RemodexModalBottomSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidebarProjectPickerSheet(
    repository: CodexRepository,
    visible: Boolean,
    initialPath: String?,
    onDismiss: () -> Unit,
    onStartBusyChange: (Boolean) -> Unit,
    onStartThread: suspend (String?) -> Unit,
    initialFoldersCollapsed: Boolean = false,
    onThreadStarted: suspend () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val service = remember(repository) { ProjectFolderService(repository) }

    var quickLocations by remember { mutableStateOf<List<CodexProjectLocation>>(emptyList()) }
    var currentPath by remember(visible, initialPath) { mutableStateOf(initialPath?.trim().orEmpty()) }
    var listing by remember { mutableStateOf<CodexProjectDirectoryListing?>(null) }
    var searchQuery by remember(visible) { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<CodexProjectDirectoryEntry>>(emptyList()) }
    var loadingError by remember { mutableStateOf<String?>(null) }
    var startError by remember { mutableStateOf<String?>(null) }
    var createName by remember(visible, initialPath) { mutableStateOf("") }
    var startBusy by remember { mutableStateOf(false) }
    var startingPath by remember { mutableStateOf<String?>(null) }
    var noCwdBusy by remember { mutableStateOf(false) }
    var createBusy by remember { mutableStateOf(false) }
    var foldersCollapsed by remember(visible, initialPath, initialFoldersCollapsed) {
        mutableStateOf(initialFoldersCollapsed)
    }

    fun normalizedPath(): String = currentPath.trim()

    fun closeSheet() {
        onStartBusyChange(false)
        onDismiss()
    }

    fun startChat(path: String) {
        val cwd = path.trim()
        if (cwd.isBlank() || startBusy) return
        startError = null
        startBusy = true
        startingPath = cwd
        onStartBusyChange(true)
        scope.launch {
            try {
                onStartThread(cwd)
                closeSheet()
                onThreadStarted()
            } catch (e: Exception) {
                startError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            } finally {
                startBusy = false
                startingPath = null
                onStartBusyChange(false)
            }
        }
    }

    fun startCloudChat() {
        if (noCwdBusy || startBusy) return
        startError = null
        noCwdBusy = true
        onStartBusyChange(true)
        scope.launch {
            try {
                onStartThread(null)
                closeSheet()
                onThreadStarted()
            } catch (e: Exception) {
                startError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            } finally {
                noCwdBusy = false
                onStartBusyChange(false)
            }
        }
    }

    fun selectFolder(path: String) {
        val cwd = path.trim()
        if (cwd.isBlank()) return
        currentPath = cwd
        searchQuery = ""
        createName = ""
        loadingError = null
    }

    fun createFolder() {
        val parent = normalizedPath()
        val name = createName.trim()
        if (parent.isBlank() || name.isBlank() || createBusy) return
        createBusy = true
        loadingError = null
        scope.launch {
            try {
                val createdPath = service.createDirectory(parent, name)
                createName = ""
                searchQuery = ""
                currentPath = createdPath
            } catch (e: Exception) {
                loadingError = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            } finally {
                createBusy = false
            }
        }
    }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        loadingError = null
        startError = null
        searchQuery = ""
        createName = ""
        startingPath = null
        quickLocations =
            runCatching { service.quickLocations() }
                .getOrElse {
                    loadingError = it.message?.takeIf { msg -> msg.isNotBlank() } ?: it.javaClass.simpleName
                    emptyList()
                }
        if (normalizedPath().isBlank()) {
            currentPath = quickLocations.firstOrNull()?.path.orEmpty()
        }
    }

    LaunchedEffect(currentPath, searchQuery, visible) {
        if (!visible) return@LaunchedEffect
        val path = normalizedPath()
        if (path.isBlank()) {
            listing = null
            searchResults = emptyList()
            return@LaunchedEffect
        }
        loadingError = null
        if (searchQuery.trim().isNotEmpty()) {
            searchResults =
                runCatching { service.searchDirectories(path, searchQuery) }
                    .getOrElse {
                        loadingError = it.message?.takeIf { msg -> msg.isNotBlank() } ?: it.javaClass.simpleName
                        emptyList()
                    }
                    .sortedWith(
                        compareBy<CodexProjectDirectoryEntry> { it.name.lowercase() }
                            .thenBy { it.path },
                    )
            listing = null
        } else {
            listing =
                runCatching { service.listDirectory(path) }
                    .getOrElse {
                        loadingError = it.message?.takeIf { msg -> msg.isNotBlank() } ?: it.javaClass.simpleName
                        null
                    }
            searchResults = emptyList()
            listing?.path?.trim()?.takeIf { it.isNotBlank() }?.let { canonical ->
                if (canonical != currentPath) {
                    currentPath = canonical
                }
            }
        }
    }

    RemodexModalBottomSheet(
        modifier = modifier,
        onDismissRequest = {
            if (!startBusy) {
                closeSheet()
            }
        },
        sheetState = sheetState,
    ) {
        val visibleEntries =
            if (searchQuery.trim().isNotEmpty()) {
                searchResults
            } else {
                listing?.entries.orEmpty().sortedWith(
                    compareBy<CodexProjectDirectoryEntry> { it.name.lowercase() }
                        .thenBy { it.path },
                )
            }
        val searchingFolders = searchQuery.trim().isNotEmpty()
        val folderListCollapsed = foldersCollapsed && !searchingFolders

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sidebar_project_picker_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.sidebar_project_picker_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = { if (!startBusy) closeSheet() },
                        enabled = !startBusy,
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }

            if (quickLocations.isNotEmpty()) {
                item {
                    ProjectPickerSectionLabel(
                        text = stringResource(R.string.sidebar_project_picker_quick_locations),
                    )
                }
                items(quickLocations, key = { "quick-${it.path}" }) { location ->
                    ProjectPickerActionRow(
                        title = location.label,
                        subtitle = location.path,
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                            )
                        },
                        enabled = !startBusy && !noCwdBusy && !createBusy,
                        busy = startBusy && startingPath == location.path,
                        onClick = { startChat(location.path) },
                    )
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.sidebar_project_picker_no_quick_locations),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                ProjectPickerSectionLabel(
                    text = stringResource(R.string.sidebar_project_picker_no_project),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            item {
                ProjectPickerActionRow(
                    title = "Cloud",
                    subtitle = stringResource(R.string.sidebar_project_picker_no_project_desc),
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Cloud,
                            contentDescription = null,
                        )
                    },
                    enabled = !startBusy && !noCwdBusy && !createBusy,
                    busy = noCwdBusy,
                    onClick = { startCloudChat() },
                )
            }

            item {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            ProjectPickerSectionLabel(
                                text = stringResource(R.string.sidebar_project_picker_browse_title),
                            )
                            Text(
                                text = currentPath.ifBlank { stringResource(R.string.sidebar_project_picker_browse_empty) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = {
                                listing?.parentPath?.let { parent ->
                                    selectFolder(parent)
                                }
                            },
                            enabled = !startBusy && !createBusy && listing?.parentPath != null,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowUpward,
                                contentDescription = stringResource(R.string.sidebar_project_picker_parent_cd),
                            )
                        }
                    }

                    SidebarSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholderText = stringResource(R.string.sidebar_project_picker_search_hint),
                    )
                }
            }

            if (loadingError != null || startError != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        loadingError?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        startError?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it },
                    enabled = !createBusy && !startBusy && currentPath.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.sidebar_project_picker_create_label)) },
                    placeholder = { Text(stringResource(R.string.sidebar_project_picker_create_placeholder)) },
                    trailingIcon = {
                        IconButton(
                            onClick = { createFolder() },
                            enabled = !createBusy && !startBusy && createName.trim().isNotEmpty() && currentPath.isNotBlank(),
                        ) {
                            if (createBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.CreateNewFolder,
                                    contentDescription = stringResource(R.string.sidebar_project_picker_create_cd),
                                )
                            }
                        }
                    },
                )
            }

            item {
                ProjectPickerCollapsibleSectionHeader(
                    text =
                        if (searchingFolders) {
                            stringResource(R.string.sidebar_project_picker_search_results)
                        } else {
                            stringResource(R.string.sidebar_project_picker_folders)
                        },
                    collapsed = folderListCollapsed,
                    toggleEnabled = !searchingFolders,
                    onToggle = { foldersCollapsed = !foldersCollapsed },
                )
            }

            if (!folderListCollapsed) {
                if (visibleEntries.isEmpty()) {
                    item {
                        Text(
                            text =
                                if (searchingFolders) {
                                    stringResource(R.string.sidebar_project_picker_empty_search)
                                } else {
                                    stringResource(R.string.sidebar_project_picker_empty_folder)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                } else {
                    items(visibleEntries, key = { "folder-${it.id}" }) { entry ->
                        ProjectPickerActionRow(
                            title = entry.name,
                            subtitle = entry.path,
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                )
                            },
                            enabled = !startBusy && !createBusy,
                            busy = false,
                            onClick = { selectFolder(entry.path) },
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { startChat(normalizedPath()) },
                    enabled = !startBusy && !createBusy && normalizedPath().isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    if (startBusy && startingPath == normalizedPath()) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                        )
                    }
                    Text(
                        text = stringResource(R.string.sidebar_project_picker_start_here),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectPickerCollapsibleSectionHeader(
    text: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
    toggleEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(enabled = toggleEnabled, onClick = onToggle),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (toggleEnabled) {
                Icon(
                    imageVector =
                        if (collapsed) {
                            Icons.AutoMirrored.Filled.KeyboardArrowRight
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProjectPickerSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProjectPickerActionRow(
    title: String,
    subtitle: String?,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(enabled = enabled && !busy, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceVariant.copy(alpha = 0.28f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = colors.surface.copy(alpha = 0.52f),
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(7.dp)
                            .size(18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let { supporting ->
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
