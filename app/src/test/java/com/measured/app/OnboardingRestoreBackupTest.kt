package com.measured.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import com.measured.app.domain.BackupSerializer
import com.measured.app.domain.DailyLog
import com.measured.app.domain.PaceOption
import com.measured.app.domain.ThemeMode
import com.measured.app.domain.UserProfile
import com.measured.app.domain.WeightUnit
import com.measured.app.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Restoring a backup during onboarding should finish setup the same way manually filling in the
 * form does - saving the profile and logs, and flagging navigation onward - without the person
 * needing to re-type anything the file already has.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingRestoreBackupTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `restoring a backup during onboarding saves the profile and logs, then flags navigation`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AndWeightApplication>()
        app.deleteDatabase("andweight.db")

        val profile = UserProfile(weightUnit = WeightUnit.KG, startWeightKg = 90.0, goalWeightKg = 80.0, pace = PaceOption.MODERATE)
        val settings = BackupSerializer.Settings(
            themeMode = ThemeMode.DARK,
            tipsEnabled = false,
            reminderEnabled = false,
            reminderHour = 21,
            reminderMinute = 0,
        )
        val logs = listOf(DailyLog(LocalDate.of(2026, 4, 1), weightKg = 90.0, calories = 2200.0))
        val backupText = BackupSerializer.build(profile, settings, logs)

        val viewModel = OnboardingViewModel(app.repository, app.settingsRepository, app)
        viewModel.restoreFromBackup(backupText)

        val deadline = System.currentTimeMillis() + 5_000
        while (!viewModel.uiState.didSave && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        assertTrue("restoring a complete backup should flag navigation onward", viewModel.uiState.didSave)
        assertEquals(profile, app.repository.getProfileOnce())
        assertEquals(2200.0, app.repository.getLogForDate(LocalDate.of(2026, 4, 1))?.calories ?: 0.0, 0.01)
    }

    @Test
    fun `restoring a file with no profile section shows an error instead of finishing setup`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AndWeightApplication>()
        app.deleteDatabase("andweight.db")

        val textWithNoProfile = BackupSerializer.build(null, null, emptyList())
        val viewModel = OnboardingViewModel(app.repository, app.settingsRepository, app)
        viewModel.restoreFromBackup(textWithNoProfile)

        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.uiState.errorMessage == null && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        assertTrue(viewModel.uiState.errorMessage != null)
        assertTrue(!viewModel.uiState.didSave)
    }
}
