package com.measured.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.measured.app.domain.AccentTheme

/** Just the pieces that vary per [AccentTheme] - everything else (background, surface, text,
 * the coral "celebration" tertiary, destructive) stays constant across themes, so achievements
 * and warnings always look the same regardless of which accent someone has picked. */
data class AccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
)

private val SageLight = AccentPalette(
    primary = LightPrimary,
    onPrimary = LightSurface,
    primaryContainer = LightPrimaryTint,
    onPrimaryContainer = LightPrimaryDark,
    secondary = LightSecondary,
    onSecondary = LightSurface,
    secondaryContainer = LightSecondaryTint,
    onSecondaryContainer = LightSecondaryDark,
)

private val SageDark = AccentPalette(
    primary = DarkPrimary,
    onPrimary = DarkText,
    primaryContainer = DarkPrimaryTint,
    onPrimaryContainer = DarkPrimaryDark,
    secondary = DarkSecondary,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSecondaryTint,
    onSecondaryContainer = DarkSecondaryDark,
)

private val BerryLight = AccentPalette(
    primary = Color(0xFF9B4A7A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E0EC),
    onPrimaryContainer = Color(0xFF6E2F56),
    secondary = Color(0xFF5B6FA8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7EAF7),
    onSecondaryContainer = Color(0xFF3C4D80),
)

private val BerryDark = AccentPalette(
    primary = Color(0xFFE0A0C6),
    onPrimary = Color(0xFF3A1F2E),
    primaryContainer = Color(0xFF5C2F49),
    onPrimaryContainer = Color(0xFFF6D9EA),
    secondary = Color(0xFFAAB8E8),
    onSecondary = Color(0xFF1E2740),
    secondaryContainer = Color(0xFF33395C),
    onSecondaryContainer = Color(0xFFD7DDF5),
)

private val OceanLight = AccentPalette(
    primary = Color(0xFF2C6E8E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBF0),
    onPrimaryContainer = Color(0xFF1D4D63),
    secondary = Color(0xFF4F8F7A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDEEEE7),
    onSecondaryContainer = Color(0xFF336153),
)

private val OceanDark = AccentPalette(
    primary = Color(0xFF7FC3DC),
    onPrimary = Color(0xFF0F2C38),
    primaryContainer = Color(0xFF1F4A5C),
    onPrimaryContainer = Color(0xFFC9EAF5),
    secondary = Color(0xFF8FCBB5),
    onSecondary = Color(0xFF123026),
    secondaryContainer = Color(0xFF254A3D),
    onSecondaryContainer = Color(0xFFCDEADF),
)

fun accentPaletteFor(theme: AccentTheme, darkTheme: Boolean): AccentPalette = when (theme) {
    AccentTheme.SAGE -> if (darkTheme) SageDark else SageLight
    AccentTheme.BERRY -> if (darkTheme) BerryDark else BerryLight
    AccentTheme.OCEAN -> if (darkTheme) OceanDark else OceanLight
}
