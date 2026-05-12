package com.remodex.mobile.ui.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR

/**
 * iOS-style sidebar row: small glyph tile + label (compact, not a full-width pill button).
 */
@Composable
fun SidebarActionRow(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {
        val colors = rememberSidebarColorPalette()
        Icon(
            painter = painterResource(LucideR.drawable.lucide_ic_plus),
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            tint = colors.secondaryText,
        )
    },
) {
    val colors = rememberSidebarColorPalette()
    val canClick = enabled && !busy
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .semantics { role = Role.Button }
                .clickable(enabled = canClick, onClick = onClick)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(21.dp).padding(3.dp),
                strokeWidth = 2.dp,
            )
        } else {
            leading()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
            color =
                if (canClick) {
                    colors.primaryText
                } else {
                    colors.primaryText.copy(alpha = 0.38f)
                },
        )
    }
}

@Composable
fun SidebarCompactActionRow(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {
        val colors = rememberSidebarColorPalette()
        Icon(
            painter = painterResource(LucideR.drawable.lucide_ic_plus),
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            tint = colors.secondaryText,
        )
    },
) {
    SidebarActionRow(
        label = label,
        enabled = enabled,
        busy = busy,
        onClick = onClick,
        modifier = modifier,
        leading = leading,
    )
}
