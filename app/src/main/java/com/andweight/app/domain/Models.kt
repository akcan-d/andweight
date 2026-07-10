package com.andweight.app.domain

import java.time.LocalDate

data class UserProfile(
    val weightUnit: WeightUnit,
    val startWeightKg: Double,
    val goalWeightKg: Double,
    /** Null when maintaining. Kept as the qualitative choice rather than a fixed kg/week number,
     * so the actual weekly rate can be re-derived against *current* weight (see [PaceGuidance])
     * every time it's needed - a percentage-of-bodyweight pace naturally tapers as you approach
     * your goal, instead of grinding on at the rate set back on day one. */
    val pace: PaceOption? = null,
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
    /** True if [calories] was raised up to the safety floor - only ever happens for a quick-pick
     * estimate, never for an exact manual entry (see [belowFloor]). */
    val floorApplied: Boolean,
    /** True if [calories] sits below the safety floor for this person's sex. Quick-pick estimates
     * are always clamped so this is never true for them; an exact entry is kept exactly as typed
     * even when it's below the floor - a manual entry is a factual report of what someone ate,
     * not a formula's guess to second-guess - so the UI just shows a caution instead. */
    val belowFloor: Boolean = false,
)
