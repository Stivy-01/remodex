package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.Instant

class RepoDiffLastTurnAggregatorTest {
    @Test
    fun lastTurnAggregatesTrailingSameTurnPatches() {
        val msgs =
            listOf(
                stub(
                    "## a\n+++ b/hello.txt\n+line",
                    turnId = "t1",
                    order = 0,
                ),
                stub(
                    "## b\n+++ b/hello2.kt\n+p",
                    turnId = "t2",
                    order = 1,
                ),
            )
        val patch = RepoDiffLastTurnAggregator.unifiedPatchFromLastTurn(msgs)
        assertNotNull(patch)
        assertEquals(true, patch.contains("hello2.kt"))
        assertEquals(false, patch.contains("hello.txt"))
    }

    @Test
    fun lastTurnFileRows_yieldOneRowPerPath_whenFenceContainsTwoFiles() {
        val body =
            """
            ```diff
            diff --git a/a/A.kt b/a/A.kt
            --- a/a/A.kt
            +++ b/a/A.kt
            @@ -0,0 +1 @@
            +a
            diff --git a/b/B.kt b/b/B.kt
            --- a/b/B.kt
            +++ b/b/B.kt
            @@ -0,0 +1 @@
            +b
            ```
            """.trimIndent()
        val msgs =
            listOf(
                stub(body, turnId = "t1", order = 0).copy(id = "merged-two-files"),
            )
        val rows = RepoDiffLastTurnAggregator.fileRowsFromLastTurn(msgs)
        assertEquals(2, rows.size)
        assertTrue(rows.any { it.path.endsWith("A.kt") }, rows.joinToString { it.path })
        assertTrue(rows.any { it.path.endsWith("B.kt") }, rows.joinToString { it.path })
        assertEquals(2, rows.map { it.stableKey }.toSet().size)
    }

    @Test
    fun lastTurnPlaceholderSnippetsAreIgnored() {
        val msgs =
            listOf(
                stub("[file change]", turnId = "t1", order = 0),
                stub("[file change]\n[file change]", turnId = "t1", order = 1),
            )
        val patch = RepoDiffLastTurnAggregator.unifiedPatchFromLastTurn(msgs)
        assertEquals(null, patch)
    }

    private fun stub(
        text: String,
        turnId: String?,
        order: Int,
    ): CodexMessage =
        CodexMessage(
            threadId = "th",
            role = CodexMessageRole.system,
            kind = CodexMessageKind.fileChange,
            text = text,
            createdAt = Instant.now(),
            turnId = turnId,
            orderIndex = order,
        )
}
