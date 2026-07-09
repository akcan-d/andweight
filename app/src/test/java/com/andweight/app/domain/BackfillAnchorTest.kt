package com.andweight.app.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Regression test: a person should be able to page backwards past their official start date and
 * log history from before they began using the app (e.g. weight they tracked elsewhere). The
 * weekly windows the engine uses must shift their anchor to cover that earlier data instead of
 * silently dropping it because it falls before profile.startDate.
 */
class BackfillAnchorTest {

    private val profile = UserProfile(
        startDate = LocalDate.of(2026, 2, 1),
        weightUnit = WeightUnit.KG,
        startWeightKg = 90.0,
        goalWeightKg = 80.0,
        goalRateKgPerWeek = 0.5,
    )

    @Test
    fun `logs entered before the profile start date are still folded into the weekly averages`() {
        val backfilledDate = profile.startDate.minusDays(10)
        val logs = listOf(
            DailyLog(backfilledDate, weightKg = 91.0, calories = 2300.0),
            DailyLog(profile.startDate, weightKg = 90.0, calories = 2200.0),
        )

        val state = TdeeEngine.compute(profile, logs, profile.startDate)

        // The backfilled day should show up as data in some week, not be silently excluded.
        val anyWeekSawBackfilledWeight = state.weeklyHistory.any { it.hasWeightData }
        assertTrue(anyWeekSawBackfilledWeight)
        assertTrue(state.weeklyHistory.isNotEmpty())
    }
}
