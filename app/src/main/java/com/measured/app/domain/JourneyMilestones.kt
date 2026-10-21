package com.measured.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

enum class MilestoneCategory(val label: String) {
    TIME("Time"),
    STREAK("Consistency"),
    PROGRESS("Progress"),
    AMOUNT("Weight moved"),
}

data class Milestone(
    val category: MilestoneCategory,
    val title: String,
    val achieved: Boolean,
    /** Only set when [achieved] is false - a short "how much further" hint. */
    val hint: String? = null,
)

/**
 * Generic, non-medical milestones for the Journey screen - deliberately built only from things
 * this app can actually verify (elapsed time, logging consistency, progress toward the stated
 * goal, weight moved), rather than asserting specific physiological outcomes at given weights or
 * dates, which would be unverifiable claims dressed up as guarantees.
 *
 * Each category shows only the single latest threshold already reached, plus the single next one
 * coming up - not every rung on the ladder - so working through a batch of logs at once doesn't
 * turn into a wall of "1kg down, 5kg down, 10kg down" chips that all say the same thing in
 * different words. The titles already carry that history implicitly (reaching "10kg down" means
 * you also passed 5kg and 1kg).
 */
object JourneyMilestones {

    private val TIME_THRESHOLDS = listOf(
        1L to "First check-in",
        7L to "One week in",
        14L to "Two weeks in",
        30L to "One month in",
        90L to "Three months in",
        180L to "Six months in",
        365L to "One year in",
    )

    private val STREAK_THRESHOLDS = listOf(
        3 to "3-day streak",
        7 to "7-day streak",
        14 to "Two-week streak",
        30 to "30-day streak",
        60 to "60-day streak",
        100 to "100-day streak",
    )

    private val PROGRESS_THRESHOLDS = listOf(
        0.10 to "10% of the way there",
        0.25 to "A quarter of the way there",
        0.50 to "Halfway there",
        0.75 to "Three-quarters there",
        1.00 to "Goal reached",
    )

    // A single round-number progression shared by every goal size, rather than one fixed list of
    // thresholds for everyone - a 3kg goal and a 50kg goal both deserve milestones that feel like
    // real progress rather than either trivial (50kg goal passing "1kg down") or unreachable (3kg
    // goal needing "50kg down"). AMOUNT_FRACTION_RANGE then picks the slice of this progression
    // that's actually meaningful for a given goal's total distance.
    private val AMOUNT_PROGRESSION_KG = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
    private val AMOUNT_PROGRESSION_LB = listOf(1.0, 2.0, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0)
    private val AMOUNT_FRACTION_RANGE = 0.05..0.95

    fun compute(profile: UserProfile, state: TdeeState, logs: List<DailyLog>, today: LocalDate): List<Milestone> {
        val milestones = mutableListOf<Milestone>()

        val firstLoggedDate = logs.minOfOrNull { it.date }
        if (firstLoggedDate != null) {
            val daysSinceStart = ChronoUnit.DAYS.between(firstLoggedDate, today) + 1
            milestones += timeMilestones(daysSinceStart)
        }

        milestones += streakMilestones(longestLoggingStreak(logs))

        if (profile.goalDirection != GoalDirection.MAINTAIN) {
            val totalNeededKg = abs(profile.goalWeightKg - state.earliestKnownWeightKg)
            if (totalNeededKg > 0.01) {
                val movedKg = abs(state.currentWeightKg - state.earliestKnownWeightKg)
                milestones += progressMilestones(movedKg / totalNeededKg)
                milestones += amountMilestones(movedKg, totalNeededKg, profile.weightUnit, profile.goalDirection)
            }
        }

        return milestones
    }

    private fun timeMilestones(daysSinceStart: Long): List<Milestone> {
        val latestAchieved = TIME_THRESHOLDS.indexOfLast { (threshold, _) -> daysSinceStart >= threshold }
        val result = mutableListOf<Milestone>()
        if (latestAchieved >= 0) {
            result += Milestone(MilestoneCategory.TIME, TIME_THRESHOLDS[latestAchieved].second, achieved = true)
        }
        TIME_THRESHOLDS.getOrNull(latestAchieved + 1)?.let { (threshold, title) ->
            val remaining = threshold - daysSinceStart
            result += Milestone(MilestoneCategory.TIME, title, achieved = false, hint = "${dayWord(remaining)} to go")
        }
        return result
    }

    private fun streakMilestones(longestStreak: Int): List<Milestone> {
        val latestAchieved = STREAK_THRESHOLDS.indexOfLast { (threshold, _) -> longestStreak >= threshold }
        val result = mutableListOf<Milestone>()
        if (latestAchieved >= 0) {
            result += Milestone(MilestoneCategory.STREAK, STREAK_THRESHOLDS[latestAchieved].second, achieved = true)
        }
        STREAK_THRESHOLDS.getOrNull(latestAchieved + 1)?.let { (threshold, title) ->
            val remaining = threshold - longestStreak
            result += Milestone(
                MilestoneCategory.STREAK,
                title,
                achieved = false,
                hint = "${dayWord(remaining.toLong())} more in a row",
            )
        }
        return result
    }

    private fun progressMilestones(rawRatio: Double): List<Milestone> {
        val ratio = rawRatio.coerceIn(0.0, 1.0)
        val latestAchieved = PROGRESS_THRESHOLDS.indexOfLast { (threshold, _) -> ratio >= threshold - 0.001 }
        val result = mutableListOf<Milestone>()
        if (latestAchieved >= 0) {
            result += Milestone(MilestoneCategory.PROGRESS, PROGRESS_THRESHOLDS[latestAchieved].second, achieved = true)
        }
        PROGRESS_THRESHOLDS.getOrNull(latestAchieved + 1)?.let { (threshold, title) ->
            val remainingPercent = ((threshold - ratio) * 100).roundToLong()
            result += Milestone(MilestoneCategory.PROGRESS, title, achieved = false, hint = "$remainingPercent% to go")
        }
        return result
    }

    private fun amountMilestones(
        movedKg: Double,
        totalNeededKg: Double,
        unit: WeightUnit,
        direction: GoalDirection,
    ): List<Milestone> {
        val progression = if (unit == WeightUnit.KG) AMOUNT_PROGRESSION_KG else AMOUNT_PROGRESSION_LB
        val totalNeededInUnit = unit.fromKg(totalNeededKg)
        val thresholdsInUnit = progression.filter {
            it / totalNeededInUnit in AMOUNT_FRACTION_RANGE
        }
        if (thresholdsInUnit.isEmpty()) return emptyList()

        val unitLabel = unit.name.lowercase()
        val verb = if (direction == GoalDirection.LOSE) "down" else "up"
        fun titleFor(thresholdInUnit: Double) = "${formatOneDecimal(thresholdInUnit)} $unitLabel $verb"

        val latestAchieved = thresholdsInUnit.indexOfLast { movedKg >= unit.toKg(it) - 0.01 }
        val result = mutableListOf<Milestone>()
        if (latestAchieved >= 0) {
            result += Milestone(MilestoneCategory.AMOUNT, titleFor(thresholdsInUnit[latestAchieved]), achieved = true)
        }
        thresholdsInUnit.getOrNull(latestAchieved + 1)?.let { thresholdInUnit ->
            val remainingInUnit = unit.fromKg(unit.toKg(thresholdInUnit) - movedKg)
            result += Milestone(
                MilestoneCategory.AMOUNT,
                titleFor(thresholdInUnit),
                achieved = false,
                hint = "${formatOneDecimal(remainingInUnit)} $unitLabel to go",
            )
        }
        return result
    }

    /** Longest run of consecutive calendar days with at least a weight or calorie entry, anywhere
     * in the history - once reached, a streak milestone stays earned even if a later streak breaks,
     * rather than being un-achieved by a missed day. */
    private fun longestLoggingStreak(logs: List<DailyLog>): Int {
        val loggedDates = logs.filter { it.weightKg != null || it.calories != null }
            .map { it.date }
            .distinct()
            .sorted()
        if (loggedDates.isEmpty()) return 0

        var longest = 1
        var current = 1
        for (i in 1 until loggedDates.size) {
            current = if (loggedDates[i] == loggedDates[i - 1].plusDays(1)) current + 1 else 1
            longest = maxOf(longest, current)
        }
        return longest
    }

    private fun dayWord(days: Long): String = if (days == 1L) "1 day" else "$days days"

    private fun formatOneDecimal(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
