package com.remodex.mobile.core.model

import java.util.UUID

/**
 * Local non-image attachment prepared by the composer.
 * Android currently serializes these into the user text payload for turn/start.
 */
data class CodexFileAttachment(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Int,
    val textContent: String?,
    val sourceUri: String? = null,
)

