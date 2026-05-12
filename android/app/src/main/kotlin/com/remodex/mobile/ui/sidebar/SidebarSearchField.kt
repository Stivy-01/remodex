package com.remodex.mobile.ui.sidebar

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import com.remodex.mobile.R

@Composable
fun SidebarSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SidebarSearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholderText = stringResource(R.string.sidebar_search_hint),
        modifier = modifier,
    )
}

@Composable
fun SidebarSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholderText: String? = null,
    modifier: Modifier = Modifier,
) {
    val hint = placeholderText ?: stringResource(R.string.sidebar_search_hint)
    val colors = rememberSidebarColorPalette()
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(22.dp)),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp, color = colors.primaryText),
        shape = RoundedCornerShape(22.dp),
        placeholder = {
            Text(
                text = hint,
                color = colors.mutedText,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_search),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.mutedText,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_x),
                        contentDescription = stringResource(R.string.cd_clear_search),
                        modifier = Modifier.size(14.dp),
                        tint = colors.mutedText,
                    )
                }
            }
        },
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent,
                cursorColor = colors.primaryText,
                focusedTextColor = colors.primaryText,
                unfocusedTextColor = colors.primaryText,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
    )
}
