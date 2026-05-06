package com.remodex.mobile.ui.turn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.data.TurnFileChangeEntryPresentation
import com.remodex.mobile.data.TurnFileChangePresentation
import com.remodex.mobile.data.TurnTimelineRichContentCache
import com.remodex.mobile.ui.shell.GitPatchHighlightedBlock
import com.remodex.mobile.ui.theme.RemodexGitAddition
import com.remodex.mobile.ui.theme.isAgentLightChrome

private const val ChevronCollapsedDegrees = 0f
private const val ChevronExpandedDegrees = 90f

/** Same width as [ToolExecutionCard] status icon so file rows and command rows share one column grid. */
internal val TurnTimelineLeadSlotWidth = 20.dp

/** Turquoise file marker: half the lead slot so it reads lighter than the command check icon. */
private val TurnTimelineFileDotSize = TurnTimelineLeadSlotWidth / 2

/**
 * Timeline file-diff summary styled like reference tool rows — compact swipe row + optional expand.
 * Multiple files each get their own accordion with that file’s unified diff fragment.
 */
@Composable
internal fun TurnFileChangeDetailCard(
    message: CodexMessage,
    modifier: Modifier = Modifier,
) {
    val preview = TurnTimelineRichContentCache.parseFileChange(message)
    val colors = MaterialTheme.colorScheme
    val summaryStyle =
        MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            lineHeight = 20.sp,
        )
    val fallbackSummaryLine =
        when {
            preview.summaryText.isNotBlank() -> preview.summaryText
            else -> stringResource(R.string.turn_timeline_diff_no_entries)
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            preview.entries.isEmpty() ->
                EmptyEntriesFileChange(
                    messageId = message.id,
                    preview = preview,
                    fallbackSummaryLine = fallbackSummaryLine,
                    summaryStyle = summaryStyle,
                )

            preview.fileCount > 1 ->
                MultipleFileChanges(
                    messageId = message.id,
                    preview = preview,
                )

            else ->
                SingleFileChange(
                    messageId = message.id,
                    preview = preview,
                    fallbackSummaryLine = fallbackSummaryLine,
                    summaryStyle = summaryStyle,
                )
        }
    }
}

/**
 * Legacy path: prose-only or unstructured text, no parsed file rows.
 */
@Composable
private fun EmptyEntriesFileChange(
    messageId: String,
    preview: TurnFileChangePresentation,
    fallbackSummaryLine: String,
    summaryStyle: androidx.compose.ui.text.TextStyle,
) {
    var expanded by rememberSaveable(messageId) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val expandedEnabled = preview.hasDetails || preview.rawText.isNotBlank()
    val rowChevronSize = 22.dp

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (expandedEnabled) {
                        Modifier.clickable { expanded = !expanded }
                    } else {
                        Modifier
                    },
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DotLeadAligned()
        Spacer(Modifier.width(8.dp))
        Text(
            text = fallbackSummaryLine,
            style = summaryStyle,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .widthIn(min = 0.dp),
        )
        if (expandedEnabled) {
            ChevronReveal(expanded = expanded, sizeDp = rowChevronSize)
        }
    }

    if (expanded && expandedEnabled) {
        FileChangeRawDetailBlock(preview = preview)
    }
}

/**
 * Exactly one logical file row; one accordion for diff / raw fallback.
 */
@Composable
private fun SingleFileChange(
    messageId: String,
    preview: TurnFileChangePresentation,
    fallbackSummaryLine: String,
    summaryStyle: androidx.compose.ui.text.TextStyle,
) {
    var expanded by rememberSaveable(messageId) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val expandedEnabled = preview.hasDetails || preview.rawText.isNotBlank()
    val rowChevronSize = 22.dp
    val entry = preview.entries.single()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (expandedEnabled) {
                        Modifier.clickable { expanded = !expanded }
                    } else {
                        Modifier
                    },
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DotLeadAligned()
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fileNameFromPath(entry.path),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                FileChangeCountOrLabel(
                    entry = entry,
                    likelyHasDiff = preview.likelyHasDiff,
                )
            }
        }
        if (expandedEnabled) {
            ChevronReveal(expanded = expanded, sizeDp = rowChevronSize)
        }
    }

    if (expanded && expandedEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val singlePatch = entry.patchChunkText?.takeIf { it.isNotBlank() }
                ?: preview.rawPatchText?.takeIf { it.isNotBlank() }
            if (singlePatch != null) {
                entry.label?.takeIf { it.isNotBlank() }?.let { label ->
                    Text(
                        text = label,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                lineHeight = 18.sp,
                            ),
                        color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }
                FileChangeHighlightedPatch(patchText = singlePatch)
            } else {
                RawTextFallback(
                    text =
                        fallbackSummaryLine.ifBlank {
                            preview.rawText
                        },
                    monospace = preview.rawPatchText == null,
                )
            }
        }
    }
}

/**
 * Independent dropdown per entry; diff is only the fragment for that file.
 */
@Composable
private fun MultipleFileChanges(
    messageId: String,
    preview: TurnFileChangePresentation,
) {
    val colors = MaterialTheme.colorScheme
    val hideDetailsCd = stringResource(R.string.turn_timeline_hide_details)
    val showDetailsCd = stringResource(R.string.turn_timeline_show_details)
    var expandedSlots by remember(messageId) { mutableStateOf(emptySet<Int>()) }
    val rowChevronSize = 22.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        preview.entries.forEachIndexed { index, entry ->
            key(entry.path, index) {
            val patchChunk = entry.patchChunkText?.takeIf { it.isNotBlank() }
            val combinedFencePatch = preview.rawPatchText?.takeIf { it.isNotBlank() }
            val expandable = patchChunk != null || combinedFencePatch != null
            val expanded = index in expandedSlots
            val accordionCd =
                "${fileNameFromPath(entry.path)}, " +
                    if (expanded) hideDetailsCd else showDetailsCd

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(MaterialTheme.shapes.small)
                            .then(
                                if (expandable) {
                                    Modifier
                                        .semantics {
                                            contentDescription = accordionCd
                                            role = Role.Button
                                        }
                                        .clickable {
                                            expandedSlots =
                                                if (expanded) {
                                                    expandedSlots - index
                                                } else {
                                                    expandedSlots + index
                                                }
                                        }
                                } else {
                                    Modifier
                                },
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DotLeadAligned()
                    Spacer(Modifier.width(8.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = fileNameFromPath(entry.path),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            FileChangeCountOrLabel(
                                entry = entry,
                                likelyHasDiff = preview.likelyHasDiff,
                            )
                        }
                        entry.label?.takeIf { it.isNotBlank() }?.let { label ->
                            Text(
                                text = label,
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        lineHeight = 18.sp,
                                    ),
                                color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (expandable) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription =
                                if (expanded) {
                                    stringResource(R.string.turn_timeline_hide_details)
                                } else {
                                    stringResource(R.string.turn_timeline_show_details)
                                },
                            tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier =
                                Modifier
                                    .padding(start = 6.dp)
                                    .size(rowChevronSize)
                                    .rotate(
                                        if (expanded) {
                                            ChevronExpandedDegrees
                                        } else {
                                            ChevronCollapsedDegrees
                                        },
                                    ),
                        )
                    }
                }

                if (expanded) {
                    Spacer(Modifier.height(6.dp))
                    when {
                        patchChunk != null -> FileChangeHighlightedPatch(patchText = patchChunk)
                        combinedFencePatch != null -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.turn_timeline_file_change_full_patch_fallback),
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            lineHeight = 18.sp,
                                        ),
                                    color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                                )
                                FileChangeHighlightedPatch(patchText = combinedFencePatch)
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun DotLeadAligned() {
    Box(
        modifier = Modifier.width(TurnTimelineLeadSlotWidth),
        contentAlignment = Alignment.Center,
    ) {
        DotLead()
    }
}

@Composable
private fun DotLead() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .size(TurnTimelineFileDotSize)
                .clip(CircleShape)
                .background(colors.secondary),
    )
}

@Composable
private fun ChevronReveal(
    expanded: Boolean,
    sizeDp: androidx.compose.ui.unit.Dp,
) {
    val colors = MaterialTheme.colorScheme
    Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription =
            if (expanded) {
                stringResource(R.string.turn_timeline_hide_details)
            } else {
                stringResource(R.string.turn_timeline_show_details)
            },
        tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
        modifier =
            Modifier
                .padding(start = 6.dp)
                .size(sizeDp)
                .rotate(
                    if (expanded) ChevronExpandedDegrees else ChevronCollapsedDegrees,
                ),
    )
}

@Composable
private fun FileChangeHighlightedPatch(patchText: String) {
    val colors = MaterialTheme.colorScheme
    val chassisAlpha =
        if (isAgentLightChrome()) {
            0.12f
        } else {
            0.26f
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.surfaceVariant.copy(alpha = chassisAlpha)),
    ) {
        GitPatchHighlightedBlock(
            patch = patchText,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
        )
    }
}

@Composable
private fun FileChangeRawDetailBlock(preview: TurnFileChangePresentation) {
    val detailsText = preview.rawPatchText?.takeIf { it.isNotBlank() } ?: preview.rawText
    if (detailsText.isBlank()) return
    val colors = MaterialTheme.colorScheme
    if (preview.rawPatchText != null) {
        FileChangeHighlightedPatch(patchText = detailsText)
    } else {
        RawTextFallback(text = detailsText, monospace = true)
    }
}

@Composable
private fun RawTextFallback(
    text: String,
    monospace: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.surfaceVariant.copy(alpha = 0.26f))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            color = colors.onSurface,
            softWrap = false,
        )
    }
}

private fun fileNameFromPath(path: String): String =
    path
        .replace('\\', '/')
        .substringAfterLast('/')
        .ifBlank { path }

@Composable
private fun FileChangeCountOrLabel(
    entry: TurnFileChangeEntryPresentation,
    likelyHasDiff: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val showCounts = entry.additions > 0 || entry.deletions > 0
    when {
        showCounts -> {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "+${entry.additions}",
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            lineHeight = 18.sp,
                        ),
                    color = RemodexGitAddition,
                )
                Text(
                    text = "-${entry.deletions}",
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            lineHeight = 18.sp,
                        ),
                    color = colors.error,
                )
            }
        }
        !likelyHasDiff -> {
            val status =
                entry.label?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.turn_timeline_file_change_changed)
            Text(
                text = status,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 18.sp,
                    ),
                color = colors.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
        else -> Unit
    }
}
