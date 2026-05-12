package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.PendingApprovalDecision
import com.remodex.mobile.core.model.PendingApprovalRequest
import com.remodex.mobile.core.model.PendingStructuredInputRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class IncomingEventRouterDesktopMirrorTest {
    @Test
    fun desktopUserMessageWithoutTurnStart_marksThreadRunningFallback() =
        runTest {
            val lifecycle = mutableListOf<Pair<String, String?>>()
            val router = newRouter(onTurnLifecycle = { threadId, turnId -> lifecycle += threadId to turnId })

            router.dispatchNotification(
                method = "codex/event/user_message",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "message" to JSONValue.Str("Prompt from PC"),
                        ),
                    ),
            )

            assertEquals(listOf<Pair<String, String?>>("thread-1" to null), lifecycle)
        }

    @Test
    fun desktopUserMessageWithTurnId_marksThreadRunningWithTurn() =
        runTest {
            val lifecycle = mutableListOf<Pair<String, String?>>()
            val router = newRouter(onTurnLifecycle = { threadId, turnId -> lifecycle += threadId to turnId })

            router.dispatchNotification(
                method = "codex/event/user_message",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "turnId" to JSONValue.Str("turn-1"),
                            "message" to JSONValue.Str("Prompt from PC"),
                        ),
                    ),
            )

            assertEquals(listOf<Pair<String, String?>>("thread-1" to "turn-1"), lifecycle)
        }

    @Test
    fun desktopUserBeforeTurnStart_isAttachedToTurnAndAssistantPlaceholderStreamsUntilFinal() =
        runTest {
            val timeline = MessageTimelineStore()
            val router = newRouter(messageTimeline = timeline)

            router.dispatchNotification(
                method = "codex/event/user_message",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "message" to JSONValue.Str("Prompt from PC"),
                        ),
                    ),
            )

            router.dispatchNotification(
                method = "turn/started",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "turnId" to JSONValue.Str("turn-1"),
                        ),
                    ),
            )

            val streaming = timeline.messagesByThread.value["thread-1"].orEmpty()
            assertEquals(listOf(CodexMessageRole.user, CodexMessageRole.assistant), streaming.map { it.role })
            assertEquals(listOf("turn-1", "turn-1"), streaming.map { it.turnId })
            assertEquals("", streaming[1].text)
            assertEquals(true, streaming[1].isStreaming)

            router.dispatchNotification(
                method = "codex/event/agent_message",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "turnId" to JSONValue.Str("turn-1"),
                            "itemId" to JSONValue.Str("assistant-1"),
                            "message" to JSONValue.Str("Final answer"),
                        ),
                    ),
            )

            val finalMessages = timeline.messagesByThread.value["thread-1"].orEmpty()
            assertEquals(2, finalMessages.size)
            assertEquals("Prompt from PC", finalMessages[0].text)
            assertEquals("Final answer", finalMessages[1].text)
            assertFalse(finalMessages[1].isStreaming)
            assertEquals("assistant-1", finalMessages[1].itemId)
        }

    @Test
    fun desktopBackgroundEvent_addsLiveThinkingWorkRow() =
        runTest {
            val timeline = MessageTimelineStore()
            val lifecycle = mutableListOf<Pair<String, String?>>()
            val router =
                newRouter(
                    messageTimeline = timeline,
                    onTurnLifecycle = { threadId, turnId -> lifecycle += threadId to turnId },
                )

            router.dispatchNotification(
                method = "turn/started",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "turnId" to JSONValue.Str("turn-1"),
                        ),
                    ),
            )
            router.dispatchNotification(
                method = "codex/event/background_event",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "turnId" to JSONValue.Str("turn-1"),
                            "call_id" to JSONValue.Str("patch-1"),
                            "message" to JSONValue.Str("Applying patch"),
                        ),
                    ),
            )

            val thinking = timeline.messagesByThread.value["thread-1"].orEmpty()
                .single { it.kind == CodexMessageKind.thinking }
            assertEquals("Applying patch", thinking.text)
            assertEquals("patch-1", thinking.itemId)
            assertEquals(true, thinking.isStreaming)
            assertEquals("thread-1" to "turn-1", lifecycle.last())
        }

    private fun newRouter(
        messageTimeline: MessageTimelineStore = MessageTimelineStore(),
        onTurnLifecycle: (threadId: String, turnId: String?) -> Unit = { _, _ -> },
    ): IncomingEventRouter =
        IncomingEventRouter(
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            threads = MutableStateFlow<List<CodexThread>>(emptyList()),
            activeThreadId = MutableStateFlow(null),
            messageTimeline = messageTimeline,
            commandDetailsStore = CommandExecutionDetailsStore(),
            onRequestThreadSync = {},
            onHydrateThread = {},
            onTurnLifecycle = onTurnLifecycle,
            onTurnFinished = {},
            isTurnStreamingActive = { _, _ -> true },
            shouldAutoApproveRequests = { false },
            onApprovalRequest = { _: PendingApprovalRequest, _: (PendingApprovalDecision) -> Unit -> },
            onStructuredInputRequest = { _: PendingStructuredInputRequest, _: (Map<String, List<String>>) -> Unit -> },
        )
}
