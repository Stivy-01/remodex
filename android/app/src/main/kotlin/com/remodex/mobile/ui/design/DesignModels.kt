package com.remodex.mobile.ui.design

data class DesignProject(
    val id: String,
    val remodexProjectId: String,
    val name: String,
    val currentDocumentId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSnapshotUrl: String?,
    val mode: DesignMode,
)

data class DesignDocument(
    val id: String,
    val projectId: String,
    val version: Int,
    val opFileUrl: String?,
    val localOpJson: String?,
    val snapshotUrl: String?,
    val thumbnailUrl: String?,
    val status: DesignDocumentStatus,
)

enum class DesignMode {
    VIEW,
    EDIT,
}

enum class DesignDocumentStatus {
    EMPTY,
    GENERATING,
    READY,
    ERROR,
    OUTDATED_SNAPSHOT,
}

data class CanvasViewport(
    val centerX: Float,
    val centerY: Float,
    val zoom: Float,
    val widthPx: Int,
    val heightPx: Int,
    val devicePixelRatio: Float,
)

data class CanvasPatch(
    val id: String,
    val documentId: String,
    val versionFrom: Int,
    val versionTo: Int,
    val type: String,
    val payloadJson: String,
    val author: PatchAuthor,
    val createdAt: Long,
)

enum class PatchAuthor {
    AI,
    USER,
    SYSTEM,
}

data class ManualOverlayEdit(
    val id: String,
    val documentId: String,
    val affectedNodeIds: List<String>,
    val operation: String,
    val payloadJson: String,
    val committed: Boolean,
    val createdAt: Long,
)

data class GenerationStep(
    val label: String,
    val status: GenerationStepStatus,
)

enum class GenerationStepStatus {
    PENDING,
    ACTIVE,
    DONE,
    ERROR,
}

data class GenerationState(
    val generationId: String? = null,
    val status: String = "idle",
    val steps: List<GenerationStep> = emptyList(),
    val documentId: String? = null,
    val documentVersion: Int = 0,
    val snapshotUrl: String? = null,
)

data class ExportFile(
    val path: String,
    val language: String,
    val content: String,
)

data class ExportResult(
    val exportId: String,
    val files: List<ExportFile>,
)

enum class ExportTarget(
    val label: String,
) {
    JETPACK_COMPOSE("Jetpack Compose"),
    REACT_NATIVE("React Native"),
    FLUTTER("Flutter"),
    REACT_TAILWIND("React + Tailwind"),
    HTML_CSS("HTML + CSS"),
}

data class SelectedNode(
    val id: String,
    val type: String,
    val name: String,
    val boundsX: Float,
    val boundsY: Float,
    val boundsWidth: Float,
    val boundsHeight: Float,
)
