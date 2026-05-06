package com.remodex.mobile.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class CodexModelOptionTest {
    @Test
    fun fromJsonObject_parsesCamelCaseRuntimeModelFields() {
        val obj =
            Json.parseToJsonElement(
                """
                {
                  "id": " codex-fast ",
                  "model": " gpt-5.4 ",
                  "displayName": " GPT-5.4 ",
                  "description": " General runtime model ",
                  "isDefault": true,
                  "supportedReasoningEfforts": [
                    {"reasoningEffort": " low ", "description": " Fast "},
                    {"reasoningEffort": " high ", "description": " Deep "}
                  ],
                  "defaultReasoningEffort": " high "
                }
                """.trimIndent(),
            ).jsonObject

        val option = CodexModelOption.fromJsonObject(obj)

        assertEquals("codex-fast", option.id)
        assertEquals("gpt-5.4", option.model)
        assertEquals("GPT-5.4", option.displayName)
        assertEquals("General runtime model", option.description)
        assertTrue(option.isDefault)
        assertEquals("high", option.defaultReasoningEffort)
        assertEquals(
            listOf(
                CodexReasoningEffortOption(reasoningEffort = "low", description = "Fast"),
                CodexReasoningEffortOption(reasoningEffort = "high", description = "Deep"),
            ),
            option.supportedReasoningEfforts,
        )
    }

    @Test
    fun fromJsonObject_parsesSnakeCaseRuntimeModelFields() {
        val obj =
            Json.parseToJsonElement(
                """
                {
                  "id": "gpt-5.4-low",
                  "display_name": "GPT-5.4 Low",
                  "description": "Low reasoning default",
                  "is_default": true,
                  "supported_reasoning_efforts": [
                    {"reasoning_effort": "minimal", "description": "Minimal"},
                    {"reasoning_effort": "medium", "description": "Medium"}
                  ],
                  "default_reasoning_effort": "minimal"
                }
                """.trimIndent(),
            ).jsonObject

        val option = CodexModelOption.fromJsonObject(obj)

        assertEquals("gpt-5.4-low", option.id)
        assertEquals("gpt-5.4-low", option.model)
        assertEquals("GPT-5.4 Low", option.displayName)
        assertTrue(option.isDefault)
        assertEquals("minimal", option.defaultReasoningEffort)
        assertEquals(listOf("minimal", "medium"), option.supportedReasoningEfforts.map { it.reasoningEffort })
        assertEquals(listOf("Minimal", "Medium"), option.supportedReasoningEfforts.map { it.description })
    }

    @Test
    fun fromJsonObject_ignoresBlankReasoningOptionsAndBlankDefaultEffort() {
        val obj =
            Json.parseToJsonElement(
                """
                {
                  "model": "gpt-5.4",
                  "displayName": "",
                  "supportedReasoningEfforts": [
                    {"reasoningEffort": " ", "description": "Blank"},
                    {"reasoning_effort": "medium", "description": "Medium"}
                  ],
                  "defaultReasoningEffort": " "
                }
                """.trimIndent(),
            ).jsonObject

        val option = CodexModelOption.fromJsonObject(obj)

        assertEquals("gpt-5.4", option.id)
        assertEquals("gpt-5.4", option.model)
        assertEquals("gpt-5.4", option.displayName)
        assertFalse(option.isDefault)
        assertEquals(null, option.defaultReasoningEffort)
        assertEquals(
            listOf(CodexReasoningEffortOption(reasoningEffort = "medium", description = "Medium")),
            option.supportedReasoningEfforts,
        )
    }
}
