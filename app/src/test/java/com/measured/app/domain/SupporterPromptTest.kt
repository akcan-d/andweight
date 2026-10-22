package com.measured.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupporterPromptTest {

    // Well past the install-age/launch-count gate by default, so each existing test keeps
    // exercising only the condition it's named for.
    private val establishedDays = 10L
    private val establishedLaunches = 10

    @Test
    fun `never shows to an existing supporter`() {
        assertFalse(
            SupporterPrompt.shouldShow(
                completedWeeks = 10,
                isSupporter = true,
                shownCount = 0,
                daysSinceLastShown = null,
                daysSinceFirstLaunch = establishedDays,
                launchCount = establishedLaunches,
            ),
        )
    }

    @Test
    fun `never shows before two completed weeks`() {
        assertFalse(
            SupporterPrompt.shouldShow(
                completedWeeks = 1,
                isSupporter = false,
                shownCount = 0,
                daysSinceLastShown = null,
                daysSinceFirstLaunch = establishedDays,
                launchCount = establishedLaunches,
            ),
        )
    }

    @Test
    fun `shows once the minimum track record is there and it's never been shown`() {
        assertTrue(
            SupporterPrompt.shouldShow(
                completedWeeks = 2,
                isSupporter = false,
                shownCount = 0,
                daysSinceLastShown = null,
                daysSinceFirstLaunch = establishedDays,
                launchCount = establishedLaunches,
            ),
        )
    }

    @Test
    fun `stops after the maximum number of showings`() {
        assertFalse(
            SupporterPrompt.shouldShow(
                completedWeeks = 20,
                isSupporter = false,
                shownCount = 3,
                daysSinceLastShown = 365L,
                daysSinceFirstLaunch = establishedDays,
                launchCount = establishedLaunches,
            ),
        )
    }

    @Test
    fun `respects the minimum gap between showings`() {
        assertFalse(
            SupporterPrompt.shouldShow(
                completedWeeks = 5,
                isSupporter = false,
                shownCount = 1,
                daysSinceLastShown = 5L,
                daysSinceFirstLaunch = establishedDays,
                launchCount = establishedLaunches,
            ),
        )
        assertTrue(
            SupporterPrompt.shouldShow(
                completedWeeks = 5,
                isSupporter = false,
                shownCount = 1,
                daysSinceLastShown = 14L,
                daysSinceFirstLaunch = establishedDays,
                launchCount = establishedLaunches,
            ),
        )
    }

    // A backup restore can bring years of "completed weeks" with it on the very first session
    // of a brand new install - this is the gate that stops the prompt from firing on that same
    // first session regardless of how established the *logged history* looks.
    @Test
    fun `never shows before this install is at least two days old, no matter how much log history exists`() {
        assertFalse(
            SupporterPrompt.shouldShow(
                completedWeeks = 50,
                isSupporter = false,
                shownCount = 0,
                daysSinceLastShown = null,
                daysSinceFirstLaunch = 0L,
                launchCount = 10,
            ),
        )
        assertTrue(
            SupporterPrompt.shouldShow(
                completedWeeks = 50,
                isSupporter = false,
                shownCount = 0,
                daysSinceLastShown = null,
                daysSinceFirstLaunch = 2L,
                launchCount = 10,
            ),
        )
    }

    @Test
    fun `never shows before the app has been launched at least three times`() {
        assertFalse(
            SupporterPrompt.shouldShow(
                completedWeeks = 50,
                isSupporter = false,
                shownCount = 0,
                daysSinceLastShown = null,
                daysSinceFirstLaunch = establishedDays,
                launchCount = 2,
            ),
        )
        assertTrue(
            SupporterPrompt.shouldShow(
                completedWeeks = 50,
                isSupporter = false,
                shownCount = 0,
                daysSinceLastShown = null,
                daysSinceFirstLaunch = establishedDays,
                launchCount = 3,
            ),
        )
    }
}
