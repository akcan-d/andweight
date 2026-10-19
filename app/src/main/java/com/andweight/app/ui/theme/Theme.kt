package com.andweight.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.andweight.app.domain.AccentTheme

// internal, not private - the home-screen widget builds its own Glance color scheme from these
// same functions so it stays visually identical to the in-app theme (see widget/WidgetTheme.kt).
internal fun lightScheme(accent: AccentPalette) = lightColorScheme(
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
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = LightTextSoft,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceAlt,
    surfaceContainerHighest = LightSurfaceAlt,
    outline = LightLine,
    outlineVariant = LightLine,
    inverseSurface = LightText,
    inverseOnSurface = LightSurface,
    inversePrimary = LightPrimaryTint,
    surfaceTint = accent.primary,
)

internal fun darkScheme(accent: AccentPalette) = darkColorScheme(
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
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceAlt,
    onSurfaceVariant = DarkTextSoft,
    surfaceContainerLowest = DarkSurface,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceAlt,
    surfaceContainerHighest = DarkSurfaceAlt,
    outline = DarkLine,
    outlineVariant = DarkLine,
    inverseSurface = DarkText,
    inverseOnSurface = DarkBackground,
    inversePrimary = DarkPrimaryTint,
    surfaceTint = accent.primary,
)

@Composable
fun AndWeightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentTheme: AccentTheme = AccentTheme.SAGE,
    content: @Composable () -> Unit,
) {
    val accent = accentPaletteFor(accentTheme, darkTheme)
    val colors = if (darkTheme) darkScheme(accent) else lightScheme(accent)
    MaterialTheme(
        colorScheme = colors,
        typography = AndWeightTypography,
        shapes = AndWeightShapes,
        content = content,
    )
}
