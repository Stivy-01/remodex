package com.remodex.mobile.ui.turn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remodex.mobile.core.transport.ConnectionState
import com.remodex.mobile.ui.home.RootReconnectAttempt
import com.remodex.mobile.ui.home.RootReconnectRecoveryAction
import com.remodex.mobile.ui.home.RootReconnectUiState

internal enum class TurnConnectionRecoveryStatus(val label: String) {
    Interrupted("Interrupted"),
    Reconnecting("Reconnecting"),
}

internal sealed interface TurnConnectionRecoveryTrailing {
    data class Action(val label: String) : TurnConnectionRecoveryTrailing
    data object Progress : TurnConnectionRecoveryTrailing
}

internal data class TurnConnectionRecoverySnapshot(
    val summary: String,
    val status: TurnConnectionRecoveryStatus,
    val trailing: TurnConnectionRecoveryTrailing,
) {
    val isActionable: Boolean get() = trailing is TurnConnectionRecoveryTrailing.Action
}

internal object TurnConnectionRecoverySnapshotBuilder {
    fun makeSnapshot(
        hasReconnectCandidate: Boolean,
        connectionState: ConnectionState,
        reconnectUiState: RootReconnectUiState,
    ): TurnConnectionRecoverySnapshot? {
        if (!hasReconnectCandidate || connectionState is ConnectionState.Connected) return null

        val trimmedError = reconnectUiState.lastErrorMessage?.trim()?.takeIf { it.isNotEmpty() }
        if (reconnectUiState.attempt == RootReconnectAttempt.WakeDisplay) {
            return TurnConnectionRecoverySnapshot(
                summary = trimmedError ?: "Trying to wake the computer display.",
                status = TurnConnectionRecoveryStatus.Reconnecting,
                trailing = TurnConnectionRecoveryTrailing.Progress,
            )
        }

        if (connectionState is ConnectionState.Connecting || reconnectUiState.isAttempting) {
            return TurnConnectionRecoverySnapshot(
                summary = "Trying to reconnect to your computer.",
                status = TurnConnectionRecoveryStatus.Reconnecting,
                trailing = TurnConnectionRecoveryTrailing.Progress,
            )
        }

        if (reconnectUiState.recoveryAction == RootReconnectRecoveryAction.ScanNewQr) {
            return TurnConnectionRecoverySnapshot(
                summary = trimmedError ?: "Scan a new QR code to reconnect this chat.",
                status = TurnConnectionRecoveryStatus.Interrupted,
                trailing = TurnConnectionRecoveryTrailing.Action("Scan QR"),
            )
        }

        if (reconnectUiState.wakeDisplayAvailable) {
            return TurnConnectionRecoverySnapshot(
                summary = trimmedError ?: "Wake the computer screen to keep this chat in sync.",
                status = TurnConnectionRecoveryStatus.Interrupted,
                trailing = TurnConnectionRecoveryTrailing.Action("Wake"),
            )
        }

        return TurnConnectionRecoverySnapshot(
            summary = trimmedError ?: "Reconnect to your computer to keep this chat in sync.",
            status = TurnConnectionRecoveryStatus.Interrupted,
            trailing = TurnConnectionRecoveryTrailing.Action("Reconnect"),
        )
    }
}

@Composable
internal fun TurnConnectionRecoveryCard(
    snapshot: TurnConnectionRecoverySnapshot,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (snapshot.isActionable) {
                        Modifier.clickable(onClick = onTap)
                    } else {
                        Modifier
                    },
                ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Connection - ${snapshot.status.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = snapshot.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.size(4.dp))
            when (val trailing = snapshot.trailing) {
                is TurnConnectionRecoveryTrailing.Action -> {
                    Text(
                        text = trailing.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TurnConnectionRecoveryTrailing.Progress -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}
