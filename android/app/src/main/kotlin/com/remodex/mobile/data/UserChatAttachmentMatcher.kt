package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexImageAttachment

internal object UserChatAttachmentMatcher {
    fun compatible(
        existing: List<CodexImageAttachment>,
        incoming: List<CodexImageAttachment>,
    ): Boolean =
        existing == incoming ||
            existing.isEmpty() ||
            incoming.isEmpty() ||
            existing.contentKeys() == incoming.contentKeys()

    fun merge(
        existing: List<CodexImageAttachment>,
        incoming: List<CodexImageAttachment>,
    ): List<CodexImageAttachment> =
        when {
            existing.isNotEmpty() -> existing
            incoming.isNotEmpty() -> incoming
            else -> emptyList()
        }

    private fun List<CodexImageAttachment>.contentKeys(): List<String> =
        map { attachment ->
            attachment.payloadDataURL?.trim()?.takeIf { it.isNotEmpty() }
                ?: attachment.sourceURL?.trim()?.takeIf { it.isNotEmpty() }
                ?: attachment.thumbnailBase64JPEG.trim()
        }
}
