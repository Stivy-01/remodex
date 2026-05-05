package com.remodex.mobile.ui.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidebarProjectPickerSheet(
    repository: CodexRepository,
    visible: Boolean,
    initialPath: String?,
    onDismiss: () -> Unit,
    onStartBusyChange: (Boolean) -> Unit,
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
                repository.startThread(cwd = cwd)
                closeSheet()
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
                repository.startThread(cwd = null)
                closeSheet()
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

    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = {
            if (!startBusy) {
                closeSheet()
            }
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 760.dp)
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.sidebar_project_picker_title),
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.sidebar_project_picker_subtitle),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { if (!startBusy) closeSheet() },
                            enabled = !startBusy,
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                }
            }

            if (quickLocations.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.sidebar_project_picker_quick_locations),
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    quickLocations.forEach { location ->
                        SidebarCompactActionRow(
                            label = location.label,
                            enabled = !startBusy && !noCwdBusy && !createBusy,
                            busy = startBusy && startingPath == location.path,
                            onClick = { startChat(location.path) },
                            leading = {
                                Surface(
                                    shape = androidx.compose.material3.MaterialTheme.shapes.small,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                        modifier =
                                            Modifier
                                                .padding(horizontal = 7.dp, vertical = 7.dp)
                                                .size(18.dp),
                                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                        Text(
                            text = location.path,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 28.dp).offset(y = (-4).dp),
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.sidebar_project_picker_no_quick_locations),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.sidebar_project_picker_no_project),
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SidebarCompactActionRow(
                    label = "Cloud",
                    enabled = !startBusy && !noCwdBusy && !createBusy,
                    busy = noCwdBusy,
                    onClick = { startCloudChat() },
                    leading = {
                        Surface(
                            shape = androidx.compose.material3.MaterialTheme.shapes.small,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Cloud,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .padding(horizontal = 7.dp, vertical = 7.dp)
                                        .size(18.dp),
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                Text(
                    text = stringResource(R.string.sidebar_project_picker_no_project_desc),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.sidebar_project_picker_browse_title),
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = currentPath.ifBlank { stringResource(R.string.sidebar_project_picker_browse_empty) },
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
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
                }

                SidebarSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholderText = stringResource(R.string.sidebar_project_picker_search_hint),
                )

                if (loadingError != null) {
                    Text(
                        text = loadingError.orEmpty(),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                }
                if (startError != null) {
                    Text(
                        text = startError.orEmpty(),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                }

                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it },
                    enabled = !createBusy && !startBusy && currentPath.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text =
                        if (searchQuery.trim().isNotEmpty()) {
                            stringResource(R.string.sidebar_project_picker_search_results)
                        } else {
                            stringResource(R.string.sidebar_project_picker_folders)
                        },
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val entries =
                        if (searchQuery.trim().isNotEmpty()) {
                            searchResults
                        } else {
                            listing?.entries.orEmpty().sortedWith(
                                compareBy<CodexProjectDirectoryEntry> { it.name.lowercase() }
                                    .thenBy { it.path },
                            )
                        }
                    if (entries.isEmpty()) {
                        item {
                            Text(
                                text =
                                    if (searchQuery.trim().isNotEmpty()) {
                                        stringResource(R.string.sidebar_project_picker_empty_search)
                                    } else {
                                        stringResource(R.string.sidebar_project_picker_empty_folder)
                                    },
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    } else {
                        items(entries, key = { it.id }) { entry ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = entry.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = entry.path,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                    )
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !startBusy && !createBusy) {
                                            selectFolder(entry.path)
                                        },
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { startChat(normalizedPath()) },
                enabled = !startBusy && !createBusy && normalizedPath().isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
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