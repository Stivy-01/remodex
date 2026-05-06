package com.remodex.mobile.core.model

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class CodexImageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val thumbnailBase64JPEG: String,
    val payloadDataURL: String? = null,
    val sourceURL: String? = null,
)
