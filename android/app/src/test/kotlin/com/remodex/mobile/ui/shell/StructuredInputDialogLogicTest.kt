package com.remodex.mobile.ui.shell

import com.remodex.mobile.core.model.PendingStructuredInputQuestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructuredInputDialogLogicTest {
    @Test
    fun buildStructuredInputAnswersPayload_prefersTypedAnswersAndTrimsWhitespace() {
        val questions =
            listOf(
                PendingStructuredInputQuestion(
                    id = "mode",
                    header = "Mode",
                    question = "Pick a mode",
                    options = emptyList(),
                ),
                PendingStructuredInputQuestion(
                    id = "name",
                    header = "Name",
                    question = "Choose a label",
                    options = emptyList(),
                ),
            )

        val payload =
            buildStructuredInputAnswersPayload(
                questions = questions,
                typedAnswersByQuestionId = mapOf("mode" to "  fast  "),
                selectedOptionByQuestionId = mapOf("name" to "  stable "),
            )

        assertEquals(
            mapOf(
                "mode" to listOf("fast"),
                "name" to listOf("stable"),
            ),
            payload,
        )
    }

    @Test
    fun buildStructuredInputAnswersPayload_returnsNullWhenAnyQuestionIsMissing() {
        val questions =
            listOf(
                PendingStructuredInputQuestion(
                    id = "mode",
                    header = "Mode",
                    question = "Pick a mode",
                    options = emptyList(),
                ),
                PendingStructuredInputQuestion(
                    id = "name",
                    header = "Name",
                    question = "Choose a label",
                    options = emptyList(),
                ),
            )

        val payload =
            buildStructuredInputAnswersPayload(
                questions = questions,
                typedAnswersByQuestionId = mapOf("mode" to "fast"),
                selectedOptionByQuestionId = emptyMap(),
            )

        assertNull(payload)
    }

    @Test
    fun shouldMaskStructuredInput_detectsSecretKeywords() {
        val secretQuestion =
            PendingStructuredInputQuestion(
                id = "token",
                header = "Credentials",
                question = "Enter the API token",
                options = emptyList(),
            )
        val regularQuestion =
            PendingStructuredInputQuestion(
                id = "topic",
                header = "Topic",
                question = "Pick a topic",
                options = emptyList(),
            )

        assertTrue(shouldMaskStructuredInput(secretQuestion))
        assertFalse(shouldMaskStructuredInput(regularQuestion))
    }
}
