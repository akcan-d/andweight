package com.measured.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import com.measured.app.domain.IntakeLevel
import com.measured.app.domain.MealTimingMode
import com.measured.app.domain.PaceOption
import com.measured.app.domain.UserProfile
import com.measured.app.domain.WeightUnit
import com.measured.app.ui.today.TodayViewModel
import kotlinx.coroutines.flow.first
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
 * In same-day mode (see [MealTimingMode.SAME_DAY], set from Settings for people who weigh in
 * after they've already finished eating), weight and meal info share one row since [mealDate]
 * equals [TodayViewModel.selectedDate]. Saving a blank day's combined form must write that as a
 * single atomic save rather than two sequential writes to the same row, which would otherwise
 * have the second write clobber the first with a stale pre-save snapshot of the field it isn't
 * touching.
 *
 * Asserts against [AndWeightRepository.getLogForDate] directly rather than the ViewModel's
 * reactive [TodayViewModel.weightLog]/[TodayViewModel.mealLog] - those depend on a chain of
 * Room's query flow re-emitting, [kotlinx.coroutines.flow.combine] picking it up, and `stateIn`
 * republishing it, which gets measurably slower the later this test lands in a long full-suite
 * run (many Robolectric sandboxes' worth of accumulated Room/DataStore overhead); a direct
 * one-shot repository read sidesteps that entirely.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodayViewModelSameDaySplitTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `saving a blank day in same-day mode writes weight and meal to one row`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AndWeightApplication>()
        app.deleteDatabase("andweight.db")
        app.repository.saveProfile(
            UserProfile(weightUnit = WeightUnit.KG, startWeightKg = 80.0, goalWeightKg = 70.0, pace = PaceOption.MODERATE),
        )
        app.settingsRepository.setMealTimingMode(MealTimingMode.SAME_DAY)
        assertEquals(MealTimingMode.SAME_DAY, app.settingsRepository.mealTimingModeFlow.first())

        val viewModel = TodayViewModel(app.repository, app.settingsRepository)
        // saveDay() bails out silently (profile.value ?: return) if the profile hasn't loaded
        // yet - waiting on mealTimingMode alone isn't enough to know saveDay() will actually do
        // anything once called.
        idleUntil(30_000) { viewModel.profile.value != null && viewModel.mealTimingMode.value == MealTimingMode.SAME_DAY }

        viewModel.onWeightTextChange("78.5")
        viewModel.onIntakeSelected(IntakeLevel.LESS_THAN_NORMAL)
        viewModel.saveDay()

        val today = LocalDate.now()
        idleUntilSuspend(30_000) {
            val row = app.repository.getLogForDate(today)
            row?.weightKg != null && row.intakeLevel != null
        }

        val result = app.repository.getLogForDate(today)
        assertEquals(78.5, result?.weightKg ?: 0.0, 0.01)
        assertEquals(IntakeLevel.LESS_THAN_NORMAL, result?.intakeLevel)
    }

    // Asserts on timeout rather than returning silently - a condition that never becomes true
    // should fail loudly right here, not masquerade as some unrelated assertion failure further
    // down once the test presses on with a false premise.
    private fun idleUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    private suspend fun idleUntilSuspend(timeoutMs: Long, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }
}
