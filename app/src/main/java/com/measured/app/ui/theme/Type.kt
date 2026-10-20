package com.measured.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.measured.app.R

/** Rounded and friendly - used for headings, numbers, and anything that should feel warm rather
 * than clinical (the big goal-countdown number, card titles). */
@OptIn(ExperimentalTextApi::class)
val Quicksand = FontFamily(
    Font(R.font.quicksand_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.quicksand_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.quicksand_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Plain and easy to read in sentences - used for body copy, lists, and anything meant to be
 * read rather than glanced at. */
@OptIn(ExperimentalTextApi::class)
val Figtree = FontFamily(
    Font(R.font.figtree_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.figtree_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.figtree_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.figtree_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

val AndWeightTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Quicksand, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = Quicksand, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = Quicksand, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = Quicksand, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Quicksand, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Quicksand, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = Quicksand, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = Quicksand, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = Quicksand, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = Figtree),
        bodyMedium = base.bodyMedium.copy(fontFamily = Figtree),
        bodySmall = base.bodySmall.copy(fontFamily = Figtree),
        labelLarge = base.labelLarge.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold),
    )
}
