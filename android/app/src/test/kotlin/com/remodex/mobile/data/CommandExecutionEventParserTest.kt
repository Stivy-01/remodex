package com.remodex.mobile.data

import com.remodex.mobile.core.model.JSONValue
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandExecutionEventParserTest {
    @Test
    fun parse_joinsLegacyCommandArray() {
        val state =
            CommandExecutionEventParser.parse(
                params =
                    mapOf(
                        "call_id" to JSONValue.Str("call-1"),
                        "command" to
                            JSONValue.Arr(
                                listOf(
                                    JSONValue.Str("bash"),
                                    JSONValue.Str("-lc"),
                                    JSONValue.Str("./gradlew test"),
                                ),
                            ),
                    ),
                eventObject = null,
                method = "exec_command_begin",
            )

        assertEquals("call-1", state.itemId)
        assertEquals("bash -lc ./gradlew test", state.fullCommand)
    }

    @Test
    fun parse_readsModernCommandCwdExitCodeAndDuration() {
        val state =
            CommandExecutionEventParser.parse(
                params =
                    mapOf(
                        "itemId" to JSONValue.Str("item-1"),
                        "command" to JSONValue.Str("git diff"),
                        "cwd" to JSONValue.Str("/repo"),
                        "exitCode" to JSONValue.NumLong(0),
                        "durationMs" to JSONValue.NumLong(1200),
                    ),
                eventObject = null,
                method = "item/commandExecution/outputDelta",
            )

        assertEquals("item-1", state.itemId)
        assertEquals("git diff", state.fullCommand)
        assertEquals("/repo", state.cwd)
        assertEquals(0, state.exitCode)
        assertEquals(1200, state.durationMs)
    }

    @Test
    fun parse_readsNestedResultExitCodeAndDuration() {
        val state =
            CommandExecutionEventParser.parse(
                params =
                    mapOf(
                        "id" to JSONValue.Str("item-1"),
                        "result" to
                            JSONValue.Obj(
                                mapOf(
                                    "exit_code" to JSONValue.NumLong(2),
                                    "duration_ms" to JSONValue.NumLong(345),
                                ),
                            ),
                    ),
                eventObject = null,
                method = "exec_command_end",
            )

        assertEquals(2, state.exitCode)
        assertEquals(345, state.durationMs)
    }

    @Test
    fun parse_derivesPhaseFromStatusAndMethod() {
        assertEquals("failed", phase(status = "failed"))
        assertEquals("failed", phase(status = "error"))
        assertEquals("stopped", phase(status = "cancelled"))
        assertEquals("stopped", phase(status = "interrupted"))
        assertEquals("completed", phase(status = "done"))
        assertEquals("completed", phase(status = null, method = "exec_command_end"))
        assertEquals("running", phase(status = null, method = "exec_command_begin"))
    }

    @Test
    fun parse_acceptsNumericStringsForExitCodeAndDuration() {
        val state =
            CommandExecutionEventParser.parse(
                params =
                    mapOf(
                        "callId" to JSONValue.Str("call-1"),
                        "exit_code" to JSONValue.Str("127"),
                        "duration_ms" to JSONValue.Str("5000"),
                    ),
                eventObject = null,
                method = "exec_command_end",
            )

        assertEquals(127, state.exitCode)
        assertEquals(5000, state.durationMs)
    }

    private fun phase(
        status: String?,
        method: String = "item/commandExecution/outputDelta",
    ): String =
        CommandExecutionEventParser.parse(
            params = status?.let { mapOf("status" to JSONValue.Str(it)) },
            eventObject = null,
            method = method,
        ).phase
}
