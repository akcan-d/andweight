package com.measured.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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

/** True only where the OS can actually supply a wallpaper-derived scheme (Android 12+) - below
 * that, [AndWeightTheme] silently falls back to the chosen [AccentTheme] regardless of what's
 * stored for "use dynamic color", so a Settings toggle flipped on an older phone (or a backup
 * restored onto one) can never end up with nothing to render. */
val supportsDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun AndWeightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentTheme: AccentTheme = AccentTheme.SAGE,
    highContrast: Boolean = false,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (useDynamicColor && supportsDynamicColor) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val accent = accentPaletteFor(accentTheme, darkTheme)
        if (darkTheme) darkScheme(accent, highContrast) else lightScheme(accent, highContrast)
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AndWeightTypography,
        shapes = AndWeightShapes,
        content = content,
    )
}
