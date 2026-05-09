package com.remodex.mobile.ui.turn

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexFileAttachment
import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.data.TurnAttachmentCodec
import com.remodex.mobile.services.WorkspaceImageService
import com.remodex.mobile.ui.LocalCodexRepository
import java.util.UUID

internal data class TurnComposerAttachment(
    val id: String = UUID.randomUUID().toString(),
    val state: TurnComposerAttachmentState,
)

internal sealed interface TurnComposerAttachmentState {
    data object Loading : TurnComposerAttachmentState

    data class ReadyImage(
        val attachment: CodexImageAttachment,
    ) : TurnComposerAttachmentState

    data class ReadyFile(
        val attachment: CodexFileAttachment,
    ) : TurnComposerAttachmentState

    data class Failed(
        val message: String,
    ) : TurnComposerAttachmentState
}

@Composable
internal fun ComposerAttachmentStrip(
    attachments: List<TurnComposerAttachment>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            ComposerAttachmentTile(
                attachment = attachment,
                onRemove = { onRemove(attachment.id) },
            )
        }
    }
}

@Composable
internal fun MessageAttachmentStrip(
    attachments: List<CodexImageAttachment>,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    var previewAttachment by remember(attachments) { mutableStateOf<CodexImageAttachment?>(null) }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            ReadyAttachmentThumbnail(
                attachment = attachment,
                onClick = { previewAttachment = attachment },
            )
        }
    }

    previewAttachment?.let { attachment ->
        AttachmentImagePreviewDialog(
            attachment = attachment,
            onDismiss = { previewAttachment = null },
        )
    }
}

@Composable
private fun ComposerAttachmentTile(
    attachment: TurnComposerAttachment,
    onRemove: () -> Unit,
) {
    Box {
        when (val state = attachment.state) {
            TurnComposerAttachmentState.Loading -> PlaceholderAttachmentThumbnail(loading = true)
            is TurnComposerAttachmentState.Failed -> PlaceholderAttachmentThumbnail(loading = false)
            is TurnComposerAttachmentState.ReadyImage -> {
                var showPreview by remember(state.attachment) { mutableStateOf(false) }
                ReadyAttachmentThumbnail(
                    attachment = state.attachment,
                    onClick = { showPreview = true },
                )
                if (showPreview) {
                    AttachmentImagePreviewDialog(
                        attachment = state.attachment,
                        onDismiss = { showPreview = false },
                    )
                }
            }
            is TurnComposerAttachmentState.ReadyFile -> ReadyFileAttachmentThumbnail(attachment = state.attachment)
        }
        IconButton(
            onClick = onRemove,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.turn_remove_attachment_cd),
            )
        }
    }
}

@Composable
private fun ReadyAttachmentThumbnail(
    attachment: CodexImageAttachment,
    onClick: (() -> Unit)? = null,
) {
    val imageBitmap = rememberAttachmentBitmap(attachment = attachment, preferPayload = false)
    val shape = MaterialTheme.shapes.medium
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier =
            Modifier
                .size(70.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadyFileAttachmentThumbnail(attachment: CodexFileAttachment) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier =
            Modifier
                .size(70.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)),
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopStart).size(18.dp),
        )
        Text(
            text = attachment.fileName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun AttachmentImagePreviewDialog(
    attachment: CodexImageAttachment,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val imageBitmap = rememberAttachmentBitmap(attachment = attachment, preferPayload = true)

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(16.dp),
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(text = stringResource(R.string.turn_preview_close))
                }
            }
        }
    }
}

@Composable
private fun PlaceholderAttachmentThumbnail(loading: Boolean) {
    Box(
        modifier =
            Modifier
                .size(70.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        } else {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun rememberAttachmentBitmap(
    attachment: CodexImageAttachment,
    preferPayload: Boolean,
): ImageBitmap? {
    val repository = LocalCodexRepository.current
    val workspaceImageService = remember(repository) { WorkspaceImageService(repository) }
    val payloadDataUrl = attachment.payloadDataURL
    val thumbnailBase64 = attachment.thumbnailBase64JPEG
    val sourceURL = attachment.sourceURL
    val workspaceImageSource = remember(sourceURL) { workspaceImagePath(sourceURL) }
    var workspacePayloadDataUrl by remember(workspaceImageSource, preferPayload) { mutableStateOf<String?>(null) }

    LaunchedEffect(workspaceImageSource, preferPayload, payloadDataUrl, thumbnailBase64) {
        if (workspaceImageSource == null ||
            !payloadDataUrl.isNullOrBlank() ||
            (!preferPayload && thumbnailBase64.isNotBlank())
        ) {
            workspacePayloadDataUrl = null
            return@LaunchedEffect
        }
        val previewDimension = if (preferPayload) 1600L else 512L
        workspacePayloadDataUrl =
            runCatching {
                workspaceImageService
                    .readPreviewDataUrl(workspaceImageSource, previewDimension)
                    ?.dataUrl
            }.getOrNull()
    }

    return remember(thumbnailBase64, payloadDataUrl, workspacePayloadDataUrl, preferPayload) {
        val imageBytes =
            when {
                preferPayload && !payloadDataUrl.isNullOrBlank() -> TurnAttachmentCodec.decodeDataUriImageData(payloadDataUrl)
                preferPayload && !workspacePayloadDataUrl.isNullOrBlank() ->
                    TurnAttachmentCodec.decodeDataUriImageData(workspacePayloadDataUrl!!)
                thumbnailBase64.isNotBlank() -> runCatching { Base64.decode(thumbnailBase64, Base64.DEFAULT) }.getOrNull()
                !payloadDataUrl.isNullOrBlank() -> TurnAttachmentCodec.decodeDataUriImageData(payloadDataUrl)
                !workspacePayloadDataUrl.isNullOrBlank() ->
                    TurnAttachmentCodec.decodeDataUriImageData(workspacePayloadDataUrl!!)
                else -> null
            } ?: return@remember null
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)?.asImageBitmap()
    }
}

private fun workspaceImagePath(sourceURL: String?): String? {
    val source = sourceURL?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (source.startsWith("data:image", ignoreCase = true)) return null
    if (source.startsWith("content:", ignoreCase = true)) return null
    if (source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)) return null
    if (source.startsWith("file://", ignoreCase = true)) {
        return Uri.parse(source).path?.takeIf { it.isNotBlank() }
    }
    return source
}
