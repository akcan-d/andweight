package com.measured.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import com.measured.app.domain.MealTimingMode
import com.measured.app.ui.today.CheckInReminderViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The Status-tab "still needs a check-in" badge (see [CheckInReminderViewModel]) was accidentally
 * deleted along with its old togglable setting in a previous batch - this is a regression test
 * for its restoration, not just a setting change, so it checks the actual on/off behavior rather
 * than just that the class compiles.
 *
 * Kept as its own class (one test) rather than bundled with the others, same reasoning as
 * [TodayViewModelMealOffsetTest] - but that alone isn't enough here: [AppDatabase]'s singleton
 * also outlives `deleteDatabase()` ACROSS test classes within one full-suite run (not just within
 * one class), so [TodayViewModelMealOffsetTest]'s own leftover yesterday-row can still be sitting
 * there when this test's class loads. Explicitly clearing today's and yesterday's rows (not just
 * calling `deleteDatabase()`) is what actually guarantees a clean slate for the two dates this
 * test cares about.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckInBadgeShowsWhenIncompleteTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `badge shows when yesterday's meal is missing in previous-day mode`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AndWeightApplication>()
        app.deleteDatabase("andweight.db")
        val today = LocalDate.now()
        app.repository.deleteLog(today)
        app.repository.deleteLog(today.minusDays(1))
        app.settingsRepository.setMealTimingMode(MealTimingMode.PREVIOUS_DAY)
        app.repository.saveLog(date = today, weightKg = 78.5, calories = null, intakeLevel = null, wasExact = false)

        val viewModel = CheckInReminderViewModel(app.repository, app.settingsRepository)
        idleUntil(30_000) { viewModel.showBadge.value }
        assertTrue(viewModel.showBadge.value)
    }

    private fun idleUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }
}
