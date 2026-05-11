package com.remodex.mobile.ui.turn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.ui.agent.TimelineListItem
import com.remodex.mobile.ui.agent.toTimelineListItems

internal enum class ChatAnchorType {
    USER_MESSAGE,
}

internal data class ChatAnchor(
    val messageId: String,
    val index: Int,
    val type: ChatAnchorType,
    val importance: Int,
)

internal data class SmartScrollNavigationState(
    val actions: List<SmartScrollAction> = emptyList(),
) {
    val visible: Boolean get() = actions.isNotEmpty()
}

internal data class SmartScrollAction(
    val label: String,
    val targetIndex: Int,
)

internal const val SMART_SCROLL_LABEL_PREV_USER = "\u2191 Previous message"
internal const val SMART_SCROLL_LABEL_NEXT_USER = "\u2193 Next message"
internal const val SMART_SCROLL_LABEL_LATEST = "\u2193 Latest"

internal fun buildChatAnchors(
    messages: List<CodexMessage>,
    listItemOffset: Int,
): List<ChatAnchor> =
    messages.toTimelineListItems().mapIndexedNotNull { itemIndex, item ->
        val lazyListIndex = itemIndex + listItemOffset
        when (item) {
            is TimelineListItem.Single ->
                item.message.toChatAnchor(lazyListIndex)
            is TimelineListItem.MessageChunk -> null
            is TimelineListItem.AssistantWorkGroup -> null
            is TimelineListItem.CommandExecutionGroup -> null
            is TimelineListItem.FileChangeGroup -> null
        }
    }

internal fun buildSmartScrollNavigationState(
    totalItemsCount: Int,
    firstVisibleItemIndex: Int,
    lastVisibleItemIndex: Int?,
    anchors: List<ChatAnchor>,
    isNearBottom: Boolean,
): SmartScrollNavigationState {
    if (totalItemsCount <= 0 || anchors.isEmpty()) return SmartScrollNavigationState()

    val previous = previousImportantAnchor(firstVisibleItemIndex, anchors)
    val next = nextImportantAnchor(lastVisibleItemIndex ?: firstVisibleItemIndex, anchors)
    val awayFromLatest = !isNearBottom

    val actions =
        if (awayFromLatest) {
            buildList {
                previous?.let { add(SmartScrollAction(SMART_SCROLL_LABEL_PREV_USER, it.index)) }
                next?.let { add(SmartScrollAction(SMART_SCROLL_LABEL_NEXT_USER, it.index)) }
                add(SmartScrollAction(SMART_SCROLL_LABEL_LATEST, totalItemsCount - 1))
            }
        } else {
            emptyList()
        }

    return SmartScrollNavigationState(actions = actions.take(3))
}

private fun CodexMessage.toChatAnchor(index: Int): ChatAnchor? {
    if (role != CodexMessageRole.user) return null
    return ChatAnchor(
        messageId = id,
        index = index,
        type = ChatAnchorType.USER_MESSAGE,
        importance = 100,
    )
}

private fun previousImportantAnchor(
    currentIndex: Int,
    anchors: List<ChatAnchor>,
): ChatAnchor? =
    anchors
        .asSequence()
        .filter { it.importance >= 70 && it.index < currentIndex }
        .maxByOrNull { it.index }

private fun nextImportantAnchor(
    currentIndex: Int,
    anchors: List<ChatAnchor>,
): ChatAnchor? =
    anchors
        .asSequence()
        .filter { it.importance >= 70 && it.index > currentIndex }
        .minByOrNull { it.index }

@Composable
internal fun SmartScrollNavigationCta(
    state: SmartScrollNavigationState,
    onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.visible,
        modifier = modifier,
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 340.dp
            val shape = RoundedCornerShape(if (compact) 18.dp else 22.dp)
            Surface(
                modifier = Modifier.shadow(10.dp, shape),
                shape = shape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier =
                        Modifier.padding(
                            horizontal = if (compact) 3.dp else 6.dp,
                            vertical = if (compact) 3.dp else 5.dp,
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.actions.forEachIndexed { index, action ->
                        if (index > 0) {
                            Surface(
                                modifier =
                                    Modifier
                                        .padding(horizontal = if (compact) 2.dp else 4.dp)
                                        .width(0.5.dp)
                                        .height(if (compact) 20.dp else 26.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                                content = {},
                            )
                        }
                        Text(
                            text = action.displayLabel(compact),
                            modifier =
                                Modifier
                                    .clickable { onNavigate(action.targetIndex) }
                                    .padding(
                                        horizontal = if (compact) 7.dp else 11.dp,
                                        vertical = if (compact) 6.dp else 8.dp,
                                    ),
                            style =
                                if (compact) {
                                    MaterialTheme.typography.labelMedium
                                } else {
                                    MaterialTheme.typography.labelLarge
                                },
                            color = Color.Unspecified,
                        )
                    }
                }
            }
        }
    }
}

private fun SmartScrollAction.displayLabel(compact: Boolean): String =
    if (!compact) {
        label
    } else {
        when (label) {
            SMART_SCROLL_LABEL_PREV_USER -> "\u2191 Prev"
            SMART_SCROLL_LABEL_NEXT_USER -> "\u2193 Next"
            SMART_SCROLL_LABEL_LATEST -> "\u2193 Latest"
            else -> label
        }
    }
