package com.measured.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DataFillRateTest {

    private val today = LocalDate.of(2026, 7, 30)

    @Test
    fun `no logs at all returns null`() {
        assertNull(DataFillRate.compute(emptyList(), today))
    }

    @Test
    fun `new user with only a few days of history is not penalized for the full 30-day window`() {
        // Started 4 days ago (5-day window: day0..day4), logged 3 of those 5 days.
        val start = today.minusDays(4)
        val logs = listOf(
            DailyLog(start, weightKg = 90.0, calories = null),
            DailyLog(start.plusDays(1), weightKg = 90.0, calories = null),
            DailyLog(start.plusDays(3), weightKg = null, calories = 2000.0),
        )
        val result = requireNotNull(DataFillRate.compute(logs, today))
        assertEquals(5, result.totalDays)
        assertEquals(3, result.loggedDays)
        assertEquals(60, result.percent)
    }

    @Test
    fun `long-time user is capped to the 30-day trailing window`() {
        val start = today.minusDays(200) // well over 30 days of history
        val logs = (0..29).map { offset -> DailyLog(today.minusDays(offset.toLong()), weightKg = 80.0, calories = null) } +
            listOf(DailyLog(start, weightKg = 80.0, calories = null))
        val result = requireNotNull(DataFillRate.compute(logs, today))
        assertEquals(30, result.totalDays)
        assertEquals(30, result.loggedDays)
        assertEquals(100, result.percent)
    }

    @Test
    fun `a day with only calories logged still counts as a logged day`() {
        val logs = listOf(DailyLog(today, weightKg = null, calories = 1800.0))
        val result = requireNotNull(DataFillRate.compute(logs, today))
        assertEquals(1, result.totalDays)
        assertEquals(1, result.loggedDays)
        assertEquals(100, result.percent)
    }

    @Test
    fun `days after today are never counted even if somehow logged`() {
        val start = today.minusDays(2)
        val logs = listOf(
            DailyLog(start, weightKg = 80.0, calories = null),
            DailyLog(today.plusDays(1), weightKg = 80.0, calories = null),
        )
        val result = requireNotNull(DataFillRate.compute(logs, today))
        assertEquals(3, result.totalDays) // start..today inclusive
        assertEquals(1, result.loggedDays)
    }
}
