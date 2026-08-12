package com.andweight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekGroupingTest {

    @Test
    fun `week start for a monday-start week`() {
        // Wednesday, Jan 14, 2026 - the Monday-start week began on Jan 12.
        val result = WeekGrouping.weekStart(LocalDate.of(2026, 1, 14), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 1, 12), result)
    }

    @Test
    fun `week start for a sunday-start week`() {
        // Wednesday, Jan 14, 2026 - the Sunday-start week began on Jan 11.
        val result = WeekGrouping.weekStart(LocalDate.of(2026, 1, 14), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 1, 11), result)
    }

    @Test
    fun `a date that already is the first day of the week returns itself`() {
        val monday = LocalDate.of(2026, 1, 12)
        assertEquals(monday, WeekGrouping.weekStart(monday, DayOfWeek.MONDAY))

        val sunday = LocalDate.of(2026, 1, 11)
        assertEquals(sunday, WeekGrouping.weekStart(sunday, DayOfWeek.SUNDAY))
    }
}
