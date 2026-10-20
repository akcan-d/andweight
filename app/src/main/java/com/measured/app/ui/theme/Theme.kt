package com.measured.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.measured.app.domain.AccentTheme

// internal, not private - the home-screen widget builds its own Glance color scheme from these
// same functions so it stays visually identical to the in-app theme (see widget/WidgetTheme.kt).
internal fun lightScheme(accent: AccentPalette, highContrast: Boolean = false) = lightColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primaryContainer,
    onPrimaryContainer = accent.onPrimaryContainer,
    secondary = accent.secondary,
    onSecondary = accent.onSecondary,
    secondaryContainer = accent.secondaryContainer,
    onSecondaryContainer = accent.onSecondaryContainer,
    tertiary = LightAccent,
    onTertiary = LightSurface,
    tertiaryContainer = LightAccentTint,
    onTertiaryContainer = LightAccentDark,
    error = LightDestructive,
    onError = LightSurface,
    errorContainer = LightDestructiveTint,
    onErrorContainer = LightDestructive,
    background = if (highContrast) LightBackgroundHC else LightBackground,
    onBackground = if (highContrast) LightTextHC else LightText,
    surface = if (highContrast) LightSurfaceHC else LightSurface,
    onSurface = if (highContrast) LightTextHC else LightText,
    surfaceVariant = if (highContrast) LightSurfaceAltHC else LightSurfaceAlt,
    onSurfaceVariant = if (highContrast) LightTextSoftHC else LightTextSoft,
    surfaceContainerLowest = if (highContrast) LightSurfaceHC else LightSurface,
    surfaceContainerLow = if (highContrast) LightSurfaceHC else LightSurface,
    surfaceContainer = if (highContrast) LightSurfaceHC else LightSurface,
    surfaceContainerHigh = if (highContrast) LightSurfaceAltHC else LightSurfaceAlt,
    surfaceContainerHighest = if (highContrast) LightSurfaceAltHC else LightSurfaceAlt,
    outline = if (highContrast) LightLineHC else LightLine,
    outlineVariant = if (highContrast) LightLineHC else LightLine,
    inverseSurface = if (highContrast) LightTextHC else LightText,
    inverseOnSurface = if (highContrast) LightSurfaceHC else LightSurface,
    inversePrimary = LightPrimaryTint,
    surfaceTint = accent.primary,
)

internal fun darkScheme(accent: AccentPalette, highContrast: Boolean = false) = darkColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primaryContainer,
    onPrimaryContainer = accent.onPrimaryContainer,
    secondary = accent.secondary,
    onSecondary = accent.onSecondary,
    secondaryContainer = accent.secondaryContainer,
    onSecondaryContainer = accent.onSecondaryContainer,
    tertiary = DarkAccent,
    onTertiary = DarkBackground,
    tertiaryContainer = DarkAccentTint,
    onTertiaryContainer = DarkAccentDark,
    error = DarkDestructive,
    onError = DarkBackground,
    errorContainer = DarkDestructiveTint,
    onErrorContainer = DarkDestructive,
    background = if (highContrast) DarkBackgroundHC else DarkBackground,
    onBackground = if (highContrast) DarkTextHC else DarkText,
    surface = if (highContrast) DarkSurfaceHC else DarkSurface,
    onSurface = if (highContrast) DarkTextHC else DarkText,
    surfaceVariant = if (highContrast) DarkSurfaceAltHC else DarkSurfaceAlt,
    onSurfaceVariant = if (highContrast) DarkTextSoftHC else DarkTextSoft,
    surfaceContainerLowest = if (highContrast) DarkSurfaceHC else DarkSurface,
    surfaceContainerLow = if (highContrast) DarkSurfaceHC else DarkSurface,
    surfaceContainer = if (highContrast) DarkSurfaceHC else DarkSurface,
    surfaceContainerHigh = if (highContrast) DarkSurfaceAltHC else DarkSurfaceAlt,
    surfaceContainerHighest = if (highContrast) DarkSurfaceAltHC else DarkSurfaceAlt,
    outline = if (highContrast) DarkLineHC else DarkLine,
    outlineVariant = if (highContrast) DarkLineHC else DarkLine,
    inverseSurface = if (highContrast) DarkTextHC else DarkText,
    inverseOnSurface = if (highContrast) DarkSurfaceHC else DarkBackground,
    inversePrimary = DarkPrimaryTint,
    surfaceTint = accent.primary,
)

@Composable
fun AndWeightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentTheme: AccentTheme = AccentTheme.SAGE,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val accent = accentPaletteFor(accentTheme, darkTheme)
    val colors = if (darkTheme) darkScheme(accent, highContrast) else lightScheme(accent, highContrast)
    MaterialTheme(
        colorScheme = colors,
        typography = AndWeightTypography,
        shapes = AndWeightShapes,
        content = content,
    )
}
