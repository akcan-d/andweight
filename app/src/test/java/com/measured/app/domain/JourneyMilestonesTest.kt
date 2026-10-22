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
    fun `time milestones show every threshold reached, not just the latest`() {
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

        // Every rung up to and including the longest streak ever reached stays achieved, not just
        // the top one.
        assertEquals(listOf("3-day streak", "7-day streak"), milestones.filter { it.achieved }.map { it.title })
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

        // Every threshold passed along the way stays visible, not just the latest one.
        val achievedTitles = milestones.filter { it.achieved }.map { it.title }
        assertEquals(listOf("10% of the way there", "A quarter of the way there", "Halfway there"), achievedTitles)
        assertEquals("Three-quarters there", milestones.first { !it.achieved }.title)
    }

    @Test
    fun `amount milestone achievement is based on physical weight moved, not the display unit`() {
        // Moving exactly 2kg should count as reached regardless of whether the person's profile
        // displays kg or lb - an earlier version evaluated against a separately-curated lb
        // progression, so switching the display unit alone (no actual weight change) could flip
        // which thresholds counted as reached.
        val kgLogs = logsOverDays(3)
        val kgMilestones = JourneyMilestones.compute(loseProfile, stateWith(currentWeightKg = 98.0, earliestKg = 100.0), kgLogs, referenceDate.plusDays(2))
            .filter { it.category == MilestoneCategory.AMOUNT }
        val lbProfile = loseProfile.copy(weightUnit = WeightUnit.LB)
        val lbMilestones = JourneyMilestones.compute(lbProfile, stateWith(currentWeightKg = 98.0, earliestKg = 100.0), kgLogs, referenceDate.plusDays(2))
            .filter { it.category == MilestoneCategory.AMOUNT }

        assertEquals(kgMilestones.count { it.achieved }, lbMilestones.count { it.achieved })
        assertTrue(kgMilestones.any { it.achieved && it.title == "2 kg down" })
        assertTrue(lbMilestones.any { it.achieved && it.title == "4 lb down" })

        // Same underlying threshold, so it must carry the same id too - this is what
        // MilestoneUnlockViewModel's "already seen" tracking actually keys off of, and it has to
        // survive a unit switch even though the title text above doesn't.
        val kgId = kgMilestones.first { it.achieved && it.title == "2 kg down" }.id
        val lbId = lbMilestones.first { it.achieved && it.title == "4 lb down" }.id
        assertEquals(kgId, lbId)
    }

    @Test
    fun `amount milestone thresholds scale down for a small goal so they stay reachable`() {
        val smallGoalProfile = loseProfile.copy(startWeightKg = 65.0, goalWeightKg = 60.0) // 5kg goal
        val logs = logsOverDays(3)
        val milestones = JourneyMilestones.compute(
            smallGoalProfile,
            stateWith(currentWeightKg = 64.0, earliestKg = 65.0),
            logs,
            referenceDate.plusDays(2),
        ).filter { it.category == MilestoneCategory.AMOUNT }

        // A 5kg goal should never surface the old fixed "10kg down" threshold - it would be
        // twice the entire goal, so it'd never be reachable at all.
        assertTrue(milestones.none { it.title == "10 kg down" })
        assertTrue(milestones.any { it.title == "1 kg down" } || milestones.any { it.title == "2 kg down" })
    }

    @Test
    fun `moving the goal farther away correctly un-achieves progress milestones`() {
        val logs = logsOverDays(3)
        val state = stateWith(currentWeightKg = 90.0, earliestKg = 100.0) // moved 10kg
        // Against the original 20kg goal distance, 10kg moved is halfway there.
        val before = JourneyMilestones.compute(loseProfile, state, logs, referenceDate.plusDays(2))
        assertTrue(before.any { it.category == MilestoneCategory.PROGRESS && it.achieved && it.title == "Halfway there" })

        // Every call recomputes fresh from the current profile - nothing here is cached from the
        // previous call - so doubling the remaining distance should immediately drop the ratio
        // back down, not leave "Halfway there" stuck as achieved.
        val fartherGoalProfile = loseProfile.copy(goalWeightKg = 60.0) // now needs 40kg total
        val after = JourneyMilestones.compute(fartherGoalProfile, state, logs, referenceDate.plusDays(2))
        assertTrue(after.none { it.category == MilestoneCategory.PROGRESS && it.achieved && it.title == "Halfway there" })
        assertTrue(after.any { it.category == MilestoneCategory.PROGRESS && it.achieved && it.title == "A quarter of the way there" })
    }

    @Test
    fun `moving the goal much closer drops amount thresholds that no longer make sense`() {
        val logs = logsOverDays(3)
        val state = stateWith(currentWeightKg = 90.0, earliestKg = 100.0) // moved 10kg
        val before = JourneyMilestones.compute(loseProfile, state, logs, referenceDate.plusDays(2))
            .filter { it.category == MilestoneCategory.AMOUNT }
        assertTrue(before.any { it.achieved && it.title == "10 kg down" })

        // The goal is now only 10kg away in total, so a "10kg down" milestone would mean "goal
        // reached" and a next rung past it would be nonsensical - it should drop out of the
        // achieved list rather than stay stuck from the previous, more distant goal.
        val closerGoalProfile = loseProfile.copy(goalWeightKg = 90.0) // now needs 10kg total
        val after = JourneyMilestones.compute(closerGoalProfile, state, logs, referenceDate.plusDays(2))
            .filter { it.category == MilestoneCategory.AMOUNT }
        assertTrue(after.none { it.title == "10 kg down" })
    }

    @Test
    fun `no logs yet means no time or streak milestones are computed`() {
        val milestones = JourneyMilestones.compute(loseProfile, stateWith(100.0), emptyList(), referenceDate)
        assertNull(milestones.firstOrNull { it.category == MilestoneCategory.TIME })
        assertTrue(milestones.none { it.category == MilestoneCategory.STREAK && it.achieved })
    }
}
