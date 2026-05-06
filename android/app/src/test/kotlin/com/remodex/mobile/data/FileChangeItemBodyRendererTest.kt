package com.remodex.mobile.data

import com.remodex.mobile.core.model.JSONValue
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileChangeItemBodyRendererTest {
    @Test
    fun renderFromIncomingItem_ignoresGlobLikeImageReferencesAndPreviewErrors() {
        val rendered =
            FileChangeItemBodyRenderer.renderFromIncomingItem(
                mapOf(
                    "changes" to
                        JSONValue.Arr(
                            listOf(
                                JSONValue.Obj(
                                    mapOf(
                                        "path" to JSONValue.Str("images/*.png"),
                                        "kind" to JSONValue.Str("update"),
                                        "diff" to JSONValue.Str("image preview timed out"),
                                    ),
                                ),
                            ),
                        ),
                    "diff" to JSONValue.Str("This image preview could not be converted into a lightweight phone preview."),
                ),
            )

        assertNull(rendered)
    }

    @Test
    fun renderFromIncomingItem_keepsValidChangesWhileSkippingNoise() {
        val rendered =
            FileChangeItemBodyRenderer.renderFromIncomingItem(
                mapOf(
                    "changes" to
                        JSONValue.Arr(
                            listOf(
                                JSONValue.Obj(
                                    mapOf(
                                        "path" to JSONValue.Str("src/App.kt"),
                                        "kind" to JSONValue.Str("update"),
                                        "additions" to JSONValue.NumLong(2),
                                        "deletions" to JSONValue.NumLong(1),
                                    ),
                                ),
                                JSONValue.Obj(
                                    mapOf(
                                        "path" to JSONValue.Str("tmp/*.png"),
                                        "kind" to JSONValue.Str("update"),
                                        "diff" to JSONValue.Str("The image file no longer exists on this Mac."),
                                    ),
                                ),
                            ),
                        ),
                ),
            )

        assertTrue(rendered?.contains("Path: src/App.kt") == true, rendered)
        assertTrue(rendered?.contains("tmp/*.png") == false, rendered)
    }
}
