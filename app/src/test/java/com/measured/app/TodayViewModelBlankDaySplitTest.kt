package com.measured.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import com.measured.app.domain.IntakeLevel
import com.measured.app.domain.PaceOption
import com.measured.app.domain.UserProfile
import com.measured.app.domain.WeightUnit
import com.measured.app.ui.today.TodayViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * On a blank day, the combined form's single Save button writes two separate rows - this day's
 * weight and the day before's meal info (see [TodayViewModel.mealDate]) - rather than one row
 * with both fields, since those two answers are now about different calendar days.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodayViewModelBlankDaySplitTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `saving a blank day writes weight and meal info to two separate rows`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AndWeightApplication>()
        app.deleteDatabase("andweight.db")
        app.repository.saveProfile(
            UserProfile(weightUnit = WeightUnit.KG, startWeightKg = 80.0, goalWeightKg = 70.0, pace = PaceOption.MODERATE),
        )

        val viewModel = TodayViewModel(app.repository)
        idleUntil(5_000) { viewModel.profile.value != null }

        viewModel.onWeightTextChange("78.5")
        viewModel.onIntakeSelected(IntakeLevel.LESS_THAN_NORMAL)
        viewModel.saveDay()

        idleUntil(5_000) { viewModel.weightLog?.weightKg != null && viewModel.mealLog?.intakeLevel != null }
        assertEquals(78.5, viewModel.weightLog?.weightKg ?: 0.0, 0.01)
        assertEquals(IntakeLevel.LESS_THAN_NORMAL, viewModel.mealLog?.intakeLevel)
        assertNull("today's weight row shouldn't also carry the previous day's calories", viewModel.weightLog?.calories)
    }

    private fun idleUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }
}
