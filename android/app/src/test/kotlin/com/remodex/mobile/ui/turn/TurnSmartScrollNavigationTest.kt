package com.remodex.mobile.ui.turn

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TurnSmartScrollNavigationTest {
    private val t0 = Instant.parse("2024-01-01T00:00:00Z")

    @Test
    fun awayFromBottomPrefersLatest() {
        val state =
            buildSmartScrollNavigationState(
                totalItemsCount = 5,
                firstVisibleItemIndex = 1,
                lastVisibleItemIndex = 2,
                anchors = anchors(5),
                isNearBottom = false,
            )

        assertEquals(
            listOf(SMART_SCROLL_LABEL_PREV_USER, SMART_SCROLL_LABEL_NEXT_USER, SMART_SCROLL_LABEL_LATEST),
            state.actions.map { it.label },
        )
        assertEquals(listOf(0, 3, 4), state.actions.map { it.targetIndex })
    }

    @Test
    fun longMiddleSessionShowsAnchorNavigationAndLatest() {
        val state =
            buildSmartScrollNavigationState(
                totalItemsCount = 18,
                firstVisibleItemIndex = 8,
                lastVisibleItemIndex = 9,
                anchors = anchors(18),
                isNearBottom = false,
            )

        assertEquals(
            listOf(SMART_SCROLL_LABEL_PREV_USER, SMART_SCROLL_LABEL_NEXT_USER, SMART_SCROLL_LABEL_LATEST),
            state.actions.map { it.label },
        )
    }

    @Test
    fun nearBottomHidesNavigationToAvoidCoveringMessageActions() {
        val state =
            buildSmartScrollNavigationState(
                totalItemsCount = 6,
                firstVisibleItemIndex = 4,
                lastVisibleItemIndex = 5,
                anchors = anchors(6),
                isNearBottom = true,
        )

        assertEquals(emptyList(), state.actions)
    }

    @Test
    fun middleSessionDoesNotReplaceAnchorsWithLostCta() {
        val state =
            buildSmartScrollNavigationState(
                totalItemsCount = 18,
                firstVisibleItemIndex = 8,
                lastVisibleItemIndex = 9,
                anchors = anchors(18),
                isNearBottom = false,
            )

        assertEquals(
            listOf(SMART_SCROLL_LABEL_PREV_USER, SMART_SCROLL_LABEL_NEXT_USER, SMART_SCROLL_LABEL_LATEST),
            state.actions.map { it.label },
        )
    }

    @Test
    fun anchorsTrackOnlyUserMessagesWithRenderedTimelineIndexes() {
        val messages =
            listOf(
                message("u1", CodexMessageRole.user),
                message("c1", CodexMessageRole.system, CodexMessageKind.commandExecution),
                message("c2", CodexMessageRole.system, CodexMessageKind.commandExecution),
                message("c3", CodexMessageRole.system, CodexMessageKind.commandExecution),
                message("c4", CodexMessageRole.system, CodexMessageKind.commandExecution),
                message("a1", CodexMessageRole.assistant),
                message("u2", CodexMessageRole.user),
            )

        val anchors = buildChatAnchors(messages, listItemOffset = 1)

        assertEquals(listOf(1, 4), anchors.map { it.index })
        assertEquals(listOf(ChatAnchorType.USER_MESSAGE, ChatAnchorType.USER_MESSAGE), anchors.map { it.type })
    }

    private fun anchors(count: Int): List<ChatAnchor> =
        (0 until count).map { index ->
            ChatAnchor(
                messageId = "m$index",
                index = index,
                type = ChatAnchorType.USER_MESSAGE,
                importance = 100,
            )
        }

    private fun message(
        id: String,
        role: CodexMessageRole,
        kind: CodexMessageKind = CodexMessageKind.chat,
    ): CodexMessage =
        CodexMessage(
            id = id,
            threadId = "thread",
            role = role,
            kind = kind,
            text = id,
            createdAt = t0,
        )
}
