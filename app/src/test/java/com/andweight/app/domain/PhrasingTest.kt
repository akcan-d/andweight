package com.andweight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PhrasingTest {

    @Test
    fun `calorie target sentence reflects the size of the deficit`() {
        assertEquals("Eat a normal, average amount today.", Phrasing.calorieTargetSentence(2500.0, 2500.0))
        assertEquals("Eat a little less than usual today.", Phrasing.calorieTargetSentence(2200.0, 2500.0))
        assertEquals("Eat noticeably less than usual today.", Phrasing.calorieTargetSentence(1800.0, 2500.0))
        assertEquals("Eat a little more than usual today.", Phrasing.calorieTargetSentence(2800.0, 2500.0))
        assertEquals("Eat noticeably more than usual today.", Phrasing.calorieTargetSentence(3200.0, 2500.0))
    }

    @Test
    fun `weight trend sentence picks a direction`() {
        assertTrue(Phrasing.weightTrendSentence(-2.0, WeightUnit.KG).contains("down"))
        assertTrue(Phrasing.weightTrendSentence(2.0, WeightUnit.KG).contains("up"))
        assertTrue(Phrasing.weightTrendSentence(0.0, WeightUnit.KG).contains("hasn't changed"))
    }

    @Test
    fun `forecast sentence handles maintain, reached, and in-progress goals`() {
        assertEquals("You're aiming to hold steady at your current weight.", Phrasing.forecastSentence(null, null))
        assertEquals("You've reached your goal - nice work!", Phrasing.forecastSentence(0.0, LocalDate.now()))
        assertTrue(Phrasing.forecastSentence(6.0, LocalDate.of(2026, 3, 1)).startsWith("About 6 weeks to go"))
    }
}
