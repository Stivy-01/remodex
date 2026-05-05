package com.remodex.mobile.ui.design.canvas

sealed class CanvasRenderState {
    data object Loading : CanvasRenderState()
    data class Ready(
        val imageUrl: String,
        val version: Int,
    ) : CanvasRenderState()
    data class Outdated(
        val imageUrl: String,
        val currentVersion: Int,
        val snapshotVersion: Int,
    ) : CanvasRenderState()
    data class Error(
        val message: String,
    ) : CanvasRenderState()

    val isReady: Boolean get() = this is Ready
    val isLoading: Boolean get() = this is Loading
    val isOutdated: Boolean get() = this is Outdated
    val isError: Boolean get() = this is Error
}
