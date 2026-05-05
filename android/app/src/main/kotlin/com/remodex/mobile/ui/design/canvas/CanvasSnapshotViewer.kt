package com.remodex.mobile.ui.design.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.composables.icons.lucide.R as LucideR

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 4f

@Composable
fun CanvasSnapshotViewer(
    state: CanvasRenderState,
    onRetry: (() -> Unit)? = null,
    onRefreshSnapshot: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is CanvasRenderState.Loading -> SnapshotLoadingState()
            is CanvasRenderState.Ready -> ZoomableSnapshot(
                imageUrl = state.imageUrl,
                version = state.version,
            )
            is CanvasRenderState.Outdated -> OutdatedSnapshot(
                imageUrl = state.imageUrl,
                currentVersion = state.currentVersion,
                snapshotVersion = state.snapshotVersion,
                onRefreshSnapshot = onRefreshSnapshot,
            )
            is CanvasRenderState.Error -> SnapshotErrorState(
                message = state.message,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun SnapshotLoadingState() {
    CircularProgressIndicator(
        modifier = Modifier.size(32.dp),
        strokeWidth = 2.dp,
    )
}

@Composable
private fun ZoomableSnapshot(
    imageUrl: String,
    version: Int,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = "Design preview v$version",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    val scaleFactor = newScale / scale

                    if (scaleFactor != 1f) {
                        val cx = centroid.x - (containerSize.width / 2f)
                        val cy = centroid.y - (containerSize.height / 2f)
                        offset =
                            Offset(
                                x = cx - (cx - offset.x) * scaleFactor,
                                y = cy - (cy - offset.y) * scaleFactor,
                            )
                    }

                    offset =
                        Offset(
                            x = offset.x + pan.x / scale,
                            y = offset.y + pan.y / scale,
                        )

                    scale = newScale
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        loading = {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
            )
        },
        error = {
            SnapshotErrorContent(
                icon = LucideR.drawable.lucide_ic_image_off,
                message = "Failed to load preview",
            )
        },
    )
}

@Composable
private fun OutdatedSnapshot(
    imageUrl: String,
    currentVersion: Int,
    snapshotVersion: Int,
    onRefreshSnapshot: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier.fillMaxSize()) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Design preview v$snapshotVersion",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        val scaleFactor = newScale / scale
                        if (scaleFactor != 1f) {
                            val cx = centroid.x - (containerSize.width / 2f)
                            val cy = centroid.y - (containerSize.height / 2f)
                            offset =
                                Offset(
                                    x = cx - (cx - offset.x) * scaleFactor,
                                    y = cy - (cy - offset.y) * scaleFactor,
                                )
                        }
                        offset =
                            Offset(
                                x = offset.x + pan.x / scale,
                                y = offset.y + pan.y / scale,
                            )
                        scale = newScale
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            loading = {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                )
            },
            error = {
                SnapshotErrorContent(
                    icon = LucideR.drawable.lucide_ic_image_off,
                    message = "Failed to load preview",
                )
            },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            SnapshotErrorContent(
                icon = LucideR.drawable.lucide_ic_refresh_cw,
                message = "Preview may be outdated",
                detail = "Document v$currentVersion, snapshot v$snapshotVersion",
            )
        }
    }
}

@Composable
private fun SnapshotErrorState(
    message: String,
    onRetry: (() -> Unit)?,
) {
    SnapshotErrorContent(
        icon = LucideR.drawable.lucide_ic_x,
        message = message,
    )
}

@Composable
private fun SnapshotErrorContent(
    icon: Int,
    message: String,
    detail: String? = null,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(40.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    androidx.compose.foundation.layout.Spacer(
        modifier = Modifier.size(8.dp),
    )
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (detail != null) {
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}
