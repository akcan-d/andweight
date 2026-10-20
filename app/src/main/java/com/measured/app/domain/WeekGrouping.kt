package com.measured.app.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Calendar-week boundaries for display purposes (e.g. paging through Journey's daily history week
 * by week) - distinct from [TdeeEngine]'s internal weekly windows, which are anchored to whenever
 * someone's first logged day happened to fall rather than a real-world first day of the week, since
 * that anchoring is what keeps the TDEE averaging stable and isn't something this changes.
 */
object WeekGrouping {

    /** The most recent date on or before [date] that falls on [firstDayOfWeek]. */
    fun weekStart(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate {
        val daysSinceWeekStart = (date.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        return date.minusDays(daysSinceWeekStart.toLong())
    }
}
