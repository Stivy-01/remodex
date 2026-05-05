package com.remodex.mobile.data

import com.remodex.mobile.core.model.PendingStructuredInputQuestion
import kotlin.test.Test
import kotlin.test.assertEquals

class StructuredInputTimelineFormatterTest {
    @Test
    fun bodyText_emptyQuestions_returnsDefault() {
        assertEquals("Input requested", StructuredInputTimelineFormatter.bodyText(emptyList()))
    }

    @Test
    fun bodyText_singleQuestion_usesPromptText() {
        assertEquals(
            "Pick mode",
            StructuredInputTimelineFormatter.bodyText(
                listOf(
                    PendingStructuredInputQuestion(
                        id = "q1",
                        header = "H",
                        question = "Pick mode",
                        options = emptyList(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun bodyText_fallsBackToHeaderOrId() {
        assertEquals(
            "Header only",
            StructuredInputTimelineFormatter.bodyText(
                listOf(
                    PendingStructuredInputQuestion(
                        id = "id-z",
                        header = "Header only",
                        question = "   ",
                        options = emptyList(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun bodyText_multipleQuestions_numberedSections() {
        val text =
            StructuredInputTimelineFormatter.bodyText(
                listOf(
                    PendingStructuredInputQuestion(
                        id = "a",
                        header = "",
                        question = "First?",
                        options = emptyList(),
                    ),
                    PendingStructuredInputQuestion(
                        id = "b",
                        header = "",
                        question = "Second?",
                        options = emptyList(),
                    ),
                ),
            )
        assertEquals(
            """
            1. First?

            2. Second?
            """.trimIndent(),
            text,
        )
    }
}
