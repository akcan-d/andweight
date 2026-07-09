package com.andweight.app.domain

import java.time.LocalDate

data class UserProfile(
    val startDate: LocalDate,
    val weightUnit: WeightUnit,
    val startWeightKg: Double,
    val goalWeightKg: Double,
    /** Magnitude only (always positive); direction comes from [goalDirection]. */
    val goalRateKgPerWeek: Double,
    val sex: Sex = Sex.UNSPECIFIED,
    val heightCm: Double? = null,
    val age: Int? = null,
    val activityLevel: ActivityLevel = ActivityLevel.LIGHT,
) {
    val goalDirection: GoalDirection = when {
        goalWeightKg > startWeightKg + 0.01 -> GoalDirection.GAIN
        goalWeightKg < startWeightKg - 0.01 -> GoalDirection.LOSE
        else -> GoalDirection.MAINTAIN
    }
}

/** One day's check-in. Both fields are optional — the engine forward-fills gaps. */
data class DailyLog(
    val date: LocalDate,
    val weightKg: Double?,
    val calories: Double?,
    /** Which quick-pick answer produced [calories], or null if [wasExactEntry] is true. */
    val intakeLevel: IntakeLevel? = null,
    val wasExactEntry: Boolean = false,
)

data class WeekSummary(
    val weekIndex: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val hasWeightData: Boolean,
    val hasCalorieData: Boolean,
    val avgWeightKg: Double?,
    val avgCalories: Double?,
    val weightDeltaKg: Double?,
    val tdee: Double?,
)

data class TdeeState(
    val initialTdeeGuess: Double,
    val currentWeightKg: Double,
    /** The earliest weight actually known for this person - the first real logged entry if one
     * exists (which may predate the profile's start date via backfilling), otherwise the weight
     * entered at onboarding. Use this, not the onboarding snapshot, as the "since you started"
     * reference point. */
    val earliestKnownWeightKg: Double,
    val currentTdee: Double,
    val targetDailyDeficit: Double,
    val recommendedCalories: Double,
    val recommendedCaloriesFloorApplied: Boolean,
    /** Null when the goal is to maintain weight, or the goal rate is zero. */
    val weeksToGoal: Double?,
    val goalDate: LocalDate?,
    val weeklyHistory: List<WeekSummary>,
)

data class CalorieResolution(
    val calories: Double,
    val floorApplied: Boolean,
)
