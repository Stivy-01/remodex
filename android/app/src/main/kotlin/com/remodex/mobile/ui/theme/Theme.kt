package com.remodex.mobile.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.remodex.mobile.core.model.AppFontStyle
import com.remodex.mobile.data.AppFontPreferences
import com.remodex.mobile.data.ThemePreferences

private val LightColorScheme =
    lightColorScheme(
        primary = RemodexLightPrimary,
        onPrimary = RemodexLightOnPrimary,
        primaryContainer = RemodexLightPrimaryContainer,
        onPrimaryContainer = RemodexLightOnPrimaryContainer,
        secondary = RemodexLightSecondary,
        onSecondary = RemodexLightOnSecondary,
        secondaryContainer = RemodexLightSecondaryContainer,
        onSecondaryContainer = RemodexLightOnSecondaryContainer,
        tertiary = RemodexLightSecondary,
        onTertiary = RemodexLightOnSecondary,
        tertiaryContainer = RemodexLightSecondaryContainer.copy(alpha = 0.94f),
        onTertiaryContainer = RemodexLightOnSecondaryContainer,
        background = RemodexLightBackground,
        onBackground = RemodexLightOnBackground,
        surface = RemodexLightSurface,
        onSurface = RemodexLightOnSurface,
        surfaceVariant = RemodexLightSurfaceVariant,
        onSurfaceVariant = RemodexLightOnSurfaceVariant,
        outline = RemodexLightOutline,
        outlineVariant = RemodexLightOutlineVariant,
        error = RemodexLightError,
        onError = RemodexLightOnError,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = RemodexDarkPrimary,
        onPrimary = RemodexDarkOnPrimary,
        primaryContainer = RemodexDarkPrimaryContainer,
        onPrimaryContainer = RemodexDarkOnPrimaryContainer,
        secondary = RemodexDarkSecondary,
        onSecondary = RemodexDarkOnSecondary,
        secondaryContainer = RemodexDarkSecondaryContainer,
        onSecondaryContainer = RemodexDarkOnSecondaryContainer,
        tertiary = RemodexDarkSecondary,
        onTertiary = RemodexDarkOnSecondary,
        tertiaryContainer = RemodexDarkSecondaryContainer.copy(alpha = 0.96f),
        onTertiaryContainer = RemodexDarkOnSecondaryContainer,
        background = RemodexDarkBackground,
        onBackground = RemodexDarkOnBackground,
        surface = RemodexDarkSurface,
        onSurface = RemodexDarkOnSurface,
        surfaceVariant = RemodexDarkSurfaceVariant,
        onSurfaceVariant = RemodexDarkOnSurfaceVariant,
        outline = RemodexDarkOutline,
        outlineVariant = RemodexDarkOutlineVariant,
        error = RemodexDarkError,
        onError = RemodexDarkOnError,
    )

@Composable
fun RemodexTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var appFontStyle by remember(context) { mutableStateOf(AppFontPreferences.readFontStyle(context)) }
    DisposableEffect(context) {
        val prefs =
            context.applicationContext.getSharedPreferences(
                ThemePreferences.PREFS_NAME,
                android.content.Context.MODE_PRIVATE,
            )
        val listener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == AppFontStyle.storageKey || key == AppFontStyle.legacyStorageKey) {
                    appFontStyle = AppFontPreferences.readFontStyle(context)
                }
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    RemodexSystemBars(
        background = colorScheme.background,
        darkTheme = darkTheme,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = remodexTypography(appFontStyle),
        shapes = RemodexShapes,
        content = content,
    )
}

@Composable
private fun RemodexSystemBars(
    background: Color,
    darkTheme: Boolean,
) {
    val context = LocalContext.current
    DisposableEffect(context, background, darkTheme) {
        val window = context.findActivity()?.window
        if (window != null) {
            val color = background.toArgb()
            window.statusBarColor = color
            window.navigationBarColor = color
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        onDispose {}
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
