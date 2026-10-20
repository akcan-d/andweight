package com.measured.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupporterPromptTest {

    @Test
    fun `never shows to an existing supporter`() {
        assertFalse(SupporterPrompt.shouldShow(completedWeeks = 10, isSupporter = true, shownCount = 0, daysSinceLastShown = null))
    }

    @Test
    fun `never shows before two completed weeks`() {
        assertFalse(SupporterPrompt.shouldShow(completedWeeks = 1, isSupporter = false, shownCount = 0, daysSinceLastShown = null))
    }

    @Test
    fun `shows once the minimum track record is there and it's never been shown`() {
        assertTrue(SupporterPrompt.shouldShow(completedWeeks = 2, isSupporter = false, shownCount = 0, daysSinceLastShown = null))
    }

    @Test
    fun `stops after the maximum number of showings`() {
        assertFalse(SupporterPrompt.shouldShow(completedWeeks = 20, isSupporter = false, shownCount = 3, daysSinceLastShown = 365L))
    }

    @Test
    fun `respects the minimum gap between showings`() {
        assertFalse(SupporterPrompt.shouldShow(completedWeeks = 5, isSupporter = false, shownCount = 1, daysSinceLastShown = 5L))
        assertTrue(SupporterPrompt.shouldShow(completedWeeks = 5, isSupporter = false, shownCount = 1, daysSinceLastShown = 14L))
    }
}
