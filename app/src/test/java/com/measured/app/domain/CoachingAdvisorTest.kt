package com.measured.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CoachingAdvisorTest {

    private val referenceDate = LocalDate.of(2026, 1, 1)

    // RELAXED at 88 kg (the currentWeightKg used by stateWithWeeks below) gives a weekly rate of
    // 0.528 kg - the ratios used by the individual tests below are chosen relative to that.
    private val loseProfile = UserProfile(
        weightUnit = WeightUnit.KG,
        startWeightKg = 90.0,
        goalWeightKg = 80.0,
        pace = PaceOption.RELAXED,
    )

    private fun weekSummary(index: Int, deltaKg: Double?, hasData: Boolean = true) = WeekSummary(
        weekIndex = index,
        startDate = referenceDate.plusDays((index - 1) * 7L),
        endDate = referenceDate.plusDays((index - 1) * 7L + 6),
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
        earliestKnownWeightKg = 90.0,
        currentTdee = 2200.0,
        targetDailyDeficit = 500.0,
        recommendedCalories = 1700.0,
        recommendedCaloriesFloorApplied = false,
        weeksToGoal = 10.0,
        goalDate = referenceDate.plusWeeks(10),
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
        val maintainProfile = loseProfile.copy(goalWeightKg = loseProfile.startWeightKg, pace = null)
        val state = stateWithWeeks(listOf(weekSummary(1, 0.05), weekSummary(2, -0.05)))
        val advice = CoachingAdvisor.advise(maintainProfile, state)
        assertEquals(AdviceSentiment.POSITIVE, advice.sentiment)
    }

    // Reported as "weird numbers": backfilling realistic, steadily-decreasing daily weights still
    // produced "eat less" advice. Traced through the real TdeeEngine pipeline (not the synthetic
    // WeekSummary helper above) to confirm this is by design, not a miscalculation - the advisor
    // compares actual loss against the *chosen pace's own target rate*, which scales with body
    // weight (see PaceGuidance), not against "any weight loss at all". The same 0.2 kg/week real
    // trajectory below reads as "too slow" under a faster-than-RELAXED pace, and "on track" under
    // RELAXED - same data, different verdict, because the target itself is different.
    private fun flatWeekLogs(startDate: LocalDate, weeklyWeights: List<Double>, dailyCalories: Double): List<DailyLog> =
        weeklyWeights.flatMapIndexed { weekIndex, weight ->
            (0 until 7).map { day -> DailyLog(startDate.plusDays(weekIndex * 7L + day), weight, dailyCalories) }
        }

    @Test
    fun `realistic slow backfill reads as behind a moderate pace but on track for a relaxed one`() {
        val profile = UserProfile(
            weightUnit = WeightUnit.KG,
            startWeightKg = 88.0,
            goalWeightKg = 75.0,
            pace = PaceOption.MODERATE,
        )
        // Three weeks, losing a real-world-plausible 0.2 kg/week throughout - not a crash diet,
        // not a plateau.
        val logs = flatWeekLogs(referenceDate, listOf(88.0, 87.8, 87.6), dailyCalories = 2000.0)
        val today = referenceDate.plusDays(20)

        val moderateState = TdeeEngine.compute(profile, logs, today)
        val moderateAdvice = CoachingAdvisor.advise(profile, moderateState)
        assertEquals(AdviceSentiment.CAUTION, moderateAdvice.sentiment)

        val relaxedState = TdeeEngine.compute(profile.copy(pace = PaceOption.RELAXED), logs, today)
        val relaxedAdvice = CoachingAdvisor.advise(profile.copy(pace = PaceOption.RELAXED), relaxedState)
        assertEquals(AdviceSentiment.POSITIVE, relaxedAdvice.sentiment)
    }
}
