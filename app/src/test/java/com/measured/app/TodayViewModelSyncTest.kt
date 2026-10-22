package com.measured.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import com.measured.app.domain.PaceOption
import com.measured.app.domain.UserProfile
import com.measured.app.domain.WeightUnit
import com.measured.app.ui.today.TodayViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Regression test for a reported bug: deleting today's entry from the Journey tab left the
 * Status tab still showing it until the day was navigated away from and back. The root cause was
 * [TodayViewModel] loading [TodayViewModel.weightLog] once per navigation action instead of
 * reacting to the logs those actions ultimately changed - so a deletion made anywhere else in the
 * app (not through this ViewModel's own save/delete methods) never reached it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodayViewModelSyncTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `deleting today's log elsewhere clears the check-in card without navigating away`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AndWeightApplication>()
        app.deleteDatabase("andweight.db")
        val today = LocalDate.now()
        app.repository.saveProfile(
            UserProfile(weightUnit = WeightUnit.KG, startWeightKg = 80.0, goalWeightKg = 70.0, pace = PaceOption.MODERATE),
        )
        app.repository.saveLog(date = today, weightKg = 79.0, calories = 2000.0, intakeLevel = null, wasExact = true)

        val viewModel = TodayViewModel(app.repository, app.settingsRepository)
        idleUntil(5_000) { viewModel.weightLog != null }
        assertNotNull("expected the seeded log to load", viewModel.weightLog)

        // Not a call through the ViewModel - this mirrors deleting the entry from the Journey tab.
        app.repository.deleteLog(today)

        idleUntil(5_000) { viewModel.weightLog == null }
        assertNull("deletion made elsewhere should still clear the check-in card", viewModel.weightLog)
    }

    private fun idleUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }
}
