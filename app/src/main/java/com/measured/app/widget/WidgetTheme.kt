package com.measured.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders as Material3ColorProviders
import com.measured.app.AndWeightApplication
import com.measured.app.domain.ThemeMode
import com.measured.app.ui.theme.accentPaletteFor
import com.measured.app.ui.theme.darkScheme
import com.measured.app.ui.theme.lightScheme
import kotlinx.coroutines.flow.first

/** Builds the widget's color scheme from the exact same tokens as the in-app theme (see
 * ui/theme/Theme.kt), so the widget never drifts from whatever accent/light-dark combination the
 * person picked in Settings, instead of falling back to Glance's generic Material You default. */
object WidgetTheme {

    data class Resolved(val colorProviders: ColorProviders, val scheme: ColorScheme)

    suspend fun resolve(context: Context): Resolved {
        val app = context.applicationContext as AndWeightApplication
        val themeMode = app.settingsRepository.themeModeFlow.first()
        val accentTheme = app.settingsRepository.accentThemeFlow.first()
        val highContrast = app.settingsRepository.highContrastFlow.first()

        val systemIsDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val isDark = when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemIsDark
        }

        val accent = accentPaletteFor(accentTheme, isDark)
        val scheme = if (isDark) darkScheme(accent, highContrast) else lightScheme(accent, highContrast)
        // Both slots get the same already-resolved scheme - Glance otherwise picks between
        // light/dark using the raw system setting, which would ignore a LIGHT/DARK override
        // chosen in Settings instead of "match system".
        return Resolved(colorProviders = Material3ColorProviders(light = scheme, dark = scheme), scheme = scheme)
    }
}
