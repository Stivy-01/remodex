package com.remodex.mobile.ui.turn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
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
import kotlin.math.abs

internal enum class ChatAnchorType {
    USER_MESSAGE,
    ASSISTANT_MESSAGE,
    TOOL_RESULT,
    ERROR,
    COMMIT_DIFF,
    COMMAND_OUTPUT,
    ACTION,
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

internal const val SMART_SCROLL_LABEL_PREV = "\u2191 Prev"
internal const val SMART_SCROLL_LABEL_NEXT = "\u2193 Next"
internal const val SMART_SCROLL_LABEL_LATEST = "\u2304 Latest"
internal const val SMART_SCROLL_LABEL_JUMP_LATEST = "\u2193 Latest"
internal const val SMART_SCROLL_LABEL_NEW_OUTPUT = "New output \u2193"
internal const val SMART_SCROLL_LABEL_BOTTOM = "\u2193 Bottom"
internal const val SMART_SCROLL_LABEL_TOP = "\u2191 Top"
internal const val SMART_SCROLL_LABEL_PREVIOUS_ACTION = "\u2191 Previous action"
internal const val SMART_SCROLL_LABEL_PREVIOUS_MESSAGE = "\u2191 Previous message"

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
    isNearTop: Boolean,
    isNearBottom: Boolean,
    hasNewMessagesBelow: Boolean,
    directionChangeCount: Int,
): SmartScrollNavigationState {
    if (totalItemsCount <= 0 || anchors.isEmpty()) return SmartScrollNavigationState()

    val previous = previousImportantAnchor(firstVisibleItemIndex, anchors)
    val next = nextImportantAnchor(lastVisibleItemIndex ?: firstVisibleItemIndex, anchors)
    val hasLongSession = anchors.size >= 8 || totalItemsCount >= 16
    val awayFromLatest = !isNearBottom
    val possiblyLost = awayFromLatest && directionChangeCount >= 3

    val actions =
        when {
            possiblyLost ->
                listOf(SmartScrollAction("Lost? Latest", totalItemsCount - 1))
            awayFromLatest && hasLongSession && !isNearTop ->
                buildList {
                    previous?.let { add(SmartScrollAction(SMART_SCROLL_LABEL_PREV, it.index)) }
                    next?.let { add(SmartScrollAction(SMART_SCROLL_LABEL_NEXT, it.index)) }
                    add(
                        SmartScrollAction(
                            if (hasNewMessagesBelow) SMART_SCROLL_LABEL_NEW_OUTPUT else SMART_SCROLL_LABEL_LATEST,
                            totalItemsCount - 1,
                        ),
                    )
                }
            awayFromLatest ->
                listOf(
                    SmartScrollAction(
                        if (hasNewMessagesBelow) SMART_SCROLL_LABEL_NEW_OUTPUT else SMART_SCROLL_LABEL_JUMP_LATEST,
                        totalItemsCount - 1,
                    ),
                )
            isNearTop && hasLongSession ->
                listOf(SmartScrollAction(SMART_SCROLL_LABEL_BOTTOM, totalItemsCount - 1))
            firstVisibleItemIndex > 12 ->
                listOf(SmartScrollAction(SMART_SCROLL_LABEL_TOP, 0))
            else ->
                previous?.let { listOf(SmartScrollAction(previousLabel(it), it.index)) }.orEmpty()
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

private fun previousLabel(anchor: ChatAnchor): String =
    when (anchor.type) {
        ChatAnchorType.COMMAND_OUTPUT,
        ChatAnchorType.COMMIT_DIFF,
        ChatAnchorType.ACTION,
        -> SMART_SCROLL_LABEL_PREVIOUS_ACTION
        else -> SMART_SCROLL_LABEL_PREVIOUS_MESSAGE
    }

internal suspend fun LazyListState.animateScrollToItemRelaxed(targetIndex: Int) {
    val maxIndex = (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
    val boundedTarget = targetIndex.coerceIn(0, maxIndex)
    var passes = 0
    while (passes < 4 && boundedTarget !in visibleItemIndexRange()) {
        val visibleItems = layoutInfo.visibleItemsInfo
        val averageItemSize =
            visibleItems
                .takeIf { it.isNotEmpty() }
                ?.map { it.size }
                ?.average()
                ?.toFloat()
                ?.coerceAtLeast(1f)
                ?: 96f
        val distanceItems = boundedTarget - firstVisibleItemIndex
        val estimatedDistancePx = distanceItems * averageItemSize - firstVisibleItemScrollOffset
        if (abs(estimatedDistancePx) <= 1f) break
        val duration = (abs(distanceItems) * 170).coerceIn(700, 2_400)
        animateScrollBy(
            value = estimatedDistancePx,
            animationSpec =
                tween(
                    durationMillis = duration,
                    easing = FastOutSlowInEasing,
                ),
        )
        passes++
    }
    if (boundedTarget in visibleItemIndexRange().expandBy(2)) {
        animateScrollToItem(boundedTarget)
    } else {
        scrollToItem(boundedTarget)
    }
}

private fun LazyListState.visibleItemIndexRange(): IntRange {
    val visibleItems = layoutInfo.visibleItemsInfo
    val first = visibleItems.firstOrNull()?.index ?: firstVisibleItemIndex
    val last = visibleItems.lastOrNull()?.index ?: first
    return first..last
}

private fun IntRange.expandBy(items: Int): IntRange = (first - items)..(last + items)

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
        Surface(
            modifier = Modifier.shadow(10.dp, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.actions.forEachIndexed { index, action ->
                    if (index > 0) {
                        Surface(
                            modifier =
                                Modifier
                                    .padding(horizontal = 4.dp)
                                    .width(0.5.dp)
                                    .height(26.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                            content = {},
                        )
                    }
                    Text(
                        text = action.label,
                        modifier =
                            Modifier
                                .clickable { onNavigate(action.targetIndex) }
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Unspecified,
                    )
                }
            }
        }
    }
}
