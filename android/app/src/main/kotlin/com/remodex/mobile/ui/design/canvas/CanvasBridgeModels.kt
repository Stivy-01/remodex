package com.remodex.mobile.ui.design.canvas

import kotlinx.serialization.Serializable

@Serializable
data class CanvasDocumentPayload(
    val documentId: String,
    val version: Int,
    val opJson: String? = null,
    val opFileUrl: String? = null,
)

@Serializable
data class CanvasViewportPayload(
    val centerX: Float,
    val centerY: Float,
    val zoom: Float,
    val widthPx: Int,
    val heightPx: Int,
    val devicePixelRatio: Float,
)

@Serializable
data class CanvasPatchPayload(
    val id: String,
    val documentId: String,
    val type: String,
    val payload: kotlinx.serialization.json.JsonObject? = null,
    val versionFrom: Int,
    val versionTo: Int,
)

@Serializable
data class CanvasSnapshotRequest(
    val format: String = "webp",
    val quality: Float = 0.82f,
)

@Serializable
data class CanvasReadyEvent(
    val renderer: String,
    val version: String,
)

@Serializable
data class CanvasSnapshotReadyEvent(
    val documentId: String,
    val version: Int,
    val dataUrl: String,
)

@Serializable
data class CanvasNodeSelectedEvent(
    val id: String,
    val type: String,
    val name: String,
    val bounds: CanvasNodeBounds,
)

@Serializable
data class CanvasNodeBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

@Serializable
data class CanvasErrorEvent(
    val code: String,
    val message: String,
)

@Serializable
data class CanvasRenderStatsEvent(
    val fps: Float,
    val memoryMb: Float,
    val nodeCount: Int,
)
