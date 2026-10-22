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
 * The meal section always asks about the day before whatever's selected (see
 * [TodayViewModel.mealDate]) - by the time someone checks in, yesterday's eating is finished and
 * known for certain, where today's isn't yet. This confirms saving actually lands the calories on
 * that earlier date rather than the date being viewed.
 *
 * Kept as its own class (one test) rather than bundled with the other - [AppDatabase]'s singleton
 * instance outlives `deleteDatabase()` within a single Robolectric test class, so a second
 * `@Test` method here would see the first one's leftover row instead of a clean database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodayViewModelMealOffsetTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `saving meal info while viewing today stores it on yesterday's date, not today's`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AndWeightApplication>()
        app.deleteDatabase("andweight.db")
        app.repository.saveProfile(
            UserProfile(weightUnit = WeightUnit.KG, startWeightKg = 80.0, goalWeightKg = 70.0, pace = PaceOption.MODERATE),
        )

        val viewModel = TodayViewModel(app.repository, app.settingsRepository)
        idleUntil(5_000) { viewModel.profile.value != null }

        viewModel.onIntakeSelected(IntakeLevel.AVERAGE)
        viewModel.saveMeal()

        idleUntil(5_000) { viewModel.mealLog?.intakeLevel == IntakeLevel.AVERAGE }
        assertEquals(IntakeLevel.AVERAGE, viewModel.mealLog?.intakeLevel)
        assertNull("today's own row shouldn't gain calories meant for the day before", viewModel.weightLog?.calories)
    }

    private fun idleUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }
}
