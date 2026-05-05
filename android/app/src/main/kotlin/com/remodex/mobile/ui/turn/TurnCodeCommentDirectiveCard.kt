package com.remodex.mobile.ui.turn

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.core.model.TurnCodeCommentDirectiveFinding
import com.remodex.mobile.core.model.TurnCodeCommentDirectiveFormatter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun TurnCodeCommentDirectiveCards(
    findings: List<TurnCodeCommentDirectiveFinding>,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    if (findings.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        findings.forEach { finding ->
            TurnCodeCommentDirectiveCard(
                finding = finding,
                contentColor = contentColor,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TurnCodeCommentDirectiveCard(
    finding: TurnCodeCommentDirectiveFinding,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val severity = (finding.priority ?: 3).coerceIn(0, 3)
    val severityColors =
        when (severity) {
            0 -> colors.errorContainer to colors.onErrorContainer
            1 -> colors.tertiaryContainer to colors.onTertiaryContainer
            2 -> colors.secondaryContainer to colors.onSecondaryContainer
            else -> colors.primaryContainer to colors.onPrimaryContainer
        }
    val lineLabel = lineLabel(finding)
    val confidenceLabel =
        finding.confidence
            ?.coerceIn(0.0, 1.0)
            ?.let { "${(it * 100).roundToInt()}%" }
    val meta =
        listOfNotNull(
            compactFileName(finding.file),
            lineLabel,
            confidenceLabel,
        ).joinToString(" | ")
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(colors.surfaceVariant.copy(alpha = 0.36f))
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.48f), MaterialTheme.shapes.small)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.turn_code_comment_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier =
                        Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(colors.surface.copy(alpha = 0.74f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                )
                Text(
                    text = "P$severity",
                    style = MaterialTheme.typography.labelSmall,
                    color = severityColors.second,
                    fontWeight = FontWeight.SemiBold,
                    modifier =
                        Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(severityColors.first)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            TextButton(
                onClick = {
                    val directive = TurnCodeCommentDirectiveFormatter.format(finding)
                    scope.launch {
                        clipboard.setClipEntry(ClipData.newPlainText("code-comment", directive).toClipEntry())
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.turn_code_comment_copy),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        Text(
            text = finding.title,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
        )
        TurnMarkdownBody(
            markdown = finding.body,
            contentColor = contentColor.copy(alpha = 0.92f),
            modifier = Modifier.fillMaxWidth(),
        )
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.76f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
    }
}

private fun compactFileName(path: String): String =
    path
        .replace('\\', '/')
        .substringAfterLast('/')
        .ifBlank { path }

private fun lineLabel(finding: TurnCodeCommentDirectiveFinding): String? {
    val start = finding.startLine ?: return null
    val end = finding.endLine
    return if (end != null && end != start) {
        "L$start-L$end"
    } else {
        "L$start"
    }
}
