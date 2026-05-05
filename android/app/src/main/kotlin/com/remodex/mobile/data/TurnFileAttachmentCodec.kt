package com.remodex.mobile.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.remodex.mobile.core.model.CodexFileAttachment
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlin.math.min

internal object TurnFileAttachmentCodec {
    private val textLikeExtensions =
        setOf(
            "txt", "md", "markdown", "json", "yaml", "yml", "xml", "csv", "tsv", "log",
            "kt", "kts", "swift", "java", "js", "ts", "tsx", "jsx", "py", "rb", "go", "rs",
            "c", "cpp", "h", "hpp", "cs", "sh", "ps1", "sql", "html", "css",
        )

    data class DecodeResult(
        val attachment: CodexFileAttachment? = null,
        val errorMessage: String? = null,
    )

    fun makeAttachment(
        context: Context,
        uri: Uri,
        maxBytes: Int,
        maxTextChars: Int,
        tooLargeMessage: (String, Int) -> String,
        loadFailedMessage: (String) -> String,
    ): DecodeResult {
        val resolver = context.contentResolver
        val meta = queryMetadata(context, uri)
        val fileName = meta.fileName ?: uri.lastPathSegment ?: "file"
        val mimeType = resolver.getType(uri)

        val stream = resolver.openInputStream(uri) ?: return DecodeResult(errorMessage = loadFailedMessage(fileName))
        val bytes = stream.use { input -> readBytesWithLimit(input::read, maxBytes) }

        if (bytes == null) {
            val declaredSize = meta.sizeBytes ?: maxBytes + 1
            return DecodeResult(errorMessage = tooLargeMessage(fileName, declaredSize))
        }

        val textContent =
            if (isTextLike(fileName, mimeType, bytes)) {
                decodeUtf8OrNull(bytes)?.let { decoded ->
                    decoded
                        .replace("\r\n", "\n")
                        .replace('\r', '\n')
                        .take(maxTextChars)
                        .trimEnd()
                }
            } else {
                null
            }

        return DecodeResult(
            attachment =
                CodexFileAttachment(
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = bytes.size,
                    textContent = textContent,
                    sourceUri = uri.toString(),
                ),
        )
    }

    private data class AttachmentMetadata(
        val fileName: String?,
        val sizeBytes: Int?,
    )

    private fun queryMetadata(
        context: Context,
        uri: Uri,
    ): AttachmentMetadata {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    val fileName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val size =
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            cursor.getLong(sizeIdx).toInt()
                        } else {
                            null
                        }
                    return@runCatching AttachmentMetadata(fileName, size)
                }
                AttachmentMetadata(null, null)
            } ?: AttachmentMetadata(null, null)
        }.getOrDefault(AttachmentMetadata(null, null))
    }

    private fun readBytesWithLimit(
        read: (ByteArray) -> Int,
        maxBytes: Int,
    ): ByteArray? {
        val out = ByteArrayOutputStream(min(maxBytes, 64 * 1024))
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val readCount = read(buf)
            if (readCount <= 0) break
            total += readCount
            if (total > maxBytes) return null
            out.write(buf, 0, readCount)
        }
        return out.toByteArray()
    }

    private fun isTextLike(
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
    ): Boolean {
        if (mimeType?.startsWith("text/") == true) return true
        if (mimeType?.contains("json") == true || mimeType?.contains("xml") == true) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext in textLikeExtensions) return true
        // Best-effort fallback: no NUL bytes usually indicates plain text/code.
        return bytes.none { it == 0.toByte() }
    }

    private fun decodeUtf8OrNull(bytes: ByteArray): String? =
        runCatching {
            val decoder =
                Charsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()
}
