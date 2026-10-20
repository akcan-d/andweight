package com.measured.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Turns the engine's raw numbers into short, plain-language sentences. The exact figures are
 * still available (see the "show details" views) - this is just what people see by default,
 * since the point of the app is "am I on track", not a precise calorie count.
 */
object Phrasing {

    private val fullDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    /**
     * How today's recommended intake compares to the person's current TDEE - which is itself an
     * estimate of what they've actually been eating and burning over the past several weeks, not
     * a fixed baseline from before they started. So "usual" here means "what you've genuinely
     * been eating lately", and it updates as that changes.
     */
    fun calorieTargetSentence(recommendedCalories: Double, tdee: Double): String {
        val delta = recommendedCalories - tdee
        return when {
            delta <= -600 -> "Eat noticeably less today."
            delta <= -150 -> "Eat a bit less today."
            delta < 150 -> "Eat about the usual today."
            delta < 600 -> "Eat a bit more today."
            else -> "Eat noticeably more today."
        }
    }

    /** A whole number plus a matching, already-pluralized unit word ("months"/"weeks"/"days") -
     * never a mix of units, and never falsely precise about a many-months-out estimate. Longer
     * horizons are shown in coarser units specifically so the number stays honest about how rough
     * the underlying TDEE-based projection still is at that distance. */
    data class GoalCountdown(val amount: Long, val unitLabel: String, val isApproximate: Boolean)

    private const val MONTH_THRESHOLD_DAYS = 183L
    private const val WEEK_THRESHOLD_DAYS = 60L
    private const val DAYS_PER_MONTH = 30.44
    private const val DAYS_PER_WEEK = 7.0

    fun goalCountdown(daysToGoal: Long): GoalCountdown = when {
        daysToGoal > MONTH_THRESHOLD_DAYS -> {
            val months = Math.round(daysToGoal / DAYS_PER_MONTH)
            GoalCountdown(months, if (months == 1L) "month" else "months", isApproximate = true)
        }
        daysToGoal >= WEEK_THRESHOLD_DAYS -> {
            val weeks = Math.round(daysToGoal / DAYS_PER_WEEK)
            GoalCountdown(weeks, if (weeks == 1L) "week" else "weeks", isApproximate = true)
        }
        else -> GoalCountdown(daysToGoal, if (daysToGoal == 1L) "day" else "days", isApproximate = false)
    }

    fun formatTargetDate(date: LocalDate): String = date.format(fullDateFormatter)

    private fun formatAmount(value: Double, unit: WeightUnit): String {
        val rounded = (value * 10).roundToInt() / 10.0
        val numberText = if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString() else rounded.toString()
        val unitLabel = if (unit == WeightUnit.KG) "kg" else "lb"
        return "$numberText $unitLabel"
    }

    fun formatWeight(kg: Double, unit: WeightUnit): String = formatAmount(unit.fromKg(kg), unit)
}
