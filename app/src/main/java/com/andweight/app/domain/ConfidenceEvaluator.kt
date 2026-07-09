package com.andweight.app.domain

import java.time.LocalDate

enum class ConfidenceLevel(val label: String) {
    LOW("Low accuracy"),
    MEDIUM("Medium accuracy"),
    HIGH("High accuracy"),
}

data class ConfidenceAssessment(val level: ConfidenceLevel, val message: String)

/**
 * How much to trust the current TDEE estimate and target - based on how much real data has gone
 * into it so far. Unlike a one-time score, this can also go back down: if logging lapses for a
 * while, the estimate is running on stale information even if it was well-calibrated a month ago,
 * so recency is checked independently of the total history.
 */
object ConfidenceEvaluator {

    private const val RECENT_WINDOW_DAYS = 7L
    private const val MIN_RECENT_DAYS_LOGGED = 3

    fun assess(state: TdeeState, logs: List<DailyLog>, today: LocalDate): ConfidenceAssessment {
        val completedWeeks = state.weeklyHistory.count { it.hasWeightData && it.hasCalorieData }

        val recentWindowStart = today.minusDays(RECENT_WINDOW_DAYS - 1)
        val recentDaysLogged = logs.count {
            !it.date.isBefore(recentWindowStart) && !it.date.isAfter(today) && (it.weightKg != null || it.calories != null)
        }
        val isStale = recentDaysLogged < MIN_RECENT_DAYS_LOGGED

        return when {
            isStale -> ConfidenceAssessment(
                ConfidenceLevel.LOW,
                "You haven't logged much in the past week, so this estimate is going stale. Check in daily to bring it back up.",
            )
            completedWeeks >= 4 -> ConfidenceAssessment(
                ConfidenceLevel.HIGH,
                "Built from several weeks of your actual results - this is a solid, personalized estimate.",
            )
            completedWeeks >= 2 -> ConfidenceAssessment(
                ConfidenceLevel.MEDIUM,
                "Based on a couple of weeks of your data - getting more accurate, but still settling in.",
            )
            else -> ConfidenceAssessment(
                ConfidenceLevel.LOW,
                "This is still a rough, formula-based starting estimate. It'll sharpen up as you log more days.",
            )
        }
    }
}
