package com.measured.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import com.measured.app.domain.MealTimingMode
import com.measured.app.ui.today.CheckInReminderViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/** See [CheckInBadgeShowsWhenIncompleteTest] for why this is its own single-test class. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckInBadgeSameDayModeTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `in same-day mode a single row with both fields clears the badge`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AndWeightApplication>()
        app.deleteDatabase("andweight.db")
        val today = LocalDate.now()
        app.repository.deleteLog(today)
        app.settingsRepository.setMealTimingMode(MealTimingMode.SAME_DAY)
        app.repository.saveLog(date = today, weightKg = 78.5, calories = null, intakeLevel = null, wasExact = false)

        val viewModel = CheckInReminderViewModel(app.repository, app.settingsRepository)
        idleUntil(30_000) { viewModel.showBadge.value }

        app.repository.saveLog(date = today, weightKg = 78.5, calories = 2000.0, intakeLevel = null, wasExact = true)
        idleUntilFalse(30_000) { viewModel.showBadge.value }
        assertFalse(viewModel.showBadge.value)
    }

    private fun idleUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    private fun idleUntilFalse(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (condition() && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertFalse("condition still true after ${timeoutMs}ms", condition())
    }
}
