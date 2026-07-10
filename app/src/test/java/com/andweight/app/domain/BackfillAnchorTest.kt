package com.andweight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Regression test: a person should be able to page backwards and log history from before their
 * oldest existing entry (e.g. weight they tracked elsewhere). The weekly windows the engine uses
 * are anchored to the oldest logged day, so that backfilled data shifts the anchor earlier instead
 * of silently falling outside every window.
 */
class BackfillAnchorTest {

    private val profile = UserProfile(
        weightUnit = WeightUnit.KG,
        startWeightKg = 90.0,
        goalWeightKg = 80.0,
        pace = PaceOption.MODERATE,
    )

    @Test
    fun `weekly windows anchor to the oldest logged day, not the day the profile happens to be viewed`() {
        val today = LocalDate.of(2026, 2, 1)
        val backfilledDate = today.minusDays(10)
        val logs = listOf(
            DailyLog(backfilledDate, weightKg = 91.0, calories = 2300.0),
            DailyLog(today, weightKg = 90.0, calories = 2200.0),
        )

        val state = TdeeEngine.compute(profile, logs, today)

        // The backfilled day should show up as data in some week, not be silently excluded.
        val anyWeekSawBackfilledWeight = state.weeklyHistory.any { it.hasWeightData }
        assertTrue(anyWeekSawBackfilledWeight)
        assertTrue(state.weeklyHistory.isNotEmpty())
        // The first week's window should start on the oldest logged date itself.
        assertEquals(backfilledDate, state.weeklyHistory.first().startDate)
    }
}
