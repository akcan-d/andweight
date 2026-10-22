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
 * ui/theme/Theme.kt), using the widgets' own independent theme choice rather than whatever the
 * app's live theme happens to be right now - see [com.measured.app.data.SettingsRepository]'s
 * widgetThemeModeFlow/widgetAccentThemeFlow/widgetHighContrastFlow for why those are kept
 * separate from the in-app ones. Falls back to Glance's generic Material You default only if
 * neither has ever been set, which doesn't happen in practice since both default to the same
 * starting values. */
object WidgetTheme {

    data class Resolved(val colorProviders: ColorProviders, val scheme: ColorScheme)

    suspend fun resolve(context: Context): Resolved {
        val app = context.applicationContext as AndWeightApplication
        val themeMode = app.settingsRepository.widgetThemeModeFlow.first()
        val accentTheme = app.settingsRepository.widgetAccentThemeFlow.first()
        val highContrast = app.settingsRepository.widgetHighContrastFlow.first()

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
