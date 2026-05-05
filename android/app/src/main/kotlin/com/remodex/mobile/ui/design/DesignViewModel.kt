package com.remodex.mobile.ui.design

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remodex.mobile.BuildConfig
import com.remodex.mobile.ui.design.canvas.CanvasBridge
import com.remodex.mobile.ui.design.canvas.CanvasRenderState
import com.remodex.mobile.ui.design.data.DesignRepository
import com.remodex.mobile.ui.design.data.MockDesignRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DesignViewModel(
    private val repository: DesignRepository = MockDesignRepository(),
) : ViewModel() {

    private val _uiMode = MutableStateFlow(DesignMode.VIEW)
    val uiMode: StateFlow<DesignMode> = _uiMode.asStateFlow()

    private val _generationState = MutableStateFlow(GenerationState())
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _currentDocument = MutableStateFlow<DesignDocument?>(null)
    val currentDocument: StateFlow<DesignDocument?> = _currentDocument.asStateFlow()

    private val _snapshotVersion = MutableStateFlow(0)
    val snapshotVersion: StateFlow<Int> = _snapshotVersion.asStateFlow()

    val snapshotRenderState: StateFlow<CanvasRenderState> = combine(
        _currentDocument,
        _snapshotVersion,
        _generationState,
    ) { doc, snapVer, gen ->
        when {
            gen.status in listOf("generating", "rendering_snapshot") -> CanvasRenderState.Loading
            doc == null -> CanvasRenderState.Loading
            gen.status == "done" && doc.snapshotUrl != null -> {
                val docVer = doc.version
                if (snapVer < docVer) {
                    CanvasRenderState.Outdated(
                        imageUrl = doc.snapshotUrl,
                        currentVersion = docVer,
                        snapshotVersion = snapVer,
                    )
                } else {
                    CanvasRenderState.Ready(
                        imageUrl = doc.snapshotUrl,
                        version = docVer,
                    )
                }
            }
            else -> CanvasRenderState.Error("No design generated yet")
        }
    }.let { flow ->
        val mutable = MutableStateFlow<CanvasRenderState>(CanvasRenderState.Loading)
        viewModelScope.launch { flow.collect { mutable.value = it } }
        mutable.asStateFlow()
    }

    private val _selectedNode = MutableStateFlow<SelectedNode?>(null)
    val selectedNode: StateFlow<SelectedNode?> = _selectedNode.asStateFlow()

    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    private val _promptText = MutableStateFlow("")
    val promptText: StateFlow<String> = _promptText.asStateFlow()

    val canvasBridge = CanvasBridge(
        onCanvasReady = { /* WebView initialized */ },
        onSnapshotReady = { documentId, version, dataUrl ->
            _snapshotVersion.value = version
        },
        onNodeSelected = { node -> _selectedNode.value = node },
        onSelectionCleared = { _selectedNode.value = null },
        onCanvasError = { code, message ->
            if (BuildConfig.DEBUG) {
                android.util.Log.e("DesignVM", "Canvas error [$code]: $message")
            }
        },
    )

    fun onPromptTextChanged(text: String) {
        _promptText.value = text
    }

    fun onSubmitPrompt() {
        val prompt = _promptText.value.trim()
        if (prompt.isBlank()) return

        _currentDocument.value = null
        _snapshotVersion.value = 0

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
            val stepLabels = listOf(
                "Understanding prompt",
                "Creating layout",
                "Adding components",
                "Styling screen",
                "Rendering preview",
                "Creating snapshot",
            )

            for (i in stepLabels.indices) {
                delay(800)
                val updated = _generationState.value.steps.toMutableList()
                updated[i] = GenerationStep(stepLabels[i], GenerationStepStatus.ACTIVE)
                if (i > 0) {
                    updated[i - 1] = GenerationStep(stepLabels[i - 1], GenerationStepStatus.DONE)
                }
                _generationState.value = _generationState.value.copy(steps = updated)
            }

            delay(400)
            _generationState.value = _generationState.value.copy(
                status = "rendering_snapshot",
                steps = _generationState.value.steps.map {
                    GenerationStep(it.label, GenerationStepStatus.DONE)
                },
            )

            val result = runCatching {
                repository.generateDesign(
                    projectId = "mock_project",
                    prompt = prompt,
                    target = null,
                )
            }

            result.fold(
                onSuccess = { genState ->
                    _generationState.value = genState
                    if (genState.documentId != null) {
                        _currentDocument.value = DesignDocument(
                            id = genState.documentId,
                            projectId = "mock_project",
                            version = genState.documentVersion,
                            opFileUrl = null,
                            localOpJson = null,
                            snapshotUrl = genState.snapshotUrl,
                            thumbnailUrl = null,
                            status = DesignDocumentStatus.READY,
                        )
                        _snapshotVersion.value = genState.documentVersion
                    }
                },
                onFailure = { error ->
                    _generationState.value = _generationState.value.copy(
                        status = "error",
                        steps = _generationState.value.steps.map {
                            if (it.status == GenerationStepStatus.ACTIVE) {
                                GenerationStep(it.label, GenerationStepStatus.ERROR)
                            } else {
                                it
                            }
                        },
                    )
                },
            )
        }
    }

    fun editDesignWithAi(prompt: String, selectedNodeId: String?) {
        if (prompt.isBlank() && selectedNodeId == null) return
        val docId = _currentDocument.value?.id ?: return

        viewModelScope.launch {
            _generationState.value = GenerationState(
                generationId = "edit_${System.currentTimeMillis()}",
                status = "generating",
                steps = listOf(
                    GenerationStep("Applying edit", GenerationStepStatus.ACTIVE),
                    GenerationStep("Rendering preview", GenerationStepStatus.PENDING),
                ),
            )

            delay(800)

            val result = runCatching {
                repository.editDocument(docId, prompt, selectedNodeId)
            }

            result.fold(
                onSuccess = { genState ->
                    _generationState.value = genState
                    val current = _currentDocument.value
                    if (current != null && genState.snapshotUrl != null) {
                        _currentDocument.value = current.copy(
                            version = genState.documentVersion,
                            snapshotUrl = genState.snapshotUrl,
                        )
                    }
                },
                onFailure = {
                    _generationState.value = _generationState.value.copy(status = "error")
                },
            )
        }
    }

    fun refreshSnapshot() {
        val doc = _currentDocument.value ?: return
        _snapshotVersion.value = doc.version
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
        val docId = _currentDocument.value?.id ?: return
        viewModelScope.launch {
            runCatching {
                repository.exportDocument(docId, target)
            }.onSuccess {
                _exportResult.value = it
            }
        }
    }

    fun clearExport() {
        _exportResult.value = null
    }

    fun resetDesignState() {
        _generationState.value = GenerationState()
        _currentDocument.value = null
        _snapshotVersion.value = 0
        _selectedNode.value = null
        _exportResult.value = null
        _promptText.value = ""
        _uiMode.value = DesignMode.VIEW
    }
}
