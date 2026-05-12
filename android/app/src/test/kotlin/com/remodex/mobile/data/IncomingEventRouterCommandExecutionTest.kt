package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.PendingApprovalDecision
import com.remodex.mobile.core.model.PendingApprovalRequest
import com.remodex.mobile.core.model.PendingStructuredInputRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class IncomingEventRouterCommandExecutionTest {
    @Test
    fun execCommandBegin_withCallIdCreatesDetails() {
        val detailsStore = CommandExecutionDetailsStore()

        newRouter(commandDetailsStore = detailsStore).dispatchNotification(
            method = "exec_command_begin",
            params =
                JSONValue.Obj(
                    mapOf(
                        "call_id" to JSONValue.Str("call-1"),
                        "command" to
                            JSONValue.Arr(
                                listOf(
                                    JSONValue.Str("bash"),
                                    JSONValue.Str("-lc"),
                                    JSONValue.Str("npm test"),
                                ),
                            ),
                        "cwd" to JSONValue.Str("/repo"),
                    ),
                ),
        )

        val details = detailsStore.detailsByItemId.value.getValue("call-1")
        assertEquals("bash -lc npm test", details.fullCommand)
        assertEquals("/repo", details.cwd)
    }

    @Test
    fun execCommandOutputDelta_updatesOutputTail() {
        val detailsStore = CommandExecutionDetailsStore()
        val router = newRouter(commandDetailsStore = detailsStore)
        router.dispatchNotification(
            method = "exec_command_begin",
            params =
                JSONValue.Obj(
                    mapOf(
                        "call_id" to JSONValue.Str("call-1"),
                        "command" to JSONValue.Str("npm test"),
                    ),
                ),
        )

        router.dispatchNotification(
            method = "exec_command_output_delta",
            params =
                JSONValue.Obj(
                    mapOf(
                        "call_id" to JSONValue.Str("call-1"),
                        "delta" to JSONValue.Str("line 1\n"),
                    ),
                ),
        )

        assertEquals("line 1\n", detailsStore.detailsByItemId.value.getValue("call-1").outputTail)
    }

    @Test
    fun execCommandEnd_updatesExitCodeAndDuration() {
        val detailsStore = CommandExecutionDetailsStore()
        val router = newRouter(commandDetailsStore = detailsStore)
        router.dispatchNotification(
            method = "exec_command_begin",
            params =
                JSONValue.Obj(
                    mapOf(
                        "call_id" to JSONValue.Str("call-1"),
                        "command" to JSONValue.Str("npm test"),
                    ),
                ),
        )

        router.dispatchNotification(
            method = "exec_command_end",
            params =
                JSONValue.Obj(
                    mapOf(
                        "call_id" to JSONValue.Str("call-1"),
                        "result" to
                            JSONValue.Obj(
                                mapOf(
                                    "exitCode" to JSONValue.NumLong(1),
                                    "durationMs" to JSONValue.NumLong(1500),
                                ),
                            ),
                    ),
                ),
        )

        val details = detailsStore.detailsByItemId.value.getValue("call-1")
        assertEquals(1, details.exitCode)
        assertEquals(1500, details.durationMs)
    }

    @Test
    fun modernOutputDelta_updatesTimelineAndDetails() =
        runTest {
            val timeline = MessageTimelineStore()
            val detailsStore = CommandExecutionDetailsStore()

            newRouter(
                messageTimeline = timeline,
                commandDetailsStore = detailsStore,
            ).dispatchNotification(
                method = "item/commandExecution/outputDelta",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "turnId" to JSONValue.Str("turn-1"),
                            "itemId" to JSONValue.Str("item-1"),
                            "command" to JSONValue.Str("git status"),
                            "delta" to JSONValue.Str("On branch main"),
                        ),
                    ),
            )

            val row = timeline.messagesByThread.value["thread-1"]?.single()
            assertEquals(CodexMessageKind.commandExecution, row?.kind)
            assertEquals("item-1", row?.itemId)
            assertEquals("running> git status", row?.text)
            val details = detailsStore.detailsByItemId.value.getValue("item-1")
            assertEquals("git status", details.fullCommand)
            assertEquals("On branch main", details.outputTail)
        }

    @Test
    fun outputDeltaWithoutItemId_updatesTimelineOnly() =
        runTest {
            val timeline = MessageTimelineStore()
            val detailsStore = CommandExecutionDetailsStore()

            newRouter(
                messageTimeline = timeline,
                commandDetailsStore = detailsStore,
            ).dispatchNotification(
                method = "item/commandExecution/outputDelta",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "turnId" to JSONValue.Str("turn-1"),
                            "delta" to JSONValue.Str("fallback text"),
                        ),
                    ),
            )

            val row = timeline.messagesByThread.value["thread-1"]?.single()
            assertEquals(CodexMessageKind.commandExecution, row?.kind)
            assertEquals(null, row?.itemId)
            assertEquals("fallback text", row?.text)
            assertTrue(detailsStore.detailsByItemId.value.isEmpty())
        }

    @Test
    fun legacyOutputDelta_doesNotReplaceExistingPreviewText() =
        runTest {
            val timeline = MessageTimelineStore()
            val detailsStore = CommandExecutionDetailsStore()
            val router = newRouter(messageTimeline = timeline, commandDetailsStore = detailsStore)

            router.dispatchNotification(
                method = "exec_command_begin",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "turnId" to JSONValue.Str("turn-1"),
                            "call_id" to JSONValue.Str("call-1"),
                            "command" to JSONValue.Str("npm test"),
                        ),
                    ),
            )

            val before = timeline.messagesByThread.value["thread-1"]?.single()?.text

            router.dispatchNotification(
                method = "exec_command_output_delta",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "turnId" to JSONValue.Str("turn-1"),
                            "call_id" to JSONValue.Str("call-1"),
                            "delta" to JSONValue.Str("line 1\n"),
                        ),
                    ),
            )

            val after = timeline.messagesByThread.value["thread-1"]?.single()?.text
            assertEquals(before, after)
            assertEquals("line 1\n", detailsStore.detailsByItemId.value.getValue("call-1").outputTail)
        }

    @Test
    fun legacyEnd_completesExistingPreviewRow() =
        runTest {
            val timeline = MessageTimelineStore()
            val detailsStore = CommandExecutionDetailsStore()
            val router = newRouter(messageTimeline = timeline, commandDetailsStore = detailsStore)

            router.dispatchNotification(
                method = "exec_command_begin",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "turnId" to JSONValue.Str("turn-1"),
                            "call_id" to JSONValue.Str("call-1"),
                            "command" to JSONValue.Str("npm test"),
                        ),
                    ),
            )

            router.dispatchNotification(
                method = "exec_command_end",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "threadId" to JSONValue.Str("thread-1"),
                            "turnId" to JSONValue.Str("turn-1"),
                            "call_id" to JSONValue.Str("call-1"),
                            "result" to JSONValue.Obj(mapOf("exitCode" to JSONValue.NumLong(0))),
                        ),
                    ),
            )

            val row = timeline.messagesByThread.value["thread-1"]?.single()
            assertEquals(CodexMessageKind.commandExecution, row?.kind)
            assertEquals("call-1", row?.itemId)
            assertEquals("completed> npm test", row?.text)
        }

    private fun newRouter(
        messageTimeline: MessageTimelineStore = MessageTimelineStore(),
        commandDetailsStore: CommandExecutionDetailsStore = CommandExecutionDetailsStore(),
    ): IncomingEventRouter =
        IncomingEventRouter(
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            threads = MutableStateFlow<List<CodexThread>>(emptyList()),
            activeThreadId = MutableStateFlow(null),
            messageTimeline = messageTimeline,
            commandDetailsStore = commandDetailsStore,
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
