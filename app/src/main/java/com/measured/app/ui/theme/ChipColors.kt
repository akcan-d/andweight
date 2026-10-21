package com.measured.app.ui.theme

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Material3's FilterChip defaults its selected state to secondaryContainer, not
 * primaryContainer. Harmless for a chip whose selected color is incidental, but every accent
 * theme's secondary is deliberately a different hue from its primary (Sage's secondary is blue,
 * Ocean's is green) - so left at the default, a selected chip's color didn't match the theme it
 * was supposedly reflecting. Every selectable FilterChip in the app should use this instead, so
 * a selected chip always reads as "this theme's own color", not a fixed, theme-independent hue. */
@Composable
fun themedFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
