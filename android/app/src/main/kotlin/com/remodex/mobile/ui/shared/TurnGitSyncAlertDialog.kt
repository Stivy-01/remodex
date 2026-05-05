package com.remodex.mobile.ui.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.remodex.mobile.core.model.TurnGitSyncAlert
import com.remodex.mobile.core.model.TurnGitSyncAlertAction
import com.remodex.mobile.core.model.TurnGitSyncAlertButtonRole

@Composable
fun TurnGitSyncAlertDialog(
    alert: TurnGitSyncAlert?,
    onDismiss: () -> Unit,
    onAction: (TurnGitSyncAlertAction) -> Unit,
) {
    if (alert == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(alert.title) },
        text = { Text(alert.message) },
        confirmButton = {
            Column {
                alert.buttons
                    .filter { it.role != TurnGitSyncAlertButtonRole.cancel }
                    .forEach { button ->
                        TextButton(onClick = { onAction(button.action) }) {
                            Text(
                                text = button.title,
                                color =
                                    if (button.role == TurnGitSyncAlertButtonRole.destructive) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                            )
                        }
                    }
                if (alert.buttons.none { it.role != TurnGitSyncAlertButtonRole.cancel }) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        },
        dismissButton = {
            val cancel = alert.buttons.firstOrNull { it.role == TurnGitSyncAlertButtonRole.cancel }
            if (cancel != null && alert.buttons.size > 1) {
                TextButton(onClick = onDismiss) {
                    Text(cancel.title)
                }
            }
        },
    )
}
