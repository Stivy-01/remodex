package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.PendingApprovalDecision
import com.remodex.mobile.core.model.PendingApprovalRequest
import com.remodex.mobile.core.model.PendingStructuredInputRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class IncomingEventRouterImageGenerationTest {
    @Test
    fun imageGenerationEnd_addsAssistantImageAttachment() =
        runTest {
            val timeline = MessageTimelineStore()
            val router = newRouter(messageTimeline = timeline)

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
                method = "codex/event/image_generation_end",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "turnId" to JSONValue.Str("turn-1"),
                            "call_id" to JSONValue.Str("ig-1"),
                            "saved_path" to JSONValue.Str("/tmp/generated.png"),
                        ),
                    ),
            )

            val row = timeline.messagesByThread.value["thread-1"].orEmpty().single()
            assertEquals(CodexMessageRole.assistant, row.role)
            assertEquals("ig-1", row.itemId)
            assertEquals("", row.text)
            assertEquals("/tmp/generated.png", row.attachments.single().sourceURL)
        }

    @Test
    fun imageGenerationEnd_updatesExistingImageItemInsteadOfDuplicating() =
        runTest {
            val timeline = MessageTimelineStore()
            val router = newRouter(messageTimeline = timeline)

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
            repeat(2) {
                router.dispatchNotification(
                    method = "codex/event/image_generation_end",
                    params =
                        JSONValue.Obj(
                            mapOf(
                                "turnId" to JSONValue.Str("turn-1"),
                                "itemId" to JSONValue.Str("ig-1"),
                                "saved_path" to JSONValue.Str("/tmp/generated.png"),
                            ),
                        ),
                )
            }

            val messages = timeline.messagesByThread.value["thread-1"].orEmpty()
            assertEquals(1, messages.size)
            assertEquals("ig-1", messages.single().itemId)
        }

    private fun newRouter(
        messageTimeline: MessageTimelineStore = MessageTimelineStore(),
    ): IncomingEventRouter =
        IncomingEventRouter(
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            threads = MutableStateFlow<List<CodexThread>>(emptyList()),
            activeThreadId = MutableStateFlow(null),
            messageTimeline = messageTimeline,
            commandDetailsStore = CommandExecutionDetailsStore(),
            onRequestThreadSync = {},
            onHydrateThread = {},
            onTurnLifecycle = { _, _ -> },
            onTurnFinished = {},
            isTurnStreamingActive = { _, _ -> false },
            shouldAutoApproveRequests = { false },
            onApprovalRequest = { _: PendingApprovalRequest, _: (PendingApprovalDecision) -> Unit -> },
            onStructuredInputRequest = { _: PendingStructuredInputRequest, _: (Map<String, List<String>>) -> Unit -> },
        )
}
