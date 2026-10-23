package com.measured.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlateauDetectorTest {

    private val today = LocalDate.of(2026, 7, 10)

    private val profile = UserProfile(
        weightUnit = WeightUnit.KG,
        startWeightKg = 90.0,
        goalWeightKg = 80.0,
        pace = PaceOption.MODERATE,
    )

    private fun flatLogs(days: Int, weightKg: Double = 90.0) =
        (0 until days).map { offset -> DailyLog(today.minusDays((days - 1 - offset).toLong()), weightKg = weightKg, calories = 2000.0) }

    @Test
    fun `maintain goal is never a plateau`() {
        val maintainProfile = profile.copy(goalWeightKg = profile.startWeightKg)
        val logs = flatLogs(28)
        val state = TdeeEngine.compute(maintainProfile, logs, today)
        val assessment = PlateauDetector.assess(maintainProfile, state)
        assertFalse(assessment.isPlateaued)
    }

    @Test
    fun `fewer than 3 flat weeks is not yet a plateau`() {
        val logs = flatLogs(14) // 2 completed weeks, both flat
        val state = TdeeEngine.compute(profile, logs, today)
        val assessment = PlateauDetector.assess(profile, state)
        assertFalse(assessment.isPlateaued)
        assertEquals(2, assessment.weeksFlat)
    }

    @Test
    fun `3 or more consecutive flat weeks is a plateau`() {
        val logs = flatLogs(28) // 4 completed weeks, all at a constant weight
        val state = TdeeEngine.compute(profile, logs, today)
        val assessment = PlateauDetector.assess(profile, state)
        assertTrue(assessment.isPlateaued)
        assertEquals(4, assessment.weeksFlat)
    }

    @Test
    fun `a recent week of real progress clears an earlier plateau`() {
        // 3 flat weeks, then a clearly-moving final week - only the streak counting back from
        // the most recent week should matter, not that a plateau happened earlier in the history.
        val flatPart = flatLogs(21)
        val movingWeek = (0 until 7).map { offset ->
            DailyLog(today.minusDays((6 - offset).toLong()), weightKg = 87.0, calories = 2000.0)
        }
        val state = TdeeEngine.compute(profile, flatPart + movingWeek, today)
        val assessment = PlateauDetector.assess(profile, state)
        assertFalse(assessment.isPlateaued)
        assertEquals(0, assessment.weeksFlat)
    }

    @Test
    fun `steady on-pace progress is not a plateau`() {
        val logs = (0 until 28).map { offset ->
            DailyLog(today.minusDays((27 - offset).toLong()), weightKg = 90.0 - offset * 0.1, calories = 2000.0)
        }
        val state = TdeeEngine.compute(profile, logs, today)
        val assessment = PlateauDetector.assess(profile, state)
        assertFalse(assessment.isPlateaued)
    }
}
