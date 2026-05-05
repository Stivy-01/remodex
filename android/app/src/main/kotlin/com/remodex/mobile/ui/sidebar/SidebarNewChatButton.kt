package com.remodex.mobile.ui.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR

/**
 * iOS-style sidebar row: small glyph tile + label (compact, not a full-width pill button).
 */
@Composable
fun SidebarCompactActionRow(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_plus),
                contentDescription = null,
                modifier =
                    Modifier
                        .padding(horizontal = 7.dp, vertical = 7.dp)
                        .size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    },
) {
    val canClick = enabled && !busy
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { role = Role.Button }
                .clickable(enabled = canClick, onClick = onClick)
                .padding(vertical = 4.dp, horizontal = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp).padding(4.dp),
                strokeWidth = 2.dp,
            )
        } else {
            leading()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (canClick) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
        )
    }
}
