package com.remodex.mobile.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.core.model.CodexRateLimitDisplayRow
import com.remodex.mobile.core.model.ContextWindowUsage
import com.remodex.mobile.core.transport.ConnectionState
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class TrustedPairSnapshot(
    val relayUrl: String?,
    val macDeviceId: String?,
    val lastTrustedMacDeviceId: String?,
)

@Composable
fun TrustedPairSummary(
    connectionState: ConnectionState,
    sessionReady: Boolean,
    snapshot: TrustedPairSnapshot?,
    modifier: Modifier = Modifier,
) {
    val relayHost =
        snapshot?.relayUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { raw -> runCatching { raw.toHttpUrlOrNull()?.host ?: raw }.getOrDefault(raw) }
    val mac =
        snapshot?.macDeviceId
            ?.takeIf { !it.isNullOrBlank() }
            ?: snapshot?.lastTrustedMacDeviceId
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.trusted_pair_summary_title),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text =
                    when {
                        connectionState is ConnectionState.Connected && sessionReady ->
                            stringResource(R.string.trusted_pair_summary_connected)
                        else -> stringResource(R.string.trusted_pair_summary_not_connected)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            relayHost?.let {
                Text(
                    text = stringResource(R.string.trusted_pair_summary_relay, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            mac?.let {
                Text(
                    text = stringResource(R.string.trusted_pair_summary_device, it.takeLast(12)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun UsageStatusSummary(
    contextUsage: ContextWindowUsage?,
    rateLimitRows: List<CodexRateLimitDisplayRow>,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.usage_status_summary_title),
                style = MaterialTheme.typography.labelLarge,
            )
            if (loading) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
            val usageText = contextUsage?.let { "${it.percentUsed}%" } ?: "-"
            val limitsText = rateLimitRows.firstOrNull()?.window?.clampedUsedPercent?.toString()?.plus("%") ?: "-"
            Text(
                text = stringResource(R.string.usage_status_summary_values, usageText, limitsText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun WorktreeGlassBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
    ) {
        Text(
            text = stringResource(R.string.worktree_glass_badge_label),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
fun ThreadRenameDialog(
    visible: Boolean,
    initialName: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!visible) return
    var value by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.thread_rename_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text(stringResource(R.string.thread_rename_dialog_label)) },
                )
                error?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = !busy,
            ) {
                Text(stringResource(R.string.thread_rename_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
