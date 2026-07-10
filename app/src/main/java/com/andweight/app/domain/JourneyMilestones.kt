package com.andweight.app.domain

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
 * Each category shows every threshold already reached, plus (at most) the single next one coming
 * up - not the whole remaining list - so this stays a small, focused set of "look how far you've
 * come" moments rather than a long checklist.
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

    private val AMOUNT_THRESHOLDS_KG = listOf(1.0, 5.0, 10.0, 20.0, 30.0, 50.0)
    private val AMOUNT_THRESHOLDS_LB = listOf(2.0, 5.0, 10.0, 25.0, 50.0, 100.0)

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
                milestones += amountMilestones(movedKg, profile.weightUnit, profile.goalDirection)
            }
        }

        return milestones
    }

    private fun timeMilestones(daysSinceStart: Long): List<Milestone> {
        var foundNext = false
        return TIME_THRESHOLDS.mapNotNull { (threshold, title) ->
            when {
                daysSinceStart >= threshold -> Milestone(MilestoneCategory.TIME, title, achieved = true)
                !foundNext -> {
                    foundNext = true
                    val remaining = threshold - daysSinceStart
                    Milestone(MilestoneCategory.TIME, title, achieved = false, hint = "${dayWord(remaining)} to go")
                }
                else -> null
            }
        }
    }

    private fun streakMilestones(longestStreak: Int): List<Milestone> {
        var foundNext = false
        return STREAK_THRESHOLDS.mapNotNull { (threshold, title) ->
            when {
                longestStreak >= threshold -> Milestone(MilestoneCategory.STREAK, title, achieved = true)
                !foundNext -> {
                    foundNext = true
                    val remaining = threshold - longestStreak
                    Milestone(MilestoneCategory.STREAK, title, achieved = false, hint = "${dayWord(remaining.toLong())} more in a row")
                }
                else -> null
            }
        }
    }

    private fun progressMilestones(rawRatio: Double): List<Milestone> {
        val ratio = rawRatio.coerceIn(0.0, 1.0)
        var foundNext = false
        return PROGRESS_THRESHOLDS.mapNotNull { (threshold, title) ->
            when {
                ratio >= threshold - 0.001 -> Milestone(MilestoneCategory.PROGRESS, title, achieved = true)
                !foundNext -> {
                    foundNext = true
                    val remainingPercent = ((threshold - ratio) * 100).roundToLong()
                    Milestone(MilestoneCategory.PROGRESS, title, achieved = false, hint = "$remainingPercent% to go")
                }
                else -> null
            }
        }
    }

    private fun amountMilestones(movedKg: Double, unit: WeightUnit, direction: GoalDirection): List<Milestone> {
        val thresholdsInUnit = if (unit == WeightUnit.KG) AMOUNT_THRESHOLDS_KG else AMOUNT_THRESHOLDS_LB
        val unitLabel = unit.name.lowercase()
        val verb = if (direction == GoalDirection.LOSE) "down" else "up"
        var foundNext = false

        return thresholdsInUnit.mapNotNull { thresholdInUnit ->
            val thresholdKg = unit.toKg(thresholdInUnit)
            val title = "${formatOneDecimal(thresholdInUnit)} $unitLabel $verb"
            when {
                movedKg >= thresholdKg - 0.01 -> Milestone(MilestoneCategory.AMOUNT, title, achieved = true)
                !foundNext -> {
                    foundNext = true
                    val remainingInUnit = unit.fromKg(thresholdKg - movedKg)
                    Milestone(MilestoneCategory.AMOUNT, title, achieved = false, hint = "${formatOneDecimal(remainingInUnit)} $unitLabel to go")
                }
                else -> null
            }
        }
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
