package com.andweight.app.domain

data class Tip(val title: String, val body: String)

/** Shared by the daily tip pop-up and the full tips list in Settings. */
object Tips {
    val all = listOf(
        Tip(
            "Log every day you can",
            "The more consistently you log your weight and how much you ate, the faster the app " +
                "locks onto your real metabolic rate - and the more you can trust what it tells you.",
        ),
        Tip(
            "Weigh in at the same time each day",
            "Try to step on the scale around the same time every day, ideally right after waking up " +
                "and before eating or drinking anything. This keeps your readings comparable day to day.",
        ),
        Tip(
            "It gets smarter the longer you use it",
            "This isn't a one-time calculation. Every entry refines the app's estimate of your " +
                "metabolism, so your numbers become more accurate the longer you stick with it.",
        ),
        Tip(
            "You don't need to be precise",
            "There's no need to count calories exactly. A quick, honest sense of how much you ate - " +
                "starving, average, stuffed - is all the app needs to work with.",
        ),
        Tip(
            "Be honest, not aspirational",
            "Log what you actually ate, not what you meant to eat. The app can only help you if what " +
                "you enter reflects reality, good day or bad.",
        ),
        Tip(
            "Missing a day here and there is fine",
            "Life happens. If you skip a day, the app carries your last known numbers forward and " +
                "keeps working - no need to panic or go back and fix anything.",
        ),
        Tip(
            "Forgot to log yesterday? Go back",
            "Use the arrows on the check-in card to step back to an earlier day and fill it in " +
                "whenever you remember. It's never too late to add a missing entry.",
        ),
        Tip(
            "Don't read too much into one day's weight",
            "Your weight can jump around from water retention, sodium, or hormones. Focus on the " +
                "weekly trend on the History tab rather than any single day's number.",
        ),
        Tip(
            "Weighed in this morning? Log it now",
            "You don't need to know your calories yet to save your weight. Log it as soon as you " +
                "weigh in, then come back and add how much you ate once the day's done.",
        ),
    )
}
