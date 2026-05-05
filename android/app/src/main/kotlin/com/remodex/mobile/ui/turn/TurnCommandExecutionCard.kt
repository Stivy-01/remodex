package com.remodex.mobile.ui.turn

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CommandExecutionDetails
import com.remodex.mobile.data.TurnCommandExecutionPresentation
import com.remodex.mobile.data.TurnCommandExecutionPreviewMerge
import com.remodex.mobile.data.TurnTimelineRichContentCache
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TurnCommandExecutionCard(
    message: CodexMessage,
    @Suppress("UNUSED_PARAMETER") contentColor: Color,
    details: CommandExecutionDetails? = null,
    modifier: Modifier = Modifier,
) {
    val parsed = TurnTimelineRichContentCache.parseCommandExecution(message)
    val preview = TurnCommandExecutionPreviewMerge.merge(parsed, details)
    var showDetailSheet by rememberSaveable(message.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val ui =
        remember(preview, details) {
            TurnMarkdownRenderCache.toolExecutionUi(preview, details, context.resources)
        }
    val accent =
        when {
            preview.isFailure -> colors.error
            preview.isRunning -> colors.onSecondaryContainer
            else -> colors.onPrimaryContainer
        }
    val onOpenDetails: (() -> Unit)? =
        if (ui.isExpandable) {
            { showDetailSheet = true }
        } else {
            null
        }

    ToolExecutionCard(
        ui = ui,
        onClick = onOpenDetails,
        modifier = modifier.fillMaxWidth(),
    )

    if (showDetailSheet) {
        CommandExecutionDetailSheet(
            preview = preview,
            phaseLabel = ui.title,
            commandPreview = ui.commandPreview,
            accent = accent,
            onDismiss = { showDetailSheet = false },
        )
    }
}

@Composable
private fun CommandOutputBlock(output: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.surfaceVariant.copy(alpha = 0.34f))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = output,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface,
            fontFamily = FontFamily.Monospace,
            softWrap = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandExecutionDetailSheet(
    preview: TurnCommandExecutionPresentation,
    phaseLabel: String,
    commandPreview: String?,
    accent: Color,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val fullCommand = preview.command.trim()
    val previewTrimmed = commandPreview?.trim().orEmpty()
    val showPreviewSection =
        previewTrimmed.isNotEmpty() &&
            !previewTrimmed.equals(fullCommand, ignoreCase = false)

    fun copy(label: String, value: String) {
        scope.launch {
            clipboard.setClipEntry(ClipData.newPlainText(label, value).toClipEntry())
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.turn_command_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (showPreviewSection) {
                DetailSection(
                    title = stringResource(R.string.turn_command_sheet_section_command_preview),
                    actionLabel = stringResource(R.string.turn_command_sheet_copy),
                    onAction = { copy("command-preview", previewTrimmed) },
                ) {
                    CommandOutputBlock(output = previewTrimmed)
                }
            }
            DetailSection(
                title = stringResource(R.string.turn_command_sheet_section_command),
                actionLabel = stringResource(R.string.turn_command_sheet_copy),
                onAction = { copy("command", fullCommand) },
            ) {
                CommandOutputBlock(output = fullCommand)
            }
            DetailSection(title = stringResource(R.string.turn_command_sheet_section_metadata)) {
                MetadataRow(
                    label = stringResource(R.string.turn_command_sheet_label_status),
                    value = phaseLabel,
                    valueColor = accent,
                )
                preview.cwd?.takeIf { it.isNotBlank() }?.let {
                    MetadataRow(
                        label = stringResource(R.string.turn_command_sheet_label_directory),
                        value = it,
                    )
                }
                preview.exitCode?.let {
                    MetadataRow(
                        label = stringResource(R.string.turn_command_sheet_label_exit_code),
                        value = it.toString(),
                        valueColor =
                            if (it == 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                    )
                }
                preview.durationMs?.let {
                    MetadataRow(
                        label = stringResource(R.string.turn_command_sheet_label_duration),
                        value = formatDuration(it),
                    )
                }
            }
            preview.outputText?.takeIf { it.isNotBlank() }?.let { output ->
                DetailSection(
                    title = stringResource(R.string.turn_command_sheet_section_output),
                    actionLabel = stringResource(R.string.turn_command_sheet_copy),
                    onAction = { copy("command-output", output) },
                ) {
                    CommandOutputBlock(output = output)
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
        content()
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.55f),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

private fun formatDuration(ms: Int): String {
    if (ms < 1000) return "${ms}ms"
    val seconds = ms / 1000.0
    if (seconds < 60) return "${"%.1f".format(seconds)}s"
    val minutes = ms / 60_000
    val remainingSeconds = (ms / 1000) % 60
    return "${minutes}m ${remainingSeconds}s"
}
