package com.andweight.app.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Shown wherever age is entered, in onboarding and in Edit Profile. There's no way to verify age,
 * so this is a caution rather than a gate - but calorie tracking isn't appropriate for a growing
 * body, and it's worth saying so plainly rather than quietly going along with it.
 */
@Composable
fun AgeWarningText(ageText: String) {
    val age = ageText.toIntOrNull() ?: return
    if (age !in 1 until 18) return
    Text(
        "This app isn't designed for people under 18 - growing bodies have different nutritional " +
            "needs than what this app accounts for. Please talk to a doctor before using a " +
            "calorie-tracking app.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
