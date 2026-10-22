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
    /** Stable, unit- and locale-independent identity - this is what "already seen/notified"
     * tracking keys off of (see [com.measured.app.ui.journey.MilestoneUnlockViewModel]), kept
     * entirely separate from [title]. [title] for AMOUNT milestones embeds a number converted to
     * the person's display unit, so the same underlying threshold's title text changes if they
     * switch kg/lb - using title text as identity would make a unit switch alone look like a
     * fresh set of milestones and re-fire "unlocked" notifications for ones already seen. */
    val id: String,
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
 * Each category returns every threshold already reached, in order, plus the single next one
 * coming up - a full achievement history rather than just the latest rung, so earlier milestones
 * stay visible and the overall "unlocked" count reflects everything actually passed rather than
 * only the most recent one per category. Everything here is computed fresh from the current
 * profile/state/logs on every call rather than cached, so it stays correct if the goal weight
 * changes (a milestone that no longer makes sense for the new goal simply stops appearing).
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
    //
    // Always expressed in kg, regardless of the person's display unit - weight is stored in kg,
    // so achievement itself is decided in kg and only the title's number is converted and rounded
    // for display. An earlier version kept a second, separately-curated lb progression so lb
    // titles landed on round numbers, but that meant switching the display unit alone (no actual
    // weight change) could flip which thresholds counted as reached, since each unit evaluated
    // against a different list. One canonical list fixes that at the cost of lb titles sometimes
    // landing on a slightly less round number.
    private val AMOUNT_PROGRESSION_KG = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
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
        for (i in 0..latestAchieved) {
            val (threshold, title) = TIME_THRESHOLDS[i]
            result += Milestone(MilestoneCategory.TIME, id = "time_$threshold", title, achieved = true)
        }
        TIME_THRESHOLDS.getOrNull(latestAchieved + 1)?.let { (threshold, title) ->
            val remaining = threshold - daysSinceStart
            result += Milestone(
                MilestoneCategory.TIME,
                id = "time_$threshold",
                title,
                achieved = false,
                hint = "${dayWord(remaining)} to go",
            )
        }
        return result
    }

    private fun streakMilestones(longestStreak: Int): List<Milestone> {
        val latestAchieved = STREAK_THRESHOLDS.indexOfLast { (threshold, _) -> longestStreak >= threshold }
        val result = mutableListOf<Milestone>()
        for (i in 0..latestAchieved) {
            val (threshold, title) = STREAK_THRESHOLDS[i]
            result += Milestone(MilestoneCategory.STREAK, id = "streak_$threshold", title, achieved = true)
        }
        STREAK_THRESHOLDS.getOrNull(latestAchieved + 1)?.let { (threshold, title) ->
            val remaining = threshold - longestStreak
            result += Milestone(
                MilestoneCategory.STREAK,
                id = "streak_$threshold",
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
        for (i in 0..latestAchieved) {
            val (threshold, title) = PROGRESS_THRESHOLDS[i]
            result += Milestone(MilestoneCategory.PROGRESS, id = "progress_$threshold", title, achieved = true)
        }
        PROGRESS_THRESHOLDS.getOrNull(latestAchieved + 1)?.let { (threshold, title) ->
            val remainingPercent = ((threshold - ratio) * 100).roundToLong()
            result += Milestone(
                MilestoneCategory.PROGRESS,
                id = "progress_$threshold",
                title,
                achieved = false,
                hint = "$remainingPercent% to go",
            )
        }
        return result
    }

    private fun amountMilestones(
        movedKg: Double,
        totalNeededKg: Double,
        unit: WeightUnit,
        direction: GoalDirection,
    ): List<Milestone> {
        val thresholdsInKg = AMOUNT_PROGRESSION_KG.filter { it / totalNeededKg in AMOUNT_FRACTION_RANGE }
        if (thresholdsInKg.isEmpty()) return emptyList()

        val unitLabel = unit.name.lowercase()
        val verb = if (direction == GoalDirection.LOSE) "down" else "up"
        // Rounded to a whole number in lb (values there tend to be larger, where a decimal reads
        // as noise) but kept to one decimal in kg (where 0.5-1kg differences are meaningful).
        fun formatForUnit(valueInUnit: Double) =
            if (unit == WeightUnit.KG) formatOneDecimal(valueInUnit) else Math.round(valueInUnit).toString()
        fun titleFor(thresholdKg: Double) = "${formatForUnit(unit.fromKg(thresholdKg))} $unitLabel $verb"
        // The threshold's kg value and goal direction, never the display unit or converted
        // number - so switching kg/lb alone can't change a milestone's identity and make an
        // already-seen one look new (see [Milestone.id]).
        fun idFor(thresholdKg: Double) = "amount_${thresholdKg}_${direction.name}"

        val latestAchieved = thresholdsInKg.indexOfLast { movedKg >= it - 0.01 }
        val result = mutableListOf<Milestone>()
        for (i in 0..latestAchieved) {
            result += Milestone(MilestoneCategory.AMOUNT, id = idFor(thresholdsInKg[i]), titleFor(thresholdsInKg[i]), achieved = true)
        }
        thresholdsInKg.getOrNull(latestAchieved + 1)?.let { thresholdKg ->
            val remainingInUnit = unit.fromKg(thresholdKg - movedKg)
            result += Milestone(
                MilestoneCategory.AMOUNT,
                id = idFor(thresholdKg),
                titleFor(thresholdKg),
                achieved = false,
                hint = "${formatForUnit(remainingInUnit)} $unitLabel to go",
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
