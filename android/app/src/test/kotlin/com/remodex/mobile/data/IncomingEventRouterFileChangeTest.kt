package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.PendingApprovalDecision
import com.remodex.mobile.core.model.PendingApprovalRequest
import com.remodex.mobile.core.model.PendingStructuredInputRequest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class IncomingEventRouterFileChangeTest {
    @Test
    fun fileChangeDelta_ignoresGlobLikeImageReferencesAndPreviewErrors() =
        runTest {
            val timeline = MessageTimelineStore()

            newRouter(timeline).dispatchNotification(
                method = "item/fileChange/outputDelta",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "turnId" to JSONValue.Str("turn-1"),
                            "delta" to JSONValue.Str("images/*.png"),
                        ),
                    ),
            )

            newRouter(timeline).dispatchNotification(
                method = "item/fileChange/outputDelta",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "turnId" to JSONValue.Str("turn-1"),
                            "delta" to JSONValue.Str("This image preview took too long to resize."),
                        ),
                    ),
            )

            assertTrue(timeline.messagesByThread.value["thread-1"].orEmpty().isEmpty())
        }

    private fun newRouter(messageTimeline: MessageTimelineStore): IncomingEventRouter =
        IncomingEventRouter(
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            threads = MutableStateFlow<List<CodexThread>>(emptyList()),
            activeThreadId = MutableStateFlow(null),
            messageTimeline = messageTimeline,
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
