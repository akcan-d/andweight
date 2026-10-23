package com.measured.app.domain

data class PlateauAssessment(val isPlateaued: Boolean, val weeksFlat: Int)

/**
 * Flags a sustained stretch of weeks where almost no net progress happened, despite consistent
 * logging - distinct from [CoachingAdvisor]'s "progress has been slow lately" message, which
 * reacts to a single recent week and is about what to eat today. A plateau is a longer-run
 * pattern worth naming and reassuring about on its own (water retention, muscle gain offsetting
 * fat loss, or the body adjusting to a new calorie level are all normal causes), not something to
 * react to by cutting further after just one quiet week.
 */
object PlateauDetector {

    private const val MIN_CONSECUTIVE_WEEKS = 3

    /** A week counts as "flat" when its net change was within a quarter of the planned weekly
     * rate in either direction - close enough to zero that it reads as a stall, not as genuine
     * (if slower than planned) progress, and not as a reversal in the wrong direction either,
     * which is a different problem with different advice. */
    private const val FLAT_RATIO_RANGE = 0.25

    fun assess(profile: UserProfile, state: TdeeState): PlateauAssessment {
        if (profile.goalDirection == GoalDirection.MAINTAIN) return PlateauAssessment(false, 0)
        val pace = profile.pace ?: return PlateauAssessment(false, 0)

        val targetWeeklyRateKg = PaceGuidance.rateForOption(pace, state.currentWeightKg, profile.goalDirection)
        if (targetWeeklyRateKg <= 0.0) return PlateauAssessment(false, 0)
        val signedTarget = if (profile.goalDirection == GoalDirection.LOSE) -targetWeeklyRateKg else targetWeeklyRateKg

        val completedWeeks = state.weeklyHistory.filter { it.hasWeightData && it.hasCalorieData }

        // Counts backward from the most recent completed week, stopping at the first week that
        // isn't flat - so a plateau has to be the *current* state, not just something that
        // happened at some point in the history.
        var weeksFlat = 0
        for (week in completedWeeks.asReversed()) {
            val delta = week.weightDeltaKg ?: break
            val ratio = delta / signedTarget
            if (ratio in -FLAT_RATIO_RANGE..FLAT_RATIO_RANGE) weeksFlat++ else break
        }

        return PlateauAssessment(isPlateaued = weeksFlat >= MIN_CONSECUTIVE_WEEKS, weeksFlat = weeksFlat)
    }
}
