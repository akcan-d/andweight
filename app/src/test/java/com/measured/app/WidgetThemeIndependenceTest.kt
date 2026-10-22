package com.measured.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import com.measured.app.domain.AccentTheme
import com.measured.app.domain.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Widgets keep their own theme, separate from the in-app one - changing the app's accent theme
 * or light/dark mode must not change what's drawn on an already-placed widget on its own, only
 * "Sync widget theme" (SettingsViewModel.syncWidgetTheme) should.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetThemeIndependenceTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `changing the app theme does not change the widget theme until explicitly synced`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AndWeightApplication>()
        val settings = app.settingsRepository

        // Known starting point for both, regardless of whatever an earlier test class left behind.
        settings.setAccentTheme(AccentTheme.SAGE)
        settings.setThemeMode(ThemeMode.SYSTEM)
        settings.setHighContrast(false)
        settings.syncWidgetThemeToApp()
        assertTrue(settings.isWidgetThemeSyncedOnce())

        settings.setAccentTheme(AccentTheme.BERRY)
        assertFalse("widget theme should not follow the app theme on its own", settings.isWidgetThemeSyncedOnce())
        assertEquals(AccentTheme.SAGE, settings.widgetAccentThemeFlow.first())
        assertEquals(AccentTheme.BERRY, settings.accentThemeFlow.first())

        settings.syncWidgetThemeToApp()
        assertTrue(settings.isWidgetThemeSyncedOnce())
        assertEquals(AccentTheme.BERRY, settings.widgetAccentThemeFlow.first())
    }
}
