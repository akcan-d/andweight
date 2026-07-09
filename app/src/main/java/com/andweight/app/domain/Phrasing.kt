package com.andweight.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns the engine's raw numbers into short, plain-language sentences. The exact figures are
 * still available (see the "show details" views) - this is just what people see by default,
 * since the point of the app is "am I on track", not a precise calorie count.
 */
object Phrasing {

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

    /**
     * How today's recommended intake compares to the person's current TDEE - which is itself an
     * estimate of what they've actually been eating and burning over the past several weeks, not
     * a fixed baseline from before they started. So "usual" here means "what you've genuinely
     * been eating lately", and it updates as that changes.
     */
    fun calorieTargetSentence(recommendedCalories: Double, tdee: Double): String {
        val delta = recommendedCalories - tdee
        return when {
            delta <= -600 -> "Eat noticeably less than you've been eating these past few weeks."
            delta <= -150 -> "Eat a little less than you've been eating these past few weeks."
            delta < 150 -> "Eat about what you've been eating these past few weeks."
            delta < 600 -> "Eat a little more than you've been eating these past few weeks."
            else -> "Eat noticeably more than you've been eating these past few weeks."
        }
    }

    fun weightTrendSentence(deltaKg: Double, unit: WeightUnit): String {
        if (abs(deltaKg) < 0.05) return "Your weight hasn't changed much since you started."
        val amount = formatAmount(unit.fromKg(abs(deltaKg)), unit)
        return if (deltaKg < 0) "You're down $amount since you started." else "You're up $amount since you started."
    }

    fun forecastSentence(weeksToGoal: Double?, goalDate: LocalDate?): String = when {
        weeksToGoal == null -> "You're aiming to hold steady at your current weight."
        weeksToGoal <= 0.0 -> "You've reached your goal - nice work!"
        else -> "About ${weeksPhrase(weeksToGoal)} to go, around ${goalDate?.format(dateFormatter)}."
    }

    private fun weeksPhrase(weeks: Double): String {
        val rounded = (weeks * 2).roundToInt() / 2.0
        return when {
            rounded <= 1.0 -> "a week"
            rounded == rounded.toInt().toDouble() -> "${rounded.toInt()} weeks"
            else -> "$rounded weeks"
        }
    }

    private fun formatAmount(value: Double, unit: WeightUnit): String {
        val rounded = (value * 10).roundToInt() / 10.0
        val numberText = if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString() else rounded.toString()
        val unitLabel = if (unit == WeightUnit.KG) "kg" else "lb"
        return "$numberText $unitLabel"
    }

    fun formatWeight(kg: Double, unit: WeightUnit): String = formatAmount(unit.fromKg(kg), unit)
}
