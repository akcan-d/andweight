package com.andweight.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF2E7D32)
private val GreenDark = Color(0xFF6FBF73)

private val LightColors = lightColorScheme(
    primary = Green,
    secondary = Color(0xFF00695C),
)

private val DarkColors = darkColorScheme(
    primary = GreenDark,
    secondary = Color(0xFF4DB6AC),
)

@Composable
fun AndWeightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
