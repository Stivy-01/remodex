package com.remodex.mobile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.Base64
import com.remodex.mobile.core.model.CodexImageAttachment
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

internal object TurnAttachmentCodec {
    private const val MAX_PAYLOAD_DIMENSION = 1600
    private const val THUMBNAIL_SIDE = 70
    private const val JPEG_QUALITY = 80

    fun makeAttachment(
        context: Context,
        uri: Uri,
    ): CodexImageAttachment? {
        val sourceData =
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: return null
        return makeAttachment(sourceData, sourceUrl = uri.toString())
    }

    fun makeAttachment(
        sourceData: ByteArray,
        sourceUrl: String? = null,
    ): CodexImageAttachment? {
        val normalizedPayload = normalizePayloadJpeg(sourceData) ?: return null
        val thumbnailBase64 = makeThumbnailBase64JPEG(normalizedPayload) ?: return null
        val payloadDataUrl = "data:image/jpeg;base64,${Base64.encodeToString(normalizedPayload, Base64.NO_WRAP)}"
        return CodexImageAttachment(
            thumbnailBase64JPEG = thumbnailBase64,
            payloadDataURL = payloadDataUrl,
            sourceURL = sourceUrl,
        )
    }

    fun attachmentFromHistorySource(sourceUrl: String?): CodexImageAttachment? {
        val normalizedSource = sourceUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val payloadDataUrl =
            if (normalizedSource.startsWith("data:image", ignoreCase = true)) {
                normalizedSource
            } else {
                null
            }
        val thumbnailBase64 =
            payloadDataUrl
                ?.let(::decodeDataUriImageData)
                ?.let(::makeThumbnailBase64JPEG)
                .orEmpty()
        return CodexImageAttachment(
            thumbnailBase64JPEG = thumbnailBase64,
            payloadDataURL = payloadDataUrl,
            sourceURL = normalizedSource,
        )
    }

    fun decodeDataUriImageData(dataUri: String): ByteArray? {
        val commaIndex = dataUri.indexOf(',')
        if (commaIndex <= 0) return null
        val metadata = dataUri.substring(0, commaIndex).lowercase()
        if (!metadata.startsWith("data:image") || !metadata.contains(";base64")) return null
        val base64Part = dataUri.substring(commaIndex + 1)
        return runCatching { Base64.decode(base64Part, Base64.DEFAULT) }.getOrNull()
    }

    private fun normalizePayloadJpeg(sourceData: ByteArray): ByteArray? {
        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeByteArray(sourceData, 0, sourceData.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_PAYLOAD_DIMENSION)
            }
        val decoded = BitmapFactory.decodeByteArray(sourceData, 0, sourceData.size, decodeOptions) ?: return null

        val longestSide = max(decoded.width, decoded.height)
        val scaled =
            if (longestSide <= MAX_PAYLOAD_DIMENSION) {
                decoded
            } else {
                val scale = MAX_PAYLOAD_DIMENSION.toFloat() / longestSide.toFloat()
                val targetWidth = max(1, (decoded.width * scale).roundToInt())
                val targetHeight = max(1, (decoded.height * scale).roundToInt())
                Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true).also {
                    if (it != decoded) decoded.recycle()
                }
            }

        return scaled.compressToJpeg(JPEG_QUALITY).also {
            scaled.recycle()
        }
    }

    private fun makeThumbnailBase64JPEG(imageData: ByteArray): String? {
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size) ?: return null
        val thumbnail = Bitmap.createBitmap(THUMBNAIL_SIDE, THUMBNAIL_SIDE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(thumbnail)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scale =
            max(
                THUMBNAIL_SIDE.toFloat() / bitmap.width.toFloat(),
                THUMBNAIL_SIDE.toFloat() / bitmap.height.toFloat(),
            )
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale
        val left = ((THUMBNAIL_SIDE - scaledWidth) / 2f).roundToInt()
        val top = ((THUMBNAIL_SIDE - scaledHeight) / 2f).roundToInt()
        val destination = Rect(left, top, left + scaledWidth.roundToInt(), top + scaledHeight.roundToInt())
        canvas.drawBitmap(bitmap, null, destination, paint)
        bitmap.recycle()
        val jpegData =
            thumbnail.compressToJpeg(JPEG_QUALITY).also {
                thumbnail.recycle()
            } ?: return null
        return Base64.encodeToString(jpegData, Base64.NO_WRAP)
    }

    private fun Bitmap.compressToJpeg(quality: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        if (!compress(Bitmap.CompressFormat.JPEG, quality, out)) {
            return null
        }
        return out.toByteArray()
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int,
    ): Int {
        var sampleSize = 1
        var candidateWidth = width
        var candidateHeight = height
        while (max(candidateWidth, candidateHeight) > maxDimension) {
            candidateWidth = max(1, candidateWidth / 2)
            candidateHeight = max(1, candidateHeight / 2)
            sampleSize *= 2
        }
        return max(1, sampleSize)
    }
}
