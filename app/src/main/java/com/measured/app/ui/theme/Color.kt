package com.measured.app.ui.theme

import androidx.compose.ui.graphics.Color

// Light - warm cream, one muted sage as primary, coral reserved for streaks/celebration, sky
// blue as a second accent. See the design mockup for the full rationale. Every color meant to be
// read as text or an icon (not just a decorative tint background) is pulled dark enough for a
// roughly 4.5:1+ contrast ratio against both white and cream - the original, lighter mockup
// palette looked right on a wide desktop canvas but read as too soft/low-contrast on an actual
// phone screen.
val LightBackground = Color(0xFFFBF7EF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceAlt = Color(0xFFF3EEE3)
val LightPrimary = Color(0xFF2D7D55)
val LightPrimaryDark = Color(0xFF1F5E3D)
val LightPrimaryTint = Color(0xFFC3E3D1)
val LightAccent = Color(0xFFAD471F)
val LightAccentDark = Color(0xFF8F3714)
val LightAccentTint = Color(0xFFF6C9A8)
val LightSecondary = Color(0xFF4A63D1)
val LightSecondaryDark = Color(0xFF37489C)
val LightSecondaryTint = Color(0xFFCBD4F1)
val LightText = Color(0xFF3A362E)
val LightTextSoft = Color(0xFF6B6458)
val LightTextFaint = Color(0xFF9A927F)
val LightLine = Color(0xFFDDD3BF)
val LightDestructive = Color(0xFFC14336)
val LightDestructiveTint = Color(0xFFFAE1DD)

// High contrast - a plain near-black/near-white pair plus a solid outline, swapped in for the
// warm (but slightly muted) neutrals above when the high-contrast accessibility setting is on.
// Accent colors (primary/accent/secondary/destructive) are left as-is - they're already tuned to
// pass contrast against these same backgrounds, just not pushed to the absolute extreme.
val LightBackgroundHC = Color(0xFFFFFFFF)
val LightSurfaceHC = Color(0xFFFFFFFF)
val LightSurfaceAltHC = Color(0xFFEAEAEA)
val LightTextHC = Color(0xFF000000)
val LightTextSoftHC = Color(0xFF1A1A1A)
val LightTextFaintHC = Color(0xFF404040)
val LightLineHC = Color(0xFF000000)

// Dark - a warm near-black rather than pure black, cards a step lighter for separation without
// a hard border; accents brightened slightly so they still read clearly on dark.
val DarkBackground = Color(0xFF221F19)
val DarkSurface = Color(0xFF2D2A22)
val DarkSurfaceAlt = Color(0xFF38342A)
val DarkPrimary = Color(0xFF7CB89C)
val DarkPrimaryDark = Color(0xFFA6D4BC)
val DarkPrimaryTint = Color(0xFF3A4C42)
val DarkAccent = Color(0xFFF5A87E)
val DarkAccentDark = Color(0xFFF8C4A8)
val DarkAccentTint = Color(0xFF4A3B30)
val DarkSecondary = Color(0xFFA7B8E8)
val DarkSecondaryDark = Color(0xFFC6D1F2)
val DarkSecondaryTint = Color(0xFF353B52)
val DarkText = Color(0xFFF2EEE4)
val DarkTextSoft = Color(0xFFB8B0A0)
val DarkTextFaint = Color(0xFF7D7768)
val DarkLine = Color(0xFF433F34)
val DarkDestructive = Color(0xFFE8897D)
val DarkDestructiveTint = Color(0xFF4A312C)

// High contrast - dark-mode counterpart to the Light*HC set above.
val DarkBackgroundHC = Color(0xFF000000)
val DarkSurfaceHC = Color(0xFF000000)
val DarkSurfaceAltHC = Color(0xFF1C1C1C)
val DarkTextHC = Color(0xFFFFFFFF)
val DarkTextSoftHC = Color(0xFFEDEDED)
val DarkTextFaintHC = Color(0xFFBFBFBF)
val DarkLineHC = Color(0xFFFFFFFF)
