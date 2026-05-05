package com.remodex.mobile.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignWorkspaceScreen(
    viewModel: DesignViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiMode by viewModel.uiMode.collectAsState()
    val generationState by viewModel.generationState.collectAsState()
    val currentDocument by viewModel.currentDocument.collectAsState()
    val selectedNode by viewModel.selectedNode.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()
    val promptText by viewModel.promptText.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Design",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        currentDocument?.let { doc ->
                            Text(
                                text = statusLabel(doc.status),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(LucideR.drawable.lucide_ic_arrow_left),
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (currentDocument != null && currentDocument?.status == DesignDocumentStatus.READY) {
                        FilledTonalButton(
                            onClick = { viewModel.onToggleMode() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = if (uiMode == DesignMode.VIEW) "Edit" else "View",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (currentDocument == null && generationState.status == "idle") {
                DesignEmptyState(
                    promptText = promptText,
                    onPromptTextChanged = viewModel::onPromptTextChanged,
                    onSubmitPrompt = viewModel::onSubmitPrompt,
                )
            } else if (generationState.status == "generating" || generationState.status == "rendering_snapshot") {
                GenerationProgressView(
                    steps = generationState.steps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                )
            } else if (currentDocument != null) {
                CanvasArea(
                    document = currentDocument!!,
                    uiMode = uiMode,
                    modifier = Modifier.weight(1f),
                )

                AnimatedVisibility(visible = selectedNode != null) {
                    selectedNode?.let { node ->
                        InspectorCard(
                            node = node,
                            onAskAiEdit = { /* TODO: wire editDocument */ },
                            onDismiss = { viewModel.onSelectionCleared() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                BottomPromptBar(
                    promptText = promptText,
                    onPromptTextChanged = viewModel::onPromptTextChanged,
                    onSubmit = viewModel::onSubmitPrompt,
                    onExport = { /* TODO: show export sheet */ },
                    hasExport = exportResult == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }

    exportResult?.let { result ->
        ExportResultSheet(
            result = result,
            onDismiss = { viewModel.clearExport() },
        )
    }
}

@Composable
private fun CanvasArea(
    document: DesignDocument,
    uiMode: DesignMode,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (uiMode) {
            DesignMode.VIEW -> DesignSnapshotViewer(
                document = document,
                modifier = Modifier.fillMaxSize(),
            )
            DesignMode.EDIT -> DesignEditPlaceholder(
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DesignSnapshotViewer(
    document: DesignDocument,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_layout_template),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Design Preview",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "v${document.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (document.status == DesignDocumentStatus.OUTDATED_SNAPSHOT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { /* TODO: refresh snapshot */ }) {
                        Text("Refresh")
                    }
                }
            }
        }
    }
}

@Composable
private fun DesignEditPlaceholder(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Edit Mode",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "WebView canvas will load here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GenerationProgressView(
    steps: List<GenerationStep>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Generating design...",
            style = MaterialTheme.typography.titleMedium,
        )

        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        steps.forEach { step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val color = when (step.status) {
                    GenerationStepStatus.DONE -> MaterialTheme.colorScheme.primary
                    GenerationStepStatus.ACTIVE -> MaterialTheme.colorScheme.secondary
                    GenerationStepStatus.ERROR -> MaterialTheme.colorScheme.error
                    GenerationStepStatus.PENDING -> MaterialTheme.colorScheme.outline
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Text(
                    text = step.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (step.status) {
                        GenerationStepStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomPromptBar(
    promptText: String,
    onPromptTextChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onExport: () -> Unit,
    hasExport: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = promptText,
            onValueChange = onPromptTextChanged,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Describe changes...") },
            maxLines = 1,
        )
        IconButton(onClick = onSubmit, enabled = promptText.isNotBlank()) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_send),
                contentDescription = "Send",
            )
        }
    }
}

@Composable
private fun InspectorCard(
    node: SelectedNode,
    onAskAiEdit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_x),
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${node.type} — ${node.boundsWidth.toInt()}x${node.boundsHeight.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onAskAiEdit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ask AI to edit this")
            }
        }
    }
}

@Composable
private fun ExportResultSheet(
    result: ExportResult,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Exported Code",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_x),
                        contentDescription = "Close",
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            result.files.forEach { file ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = file.path,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = file.language,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: DesignDocumentStatus): String = when (status) {
    DesignDocumentStatus.EMPTY -> "Empty"
    DesignDocumentStatus.GENERATING -> "Generating..."
    DesignDocumentStatus.READY -> "Ready"
    DesignDocumentStatus.ERROR -> "Error"
    DesignDocumentStatus.OUTDATED_SNAPSHOT -> "Preview may be outdated"
}
