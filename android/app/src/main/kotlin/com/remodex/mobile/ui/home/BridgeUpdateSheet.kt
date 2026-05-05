package com.remodex.mobile.ui.home

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import kotlinx.coroutines.launch

/**
 * Actionable recovery when bridge build mismatches (parity with [BridgeUpdateSheet.swift](CodexMobile/CodexMobile/Views/Home/BridgeUpdateSheet.swift)).
 * Show when [visible] and service exposes a bridge update prompt model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgeUpdateSheet(
    visible: Boolean,
    title: String,
    message: String,
    installCommand: String? = null,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onScanNewQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            installCommand?.takeIf { it.isNotBlank() }?.let { cmd ->
                Text(
                    text = stringResource(R.string.bridge_update_run_on_computer_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = cmd,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipData.newPlainText("bridge-install-command", cmd).toClipEntry())
                        }
                    },
                ) {
                    Text(stringResource(R.string.bridge_update_copy_install_command))
                }
            }
            TextButton(onClick = onRetry) { Text("Retry") }
            TextButton(onClick = onScanNewQr) { Text("Scan new QR") }
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
