package com.remodex.mobile.ui.design

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DesignViewModel : ViewModel() {

    private val _uiMode = MutableStateFlow(DesignMode.VIEW)
    val uiMode: StateFlow<DesignMode> = _uiMode.asStateFlow()

    private val _generationState = MutableStateFlow(GenerationState())
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _currentDocument = MutableStateFlow<DesignDocument?>(null)
    val currentDocument: StateFlow<DesignDocument?> = _currentDocument.asStateFlow()

    private val _selectedNode = MutableStateFlow<SelectedNode?>(null)
    val selectedNode: StateFlow<SelectedNode?> = _selectedNode.asStateFlow()

    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    private val _promptText = MutableStateFlow("")
    val promptText: StateFlow<String> = _promptText.asStateFlow()

    fun onPromptTextChanged(text: String) {
        _promptText.value = text
    }

    fun onSubmitPrompt() {
        val prompt = _promptText.value.trim()
        if (prompt.isBlank()) return

        _generationState.value = GenerationState(
            generationId = "gen_${System.currentTimeMillis()}",
            status = "generating",
            steps = listOf(
                GenerationStep("Understanding prompt", GenerationStepStatus.PENDING),
                GenerationStep("Creating layout", GenerationStepStatus.PENDING),
                GenerationStep("Adding components", GenerationStepStatus.PENDING),
                GenerationStep("Styling screen", GenerationStepStatus.PENDING),
                GenerationStep("Rendering preview", GenerationStepStatus.PENDING),
                GenerationStep("Creating snapshot", GenerationStepStatus.PENDING),
            ),
        )

        viewModelScope.launch {
            val steps = listOf(
                "Understanding prompt",
                "Creating layout",
                "Adding components",
                "Styling screen",
                "Rendering preview",
                "Creating snapshot",
            )

            for (i in steps.indices) {
                delay(800)
                val updated = _generationState.value.steps.toMutableList()
                updated[i] = GenerationStep(steps[i], GenerationStepStatus.ACTIVE)
                if (i > 0) {
                    updated[i - 1] = GenerationStep(steps[i - 1], GenerationStepStatus.DONE)
                }
                _generationState.value = _generationState.value.copy(steps = updated)
            }

            delay(400)
            val finalSteps = _generationState.value.steps.map {
                GenerationStep(it.label, GenerationStepStatus.DONE)
            }
            _generationState.value = _generationState.value.copy(
                status = "rendering_snapshot",
                steps = finalSteps,
            )

            delay(600)
            val docId = "doc_${System.currentTimeMillis()}"
            _generationState.value = _generationState.value.copy(
                status = "done",
                documentId = docId,
                documentVersion = 1,
                snapshotUrl = "mock_snapshot_${docId}",
            )

            _currentDocument.value = DesignDocument(
                id = docId,
                projectId = "mock_project",
                version = 1,
                opFileUrl = null,
                localOpJson = null,
                snapshotUrl = "mock_snapshot_${docId}",
                thumbnailUrl = null,
                status = DesignDocumentStatus.READY,
            )
        }
    }

    fun onToggleMode() {
        _uiMode.value = when (_uiMode.value) {
            DesignMode.VIEW -> DesignMode.EDIT
            DesignMode.EDIT -> DesignMode.VIEW
        }
    }

    fun onNodeSelected(node: SelectedNode) {
        _selectedNode.value = node
    }

    fun onSelectionCleared() {
        _selectedNode.value = null
    }

    fun requestExport(target: ExportTarget) {
        _exportResult.value = ExportResult(
            exportId = "exp_${System.currentTimeMillis()}",
            files = listOf(
                ExportFile(
                    path = "OnboardingScreen.kt",
                    language = "kotlin",
                    content = """
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Welcome to RideTracker",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGetStarted) {
            Text("Get Started")
        }
    }
}
                    """.trimIndent(),
                ),
            ),
        )
    }

    fun clearExport() {
        _exportResult.value = null
    }

    fun resetDesignState() {
        _generationState.value = GenerationState()
        _currentDocument.value = null
        _selectedNode.value = null
        _exportResult.value = null
        _promptText.value = ""
        _uiMode.value = DesignMode.VIEW
    }
}
