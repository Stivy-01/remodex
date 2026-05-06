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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import com.remodex.mobile.ui.design.canvas.CanvasBridge
import com.remodex.mobile.ui.design.canvas.CanvasRenderState
import com.remodex.mobile.ui.design.canvas.CanvasSnapshotViewer
import com.remodex.mobile.ui.design.canvas.CanvasWebView

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
    val snapshotRenderState by viewModel.snapshotRenderState.collectAsState()
    val selectedNode by viewModel.selectedNode.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()
    val promptText by viewModel.promptText.collectAsState()
    var showExportSheet by remember { mutableStateOf(false) }

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
                                text = statusLabel(doc.status, generationState.status),
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
                        IconButton(onClick = { showExportSheet = true }) {
                            Icon(
                                painter = painterResource(LucideR.drawable.lucide_ic_download),
                                contentDescription = "Export",
                            )
                        }
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
            } else if (generationState.status == "error") {
                GenerationErrorView(
                    onRetry = viewModel::onSubmitPrompt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                )
            } else if (generationState.status in listOf("generating", "rendering_snapshot")) {
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
                    snapshotRenderState = snapshotRenderState,
                    canvasBridge = viewModel.canvasBridge,
                    onRefreshSnapshot = viewModel::refreshSnapshot,
                    modifier = Modifier.weight(1f),
                )

                AnimatedVisibility(visible = selectedNode != null) {
                    selectedNode?.let { node ->
                        InspectorCard(
                            node = node,
                            onAskAiEdit = {
                                viewModel.editDesignWithAi(
                                    prompt = promptText,
                                    selectedNodeId = node?.id,
                                )
                            },
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
                    onSubmit = { viewModel.editDesignWithAi(promptText, selectedNode?.id) },
                    onExport = { showExportSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }

    DesignExportSheet(
        visible = showExportSheet,
        onDismiss = { showExportSheet = false },
        onExport = { target ->
            viewModel.requestExport(target)
        },
        exportResult = exportResult,
        onClearExport = { viewModel.clearExport() },
        isLoading = false,
    )
}

@Composable
private fun CanvasArea(
    document: DesignDocument,
    uiMode: DesignMode,
    snapshotRenderState: CanvasRenderState,
    canvasBridge: CanvasBridge,
    onRefreshSnapshot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (uiMode) {
            DesignMode.VIEW -> CanvasSnapshotViewer(
                state = snapshotRenderState,
                onRetry = null,
                onRefreshSnapshot = onRefreshSnapshot,
                modifier = Modifier.fillMaxSize(),
            )
            DesignMode.EDIT -> CanvasWebView(
                bridge = canvasBridge,
                modifier = Modifier.fillMaxSize(),
            )
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onExport) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_download),
                contentDescription = "Export",
            )
        }
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
private fun GenerationErrorView(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(LucideR.drawable.lucide_ic_triangle_alert),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Design generation failed",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Something went wrong. You can retry or go back to the chat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetry) {
                Text("Retry")
            }
            FilledTonalButton(onClick = onRetry) {
                Text("Back to chat")
            }
        }
    }
}

private fun statusLabel(docStatus: DesignDocumentStatus, genStatus: String): String = when {
    genStatus == "error" -> "Generation failed"
    genStatus == "generating" -> "Generating..."
    genStatus == "rendering_snapshot" -> "Rendering..."
    else -> when (docStatus) {
        DesignDocumentStatus.EMPTY -> "Empty"
        DesignDocumentStatus.GENERATING -> "Generating..."
        DesignDocumentStatus.READY -> "Ready"
        DesignDocumentStatus.ERROR -> "Error"
        DesignDocumentStatus.OUTDATED_SNAPSHOT -> "Preview may be outdated"
    }
}
