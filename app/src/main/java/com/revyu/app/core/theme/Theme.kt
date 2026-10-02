package com.revyu.app.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.revyu.app.core.preferences.AccentColor
import com.revyu.app.core.preferences.ThemeMode

/**
 * Revyu's Material3 color schemes are derived from the active [AccentColor] and handed the
 * warm cream/ink palette used across the app (see Color.kt). The four prototype accents
 * (orange, green, purple, blue) each carry their own primary / container / contrast roles
 * for light and dark, so switching accent restyles the whole surface language — not just a
 * single "active tab" pill.
 */
@Composable
internal fun accentLightColorScheme(accent: AccentColor): androidx.compose.material3.ColorScheme {
    val p = accent.palette
    return lightColorScheme(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = p.accentContainer,
        onPrimaryContainer = p.onAccentContainer,
        inversePrimary = p.accentDark,

        secondary = p.accent,
        onSecondary = p.onAccent,
        secondaryContainer = p.accentContainer,
        onSecondaryContainer = p.onAccentContainer,

        tertiary = p.accent,
        onTertiary = Paper,
        tertiaryContainer = p.accentContainer,
        onTertiaryContainer = InkNavy,

        background = Paper,
        onBackground = InkNavy,
        surface = PaperElevated,
        onSurface = InkNavy,
        surfaceVariant = PaperDim,
        onSurfaceVariant = InkNavyMuted,

        error = ErrorRust,
        onError = Paper,
        errorContainer = ErrorRustMuted,
        onErrorContainer = ErrorRust,

        outline = MarginGray,
        outlineVariant = MarginGrayLight,

        inverseSurface = InkNavy,
        inverseOnSurface = Paper
    )
}

@Composable
internal fun accentDarkColorScheme(accent: AccentColor): androidx.compose.material3.ColorScheme {
    val p = accent.palette
    return darkColorScheme(
        primary = p.accentDark,
        onPrimary = p.onAccentDark,
        primaryContainer = p.accentContainerDark,
        onPrimaryContainer = p.onAccentContainerDark,
        inversePrimary = p.accent,

        secondary = p.accentDark,
        onSecondary = p.onAccentDark,
        secondaryContainer = p.accentContainerDark,
        onSecondaryContainer = p.onAccentContainerDark,

        tertiary = p.accent,
        onTertiary = InkNavy,
        tertiaryContainer = p.accentContainer,
        onTertiaryContainer = DarkText,

        background = DarkInkNavy,
        onBackground = DarkText,
        surface = DarkSurface,
        onSurface = DarkText,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkTextMuted,

        error = DarkError,
        onError = DarkErrorContainer,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkText,

        outline = DarkOutline,
        outlineVariant = DarkSurfaceVariant,

        inverseSurface = DarkText,
        inverseOnSurface = DarkInkNavy
    )
}

@Composable
fun RevyuTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.ORANGE,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (darkTheme) accentDarkColorScheme(accentColor) else accentLightColorScheme(accentColor),
        typography = RevyuTypography,
        shapes = RevyuShapes,
        content = content
    )
}

object RevyuTheme {
    val colors: androidx.compose.material3.ColorScheme @Composable
    @ReadOnlyComposable get() = MaterialTheme.colorScheme
}
