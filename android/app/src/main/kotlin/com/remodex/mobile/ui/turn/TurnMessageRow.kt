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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Timeline turn row: chat + dedicated layouts per [CodexMessageKind] (J.4/J.6).
 */
@Composable
fun TurnMessageRow(
    message: CodexMessage,
    modifier: Modifier = Modifier,
    commandExecutionDetails: CommandExecutionDetails? = null,
    onOpenFullMessage: ((CodexMessage) -> Unit)? = null,
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
                val isStreamingAssistantChat =
                    message.role == CodexMessageRole.assistant &&
                        message.kind == CodexMessageKind.chat &&
                        message.isStreaming
                val revealedBodyMarkdown =
                    rememberStreamingAssistantMarkdown(
                        messageId = message.id,
                        markdown = bodyMarkdown,
                        isStreaming = isStreamingAssistantChat,
                    )
                val displayedBodyMarkdown = revealedBodyMarkdown
                val cappedBody =
                    remember(message.id, message.role, message.kind, displayedBodyMarkdown) {
                        capTimelineBody(message, displayedBodyMarkdown)
                    }
                val streamingModifier =
                    Modifier
                        .fillMaxWidth()
                        .streamingAssistantShimmer(isStreamingAssistantChat)
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
                        if (cappedBody.text.isNotEmpty()) {
                            if (shouldRenderMarkdownBody(message)) {
                                TurnRichMarkdownBody(
                                    markdown = cappedBody.text,
                                    contentColor = onBubble,
                                    modifier = streamingModifier,
                                    keyPrefix = message.id,
                                )
                            } else {
                                Text(
                                    text = cappedBody.text,
                                    style =
                                        if (message.role == CodexMessageRole.system && message.kind == CodexMessageKind.thinking) {
                                            MaterialTheme.typography.bodySmall
                                        } else {
                                            MaterialTheme.typography.bodyMedium
                                        },
                                    color = onBubble,
                                    modifier = streamingModifier,
                                )
                            }
                            if (cappedBody.truncated && onOpenFullMessage != null) {
                                TextButton(onClick = { onOpenFullMessage(message) }) {
                                    Text(stringResource(R.string.turn_message_see_more))
                                }
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
private fun rememberStreamingAssistantMarkdown(
    messageId: String,
    markdown: String,
    isStreaming: Boolean,
): String {
    if (isStreaming && markdown.length > STREAMING_ASSISTANT_REVEAL_DISABLE_CHARS) {
        return markdown
    }
    var displayed by remember(messageId) { mutableStateOf(if (isStreaming) "" else markdown) }
    val latestMarkdown by rememberUpdatedState(markdown)

    LaunchedEffect(messageId, isStreaming) {
        if (!isStreaming) {
            displayed = latestMarkdown
            return@LaunchedEffect
        }

        if (displayed.isEmpty() && STREAMING_ASSISTANT_INITIAL_DELAY_MS > 0L) {
            delay(STREAMING_ASSISTANT_INITIAL_DELAY_MS)
        }

        while (isActive) {
            val target = latestMarkdown
            displayed =
                when {
                    target.isEmpty() -> ""
                    displayed.isEmpty() -> target.take(streamingRevealStep(target.length))
                    target.startsWith(displayed) && displayed.length < target.length -> {
                        val nextLength =
                            (displayed.length + streamingRevealStep(target.length - displayed.length))
                                .coerceAtMost(target.length)
                        target.take(nextLength)
                    }
                    target == displayed -> displayed
                    else -> target
                }
            delay(STREAMING_ASSISTANT_REVEAL_FRAME_MS)
        }
    }

    return if (isStreaming) displayed else markdown
}

private fun streamingRevealStep(remaining: Int): Int =
    when {
        remaining > 180 -> 32
        remaining > 80 -> 20
        remaining > 24 -> 8
        else -> 3
    }

@Composable
private fun Modifier.streamingAssistantShimmer(enabled: Boolean): Modifier {
    return if (enabled) this.shimmer() else this
}

private const val STREAMING_ASSISTANT_INITIAL_DELAY_MS = 80L
private const val STREAMING_ASSISTANT_REVEAL_FRAME_MS = 34L
private const val STREAMING_ASSISTANT_REVEAL_DISABLE_CHARS = 1_800
private const val USER_MESSAGE_INLINE_MAX_CHARS = 1_200
private const val USER_MESSAGE_INLINE_MAX_LINES = 12
private const val ASSISTANT_MESSAGE_INLINE_MAX_LINES = 18
private const val PLAN_MESSAGE_INLINE_MAX_CHARS = 1_200
private const val PLAN_MESSAGE_INLINE_MAX_LINES = 12
private const val PLAN_MESSAGE_MAX_VISIBLE_STEPS = 4

private data class CappedTimelineBody(
    val text: String,
    val truncated: Boolean,
)

private fun capTimelineBody(
    message: CodexMessage,
    text: String,
): CappedTimelineBody {
    val (maxChars, maxLines) =
        when {
            message.role == CodexMessageRole.user && message.kind == CodexMessageKind.chat ->
                USER_MESSAGE_INLINE_MAX_CHARS to USER_MESSAGE_INLINE_MAX_LINES
            message.role == CodexMessageRole.assistant && message.kind == CodexMessageKind.chat ->
                Int.MAX_VALUE to ASSISTANT_MESSAGE_INLINE_MAX_LINES
            message.kind == CodexMessageKind.plan ->
                PLAN_MESSAGE_INLINE_MAX_CHARS to PLAN_MESSAGE_INLINE_MAX_LINES
            else -> return CappedTimelineBody(text = text, truncated = false)
        }
    return capTextForTimeline(text, maxChars = maxChars, maxLines = maxLines)
}

private fun capTextForTimeline(
    text: String,
    maxChars: Int,
    maxLines: Int,
): CappedTimelineBody {
    if (text.isBlank()) return CappedTimelineBody(text = text, truncated = false)
    val lineCount = text.count { it == '\n' } + 1
    val shouldTruncate = text.length > maxChars || lineCount > maxLines
    if (!shouldTruncate) return CappedTimelineBody(text = text, truncated = false)
    val byLines = text.lineSequence().take(maxLines).joinToString("\n")
    val clipped =
        if (byLines.length > maxChars) {
            byLines.take(maxChars)
        } else {
            byLines
        }
    return CappedTimelineBody(text = clipped.trimEnd() + "\n...", truncated = true)
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
        val steps = message.planState?.steps.orEmpty()
        steps.take(PLAN_MESSAGE_MAX_VISIBLE_STEPS).forEachIndexed { index, step ->
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
        val hiddenStepCount = steps.size - PLAN_MESSAGE_MAX_VISIBLE_STEPS
        if (hiddenStepCount > 0) {
            Text(
                text = stringResource(R.string.turn_plan_more_steps, hiddenStepCount),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.72f),
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
