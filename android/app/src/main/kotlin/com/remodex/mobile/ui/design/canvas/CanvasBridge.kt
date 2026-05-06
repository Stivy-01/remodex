package com.remodex.mobile.ui.design.canvas

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.remodex.mobile.BuildConfig
import com.remodex.mobile.ui.design.SelectedNode
import kotlinx.serialization.json.Json

class CanvasBridge(
    private val onCanvasReady: () -> Unit,
    private val onSnapshotReady: (documentId: String, version: Int, dataUrl: String) -> Unit,
    private val onNodeSelected: (node: SelectedNode) -> Unit,
    private val onSelectionCleared: () -> Unit,
    private val onCanvasError: (code: String, message: String) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var webView: WebView? = null

    fun attach(webView: WebView) {
        this.webView = webView
    }

    fun detach() {
        this.webView = null
    }

    private fun eval(js: String) {
        webView?.evaluateJavascript("javascript:$js", null)
    }

    fun loadDocument(payload: String) = eval(
        "window.OpenPencilMobile && window.OpenPencilMobile.loadDocument($payload)",
    )

    fun applyPatch(patch: String) = eval(
        "window.OpenPencilMobile && window.OpenPencilMobile.applyPatch($patch)",
    )

    fun setViewport(viewport: String) = eval(
        "window.OpenPencilMobile && window.OpenPencilMobile.setViewport($viewport)",
    )

    fun setRenderQuality(level: String) = eval(
        "window.OpenPencilMobile && window.OpenPencilMobile.setRenderQuality('$level')",
    )

    fun selectNode(nodeId: String) = eval(
        "window.OpenPencilMobile && window.OpenPencilMobile.selectNode('$nodeId')",
    )

    fun clearSelection() = eval(
        "window.OpenPencilMobile && window.OpenPencilMobile.clearSelection()",
    )

    fun requestSnapshot() = eval(
        "window.OpenPencilMobile && window.OpenPencilMobile.requestSnapshot({format:'webp',quality:0.82})",
    )

    fun disposeCanvas() = eval(
        "window.OpenPencilMobile && window.OpenPencilMobile.disposeCanvas()",
    )

    @JavascriptInterface
    fun onCanvasReady(jsonStr: String) {
        log { "onCanvasReady: $jsonStr" }
        onCanvasReady()
    }

    @JavascriptInterface
    fun onDocumentLoaded(jsonStr: String) {
        log { "onDocumentLoaded: $jsonStr" }
    }

    @JavascriptInterface
    fun onPatchApplied(jsonStr: String) {
        log { "onPatchApplied: $jsonStr" }
    }

    @JavascriptInterface
    fun onNodeSelected(jsonStr: String) {
        log { "onNodeSelected: $jsonStr" }
        runCatching {
            val data = json.decodeFromString<NodeSelectedJson>(jsonStr)
            onNodeSelected(
                SelectedNode(
                    id = data.id,
                    type = data.type,
                    name = data.name,
                    boundsX = data.bounds.x,
                    boundsY = data.bounds.y,
                    boundsWidth = data.bounds.width,
                    boundsHeight = data.bounds.height,
                ),
            )
        }
    }

    @JavascriptInterface
    fun onSelectionCleared() {
        log { "onSelectionCleared" }
        onSelectionCleared()
    }

    @JavascriptInterface
    fun onSnapshotReady(jsonStr: String) {
        log { "onSnapshotReady: $jsonStr" }
        runCatching {
            val data = json.decodeFromString<SnapshotReadyJson>(jsonStr)
            onSnapshotReady(data.documentId, data.version, data.dataUrl)
        }
    }

    @JavascriptInterface
    fun onRenderStats(jsonStr: String) {
        log { "onRenderStats: $jsonStr" }
    }

    @JavascriptInterface
    fun onCanvasError(jsonStr: String) {
        log { "onCanvasError: $jsonStr" }
        runCatching {
            val data = json.decodeFromString<CanvasErrorJson>(jsonStr)
            onCanvasError(data.code, data.message)
        }
    }

    @JavascriptInterface
    fun onMemoryWarning(jsonStr: String) {
        log { "onMemoryWarning: $jsonStr" }
    }

    private companion object {
        fun log(msg: () -> String) {
            if (BuildConfig.DEBUG) {
                Log.d("CanvasBridge", msg())
            }
        }
    }
}

private data class NodeSelectedJson(
    val id: String,
    val type: String,
    val name: String,
    val bounds: BoundsJson,
)

private data class BoundsJson(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

private data class SnapshotReadyJson(
    val documentId: String,
    val version: Int,
    val dataUrl: String,
)

private data class CanvasErrorJson(
    val code: String,
    val message: String,
)
