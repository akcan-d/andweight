package com.andweight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PhrasingTest {

    @Test
    fun `calorie target sentence reflects the size of the deficit`() {
        assertEquals("Eat about the usual today.", Phrasing.calorieTargetSentence(2500.0, 2500.0))
        assertEquals("Eat a bit less today.", Phrasing.calorieTargetSentence(2200.0, 2500.0))
        assertEquals("Eat noticeably less today.", Phrasing.calorieTargetSentence(1800.0, 2500.0))
        assertEquals("Eat a bit more today.", Phrasing.calorieTargetSentence(2800.0, 2500.0))
        assertEquals("Eat noticeably more today.", Phrasing.calorieTargetSentence(3200.0, 2500.0))
    }

    @Test
    fun `weight trend sentence picks a direction`() {
        assertTrue(Phrasing.weightTrendSentence(-2.0, WeightUnit.KG).contains("down"))
        assertTrue(Phrasing.weightTrendSentence(2.0, WeightUnit.KG).contains("up"))
        assertTrue(Phrasing.weightTrendSentence(0.0, WeightUnit.KG).contains("hasn't changed"))
    }

    @Test
    fun `goal countdown shows exact days under 60 days`() {
        val countdown = Phrasing.goalCountdown(42)
        assertEquals(42L, countdown.amount)
        assertEquals("days", countdown.unitLabel)
        assertTrue(!countdown.isApproximate)
    }

    @Test
    fun `goal countdown singularizes exactly one day`() {
        val countdown = Phrasing.goalCountdown(1)
        assertEquals(1L, countdown.amount)
        assertEquals("day", countdown.unitLabel)
    }

    @Test
    fun `goal countdown switches to weeks between 60 days and 6 months`() {
        val countdown = Phrasing.goalCountdown(90)
        assertEquals("weeks", countdown.unitLabel)
        assertEquals(13L, countdown.amount) // round(90 / 7.0)
        assertTrue(countdown.isApproximate)
    }

    @Test
    fun `goal countdown switches to months beyond 6 months`() {
        val countdown = Phrasing.goalCountdown(300)
        assertEquals("months", countdown.unitLabel)
        assertEquals(10L, countdown.amount) // round(300 / 30.44)
        assertTrue(countdown.isApproximate)
    }

    @Test
    fun `goal countdown never mixes units`() {
        val countdown = Phrasing.goalCountdown(478)
        // Should read as a single whole number of months, not "7 months 2 weeks 3 days".
        assertEquals("months", countdown.unitLabel)
        assertTrue(countdown.amount in 14..16)
    }

    @Test
    fun `target date is formatted with a full date including year`() {
        assertEquals("Mar 1, 2026", Phrasing.formatTargetDate(LocalDate.of(2026, 3, 1)))
    }
}
