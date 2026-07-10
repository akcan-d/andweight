package com.andweight.app.domain

enum class PaceOption(val label: String) {
    RELAXED("Slow and easy"),
    MODERATE("Steady and balanced"),
    FASTEST("Faster, within safe limits"),
}

/**
 * Turns a qualitative pace choice into an actual weekly rate, instead of letting someone type in
 * an arbitrary number. There's no "unlimited" option on purpose - even the fastest tier is capped
 * at a rate generally considered sustainable for the person's size, since going faster than that
 * tends to cost muscle mass and rarely stays off.
 *
 * Cutting guidance follows an "up to ~1.5% of body weight per week" ceiling for the fastest tier,
 * capped in absolute terms at 2 kg/week regardless of size. Gaining guidance is deliberately
 * slower - a "lean bulk" is usually recommended at roughly a third of that rate, to limit fat
 * gained alongside muscle.
 */
object PaceGuidance {

    /** [weightKg] is whatever weight the rate should be evaluated against right now - current
     * weight in day-to-day use, or the just-entered starting weight during onboarding before any
     * of it is "current" yet. The percentage-of-bodyweight basis means this should always be
     * re-evaluated against the latest known weight rather than cached from a single point in time. */
    fun maxSafeWeeklyRateKg(weightKg: Double, direction: GoalDirection): Double = when (direction) {
        GoalDirection.LOSE -> minOf(2.0, weightKg * 0.015)
        GoalDirection.GAIN -> minOf(1.0, weightKg * 0.005)
        GoalDirection.MAINTAIN -> 0.0
    }

    fun rateForOption(option: PaceOption, weightKg: Double, direction: GoalDirection): Double {
        val max = maxSafeWeeklyRateKg(weightKg, direction)
        val fraction = when (option) {
            PaceOption.RELAXED -> 0.4
            PaceOption.MODERATE -> 0.7
            PaceOption.FASTEST -> 1.0
        }
        return max * fraction
    }
}
