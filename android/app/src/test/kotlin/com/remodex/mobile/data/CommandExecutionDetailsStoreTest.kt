package com.remodex.mobile.data

import com.remodex.mobile.core.model.CommandExecutionDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandExecutionDetailsStoreTest {
    @Test
    fun upsert_createsDetailsWithCommandAndCwd() {
        val store = CommandExecutionDetailsStore()

        store.upsertFromState(
            itemId = "item-1",
            fullCommand = "git status",
            cwd = "/repo",
            exitCode = null,
            durationMs = null,
        )

        val details = store.detailsByItemId.value.getValue("item-1")
        assertEquals("git status", details.fullCommand)
        assertEquals("/repo", details.cwd)
    }

    @Test
    fun upsert_preservesLongerCommand() {
        val store = CommandExecutionDetailsStore()

        store.upsertFromState("item-1", "git", null, null, null)
        store.upsertFromState("item-1", "git status --short", null, null, null)
        store.upsertFromState("item-1", "git status", null, null, null)

        assertEquals("git status --short", store.detailsByItemId.value.getValue("item-1").fullCommand)
    }

    @Test
    fun upsert_updatesExitCodeAndDurationOnCompletion() {
        val store = CommandExecutionDetailsStore()

        store.upsertFromState("item-1", "npm test", "/repo", null, null)
        store.upsertFromState("item-1", null, null, 1, 2450)

        val details = store.detailsByItemId.value.getValue("item-1")
        assertEquals("npm test", details.fullCommand)
        assertEquals("/repo", details.cwd)
        assertEquals(1, details.exitCode)
        assertEquals(2450, details.durationMs)
    }

    @Test
    fun appendOutput_truncatesToMaxOutputLines() {
        val store = CommandExecutionDetailsStore()
        store.upsertFromState("item-1", "command", null, null, null)

        store.appendOutput(
            itemId = "item-1",
            chunk = (1..35).joinToString("\n") { "line-$it" },
        )

        val lines = store.detailsByItemId.value.getValue("item-1").outputTail.lines()
        assertEquals(CommandExecutionDetails.MAX_OUTPUT_LINES, lines.size)
        assertEquals("line-6", lines.first())
        assertEquals("line-35", lines.last())
    }

    @Test
    fun appendOutput_withoutItemIdOrDetailsDoesNotCreateDirtyState() {
        val store = CommandExecutionDetailsStore()

        store.appendOutput(null, "ignored")
        store.appendOutput("missing", "ignored")

        assertTrue(store.detailsByItemId.value.isEmpty())
    }
}
