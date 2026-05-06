package com.remodex.mobile.ui.turn

import android.graphics.Bitmap
import com.remodex.mobile.core.model.CodexFileAttachment
import com.remodex.mobile.core.model.CodexImageAttachment
import java.io.ByteArrayOutputStream

internal fun List<TurnComposerAttachment>.withLoadingAttachment(attachmentId: String): List<TurnComposerAttachment> =
    this + TurnComposerAttachment(attachmentId, TurnComposerAttachmentState.Loading)

internal fun List<TurnComposerAttachment>.withImageAttachmentResult(
    attachmentId: String,
    attachment: CodexImageAttachment?,
    failedMessage: String,
): List<TurnComposerAttachment> =
    map { existing ->
        if (existing.id != attachmentId) {
            existing
        } else if (attachment != null) {
            existing.copy(state = TurnComposerAttachmentState.ReadyImage(attachment))
        } else {
            existing.copy(state = TurnComposerAttachmentState.Failed(failedMessage))
        }
    }

internal fun List<TurnComposerAttachment>.withFileAttachmentResult(
    attachmentId: String,
    attachment: CodexFileAttachment?,
    failedMessage: String,
): List<TurnComposerAttachment> =
    map { existing ->
        if (existing.id != attachmentId) {
            existing
        } else if (attachment != null) {
            existing.copy(state = TurnComposerAttachmentState.ReadyFile(attachment))
        } else {
            existing.copy(state = TurnComposerAttachmentState.Failed(failedMessage))
        }
    }

internal fun appendFileAttachmentsToDraft(
    baseText: String,
    files: List<CodexFileAttachment>,
    binarySummary: String,
): String {
    if (files.isEmpty()) return baseText.trim()
    val sb = StringBuilder(baseText.trimEnd())
    if (sb.isNotEmpty()) {
        sb.append("\n\n")
    }
    sb.append("Attached files:\n")
    files.forEachIndexed { index, file ->
        sb
            .append(index + 1)
            .append(". ")
            .append(file.fileName)
            .append(" (")
            .append(formatBytes(file.sizeBytes))
        file.mimeType?.takeIf { it.isNotBlank() }?.let { mime ->
            sb.append(", ").append(mime)
        }
        sb.append(")\n")
        val content = file.textContent?.trim()
        if (!content.isNullOrEmpty()) {
            sb.append("```").append(inferCodeFenceLanguage(file.fileName)).append('\n')
            sb.append(content).append('\n')
            sb.append("```\n")
        } else {
            sb.append(binarySummary).append('\n')
        }
    }
    return sb.toString().trimEnd()
}

internal fun inferCodeFenceLanguage(fileName: String): String =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> "kotlin"
        "swift" -> "swift"
        "js", "jsx" -> "javascript"
        "ts", "tsx" -> "typescript"
        "py" -> "python"
        "java" -> "java"
        "json" -> "json"
        "xml" -> "xml"
        "yaml", "yml" -> "yaml"
        "md", "markdown" -> "markdown"
        "sql" -> "sql"
        "sh" -> "bash"
        "ps1" -> "powershell"
        "html" -> "html"
        "css" -> "css"
        else -> "text"
    }

internal fun formatBytes(bytes: Int): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1fKB", kb)
    val mb = kb / 1024.0
    return String.format("%.1fMB", mb)
}

internal fun Bitmap.toJpegByteArray(quality: Int = 80): ByteArray? {
    val out = ByteArrayOutputStream()
    return if (compress(Bitmap.CompressFormat.JPEG, quality, out)) {
        out.toByteArray()
    } else {
        null
    }
}
