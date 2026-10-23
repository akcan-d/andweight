package com.measured.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

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

    /** The phone's own notion of which day starts the week (Monday across most of the world,
     * Sunday in the US, and so on) - there's no separate in-app setting for this, since every other
     * calendar/weekly view on the same phone already follows it and a second place to configure the
     * same thing would just be one more thing to get out of sync. */
    fun systemFirstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
}
