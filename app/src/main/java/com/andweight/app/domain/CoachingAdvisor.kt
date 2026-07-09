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
 * short, honest nudge. Deliberately conservative early on - with only a week or two of data the
 * TDEE estimate is still finding its footing, so advice before then would just be noise.
 */
object CoachingAdvisor {

    private const val MIN_WEEKS_FOR_ADVICE = 2

    fun advise(profile: UserProfile, state: TdeeState): Advice {
        val completedWeeks = state.weeklyHistory.filter { it.hasWeightData && it.hasCalorieData }
        if (completedWeeks.size < MIN_WEEKS_FOR_ADVICE) {
            return Advice(
                "Keep logging every day you can. Once you've got a couple of weeks of data, " +
                    "we'll be able to tell you how you're really doing.",
                AdviceSentiment.NEUTRAL,
            )
        }

        val recentDeltaKg = completedWeeks.last().weightDeltaKg ?: 0.0

        if (profile.goalDirection == GoalDirection.MAINTAIN) {
            return if (abs(recentDeltaKg) < 0.3) {
                Advice("You're holding steady - exactly what you're aiming for.", AdviceSentiment.POSITIVE)
            } else if (recentDeltaKg > 0) {
                Advice("You've been drifting upward. Try scaling back your portions a little.", AdviceSentiment.CAUTION)
            } else {
                Advice("You've been drifting downward. Try eating a little more to hold steady.", AdviceSentiment.CAUTION)
            }
        }

        val targetWeeklyDeltaKg = when (profile.goalDirection) {
            GoalDirection.LOSE -> -profile.goalRateKgPerWeek
            GoalDirection.GAIN -> profile.goalRateKgPerWeek
            GoalDirection.MAINTAIN -> 0.0
        }
        if (targetWeeklyDeltaKg == 0.0) {
            return Advice("You're on track.", AdviceSentiment.POSITIVE)
        }

        val progressRatio = recentDeltaKg / targetWeeklyDeltaKg

        return when {
            progressRatio < 0.3 -> {
                val verb = if (profile.goalDirection == GoalDirection.LOSE) "eating a bit less" else "eating a bit more"
                Advice("Progress has been slow lately. Try $verb than you have been.", AdviceSentiment.CAUTION)
            }
            progressRatio > 1.8 -> {
                val verb = if (profile.goalDirection == GoalDirection.LOSE) "eating a little more" else "eating a little less"
                Advice("You're moving faster than planned. Consider $verb to keep it sustainable.", AdviceSentiment.CAUTION)
            }
            else -> Advice("You're doing great - right on pace for your goal. Keep it up!", AdviceSentiment.POSITIVE)
        }
    }
}
