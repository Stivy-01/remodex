package com.remodex.mobile.ui.turn

import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Test

class CodeFenceLanguageResolverTest {
    @Test
    fun parse_handlesFenceWithoutLanguage() {
        val segments =
            MarkdownFenceSegmentParser.parse(
                """
                ```
                plain text
                ```
                """.trimIndent(),
            )

        val code = assertIs<MarkdownFenceSegment.Code>(segments.single())
        assertEquals("plain text", code.code)
        assertEquals(null, code.language)
    }

    @Test
    fun parse_preservesRepeatedFenceLanguagesByContentOrder() {
        val segments =
            MarkdownFenceSegmentParser.parse(
                """
                ```kotlin
                println("hi")
                ```

                ```swift
                println("hi")
                ```
                """.trimIndent(),
            )

        val first = assertIs<MarkdownFenceSegment.Code>(segments[0])
        val second = assertIs<MarkdownFenceSegment.Code>(segments[1])
        assertEquals("kotlin", first.language)
        assertEquals("swift", second.language)
        assertEquals("""println("hi")""", first.code)
        assertEquals("""println("hi")""", second.code)
    }
}
