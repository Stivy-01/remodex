package com.remodex.mobile.ui.turn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import com.remodex.mobile.R
import com.remodex.mobile.ui.theme.AgentLightColors
import com.remodex.mobile.ui.theme.RemodexComposerCapsuleChrome
import com.remodex.mobile.ui.theme.isAgentLightChrome
import com.valentinilk.shimmer.shimmer

internal enum class TurnVoicePhase {
    Idle,
    Recording,
    Transcribing,
}

/**
 * Composer J.2/J.6: testo + picker immagini + preview allegati.
 *
 * In the iOS/SwiftUI surface this is the prompt composer (`TurnComposer*` family); keep this type
 * as the single Android entry point for that responsibility. [composerEnvironment] hosts the
 * Swift-style secondary row (Local / access / branch / usage ring) when the pane has room for it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TurnComposerBar(
    draft: String,
    attachments: List<TurnComposerAttachment>,
    model: TurnComposerModel,
    isPlanModeEnabled: Boolean,
    runtimeControls: TurnComposerRuntimeControlsState = TurnComposerRuntimeControlsState(),
    mentionChips: List<ComposerMentionChipPayload> = emptyList(),
    autocomplete: TurnComposerAutocompleteState? = null,
    onDraftChange: (String) -> Unit,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onTakePhoto: () -> Unit,
    onSetPlanModeEnabled: (Boolean) -> Unit,
    onSelectModel: (TurnComposerRuntimeOption) -> Unit = {},
    onSelectReasoningEffort: (TurnComposerRuntimeOption) -> Unit = {},
    onSelectAccessMode: (TurnComposerRuntimeOption) -> Unit = {},
    onSelectServiceTier: (TurnComposerRuntimeOption) -> Unit = {},
    onRemoveAttachment: (String) -> Unit,
    onRemoveMentionChip: (ComposerMentionChipPayload) -> Unit = {},
    onSelectAutocomplete: (TurnComposerAutocompleteItem) -> Unit = {},
    onSend: () -> Unit,
    onStopTurn: () -> Unit = {},
    voiceUiEnabled: Boolean = false,
    voiceAudioLevels: List<Float> = emptyList(),
    voiceRecordingDurationSeconds: Double = 0.0,
    onVoiceClick: () -> Unit = {},
    onCancelVoiceRecording: () -> Unit = {},
    composerEnvironment: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isAttachmentMenuExpanded by remember { mutableStateOf(false) }
    val textInteractionSource = remember { MutableInteractionSource() }
    val actions = remember(model, voiceUiEnabled) { TurnComposerToolbarActionsBuilder.build(model, voiceUiEnabled) }
    val locks = remember(model) { model.deriveInteractionLocks() }
    val runtimeToolbarMenu = remember(runtimeControls) { TurnComposerRuntimeToolbarMenuBuilder.build(runtime = runtimeControls) }
    val agentLightChrome = isAgentLightChrome()
    val neutralToolbarIconTint =
        if (agentLightChrome) AgentLightColors.IconMuted else MaterialTheme.colorScheme.onSurfaceVariant
    val composerHintColor =
        if (agentLightChrome) {
            MaterialTheme.colorScheme.outlineVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ComposerAttachmentStrip(
            attachments = attachments,
            onRemove = onRemoveAttachment,
        )
        ComposerMentionChipStrip(
            chips = mentionChips,
            onRemove = onRemoveMentionChip,
        )
        autocomplete?.takeIf { it.isVisible }?.let { state ->
            val autoShape = MaterialTheme.shapes.small
            val autoBg =
                if (agentLightChrome) {
                    AgentLightColors.Surface.copy(alpha = 0.96f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                }
            val autoModifier =
                if (agentLightChrome) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = autoShape,
                    )
                } else {
                    Modifier
                }
            Surface(
                modifier = autoModifier.heightIn(max = 280.dp),
                shape = autoShape,
                color = autoBg,
            ) {
                Column(
                    modifier =
                        Modifier
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.isLoading && state.items.isEmpty()) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = stringResource(R.string.turn_runtime_loading),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    state.items.forEach { item ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .clickable { onSelectAutocomplete(item) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(autocompleteIconRes(item.payload.kind)),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = autocompleteIconTint(item.payload.kind),
                            )
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = item.title,
                                    color = autocompleteTitleTint(item.payload.kind),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                item.subtitle?.let { subtitle ->
                                    Text(
                                        text = subtitle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (model.voicePhase == TurnVoicePhase.Recording) {
            VoiceRecordingCapsule(
                audioLevels = voiceAudioLevels,
                durationSeconds = voiceRecordingDurationSeconds,
                onCancel = onCancelVoiceRecording,
            )
        }
        RemodexComposerCapsuleChrome(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent),
                    interactionSource = textInteractionSource,
                    enabled = actions.textFieldEnabled,
                    minLines = 1,
                    maxLines = 4,
                    textStyle =
                        MaterialTheme.typography.bodyMedium.merge(
                            TextStyle(color = MaterialTheme.colorScheme.onSurface),
                        ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(Color.Transparent)
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (draft.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.turn_composer_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = composerHintColor,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box {
                        IconButton(
                            onClick = { isAttachmentMenuExpanded = true },
                            enabled = !locks.runtimeControlsLocked,
                            modifier = Modifier.size(33.dp),
                        ) {
                            Icon(
                                painter = painterResource(LucideR.drawable.lucide_ic_paperclip),
                                contentDescription = stringResource(R.string.turn_add_images_cd),
                                modifier = Modifier.size(19.dp),
                                tint = neutralToolbarIconTint,
                            )
                        }
                        DropdownMenu(
                            expanded = isAttachmentMenuExpanded,
                            onDismissRequest = { isAttachmentMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.turn_attachment_menu_library)) },
                                onClick = {
                                    isAttachmentMenuExpanded = false
                                    onPickImages()
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(LucideR.drawable.lucide_ic_image_plus),
                                        contentDescription = null,
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.turn_attachment_menu_file)) },
                                onClick = {
                                    isAttachmentMenuExpanded = false
                                    onPickFiles()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.turn_attachment_menu_camera)) },
                                onClick = {
                                    isAttachmentMenuExpanded = false
                                    onTakePhoto()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isPlanModeEnabled) {
                                            "${stringResource(R.string.turn_plan_mode_chip)}: on"
                                        } else {
                                            stringResource(R.string.turn_plan_mode_chip)
                                        },
                                    )
                                },
                                onClick = {
                                    isAttachmentMenuExpanded = false
                                    onSetPlanModeEnabled(!isPlanModeEnabled)
                                },
                                enabled = !locks.runtimeControlsLocked,
                            )
                        }
                    }
                    UnifiedComposerModelRuntimeChip(
                        runtimeControls = runtimeControls,
                        runtimeToolbarMenu = runtimeToolbarMenu,
                        enabled = true,
                        onSelectModel = onSelectModel,
                        onSelectReasoningEffort = onSelectReasoningEffort,
                        onSelectServiceTier = onSelectServiceTier,
                        modifier =
                            Modifier
                                .wrapContentWidth()
                                .widthIn(max = 136.dp),
                    )
                    if (isPlanModeEnabled) {
                        ComposerPlanModeBadge(
                            onClick = { onSetPlanModeEnabled(false) },
                        )
                    }
                    Box(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier.size(33.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (actions.voiceIcon == TurnComposerVoiceToolbarIcon.Progress) {
                            val transcribingLabel = stringResource(R.string.turn_voice_transcribing_cd)
                            CircularProgressIndicator(
                                modifier =
                                    Modifier
                                        .size(22.dp)
                                        .semantics { contentDescription = transcribingLabel },
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(
                                onClick = onVoiceClick,
                                enabled = actions.voiceControlEnabled,
                                modifier = Modifier.size(33.dp),
                            ) {
                                when (actions.voiceIcon) {
                                    TurnComposerVoiceToolbarIcon.Stop -> {
                                        Icon(
                                            painter = painterResource(LucideR.drawable.lucide_ic_circle_stop),
                                            contentDescription = stringResource(R.string.turn_voice_stop_cd),
                                            modifier = Modifier.size(19.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    TurnComposerVoiceToolbarIcon.Mic -> {
                                        Icon(
                                            painter = painterResource(LucideR.drawable.lucide_ic_mic),
                                            contentDescription = stringResource(R.string.turn_voice_mic_cd),
                                            modifier = Modifier.size(19.dp),
                                            tint = neutralToolbarIconTint,
                                        )
                                    }
                                    TurnComposerVoiceToolbarIcon.None,
                                    TurnComposerVoiceToolbarIcon.Progress,
                                    -> Unit
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.size(33.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (actions.stopButtonVisible) {
                            val stopCd = stringResource(R.string.turn_stop)
                            Box(
                                modifier =
                                    Modifier
                                        .size(33.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                        .clickable(
                                            enabled = actions.stopButtonEnabled,
                                            onClick = onStopTurn,
                                        )
                                        .semantics { contentDescription = stopCd },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(LucideR.drawable.lucide_ic_circle_stop),
                                    contentDescription = null,
                                    modifier = Modifier.size(19.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else if (actions.sendShowsProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            val cs = MaterialTheme.colorScheme
                            val sendCd = stringResource(R.string.turn_send_cd)
                            val sendBg =
                                if (actions.sendButtonEnabled) {
                                    cs.onSurface
                                } else {
                                    cs.surfaceVariant.copy(alpha = 0.8f)
                                }
                            val iconTint =
                                if (actions.sendButtonEnabled) {
                                    cs.surface
                                } else {
                                    cs.onSurfaceVariant.copy(alpha = 0.45f)
                                }
                            Box(
                                modifier =
                                    Modifier
                                        .size(33.dp)
                                        .clip(CircleShape)
                                        .background(sendBg)
                                        .clickable(
                                            enabled = actions.sendButtonEnabled,
                                            onClick = onSend,
                                        )
                                        .semantics { contentDescription = sendCd },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(LucideR.drawable.lucide_ic_arrow_up),
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                    tint = iconTint,
                                )
                            }
                        }
                    }
                }
            }
        }
        composerEnvironment()
    }
}

@Composable
private fun autocompleteIconTint(kind: ComposerMentionKind): Color =
    when (kind) {
        ComposerMentionKind.Skill -> MaterialTheme.colorScheme.primary
        ComposerMentionKind.Plugin -> MaterialTheme.colorScheme.tertiary
        ComposerMentionKind.File -> MaterialTheme.colorScheme.onSurfaceVariant
        ComposerMentionKind.SlashCommand -> MaterialTheme.colorScheme.secondary
    }

@Composable
private fun autocompleteTitleTint(kind: ComposerMentionKind): Color =
    when (kind) {
        ComposerMentionKind.Skill -> MaterialTheme.colorScheme.primary
        ComposerMentionKind.Plugin -> MaterialTheme.colorScheme.onSurface
        ComposerMentionKind.File,
        ComposerMentionKind.SlashCommand,
        -> MaterialTheme.colorScheme.onSurface
    }

private fun autocompleteIconRes(kind: ComposerMentionKind): Int =
    when (kind) {
        ComposerMentionKind.File -> LucideR.drawable.lucide_ic_file
        ComposerMentionKind.Skill -> LucideR.drawable.lucide_ic_square_asterisk
        ComposerMentionKind.Plugin -> LucideR.drawable.lucide_ic_blocks
        ComposerMentionKind.SlashCommand -> LucideR.drawable.lucide_ic_command
    }

@Composable
private fun ComposerPlanModeBadge(
    onClick: () -> Unit,
) {
    val lightChrome = isAgentLightChrome()
    val shape = RoundedCornerShape(999.dp)
    val background =
        if (lightChrome) {
            Color(0xFFFFF2A8)
        } else {
            Color(0xFF4D3B12)
        }
    val glow =
        if (lightChrome) {
            Color(0xFFFFD84A).copy(alpha = 0.48f)
        } else {
            Color(0xFFFFD666).copy(alpha = 0.28f)
        }
    val textColor =
        if (lightChrome) {
            Color(0xFF604500)
        } else {
            Color(0xFFFFE08A)
        }
    val label = stringResource(R.string.turn_plan_mode_chip)

    Box(
        modifier =
            Modifier
                .shimmer()
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            background,
                            glow,
                            background,
                        ),
                    ),
                )
                .clickable(onClick = onClick)
                .semantics { contentDescription = label }
                .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ComposerMentionChipStrip(
    chips: List<ComposerMentionChipPayload>,
    onRemove: (ComposerMentionChipPayload) -> Unit,
) {
    if (chips.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { chip ->
            FilterChip(
                selected = false,
                onClick = {},
                enabled = true,
                label = {
                    val prefix =
                        when (chip.kind) {
                            ComposerMentionKind.File -> "@"
                            ComposerMentionKind.Skill -> "$"
                            ComposerMentionKind.Plugin -> "@"
                            ComposerMentionKind.SlashCommand -> "/"
                        }
                    Text(text = "$prefix${chip.displayLabel ?: chip.semanticValue}")
                },
                trailingIcon = {
                    IconButton(
                        onClick = { onRemove(chip) },
                        modifier = Modifier.size(18.dp),
                    ) {
                        Icon(
                            painter = painterResource(LucideR.drawable.lucide_ic_x),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                },
            )
        }
    }
}
