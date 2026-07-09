package com.andweight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CoachingAdvisorTest {

    private val loseProfile = UserProfile(
        startDate = LocalDate.of(2026, 1, 1),
        weightUnit = WeightUnit.KG,
        startWeightKg = 90.0,
        goalWeightKg = 80.0,
        goalRateKgPerWeek = 0.5,
    )

    private fun weekSummary(index: Int, deltaKg: Double?, hasData: Boolean = true) = WeekSummary(
        weekIndex = index,
        startDate = loseProfile.startDate.plusDays((index - 1) * 7L),
        endDate = loseProfile.startDate.plusDays((index - 1) * 7L + 6),
        hasWeightData = hasData,
        hasCalorieData = hasData,
        avgWeightKg = 88.0,
        avgCalories = 2000.0,
        weightDeltaKg = deltaKg,
        tdee = 2200.0,
    )

    private fun stateWithWeeks(weeks: List<WeekSummary>) = TdeeState(
        initialTdeeGuess = 2500.0,
        currentWeightKg = 88.0,
        currentTdee = 2200.0,
        targetDailyDeficit = 500.0,
        recommendedCalories = 1700.0,
        recommendedCaloriesFloorApplied = false,
        weeksToGoal = 10.0,
        goalDate = loseProfile.startDate.plusWeeks(10),
        weeklyHistory = weeks,
    )

    @Test
    fun `not enough data yet gives a neutral encouragement`() {
        val state = stateWithWeeks(listOf(weekSummary(1, -0.5)))
        val advice = CoachingAdvisor.advise(loseProfile, state)
        assertEquals(AdviceSentiment.NEUTRAL, advice.sentiment)
    }

    @Test
    fun `losing right around the goal rate is positive`() {
        val state = stateWithWeeks(listOf(weekSummary(1, -0.5), weekSummary(2, -0.5)))
        val advice = CoachingAdvisor.advise(loseProfile, state)
        assertEquals(AdviceSentiment.POSITIVE, advice.sentiment)
    }

    @Test
    fun `barely losing any weight triggers a caution to eat less`() {
        val state = stateWithWeeks(listOf(weekSummary(1, -0.05), weekSummary(2, -0.05)))
        val advice = CoachingAdvisor.advise(loseProfile, state)
        assertEquals(AdviceSentiment.CAUTION, advice.sentiment)
    }

    @Test
    fun `losing much faster than planned triggers a caution to eat more`() {
        val state = stateWithWeeks(listOf(weekSummary(1, -1.5), weekSummary(2, -1.5)))
        val advice = CoachingAdvisor.advise(loseProfile, state)
        assertEquals(AdviceSentiment.CAUTION, advice.sentiment)
    }

    @Test
    fun `maintain goal holding steady is positive`() {
        val maintainProfile = loseProfile.copy(goalWeightKg = loseProfile.startWeightKg, goalRateKgPerWeek = 0.0)
        val state = stateWithWeeks(listOf(weekSummary(1, 0.05), weekSummary(2, -0.05)))
        val advice = CoachingAdvisor.advise(maintainProfile, state)
        assertEquals(AdviceSentiment.POSITIVE, advice.sentiment)
    }
}
