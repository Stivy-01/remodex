package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.TurnCodeCommentDirectiveParsing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadHistoryDecoderSanitizationTest {

    @Test
    fun decodeFromThreadRead_preservesCodeCommentDirectivesInHistoryText() {
        val history =
            ThreadHistoryDecoder.decodeFromThreadRead(
                threadId = "thread-1",
                threadObject =
                    mapOf(
                        "turns" to
                            JSONValue.Arr(
                                listOf(
                                    JSONValue.Obj(
                                        mapOf(
                                            "id" to JSONValue.Str("turn-1"),
                                            "items" to
                                                JSONValue.Arr(
                                                    listOf(
                                                        JSONValue.Obj(
                                                            mapOf(
                                                                "id" to JSONValue.Str("item-1"),
                                                                "type" to JSONValue.Str("assistant_message"),
                                                                "content" to
                                                                    JSONValue.Arr(
                                                                        listOf(
                                                                            JSONValue.Obj(
                                                                                mapOf(
                                                                                    "type" to JSONValue.Str("text"),
                                                                                    "text" to JSONValue.Str(
                                                                                        """Hi ::code-comment{title="Bug" body="Body" file="Foo.kt"} there""",
                                                                                    ),
                                                                                ),
                                                                            ),
                                                                        ),
                                                                    ),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                        ),
                                    ),
                                ),
                            ),
                    ),
            )

        assertEquals(1, history.size)
        assertEquals("""Hi ::code-comment{title="Bug" body="Body" file="Foo.kt"} there""", history.first().text)

        val renderLayerParse = TurnCodeCommentDirectiveParsing.parseCodeCommentDirectives(history.first().text)
        assertEquals("Hi there", renderLayerParse.cleanedText)
        assertEquals(1, renderLayerParse.findings.size)
        assertEquals("Bug", renderLayerParse.findings.single().title)
    }

    @Test
    fun decodeFromThreadRead_preservesAssistantPhaseMetadata() {
        val history =
            ThreadHistoryDecoder.decodeFromThreadRead(
                threadId = "thread-1",
                threadObject =
                    mapOf(
                        "turns" to
                            JSONValue.Arr(
                                listOf(
                                    JSONValue.Obj(
                                        mapOf(
                                            "id" to JSONValue.Str("turn-1"),
                                            "items" to
                                                JSONValue.Arr(
                                                    listOf(
                                                        JSONValue.Obj(
                                                            mapOf(
                                                                "id" to JSONValue.Str("item-1"),
                                                                "type" to JSONValue.Str("assistant_message"),
                                                                "phase" to JSONValue.Str("final-answer"),
                                                                "content" to
                                                                    JSONValue.Arr(
                                                                        listOf(
                                                                            JSONValue.Obj(
                                                                                mapOf(
                                                                                    "type" to JSONValue.Str("text"),
                                                                                    "text" to JSONValue.Str("Done"),
                                                                                ),
                                                                            ),
                                                                        ),
                                                                    ),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                        ),
                                    ),
                                ),
                            ),
                    ),
            )

        assertEquals(1, history.size)
        assertEquals(CodexMessageRole.assistant, history.first().role)
        assertEquals("final_answer", history.first().assistantPhase)
    }

    @Test
    fun decodeCompletedItem_preservesAssistantPhaseMetadata() {
        val decoded =
            ThreadHistoryDecoder.decodeCompletedItem(
                mapOf(
                    "type" to JSONValue.Str("message"),
                    "role" to JSONValue.Str("assistant"),
                    "phase" to JSONValue.Str("final-answer"),
                    "content" to
                        JSONValue.Arr(
                            listOf(
                                JSONValue.Obj(
                                    mapOf(
                                        "type" to JSONValue.Str("output_text"),
                                        "text" to JSONValue.Str("Done"),
                                    ),
                                ),
                            ),
                        ),
                ),
            )

        assertTrue(decoded != null)
        assertEquals(CodexMessageRole.assistant, decoded?.role)
        assertEquals("final_answer", decoded?.assistantPhase)
    }

    @Test
    fun decodeCompletedItem_stripsThinkingTagsButPreservesDirectives() {
        val decoded =
            ThreadHistoryDecoder.decodeCompletedItem(
                mapOf(
                    "type" to JSONValue.Str("reasoning"),
                    "summary" to JSONValue.Str(
                        """::code-comment{title="Bug" body="Body" file="Foo.kt"}<thinking>Deep reasoning</thinking>""",
                    ),
                ),
            )

        assertTrue(decoded != null)
        assertEquals(CodexMessageKind.thinking, decoded?.kind)
        assertEquals("""::code-comment{title="Bug" body="Body" file="Foo.kt"}Deep reasoning""", decoded?.text)
    }

    @Test
    fun decodeCompletedItem_fileChange_prefersTextOverPlaceholderPreview() {
        val decoded =
            ThreadHistoryDecoder.decodeCompletedItem(
                mapOf(
                    "type" to JSONValue.Str("file_change"),
                    "text" to JSONValue.Str("Path: Turn/CopyBlockButton.swift\nKind: update\nTotals: +1 -2"),
                ),
            )

        assertTrue(decoded != null)
        assertEquals(CodexMessageKind.fileChange, decoded?.kind)
        assertTrue(decoded?.text?.contains("CopyBlockButton.swift") == true)
    }

    @Test
    fun decodeCompletedItem_fileChange_rendersChangesArrayIntoBody() {
        val decoded =
            ThreadHistoryDecoder.decodeCompletedItem(
                mapOf(
                    "type" to JSONValue.Str("file_change"),
                    "changes" to
                        JSONValue.Arr(
                            listOf(
                                JSONValue.Obj(
                                    mapOf(
                                        "path" to JSONValue.Str("Turn/CopyBlockButton.swift"),
                                        "kind" to JSONValue.Str("update"),
                                        "totals" to JSONValue.Obj(mapOf("additions" to JSONValue.NumLong(1), "deletions" to JSONValue.NumLong(2))),
                                        "diff" to JSONValue.Str("@@ -1,1 +1,1 @@\n- a\n+ b\n"),
                                    ),
                                ),
                            ),
                        ),
                ),
            )

        assertTrue(decoded != null)
        assertEquals(CodexMessageKind.fileChange, decoded?.kind)
        assertTrue(decoded?.text?.contains("Path: Turn/CopyBlockButton.swift") == true)
        assertTrue(decoded?.text?.contains("Totals: +1 -2") == true)
        assertTrue(decoded?.text?.contains("```diff") == true)
    }

    @Test
    fun decodeCompletedItem_diffSuppressesGenericPreviewWithoutFileEvidence() {
        val decoded =
            ThreadHistoryDecoder.decodeCompletedItem(
                mapOf(
                    "type" to JSONValue.Str("diff"),
                    "title" to JSONValue.Str("Repository changes"),
                    "summary" to JSONValue.Str("Updated files"),
                    "path" to JSONValue.Str("src/App.kt"),
                ),
            )

        assertEquals(null, decoded)
    }

    @Test
    fun decodeCompletedItem_diffRendersUnifiedPatchWithFileEvidence() {
        val decoded =
            ThreadHistoryDecoder.decodeCompletedItem(
                mapOf(
                    "type" to JSONValue.Str("diff"),
                    "diff" to
                        JSONValue.Str(
                            """
                            diff --git a/src/App.kt b/src/App.kt
                            --- a/src/App.kt
                            +++ b/src/App.kt
                            @@ -1 +1,2 @@
                             class App
                            +val enabled = true
                            """.trimIndent(),
                        ),
                ),
            )

        assertTrue(decoded != null)
        assertEquals(CodexMessageKind.fileChange, decoded?.kind)
        assertTrue(decoded?.text?.contains("diff --git a/src/App.kt b/src/App.kt") == true)
    }

    @Test
    fun decodeCompletedItem_imageViewRendersAssistantImageAttachment() {
        val decoded =
            ThreadHistoryDecoder.decodeCompletedItem(
                mapOf(
                    "id" to JSONValue.Str("view-1"),
                    "type" to JSONValue.Str("imageView"),
                    "path" to JSONValue.Str("/tmp/generated view.png"),
                ),
            )

        assertTrue(decoded != null)
        assertEquals(CodexMessageRole.assistant, decoded?.role)
        assertEquals(CodexMessageKind.chat, decoded?.kind)
        assertEquals("", decoded?.text)
        assertEquals("/tmp/generated view.png", decoded?.attachments?.single()?.sourceURL)
    }

    @Test
    fun decodeFromThreadRead_imageGenerationHistoryRendersAssistantImageAttachment() {
        val messages =
            ThreadHistoryDecoder.decodeFromThreadRead(
                threadId = "thread-image",
                threadObject =
                    mapOf(
                        "turns" to
                            JSONValue.Arr(
                                listOf(
                                    JSONValue.Obj(
                                        mapOf(
                                            "id" to JSONValue.Str("turn-image"),
                                            "items" to
                                                JSONValue.Arr(
                                                    listOf(
                                                        JSONValue.Obj(
                                                            mapOf(
                                                                "id" to JSONValue.Str("ig-1"),
                                                                "type" to JSONValue.Str("image_generation"),
                                                                "saved_path" to JSONValue.Str("/tmp/generated.png"),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                        ),
                                    ),
                                ),
                            ),
                    ),
            )

        val row = messages.single()
        assertEquals(CodexMessageRole.assistant, row.role)
        assertEquals(CodexMessageKind.chat, row.kind)
        assertEquals("ig-1", row.itemId)
        assertEquals("/tmp/generated.png", row.attachments.single().sourceURL)
    }
}
