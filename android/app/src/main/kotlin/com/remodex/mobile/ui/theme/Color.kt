package com.remodex.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Minimal mobile light palette (code-agent): warm white chroma + soft greys + few accents.
 *
 * Material `LightColorScheme` maps these values to roles (`RemodexLight*` aliases below).
 */
object AgentLightColors {
    /** Screen/root background — warm white. */
    val ScreenBg = Color(0xFFFBFBFA)
    /** Default surface / opaque card (opaque here; overlays use Compose alpha where needed). */
    val Surface = Color(0xFFFFFFFF)
    /** Pills / composer-adjacent soft fills. */
    val SurfaceSoft = Color(0xFFF4F4F4)

    /** Light separators / “almost invisible” borders. */
    val Border = Color(0xFFEAEAEA)

    val TextPrimary = Color(0xFF171717)
    val TextSecondary = Color(0xFF777777)
    val TextMuted = Color(0xFFB5B5B5)

    val LinkBlue = Color(0xFF2F80ED)

    val AdditionGreen = Color(0xFF35C76A)
    val DeletionRed = Color(0xFFFF5A65)
    val WarningOrange = Color(0xFFFF9B45)

    val IconMuted = Color(0xFF8E8E93)
}

// Light — maps [AgentLightColors] onto Material tokens used across the app
val RemodexLightBackground = AgentLightColors.ScreenBg
val RemodexLightSurface = AgentLightColors.Surface
val RemodexLightSurfaceVariant = AgentLightColors.SurfaceSoft
val RemodexLightOnBackground = AgentLightColors.TextPrimary
val RemodexLightOnSurface = AgentLightColors.TextPrimary
/** Body / labels on surface (secondary text). */
val RemodexLightOnSurfaceVariant = AgentLightColors.TextSecondary
val RemodexLightPrimary = AgentLightColors.TextPrimary
val RemodexLightOnPrimary = AgentLightColors.Surface
val RemodexLightPrimaryContainer = AgentLightColors.SurfaceSoft
val RemodexLightOnPrimaryContainer = AgentLightColors.TextPrimary

val RemodexLightSecondary = AgentLightColors.LinkBlue
val RemodexLightOnSecondary = AgentLightColors.Surface
/** Link / Thinking pill tint. */
private val SecondaryContainerBlend = AgentLightColors.LinkBlue.copy(alpha = 0.10f)

val RemodexLightSecondaryContainer =
    SecondaryContainerBlend.over(AgentLightColors.Surface)
/** Solid fill for readability on white/soft surfaces. */
val RemodexLightOnSecondaryContainer = AgentLightColors.LinkBlue

val RemodexLightOutline = AgentLightColors.Border
val RemodexLightOutlineVariant = AgentLightColors.TextMuted

val RemodexLightError = AgentLightColors.DeletionRed
val RemodexLightOnError = AgentLightColors.Surface

private fun Color.over(base: Color): Color {
    val a = alpha
    if (a <= 0f) return base
    if (a >= 1f) return this
    fun ch(c: Float, b: Float) = b * (1f - a) + c * a
    return Color(red = ch(red, base.red), green = ch(green, base.green), blue = ch(blue, base.blue))
}

// Dark — graphite surfaces (reference warm gray, not pure black)
val RemodexDarkBackground = Color(0xFF101214)
val RemodexDarkSurface = Color(0xFF15181B)
val RemodexDarkSurfaceVariant = Color(0xFF202429)
val RemodexDarkOnBackground = Color(0xFFECE9E1)
val RemodexDarkOnSurface = Color(0xFFECE9E1)
val RemodexDarkOnSurfaceVariant = Color(0xFFB9B3A8)
val RemodexDarkPrimary = Color(0xFFECE9E1)
val RemodexDarkOnPrimary = Color(0xFF101214)
val RemodexDarkPrimaryContainer = Color(0xFF2A2F35)
val RemodexDarkOnPrimaryContainer = Color(0xFFECE9E1)
val RemodexDarkSecondary = Color(0xFF9CC7AE)
val RemodexDarkOnSecondary = Color(0xFF101214)
val RemodexDarkSecondaryContainer = Color(0xFF2A3D34)
val RemodexDarkOnSecondaryContainer = Color(0xFFD4E5DC)
val RemodexDarkOutline = Color(0xFF41464C)
val RemodexDarkOutlineVariant = Color(0xFF6B7268)
val RemodexDarkError = Color(0xFFFFB4AB)
val RemodexDarkOnError = Color(0xFF690005)

/** Git-style addition line counts (toolbar + timeline). */
val RemodexGitAddition = AgentLightColors.AdditionGreen

/** Unified-diff row washes (inline patch); light: very soft tint on warm screen bg. */
val RemodexGitDiffAdditionBgLight =
    AgentLightColors.AdditionGreen.copy(alpha = 0.055f).over(AgentLightColors.ScreenBg)
val RemodexGitDiffDeletionBgLight =
    AgentLightColors.DeletionRed.copy(alpha = 0.045f).over(AgentLightColors.ScreenBg)
val RemodexGitDiffAdditionBgDark = Color(0xFF143524)
val RemodexGitDiffDeletionBgDark = Color(0xFF3A181C)
val RemodexGitDiffMetaBgLight = Color(0x14000000)
val RemodexGitDiffMetaBgDark = Color(0x22FFFFFF)

/** Full-access (shield-alert) light icon — matches warning/orange accent from AgentLightColors. */
val RemodexFullAccessIconLight = AgentLightColors.WarningOrange
val RemodexFullAccessIconDark = Color(0xFFE8A87C)
