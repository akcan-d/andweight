package com.andweight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceGuidanceTest {

    @Test
    fun `max safe loss rate is 1 percent of body weight, capped at 2kg per week`() {
        // 1% of 90kg = 0.9, well under the 2kg cap.
        assertEquals(0.9, PaceGuidance.maxSafeWeeklyRateKg(90.0, GoalDirection.LOSE), 1e-9)
        // 1% of 250kg = 2.5, which must be clamped down to the absolute 2kg/week cap.
        assertEquals(2.0, PaceGuidance.maxSafeWeeklyRateKg(250.0, GoalDirection.LOSE), 1e-9)
    }

    @Test
    fun `max safe gain rate is slower than loss, capped at 1kg per week`() {
        assertEquals(0.4, PaceGuidance.maxSafeWeeklyRateKg(80.0, GoalDirection.GAIN), 1e-9)
        assertEquals(1.0, PaceGuidance.maxSafeWeeklyRateKg(300.0, GoalDirection.GAIN), 1e-9)
    }

    @Test
    fun `maintain has no rate at all`() {
        assertEquals(0.0, PaceGuidance.maxSafeWeeklyRateKg(90.0, GoalDirection.MAINTAIN), 1e-9)
    }

    @Test
    fun `no pace option ever exceeds the safe maximum`() {
        val max = PaceGuidance.maxSafeWeeklyRateKg(90.0, GoalDirection.LOSE)
        for (option in PaceOption.entries) {
            val rate = PaceGuidance.rateForOption(option, 90.0, GoalDirection.LOSE)
            assertTrue("rate for $option ($rate) must not exceed max ($max)", rate <= max + 1e-9)
            assertTrue("rate for $option must be positive", rate > 0.0)
        }
    }

    @Test
    fun `relaxed is slower than moderate which is slower than fastest`() {
        val relaxed = PaceGuidance.rateForOption(PaceOption.RELAXED, 90.0, GoalDirection.LOSE)
        val moderate = PaceGuidance.rateForOption(PaceOption.MODERATE, 90.0, GoalDirection.LOSE)
        val fastest = PaceGuidance.rateForOption(PaceOption.FASTEST, 90.0, GoalDirection.LOSE)
        assertTrue(relaxed < moderate)
        assertTrue(moderate < fastest)
    }
}
