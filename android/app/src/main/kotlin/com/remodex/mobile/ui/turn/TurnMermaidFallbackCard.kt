package com.remodex.mobile.ui.turn

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.remodex.mobile.R
import com.remodex.mobile.core.model.TurnTimelineCacheKey
import com.remodex.mobile.data.TurnMarkdownSegmentKind
import com.remodex.mobile.data.TurnTimelineRichContentCache
import kotlinx.coroutines.launch

@Composable
internal fun TurnRichMarkdownBody(
    markdown: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
    keyPrefix: String = TurnTimelineCacheKey.textKey("rich-markdown", markdown),
) {
    val segments = TurnTimelineRichContentCache.parseMermaidMarkdown(markdown)
    if (segments == null) {
        TurnMarkdownBody(
            markdown = markdown,
            contentColor = contentColor,
            modifier = modifier,
            keyPrefix = keyPrefix,
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            when (segment.kind) {
                TurnMarkdownSegmentKind.markdown -> {
                    if (segment.text.isNotBlank()) {
                        TurnMarkdownBody(
                            markdown = segment.text,
                            contentColor = contentColor,
                            modifier = Modifier.fillMaxWidth(),
                            keyPrefix = "$keyPrefix-md-$index",
                        )
                    }
                }
                TurnMarkdownSegmentKind.mermaid -> {
                    if (segment.text.isNotBlank()) {
                        TurnMermaidFallbackCard(
                            code = segment.text,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TurnMermaidFallbackCard(
    code: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val previewState = remember(code) { TurnMermaidWebViewState() }
    val darkMode = isSystemInDarkTheme()
    var showPreview by remember(code) { mutableStateOf(false) }

    DisposableEffect(previewState) {
        onDispose { previewState.dispose() }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(colors.surface.copy(alpha = 0.62f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.turn_timeline_mermaid_title),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { showPreview = true }) {
                    Text(text = stringResource(R.string.turn_mermaid_preview_action))
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipData.newPlainText("mermaid", code).toClipEntry())
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.turn_markdown_copy_code),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }
        if (previewState.shouldShowFallback) {
            TurnMermaidSourceFallbackCard(
                code = code,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TurnMermaidWebViewCard(
                code = code,
                darkMode = darkMode,
                state = previewState,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showPreview) {
        TurnMermaidPreviewDialog(
            code = code,
            darkMode = darkMode,
            onDismiss = { showPreview = false },
        )
    }
}

@Composable
private fun TurnMermaidPreviewDialog(
    code: String,
    darkMode: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val previewState = remember(code) { TurnMermaidWebViewState() }

    DisposableEffect(previewState) {
        onDispose { previewState.dispose() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.turn_mermaid_preview_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onBackground,
                    )
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.turn_preview_close))
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (previewState.shouldShowFallback) {
                        TurnMermaidSourceFallbackCard(
                            code = code,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        TurnMermaidWebViewCard(
                            code = code,
                            darkMode = darkMode,
                            state = previewState,
                            modifier = Modifier.fillMaxSize(),
                            fillsAvailableSpace = true,
                            enablesInteraction = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnMermaidSourceFallbackCard(
    code: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.turn_timeline_mermaid_fallback),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.82f),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(colors.surfaceVariant.copy(alpha = 0.38f))
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurface,
                softWrap = false,
            )
        }
    }
}
