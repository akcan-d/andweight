package com.andweight.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightSurface,
    primaryContainer = LightPrimaryTint,
    onPrimaryContainer = LightPrimaryDark,
    secondary = LightSecondary,
    onSecondary = LightSurface,
    secondaryContainer = LightSecondaryTint,
    onSecondaryContainer = LightSecondaryDark,
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
    surfaceTint = LightPrimary,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkText,
    primaryContainer = DarkPrimaryTint,
    onPrimaryContainer = DarkPrimaryDark,
    secondary = DarkSecondary,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSecondaryTint,
    onSecondaryContainer = DarkSecondaryDark,
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
    surfaceTint = DarkPrimary,
)

@Composable
fun AndWeightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AndWeightTypography,
        shapes = AndWeightShapes,
        content = content,
    )
}
