package com.remodex.mobile.ui.turn

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import com.remodex.mobile.R
import kotlinx.coroutines.launch

private const val CODE_BLOCK_PREVIEW_MAX_LINES = 160
private const val CODE_BLOCK_PREVIEW_MAX_CHARS = 8_000

/**
 * J.5/J.20 — Markdown M3 + syntax highlight; code blocks with language header and copy action.
 */
@Composable
fun TurnMarkdownBody(
    markdown: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val displayMarkdown = TurnMarkdownRenderCache.visibleProse(markdown)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val segments = TurnMarkdownRenderCache.fenceSegments(displayMarkdown)
    val dispatchRepoDiff = LocalOpenRepoDiffForMarkdownLink.current
    val defaultUriHandler = LocalUriHandler.current
    val repoFileMarkdownUriHandler =
        remember(dispatchRepoDiff, defaultUriHandler) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    val trimmed = uri.trim()
                    if (RepoMarkdownFileLink.looksLikeLinkToLocalRepoFile(trimmed)) {
                        dispatchRepoDiff(trimmed)
                    } else {
                        runCatching { defaultUriHandler.openUri(trimmed) }
                    }
                }
            }
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownFenceSegment.Text -> {
                    val state = rememberMarkdownState(segment.markdown, retainState = true)
                    val bodyStyle = MaterialTheme.typography.bodyMedium
                    CompositionLocalProvider(LocalUriHandler provides repoFileMarkdownUriHandler) {
                        Markdown(
                            markdownState = state,
                            modifier = Modifier.fillMaxWidth(),
                            colors = markdownColor(text = contentColor),
                            typography =
                                markdownTypography(
                                    h1 =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 17.sp,
                                            lineHeight = 22.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    h2 =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 16.sp,
                                            lineHeight = 21.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    h3 =
                                        bodyStyle.copy(
                                            fontSize = 15.sp,
                                            lineHeight = 20.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    h4 = bodyStyle.copy(fontWeight = FontWeight.SemiBold),
                                    h5 = bodyStyle.copy(fontWeight = FontWeight.Medium),
                                    h6 = bodyStyle.copy(fontWeight = FontWeight.Medium),
                                    text = bodyStyle,
                                    paragraph = bodyStyle,
                                    ordered = bodyStyle,
                                    bullet = bodyStyle,
                                    list = bodyStyle,
                                    table = bodyStyle,
                                    code = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    inlineCode = bodyStyle.copy(fontFamily = FontFamily.Monospace),
                                    quote = bodyStyle,
                                    textLink =
                                        TextLinkStyles(
                                            style =
                                                bodyStyle
                                                    .copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        textDecoration = TextDecoration.Underline,
                                                    )
                                                    .toSpanStyle(),
                                        ),
                                ),
                            components =
                                markdownComponents(
                                    codeBlock = { model ->
                                        CodeBlockCard(
                                            raw = model.content.trimEnd(),
                                            language = null,
                                            onCopy = { text ->
                                                scope.launch {
                                                    clipboard.setClipEntry(ClipData.newPlainText("code", text).toClipEntry())
                                                }
                                            },
                                        )
                                    },
                                ),
                        )
                    }
                }
                is MarkdownFenceSegment.Code -> {
                    CodeBlockCard(
                        raw = segment.code,
                        language = segment.language,
                        onCopy = { text ->
                            scope.launch {
                                clipboard.setClipEntry(ClipData.newPlainText("code", text).toClipEntry())
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    raw: String,
    language: String?,
    onCopy: (String) -> Unit,
) {
    if (raw.isEmpty()) return
    var expanded by rememberSaveable(raw) { mutableStateOf(false) }
    val preview =
        remember(raw, expanded) {
            if (expanded) {
                raw
            } else {
                codeBlockPreview(raw)
            }
        }
    val isTruncated = preview.length < raw.length
    val colors = MaterialTheme.colorScheme
    val normalizedLanguage = language?.trim()?.ifBlank { null }
    val languageLabel = normalizedLanguage ?: stringResource(R.string.turn_markdown_code_unknown)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(colors.surface.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = languageLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            TextButton(onClick = { onCopy(raw) }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.turn_markdown_copy_code),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurface,
                softWrap = false,
            )
        }
        if (isTruncated) {
            TextButton(
                onClick = { expanded = true },
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .padding(end = 8.dp, bottom = 4.dp),
            ) {
                Text("Show full block")
            }
        }
    }
}

private fun codeBlockPreview(raw: String): String {
    if (raw.length <= CODE_BLOCK_PREVIEW_MAX_CHARS && raw.count { it == '\n' } < CODE_BLOCK_PREVIEW_MAX_LINES) {
        return raw
    }
    val lines = raw.lineSequence().take(CODE_BLOCK_PREVIEW_MAX_LINES).toList()
    val byLines = lines.joinToString("\n")
    val clipped =
        if (byLines.length > CODE_BLOCK_PREVIEW_MAX_CHARS) {
            byLines.take(CODE_BLOCK_PREVIEW_MAX_CHARS)
        } else {
            byLines
        }
    val hiddenLines = (raw.count { it == '\n' } + 1 - lines.size).coerceAtLeast(0)
    val hiddenSuffix =
        if (hiddenLines > 0) {
            "\n... $hiddenLines more lines hidden"
        } else {
            "\n... block truncated"
        }
    return clipped.trimEnd() + hiddenSuffix
}

internal sealed interface MarkdownFenceSegment {
    data class Text(val markdown: String) : MarkdownFenceSegment

    data class Code(
        val code: String,
        val language: String?,
    ) : MarkdownFenceSegment
}

internal object MarkdownFenceSegmentParser {
    fun parse(markdown: String): List<MarkdownFenceSegment> {
        if (markdown.isBlank()) return emptyList()

        val lines = markdown.split('\n')
        val segments = mutableListOf<MarkdownFenceSegment>()
        val textBuffer = mutableListOf<String>()
        val codeBuffer = mutableListOf<String>()
        var activeFence: MarkdownFence? = null

        fun flushText() {
            val text = textBuffer.joinToString("\n").trimEnd()
            if (text.isNotBlank()) {
                segments += MarkdownFenceSegment.Text(text)
            }
            textBuffer.clear()
        }

        fun flushCode(fence: MarkdownFence) {
            val code = codeBuffer.joinToString("\n").trimEnd()
            if (code.isNotBlank()) {
                segments += MarkdownFenceSegment.Code(code = code, language = fence.language)
            }
            codeBuffer.clear()
        }

        lines.forEach { line ->
            val trimmed = line.trimStart()
            val fence = activeFence
            if (fence == null) {
                val opening = parseOpeningFence(trimmed)
                if (opening == null) {
                    textBuffer += line
                } else {
                    flushText()
                    activeFence = opening
                }
            } else if (isClosingFence(trimmed, fence)) {
                flushCode(fence)
                activeFence = null
            } else {
                codeBuffer += line
            }
        }

        val danglingFence = activeFence
        if (danglingFence != null) {
            textBuffer += danglingFence.openingLine
            textBuffer += codeBuffer
            codeBuffer.clear()
        }
        flushText()

        return segments.ifEmpty { listOf(MarkdownFenceSegment.Text(markdown)) }
    }

    private data class MarkdownFence(
        val markerChar: Char,
        val markerLength: Int,
        val language: String?,
        val openingLine: String,
    )

    private fun parseOpeningFence(trimmedLine: String): MarkdownFence? {
        val markerChar =
            when {
                trimmedLine.startsWith("```") -> '`'
                trimmedLine.startsWith("~~~") -> '~'
                else -> return null
            }
        val markerLength = trimmedLine.takeWhile { it == markerChar }.length
        if (markerLength < 3) return null
        val trailing = trimmedLine.drop(markerLength).trim()
        if (markerChar in trailing) return null
        val language =
            trailing
                .substringBefore(' ')
                .trim()
                .takeIf { it.isNotEmpty() }
        return MarkdownFence(
            markerChar = markerChar,
            markerLength = markerLength,
            language = language,
            openingLine = trimmedLine,
        )
    }

    private fun isClosingFence(
        trimmedLine: String,
        fence: MarkdownFence,
    ): Boolean {
        if (!trimmedLine.startsWith(fence.markerChar.toString().repeat(fence.markerLength))) return false
        val markerRun = trimmedLine.takeWhile { it == fence.markerChar }
        return markerRun.length >= fence.markerLength && trimmedLine.drop(markerRun.length).isBlank()
    }
}
