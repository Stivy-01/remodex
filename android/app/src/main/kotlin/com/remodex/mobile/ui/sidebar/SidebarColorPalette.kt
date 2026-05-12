package com.remodex.mobile.ui.sidebar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.remodex.mobile.ui.theme.isAgentLightChrome

@Immutable
data class SidebarColorPalette(
    val background: Color,
    val surface: Color,
    val selectedRow: Color,
    val border: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val green: Color,
    val red: Color,
)

@Composable
fun rememberSidebarColorPalette(
    darkTheme: Boolean = !isAgentLightChrome(),
): SidebarColorPalette =
    remember(darkTheme) {
        if (darkTheme) {
            SidebarColorPalette(
                background = Color(0xFF050708),
                surface = Color(0xFF111516),
                selectedRow = Color(0xFF111516),
                border = Color(0xFF2A2D2E),
                primaryText = Color(0xFFE6E2DC),
                secondaryText = Color(0xFFAAA59F),
                mutedText = Color(0xFF9B9791),
                green = Color(0xFF55D979),
                red = Color(0xFFFF6B6B),
            )
        } else {
            SidebarColorPalette(
                background = Color(0xFFF6F5F2),
                surface = Color(0xFFEEEDE9),
                selectedRow = Color(0xFFEDEBE5),
                border = Color(0xFFD8D5CD),
                primaryText = Color(0xFF1E1F21),
                secondaryText = Color(0xFF5F6266),
                mutedText = Color(0xFF8A8D91),
                green = Color(0xFF18A957),
                red = Color(0xFFC4484D),
            )
        }
    }
