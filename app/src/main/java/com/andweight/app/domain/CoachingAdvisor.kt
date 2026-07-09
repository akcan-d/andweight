package com.andweight.app.domain

import kotlin.math.abs

enum class AdviceSentiment {
    POSITIVE,
    NEUTRAL,
    CAUTION,
}

data class Advice(val message: String, val sentiment: AdviceSentiment)

/**
 * Compares how the person has actually been trending against their stated goal and produces a
 * single, self-contained piece of guidance - deliberately the *only* "what should I eat today"
 * message shown, so it can never contradict a separately-computed line. When trend and static
 * target agree, this folds the target phrase in; when they don't (e.g. already ahead of pace),
 * the trend-aware guidance takes over instead of also stating the static "eat less" delta, which
 * would otherwise read as conflicting advice.
 *
 * Deliberately conservative early on - with only a week or two of data the TDEE estimate is
 * still finding its footing, so advice before then would just be noise.
 */
object CoachingAdvisor {

    private const val MIN_WEEKS_FOR_ADVICE = 2

    fun advise(profile: UserProfile, state: TdeeState): Advice {
        val completedWeeks = state.weeklyHistory.filter { it.hasWeightData && it.hasCalorieData }
        val targetSentence = Phrasing.calorieTargetSentence(state.recommendedCalories, state.currentTdee)

        if (completedWeeks.size < MIN_WEEKS_FOR_ADVICE) {
            // Being upfront that the number shown is a rough starting guess, rather than telling
            // people not to worry about a target while a specific (lower, if losing) one is still
            // sitting right there in the details view - that combination read as contradictory.
            return Advice(
                "Keep logging every day you can - the target below is just a rough starting estimate " +
                    "based on formulas, not your real results yet. It'll get more accurate the more you log.",
                AdviceSentiment.NEUTRAL,
            )
        }

        val recentDeltaKg = completedWeeks.last().weightDeltaKg ?: 0.0

        if (profile.goalDirection == GoalDirection.MAINTAIN) {
            return when {
                abs(recentDeltaKg) < 0.3 ->
                    Advice("You're holding steady - exactly what you're aiming for. Keep eating about what feels normal for you.", AdviceSentiment.POSITIVE)
                recentDeltaKg > 0 ->
                    Advice("You've been drifting upward lately. Try scaling back your portions a little.", AdviceSentiment.CAUTION)
                else ->
                    Advice("You've been drifting downward lately. Try adding a little more to your meals.", AdviceSentiment.CAUTION)
            }
        }

        val targetWeeklyDeltaKg = when (profile.goalDirection) {
            GoalDirection.LOSE -> -profile.goalRateKgPerWeek
            GoalDirection.GAIN -> profile.goalRateKgPerWeek
            GoalDirection.MAINTAIN -> 0.0
        }
        if (targetWeeklyDeltaKg == 0.0) {
            return Advice("You're on track. $targetSentence", AdviceSentiment.POSITIVE)
        }

        val progressRatio = recentDeltaKg / targetWeeklyDeltaKg

        return when {
            progressRatio < 0.3 -> {
                val verb = if (profile.goalDirection == GoalDirection.LOSE) "eating a bit less" else "eating a bit more"
                Advice("Progress has been slow lately. Try $verb than you have been the past couple of weeks.", AdviceSentiment.CAUTION)
            }
            progressRatio > 1.8 -> {
                val verb = if (profile.goalDirection == GoalDirection.LOSE) "eating a little more" else "eating a little less"
                Advice(
                    "You're moving faster than planned, which isn't necessarily healthier - consider $verb " +
                        "to keep it sustainable and protect your energy and muscle mass.",
                    AdviceSentiment.CAUTION,
                )
            }
            else -> Advice("You're doing great - right on pace for your goal. $targetSentence", AdviceSentiment.POSITIVE)
        }
    }
}
