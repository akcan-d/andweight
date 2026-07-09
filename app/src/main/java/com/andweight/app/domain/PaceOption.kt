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

    fun maxSafeWeeklyRateKg(startWeightKg: Double, direction: GoalDirection): Double = when (direction) {
        GoalDirection.LOSE -> minOf(2.0, startWeightKg * 0.015)
        GoalDirection.GAIN -> minOf(1.0, startWeightKg * 0.005)
        GoalDirection.MAINTAIN -> 0.0
    }

    fun rateForOption(option: PaceOption, startWeightKg: Double, direction: GoalDirection): Double {
        val max = maxSafeWeeklyRateKg(startWeightKg, direction)
        val fraction = when (option) {
            PaceOption.RELAXED -> 0.4
            PaceOption.MODERATE -> 0.7
            PaceOption.FASTEST -> 1.0
        }
        return max * fraction
    }

    /** Reverse lookup for pre-selecting a pace chip against an already-stored rate (e.g. when
     * re-opening the profile editor) - the rate itself isn't tagged with which named option
     * produced it, so this just finds the option whose rate is currently closest. */
    fun closestOption(rateKgPerWeek: Double, startWeightKg: Double, direction: GoalDirection): PaceOption =
        PaceOption.entries.minByOrNull { kotlin.math.abs(rateForOption(it, startWeightKg, direction) - rateKgPerWeek) }
            ?: PaceOption.MODERATE
}
