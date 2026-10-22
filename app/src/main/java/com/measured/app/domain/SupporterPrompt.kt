package com.measured.app.domain

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

    // Gates on this install's actual age/use, separate from completedWeeks - someone who
    // restores a backup full of old weeks on their very first run of a fresh install shouldn't
    // get this prompt in that same first session just because the *logged history* looks
    // established, before they've spent any real time with the app itself.
    const val MIN_DAYS_SINCE_FIRST_LAUNCH = 2L
    const val MIN_LAUNCH_COUNT = 3

    fun shouldShow(
        completedWeeks: Int,
        isSupporter: Boolean,
        shownCount: Int,
        daysSinceLastShown: Long?,
        daysSinceFirstLaunch: Long,
        launchCount: Int,
    ): Boolean {
        if (isSupporter) return false
        if (shownCount >= MAX_SHOWN_COUNT) return false
        if (completedWeeks < MIN_COMPLETED_WEEKS) return false
        if (daysSinceLastShown != null && daysSinceLastShown < MIN_DAYS_BETWEEN_SHOWS) return false
        if (daysSinceFirstLaunch < MIN_DAYS_SINCE_FIRST_LAUNCH) return false
        if (launchCount < MIN_LAUNCH_COUNT) return false
        return true
    }
}
