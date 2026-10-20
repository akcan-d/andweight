package com.measured.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.measured.app.domain.GoalDirection
import com.measured.app.domain.PaceOption
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

/**
 * Reproduces a user-reported crash: the app crashed right after onboarding ("Start tracking"),
 * and then crashed on every subsequent cold launch too - i.e. as soon as a profile already
 * existed in the database when MainActivity started.
 *
 * Two real bugs were found and fixed:
 *  1. Navigating with `navController.navigate()` directly inside a coroutine right after a
 *     suspend Room call resumed (not guaranteed to land on the main thread).
 *  2. `CheckInCard` nested a `Modifier.verticalScroll()` Column inside the Today screen's
 *     LazyColumn, which Compose explicitly disallows (infinite height constraint).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashReproTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `launching with an existing profile does not crash`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AndWeightApplication>()
        app.deleteDatabase("andweight.db")
        app.repository.saveProfile(
            UserProfile(
                weightUnit = WeightUnit.KG,
                startWeightKg = 80.0,
                goalWeightKg = 70.0,
                pace = PaceOption.MODERATE,
            ),
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `saving onboarding persists the profile and flags navigation`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AndWeightApplication>()
        app.deleteDatabase("andweight.db")

        val viewModel = OnboardingViewModel(app.repository)
        viewModel.onStartWeightChange("80")
        viewModel.onGoalDirectionChange(GoalDirection.LOSE)
        viewModel.onGoalWeightChange("70")
        viewModel.onPaceSelected(PaceOption.MODERATE)

        viewModel.save()

        // The actual DB write happens on Room's real background thread pool, which needs a little
        // real wall-clock time before it posts its completion back to Robolectric's (paused) main
        // Looper - so poll instead of idling exactly once.
        val deadline = System.currentTimeMillis() + 5_000
        while (!viewModel.uiState.didSave && System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        assertTrue(viewModel.uiState.didSave)
        val saved = app.repository.getProfileOnce()
        assertEquals(80.0, saved?.startWeightKg)
        assertEquals(70.0, saved?.goalWeightKg)
    }
}
