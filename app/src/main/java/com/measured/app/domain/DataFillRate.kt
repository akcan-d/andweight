package com.measured.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class DataFillRateResult(
    val loggedDays: Int,
    val totalDays: Int,
    val percent: Int,
)

/**
 * What fraction of recent days actually got a check-in, over a fixed trailing window - but capped
 * to how long the person has actually been using the app, so a brand-new user doesn't see an
 * artificially low percentage just because most of the last 30 days predate their first entry.
 */
object DataFillRate {

    private const val WINDOW_DAYS = 30L

    fun compute(logs: List<DailyLog>, today: LocalDate): DataFillRateResult? {
        val firstLoggedDate = logs.minOfOrNull { it.date } ?: return null

        val windowStart = maxOf(today.minusDays(WINDOW_DAYS - 1), firstLoggedDate)
        val totalDays = (ChronoUnit.DAYS.between(windowStart, today) + 1).toInt()

        val loggedDays = logs.count {
            !it.date.isBefore(windowStart) && !it.date.isAfter(today) && (it.weightKg != null || it.calories != null)
        }

        val percent = ((loggedDays.toDouble() / totalDays) * 100.0).let { Math.round(it).toInt() }
        return DataFillRateResult(loggedDays, totalDays, percent)
    }
}
