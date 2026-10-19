package com.andweight.app.domain

/**
 * Gates the occasional "support the app?" nudge - deliberately rare and tied to a meaningful
 * moment (some real track record already built up) rather than a cold-launch interstitial, and
 * capped at a handful of showings ever so it can't turn into the kind of nagging pattern this
 * app otherwise avoids. A persistent entry in Settings covers anyone who wants to support the
 * app without waiting for this prompt's timing.
 */
object SupporterPrompt {

    const val MIN_COMPLETED_WEEKS = 2
    const val MAX_SHOWN_COUNT = 3
    const val MIN_DAYS_BETWEEN_SHOWS = 14L

    fun shouldShow(completedWeeks: Int, isSupporter: Boolean, shownCount: Int, daysSinceLastShown: Long?): Boolean {
        if (isSupporter) return false
        if (shownCount >= MAX_SHOWN_COUNT) return false
        if (completedWeeks < MIN_COMPLETED_WEEKS) return false
        if (daysSinceLastShown != null && daysSinceLastShown < MIN_DAYS_BETWEEN_SHOWS) return false
        return true
    }
}
