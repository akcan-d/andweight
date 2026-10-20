package com.measured.app.domain

data class Tip(val title: String, val body: String)

/** Shared by the daily tip pop-up and the full tips list in Settings. */
object Tips {

    /** How many of the tips below (in order, from the start of [all]) get shown first - once per
     * launch of the pop-up - before it switches to picking randomly. Keeps the most fundamental,
     * safety-relevant information in front of new users first. */
    const val PRIORITY_COUNT = 5

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
                "much less, about the same, much more - is all the app needs to work with.",
        ),
        Tip(
            "This is about health, not just a number",
            "Weight is only one part of the picture - muscle mass and body composition matter just " +
                "as much. Faster isn't better here: this app is built to guide you toward a " +
                "sustainable pace, not the quickest one.",
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
                "longer trend rather than any single day's number.",
        ),
        Tip(
            "Weighed in this morning? Log it now",
            "You don't need to know your calories yet to save your weight. Log it as soon as you " +
                "weigh in, then come back and add how much you ate once the day's done.",
        ),
        Tip(
            "Losing weight too fast can backfire",
            "Rapid weight loss often means losing muscle along with fat, and tends to be harder to " +
                "sustain. The app's pace options are capped for this reason - slower and steadier " +
                "usually wins out.",
        ),
        Tip(
            "Struggling with food, not just weight?",
            "If tracking your eating ever starts to feel distressing or obsessive, please reach out " +
                "to a doctor or a mental health professional. This app is a general tool, not a " +
                "substitute for medical or psychological care.",
        ),
    )
}
