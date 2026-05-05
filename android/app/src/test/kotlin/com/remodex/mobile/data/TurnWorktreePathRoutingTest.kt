package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexThread
import com.remodex.mobile.core.model.CodexThreadSyncState
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TurnWorktreePathRoutingTest {

    @Test
    fun liveThread_returnsNullWhenNoSibling() {
        val tmp = Files.createTempDirectory("wt-routing").toFile().canonicalPath
        val threads =
            listOf(
                CodexThread(
                    id = "other",
                    cwd = "$tmp/other",
                    syncState = CodexThreadSyncState.live,
                ),
            )

        assertNull(
            TurnWorktreePathRouting.liveThreadAtProjectPath(
                projectPath = "$tmp/missing",
                threads = threads,
                currentThreadId = "curr",
            ),
        )
    }

    @Test
    fun liveThread_prefersLaterUpdatedSibling() {
        val tmp = Files.createTempDirectory("wt-routing2").toFile().canonicalPath
        val newer = Instant.parse("2026-04-28T18:00:00Z")
        val older = Instant.parse("2026-04-01T12:00:00Z")

        val threads =
            listOf(
                CodexThread(
                    id = "old",
                    cwd = tmp,
                    updatedAt = older,
                    syncState = CodexThreadSyncState.live,
                ),
                CodexThread(
                    id = "new",
                    cwd = tmp,
                    updatedAt = newer,
                    syncState = CodexThreadSyncState.live,
                ),
            )

        assertEquals(
            "new",
            TurnWorktreePathRouting.liveThreadAtProjectPath(tmp, threads, currentThreadId = "curr")?.id,
        )
    }

    @Test
    fun liveThread_skipsSelf() {
        val tmp = Files.createTempDirectory("wt-routing3").toFile().canonicalPath

        assertNull(
            TurnWorktreePathRouting.liveThreadAtProjectPath(
                projectPath = tmp,
                threads =
                    listOf(
                        CodexThread(id = "self", cwd = tmp, syncState = CodexThreadSyncState.live),
                    ),
                currentThreadId = "self",
            ),
        )
    }
}
