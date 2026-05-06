package com.remodex.mobile.ui.turn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageDeliveryState
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.core.model.CommandExecutionDetails
import com.remodex.mobile.core.model.TurnThinkingDisclosureHints
import com.remodex.mobile.ui.agent.FileEditRow
import com.remodex.mobile.ui.agent.ToolCallRow
import com.remodex.mobile.ui.theme.isAgentLightChrome

/**
 * Timeline turn row: chat + dedicated layouts per [CodexMessageKind] (J.4/J.6).
 */
@Composable
fun TurnMessageRow(
    message: CodexMessage,
    modifier: Modifier = Modifier,
    commandExecutionDetails: CommandExecutionDetails? = null,
) {
    val isTimelineToolRow =
        message.role == CodexMessageRole.system &&
            (message.kind == CodexMessageKind.commandExecution ||
                message.kind == CodexMessageKind.fileChange)
    val horizontalAlignment =
        when (message.role) {
            CodexMessageRole.user -> Alignment.End
            CodexMessageRole.assistant -> Alignment.Start
            CodexMessageRole.system ->
                if (isTimelineToolRow) {
                    Alignment.Start
                } else {
                    Alignment.CenterHorizontally
                }
        }
    val colors = MaterialTheme.colorScheme
    val isLightChrome = isAgentLightChrome()
    val bubbleColor =
        when (message.role) {
            CodexMessageRole.user ->
                if (isLightChrome) {
                    colors.surfaceVariant.copy(alpha = 1f)
                } else {
                    colors.surfaceVariant.copy(alpha = 0.55f)
                }
            CodexMessageRole.assistant -> colors.surface.copy(alpha = 0.0f)
            CodexMessageRole.system ->
                when (message.kind) {
                    CodexMessageKind.thinking -> Color.Transparent
                    CodexMessageKind.fileChange -> Color.Transparent
                    CodexMessageKind.commandExecution -> Color.Transparent
                    CodexMessageKind.subagentAction -> Color.Transparent
                    CodexMessageKind.plan -> colors.primaryContainer.copy(alpha = 0.22f)
                    CodexMessageKind.pendingApproval,
                    CodexMessageKind.userInputPrompt,
                    ->
                        colors.secondaryContainer.copy(alpha = 0.35f)
                    else -> colors.secondaryContainer.copy(alpha = 0.25f)
                }
        }
    val onBubble =
        when (message.role) {
            CodexMessageRole.user -> colors.onSurface
            CodexMessageRole.assistant -> colors.onBackground
            CodexMessageRole.system -> colors.onSurfaceVariant
        }
    val bubbleBorder =
        if (message.role == CodexMessageRole.user && isLightChrome) {
            BorderStroke(0.5.dp, colors.outline.copy(alpha = 0.58f))
        } else {
            null
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlignment,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = bubbleColor,
            border = bubbleBorder,
            modifier =
                if (isTimelineToolRow) {
                    Modifier
                        .fillMaxWidth(0.96f)
                        .widthIn(max = 620.dp)
                } else {
                    Modifier
                        .fillMaxWidth(
                            when (message.role) {
                                CodexMessageRole.user -> 0.92f
                                CodexMessageRole.assistant -> 0.98f
                                CodexMessageRole.system -> 0.96f
                            },
                        )
                        .widthIn(max = 620.dp)
                },
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal =
                            when (message.role) {
                                CodexMessageRole.user -> 12.dp
                                CodexMessageRole.assistant -> 4.dp
                                CodexMessageRole.system -> 12.dp
                            },
                        vertical =
                            when (message.role) {
                                CodexMessageRole.user -> 10.dp
                                CodexMessageRole.assistant -> 8.dp
                                CodexMessageRole.system -> 8.dp
                            },
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val kindLabel = systemKindLabel(message)
                if (kindLabel != null) {
                    Text(
                        text = kindLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = onBubble.copy(alpha = 0.88f),
                    )
                }
                if (message.role != CodexMessageRole.system && message.attachments.isNotEmpty()) {
                    MessageAttachmentStrip(attachments = message.attachments)
                }
                val directiveOutcome =
                    TurnDirectiveRenderCache.parseCodeCommentDirectives(message.id, message.text)
                val bodyMarkdown =
                    timelineBodyText(
                        message = message,
                        skipThinkingHeadline = kindLabel != null,
                        cleanedDirectiveText = directiveOutcome.cleanedText,
                        hasDirectiveFindings = directiveOutcome.hasFindings,
                    )
                when (message.kind) {
                    CodexMessageKind.thinking -> {
                        TurnThinkingTimelineRow(
                            bodyMarkdown =
                                timelineBodyText(
                                    message = message,
                                    skipThinkingHeadline = true,
                                    cleanedDirectiveText = directiveOutcome.cleanedText,
                                    hasDirectiveFindings = directiveOutcome.hasFindings,
                                ),
                            contentColor = onBubble,
                            useMarkdown = shouldRenderMarkdownBody(message),
                            isStreaming = message.isStreaming,
                            messageId = message.id,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    CodexMessageKind.fileChange -> {
                        FileEditRow(
                            message = message,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    CodexMessageKind.commandExecution -> {
                        ToolCallRow(
                            message = message,
                            contentColor = onBubble,
                            details = commandExecutionDetails,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    CodexMessageKind.subagentAction -> {
                        TurnSubagentActionCard(
                            message = message,
                            contentColor = onBubble,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> {
                        if (bodyMarkdown.isNotEmpty()) {
                            if (shouldRenderMarkdownBody(message)) {
                                TurnRichMarkdownBody(
                                    markdown = bodyMarkdown,
                                    contentColor = onBubble,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(
                                    text = bodyMarkdown,
                                    style =
                                        if (message.role == CodexMessageRole.system && message.kind == CodexMessageKind.thinking) {
                                            MaterialTheme.typography.bodySmall
                                        } else {
                                            MaterialTheme.typography.bodyMedium
                                        },
                                    color = onBubble,
                                )
                            }
                        }
                    }
                }
                if (directiveOutcome.findings.isNotEmpty()) {
                    TurnCodeCommentDirectiveCards(
                        findings = directiveOutcome.findings,
                        contentColor = onBubble,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (message.kind == CodexMessageKind.plan) {
                    PlanMessageDetails(
                        message = message,
                        contentColor = onBubble,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (message.kind == CodexMessageKind.pendingApproval) {
                    Text(
                        text = stringResource(R.string.turn_timeline_pending_approval_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = onBubble.copy(alpha = 0.72f),
                    )
                }
                if (message.kind == CodexMessageKind.userInputPrompt) {
                    Text(
                        text = stringResource(R.string.turn_timeline_structured_input_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = onBubble.copy(alpha = 0.72f),
                    )
                }
                userDeliveryCaption(message)?.let { cap ->
                    Text(
                        text = cap,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            when (message.deliveryState) {
                                CodexMessageDeliveryState.failed -> MaterialTheme.colorScheme.error
                                else -> onBubble.copy(alpha = 0.75f)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanMessageDetails(
    message: CodexMessage,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val explanation = message.planState?.explanation?.trim().orEmpty()
    val body = message.text.trim()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (explanation.isNotEmpty() && explanation != body) {
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.82f),
            )
        }
        message.planState?.steps?.forEachIndexed { index, step ->
            val statusPrefix =
                when (step.status) {
                    com.remodex.mobile.core.model.CodexPlanStepStatus.completed -> "[done]"
                    com.remodex.mobile.core.model.CodexPlanStepStatus.inProgress -> "[doing]"
                    com.remodex.mobile.core.model.CodexPlanStepStatus.pending -> "[todo]"
                }
            Text(
                text = "${index + 1}. $statusPrefix ${step.step}",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.9f),
            )
        }
    }
}

/** Chat utente/assistant, plan e reasoning: markdown; diff/path comando restano testo semplice. */
private fun shouldRenderMarkdownBody(message: CodexMessage): Boolean {
    return when (message.kind) {
        CodexMessageKind.chat ->
            message.role == CodexMessageRole.user || message.role == CodexMessageRole.assistant
        CodexMessageKind.plan,
        CodexMessageKind.thinking,
        -> true
        CodexMessageKind.fileChange,
        CodexMessageKind.commandExecution,
        CodexMessageKind.subagentAction,
        CodexMessageKind.userInputPrompt,
        CodexMessageKind.pendingApproval,
        -> false
    }
}

@Composable
private fun systemKindLabel(message: CodexMessage): String? {
    if (message.role != CodexMessageRole.system) return null
    return when (message.kind) {
        CodexMessageKind.chat -> null
        CodexMessageKind.thinking -> null
        CodexMessageKind.fileChange -> null
        CodexMessageKind.commandExecution -> null
        CodexMessageKind.plan -> stringResource(R.string.turn_timeline_kind_plan)
        CodexMessageKind.subagentAction -> stringResource(R.string.turn_timeline_kind_subagent)
        CodexMessageKind.userInputPrompt -> stringResource(R.string.turn_timeline_kind_prompt)
        CodexMessageKind.pendingApproval -> stringResource(R.string.turn_timeline_kind_pending_approval)
    }
}

@Composable
private fun userDeliveryCaption(message: CodexMessage): String? {
    if (message.role != CodexMessageRole.user) return null
    return when (message.deliveryState) {
        CodexMessageDeliveryState.pending -> stringResource(R.string.turn_delivery_sending)
        CodexMessageDeliveryState.failed -> stringResource(R.string.turn_delivery_failed)
        else -> null
    }
}

@Composable
private fun timelineBodyText(
    message: CodexMessage,
    skipThinkingHeadline: Boolean,
    cleanedDirectiveText: String,
    hasDirectiveFindings: Boolean,
): String {
    val raw =
        when (message.kind) {
            CodexMessageKind.thinking -> TurnThinkingDisclosureHints.stripSimpleThinkingTags(cleanedDirectiveText).trim()
            else -> cleanedDirectiveText.trim()
        }
    if (raw.isEmpty() && hasDirectiveFindings) return ""
    return when (message.kind) {
        CodexMessageKind.chat ->
            when {
                raw.isEmpty() && message.attachments.isNotEmpty() -> ""
                raw.isNotEmpty() ->
                    if (message.isStreaming) {
                        "$raw ..."
                    } else {
                        raw
                    }
                message.isStreaming -> stringResource(R.string.turn_message_streaming_placeholder)
                else -> stringResource(R.string.turn_message_empty_placeholder)
            }
        CodexMessageKind.thinking ->
            when {
                skipThinkingHeadline && raw.isNotEmpty() ->
                    if (message.isStreaming) {
                        "$raw ..."
                    } else {
                        raw
                    }
                skipThinkingHeadline && message.isStreaming ->
                    stringResource(R.string.turn_message_streaming_placeholder)
                skipThinkingHeadline -> stringResource(R.string.turn_message_empty_placeholder)
                raw.isNotEmpty() -> stringResource(R.string.turn_message_thinking_with_text, raw)
                else -> stringResource(R.string.turn_message_thinking)
            }
        CodexMessageKind.fileChange,
        CodexMessageKind.commandExecution,
        CodexMessageKind.plan,
        ->
            when {
                raw.isNotEmpty() ->
                    if (message.isStreaming) {
                        "$raw ..."
                    } else {
                        raw
                    }
                message.isStreaming -> stringResource(R.string.turn_message_streaming_placeholder)
                else -> stringResource(R.string.turn_message_empty_placeholder)
            }
        CodexMessageKind.subagentAction,
        CodexMessageKind.userInputPrompt,
        ->
            if (raw.isNotEmpty()) {
                raw
            } else {
                stringResource(R.string.turn_timeline_kind_other)
            }
        CodexMessageKind.pendingApproval ->
            if (raw.isNotEmpty()) {
                raw
            } else {
                stringResource(R.string.turn_timeline_kind_pending_approval)
            }
    }
}
