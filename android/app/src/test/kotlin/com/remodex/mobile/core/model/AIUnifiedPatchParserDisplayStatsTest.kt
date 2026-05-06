package com.remodex.mobile.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AIUnifiedPatchParserDisplayStatsTest {

    /** Last-turn timeline chunks often omit `+++` / `diff --git` rows; analyze yields no fileChanges. */
    @Test
    fun additionsDeletionsForDisplay_countsHunkLinesWhenPathMissing() {
        val fragment =
            """
            @@ -34,2 +34,4 @@
             package x
             const val OLD = true
            +const val NEW = false
            +const val MORE = 1

            @@ -219,2 +221,7 @@

            -removed
            +a
            +b

            """.trimIndent()

        val (adds, dels) = AIUnifiedPatchParser.additionsDeletionsForDisplay(fragment)
        assertEquals(4, adds)
        assertEquals(1, dels)
    }

    @Test
    fun countUnifiedDiffBodyLineStats_ignoresHeaders() {
        val s =
            AIUnifiedPatchParser.countUnifiedDiffBodyLineStats(
                """
                --- a/File.kt
                +++ b/File.kt
                @@ -1 +1 @@
                -old
                +new
                """.trimIndent(),
            )
        assertEquals(1, s.first)
        assertEquals(1, s.second)
    }
}
