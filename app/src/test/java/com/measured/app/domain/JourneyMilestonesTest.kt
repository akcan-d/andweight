package com.measured.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JourneyMilestonesTest {

    private val referenceDate = LocalDate.of(2026, 1, 1)

    private val loseProfile = UserProfile(
        weightUnit = WeightUnit.KG,
        startWeightKg = 100.0,
        goalWeightKg = 80.0,
        pace = PaceOption.MODERATE,
    )

    private fun stateWith(currentWeightKg: Double, earliestKg: Double = 100.0) = TdeeState(
        initialTdeeGuess = 2500.0,
        currentWeightKg = currentWeightKg,
        earliestKnownWeightKg = earliestKg,
        currentTdee = 2200.0,
        targetDailyDeficit = 500.0,
        recommendedCalories = 1700.0,
        recommendedCaloriesFloorApplied = false,
        weeksToGoal = 10.0,
        goalDate = referenceDate.plusWeeks(10),
        weeklyHistory = emptyList(),
    )

    private fun logsOverDays(days: Int) = (0 until days).map {
        DailyLog(referenceDate.plusDays(it.toLong()), weightKg = 100.0, calories = 2000.0)
    }

    @Test
    fun `time milestones mark everything reached so far as achieved and only the next one as upcoming`() {
        val logs = logsOverDays(8) // day 1 through day 8 logged
        val today = referenceDate.plusDays(7) // 8 days since first log, inclusive
        val milestones = JourneyMilestones.compute(loseProfile, stateWith(99.0), logs, today)
            .filter { it.category == MilestoneCategory.TIME }

        val achievedTitles = milestones.filter { it.achieved }.map { it.title }
        assertEquals(listOf("First check-in", "One week in"), achievedTitles)

        val next = milestones.first { !it.achieved }
        assertEquals("Two weeks in", next.title)
        assertEquals("6 days to go", next.hint)
    }

    @Test
    fun `longest streak is remembered even after it is later broken`() {
        val logs = logsOverDays(7) + DailyLog(referenceDate.plusDays(10), weightKg = 98.0, calories = 2000.0)
        val today = referenceDate.plusDays(10)
        val milestones = JourneyMilestones.compute(loseProfile, stateWith(98.0), logs, today)
            .filter { it.category == MilestoneCategory.STREAK }

        assertTrue(milestones.any { it.achieved && it.title == "7-day streak" })
        val next = milestones.first { !it.achieved }
        assertEquals("Two-week streak", next.title)
    }

    @Test
    fun `progress and amount milestones are absent when maintaining`() {
        val maintainProfile = loseProfile.copy(goalWeightKg = loseProfile.startWeightKg, pace = null)
        val logs = logsOverDays(3)
        val milestones = JourneyMilestones.compute(maintainProfile, stateWith(100.0), logs, referenceDate.plusDays(2))

        assertTrue(milestones.none { it.category == MilestoneCategory.PROGRESS })
        assertTrue(milestones.none { it.category == MilestoneCategory.AMOUNT })
    }

    @Test
    fun `progress ratio reflects how far current weight has moved toward the goal`() {
        val logs = logsOverDays(3)
        // Started at 100, goal 80 (needs 20kg), currently at 90 -> halfway there.
        val milestones = JourneyMilestones.compute(loseProfile, stateWith(currentWeightKg = 90.0, earliestKg = 100.0), logs, referenceDate.plusDays(2))
            .filter { it.category == MilestoneCategory.PROGRESS }

        val achievedTitles = milestones.filter { it.achieved }.map { it.title }
        assertEquals(listOf("10% of the way there", "A quarter of the way there", "Halfway there"), achievedTitles)
        assertEquals("Three-quarters there", milestones.first { !it.achieved }.title)
    }

    @Test
    fun `amount milestones use clean numbers in the person's own unit`() {
        val lbProfile = loseProfile.copy(weightUnit = WeightUnit.LB)
        val logs = logsOverDays(3)
        // Moved 4.5 kg =~ 9.9 lb, so "5 lb down" should be achieved but "10 lb down" should not yet.
        val milestones = JourneyMilestones.compute(lbProfile, stateWith(currentWeightKg = 95.5, earliestKg = 100.0), logs, referenceDate.plusDays(2))
            .filter { it.category == MilestoneCategory.AMOUNT }

        assertTrue(milestones.any { it.achieved && it.title == "5 lb down" })
        assertTrue(milestones.any { !it.achieved && it.title == "10 lb down" })
    }

    @Test
    fun `no logs yet means no time or streak milestones are computed`() {
        val milestones = JourneyMilestones.compute(loseProfile, stateWith(100.0), emptyList(), referenceDate)
        assertNull(milestones.firstOrNull { it.category == MilestoneCategory.TIME })
        assertTrue(milestones.none { it.category == MilestoneCategory.STREAK && it.achieved })
    }
}
