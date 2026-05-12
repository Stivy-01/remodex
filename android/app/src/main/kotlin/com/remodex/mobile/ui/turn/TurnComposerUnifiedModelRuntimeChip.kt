package com.remodex.mobile.ui.turn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import com.remodex.mobile.R
import com.remodex.mobile.ui.theme.RemodexDropdownMenu
import com.remodex.mobile.ui.theme.RemodexPopupChrome
import com.remodex.mobile.ui.theme.isAgentLightChrome

/** Compact footnote / unified chip icon size. */
internal val ComposerFootnoteIconDp = 13.dp

internal fun compactModelStickerForUnifiedChip(option: TurnComposerRuntimeOption): String {
    val t = option.label.trim()
    val lowered = t.lowercase()
    if (lowered.startsWith("gpt-")) {
        val rest = t.drop(4).trimStart()
        return rest.substringBefore(' ').substringBefore('|').takeIf { it.isNotBlank() } ?: t
    }
    return if (t.length <= 16) {
        t
    } else {
        t.take(14) + "…"
    }
}

internal fun reasoningOrSpeedCompactCaption(runtimeControls: TurnComposerRuntimeControlsState): String =
    when {
        runtimeControls.reasoningEffort.enabled -> runtimeControls.reasoningEffort.selected.label
        else -> runtimeControls.serviceTier.selected.label
    }.trim()

internal fun unifiedModelRuntimeChipLabel(runtimeControls: TurnComposerRuntimeControlsState): String {
    val m = compactModelStickerForUnifiedChip(runtimeControls.model.selected)
    val r = reasoningOrSpeedCompactCaption(runtimeControls)
    return if (r.isBlank()) m else "$m $r"
}

/** Single pill + menu (Codex desktop: model caption + reasoning + speed). */
@Composable
internal fun UnifiedComposerModelRuntimeChip(
    runtimeControls: TurnComposerRuntimeControlsState,
    runtimeToolbarMenu: TurnComposerToolbarMenu,
    enabled: Boolean,
    onSelectModel: (TurnComposerRuntimeOption) -> Unit,
    onSelectReasoningEffort: (TurnComposerRuntimeOption) -> Unit,
    onSelectServiceTier: (TurnComposerRuntimeOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    val modelState = runtimeControls.model
    val modelOptions =
        remember(modelState.selected, modelState.options) {
            if (modelState.options.any { it.id == modelState.selected.id }) {
                modelState.options
            } else {
                listOf(modelState.selected) + modelState.options
            }
        }

    val toolbarHasSelectable =
        runtimeToolbarMenu.sections.any { section -> section.items.any { it.enabled } }

    val hasModelChoice = modelOptions.any { it.enabled }

    val canOpenMenu =
        enabled &&
            (hasModelChoice || toolbarHasSelectable)

    val firstSectionIsReasoning =
        runtimeToolbarMenu.sections.firstOrNull()?.iconKind ==
            TurnComposerToolbarIconKind.ReasoningSection

    fun applyToolbarAction(action: TurnComposerToolbarActionKey) {
        when (action.kind) {
            TurnComposerToolbarActionKey.Kind.ReasoningEffort ->
                runtimeControls.reasoningEffort.options
                    .firstOrNull { it.id == action.id }
                    ?.let(onSelectReasoningEffort)

            TurnComposerToolbarActionKey.Kind.ServiceTierSpeed ->
                runtimeControls.serviceTier.options
                    .firstOrNull { it.id == action.id }
                    ?.let(onSelectServiceTier)
        }
    }

    Box {
        val lightChrome = isAgentLightChrome()
        val showFastModeIcon = runtimeControls.serviceTier.selected.id != TURN_COMPOSER_RUNTIME_AUTO_ID
        Surface(
            modifier =
                modifier
                    .wrapContentWidth()
                    .widthIn(max = 136.dp)
                    .border(RemodexPopupChrome.borderStroke(), RoundedCornerShape(999.dp))
                    .clickable(enabled = canOpenMenu) { expanded = true },
            shape = RoundedCornerShape(999.dp),
            color = RemodexPopupChrome.surfaceColor().copy(alpha = if (lightChrome) 0.92f else 0.72f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (showFastModeIcon) {
                        Icon(
                            painter = painterResource(LucideR.drawable.lucide_ic_zap),
                            contentDescription = null,
                            modifier = Modifier.size(ComposerFootnoteIconDp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = unifiedModelRuntimeChipLabel(runtimeControls),
                        modifier = Modifier.widthIn(max = 94.dp),
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_chevron_down),
                    contentDescription = null,
                    modifier = Modifier.size(ComposerFootnoteIconDp),
                )
            }
        }

        RemodexDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val sections = runtimeToolbarMenu.sections

            if (firstSectionIsReasoning && sections.isNotEmpty()) {
                TurnComposerToolbarMenuSectionBlock(
                    section = sections.first(),
                    onPick = {
                        expanded = false
                        applyToolbarAction(it)
                    },
                )
                HorizontalDivider()
            }

            val modelHeading = stringResource(R.string.turn_runtime_model_label)
            DropdownMenuItem(
                text = {
                    Text(
                        text = modelHeading,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = {},
                enabled = false,
            )
            modelOptions.forEach { option ->
                val selected = option.id == modelState.selected.id
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = option.label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectModel(option)
                    },
                    enabled = enabled && option.enabled,
                )
            }

            val startIdx = if (firstSectionIsReasoning) 1 else 0
            val tailSections = sections.drop(startIdx)
            if (tailSections.isNotEmpty()) {
                HorizontalDivider()
                tailSections.forEachIndexed { ix, sec ->
                    if (ix > 0) {
                        HorizontalDivider()
                    }
                    TurnComposerToolbarMenuSectionBlock(
                        section = sec,
                        onPick = {
                            expanded = false
                            applyToolbarAction(it)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TurnComposerToolbarMenuSectionBlock(
    section: TurnComposerToolbarMenuSection,
    onPick: (TurnComposerToolbarActionKey) -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = section.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = {},
        enabled = false,
    )
    section.items.forEach { item ->
        DropdownMenuItem(
            text = {
                Row(
                    modifier = Modifier.widthIn(max = 260.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            fontWeight =
                                if (item.checked) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        item.detail?.let { detail ->
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (item.checked) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            onClick = { onPick(item.actionKey) },
            enabled = item.enabled,
        )
    }
}
