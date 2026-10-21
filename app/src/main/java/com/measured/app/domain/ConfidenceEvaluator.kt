package com.measured.app.domain

import java.time.LocalDate

enum class ConfidenceLevel(val label: String) {
    LOW("Low accuracy"),
    MEDIUM("Medium accuracy"),
    HIGH("High accuracy"),
}

data class ConfidenceAssessment(
    val level: ConfidenceLevel,
    val message: String,
    /** True only for the specific message that suggests filling in profile details - lets the UI
     * offer a direct link to the profile editor instead of just saying "in Settings". */
    val promptProfileDetails: Boolean = false,
)

/**
 * How much to trust the current TDEE estimate and target - based on how much real data has gone
 * into it so far. Unlike a one-time score, this can also go back down: if logging lapses for a
 * while after a real track record has been built up, the estimate is running on stale information
 * even if it was well-calibrated a month ago, so recency is checked independently of the total
 * history. That staleness check is deliberately gated behind already having some completed weeks -
 * someone who just started today hasn't "gone stale", they just haven't logged much yet, and
 * shouldn't be greeted with a red warning on day one.
 */
object ConfidenceEvaluator {

    private const val RECENT_WINDOW_DAYS = 7L
    private const val MIN_RECENT_DAYS_LOGGED = 3
    private const val MIN_WEEKS_BEFORE_STALENESS_APPLIES = 2

    /** How far the estimate can drift from its starting formula-based guess before treating that
     * drift itself as a reason to doubt it, rather than trust. The adaptive algorithm is supposed
     * to move the estimate toward whatever number reconciles logged calories with real weight
     * change - that's correct behavior when someone's own sense of "usual" eating turns out to be
     * off. But a 60%+ swing either way usually means recent quick-pick choices don't track real
     * results closely enough for the number to mean much yet, not that a real metabolism actually
     * moved that far. */
    private const val DRIFT_RATIO_THRESHOLD = 1.6

    /** Out of 3 - each is independently used (with a population-average fallback for whichever is
     * missing) by [TdeeEngine.initialTdeeGuess], so a profile with 2 of 3 filled in is genuinely
     * more accurate than one with none, not just "not fully done yet". */
    private const val DETAILS_NEEDED_FOR_MEDIUM = 2

    fun assess(profile: UserProfile, state: TdeeState, logs: List<DailyLog>, today: LocalDate): ConfidenceAssessment {
        val completedWeeks = state.weeklyHistory.count { it.hasWeightData && it.hasCalorieData }

        // Body fat % alone drives the formula (Katch-McArdle bypasses sex/height/age entirely - see
        // TdeeEngine.initialTdeeGuess), so it counts as full accuracy on its own rather than just
        // one more field toward a majority.
        val hasBodyFat = profile.bodyFatPercent != null
        val profileDetailCount = listOf(
            profile.age != null,
            profile.heightCm != null,
            profile.sex != Sex.UNSPECIFIED,
        ).count { it }
        val hasFullProfileDetails = hasBodyFat || profileDetailCount == 3
        val hasEnoughProfileDetails = hasBodyFat || profileDetailCount >= DETAILS_NEEDED_FOR_MEDIUM

        val recentWindowStart = today.minusDays(RECENT_WINDOW_DAYS - 1)
        val recentDaysLogged = logs.count {
            !it.date.isBefore(recentWindowStart) && !it.date.isAfter(today) && (it.weightKg != null || it.calories != null)
        }
        val isStale = completedWeeks >= MIN_WEEKS_BEFORE_STALENESS_APPLIES && recentDaysLogged < MIN_RECENT_DAYS_LOGGED

        val hasDrifted = completedWeeks >= MIN_WEEKS_BEFORE_STALENESS_APPLIES &&
            state.initialTdeeGuess > 0.0 &&
            (state.currentTdee / state.initialTdeeGuess).let { it > DRIFT_RATIO_THRESHOLD || it < 1.0 / DRIFT_RATIO_THRESHOLD }

        // "N more days" assumes logging daily from here on - the honest, achievable version of that
        // promise, since a completed week only needs one day of each kind of entry somewhere in it.
        fun daysToNextLevel(targetWeeks: Int): String {
            val days = (targetWeeks - completedWeeks).coerceAtLeast(1) * 7
            return "Enter data for about $days more day${if (days == 1) "" else "s"} to reach the next level."
        }

        return when {
            isStale -> {
                val daysNeeded = (MIN_RECENT_DAYS_LOGGED - recentDaysLogged).coerceAtLeast(1)
                ConfidenceAssessment(
                    ConfidenceLevel.LOW,
                    "You haven't logged much in the past week, so this estimate is going stale. Enter " +
                        "data for $daysNeeded more day${if (daysNeeded == 1) "" else "s"} this week to bring it back up.",
                )
            }
            hasDrifted -> ConfidenceAssessment(
                ConfidenceLevel.LOW,
                "This estimate has moved a lot from where it started - usually that means recent " +
                    "quick-pick choices (\"a bit less\", \"much more\", etc.) don't quite match what " +
                    "actually happened on the scale. Logging exact calories for a few days can help it " +
                    "settle on the right number faster.",
            )
            completedWeeks >= 4 -> ConfidenceAssessment(
                ConfidenceLevel.HIGH,
                "Built from several weeks of your actual results - this is a solid, personalized estimate.",
            )
            completedWeeks >= 2 -> ConfidenceAssessment(
                ConfidenceLevel.MEDIUM,
                "Based on a couple of weeks of your data - getting more accurate, but still settling in. " +
                    daysToNextLevel(4),
            )
            hasBodyFat -> ConfidenceAssessment(
                ConfidenceLevel.MEDIUM,
                "Starting estimate uses your body fat percentage for the most accurate formula available. " +
                    daysToNextLevel(4),
            )
            hasFullProfileDetails -> ConfidenceAssessment(
                ConfidenceLevel.MEDIUM,
                "Starting estimate uses your sex, height, and age for a fully personalized formula. " +
                    daysToNextLevel(4),
            )
            hasEnoughProfileDetails -> ConfidenceAssessment(
                ConfidenceLevel.MEDIUM,
                "Starting estimate uses most of what you've told us about yourself - filling in the last " +
                    "detail (sex, height, or age), or your body fat percentage, would sharpen it further. " +
                    daysToNextLevel(4),
                promptProfileDetails = true,
            )
            else -> ConfidenceAssessment(
                ConfidenceLevel.LOW,
                "This is still a rough, formula-based starting estimate. Add your sex, height, and age " +
                    "(or your body fat percentage, if you know it) to sharpen it up right away. Otherwise, " +
                    daysToNextLevel(2).replaceFirstChar { it.lowercase() },
                promptProfileDetails = true,
            )
        }
    }
}
