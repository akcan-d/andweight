package com.measured.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import com.measured.app.domain.IntakeLevel
import com.measured.app.domain.MealTimingMode
import com.measured.app.domain.PaceOption
import com.measured.app.domain.UserProfile
import com.measured.app.domain.WeightUnit
import com.measured.app.ui.today.TodayViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * In same-day mode (see [MealTimingMode.SAME_DAY], set from Settings for people who weigh in
 * after they've already finished eating), weight and meal info share one row since [mealDate]
 * equals [TodayViewModel.selectedDate]. Saving a blank day's combined form must write that as a
 * single atomic save rather than two sequential writes to the same row, which would otherwise
 * have the second write clobber the first with a stale pre-save snapshot of the field it isn't
 * touching.
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

        val viewModel = TodayViewModel(app.repository, app.settingsRepository)
        idleUntil(5_000) { viewModel.mealTimingMode.value == MealTimingMode.SAME_DAY }

        viewModel.onWeightTextChange("78.5")
        viewModel.onIntakeSelected(IntakeLevel.LESS_THAN_NORMAL)
        viewModel.saveDay()

        idleUntil(5_000) { viewModel.weightLog?.weightKg != null && viewModel.weightLog?.intakeLevel != null }
        assertEquals(78.5, viewModel.weightLog?.weightKg ?: 0.0, 0.01)
        assertEquals(IntakeLevel.LESS_THAN_NORMAL, viewModel.weightLog?.intakeLevel)
        assertEquals(viewModel.weightLog, viewModel.mealLog)
    }

    private fun idleUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }
}
