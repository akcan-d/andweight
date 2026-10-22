package com.measured.app.domain

import java.time.LocalDate

/** What counts as "today's check-in is done" - shared by the Status-tab reminder badge and the
 * daily reminder notification so both agree on the answer. Meal info is reported about whichever
 * date is currently due (see [MealTimingMode]), not necessarily today's own date. */
object CheckInStatus {
    fun mealDateFor(today: LocalDate, mode: MealTimingMode): LocalDate =
        if (mode == MealTimingMode.SAME_DAY) today else today.minusDays(1)

    fun isComplete(todayLog: DailyLog?, mealLog: DailyLog?): Boolean =
        todayLog?.weightKg != null && mealLog?.calories != null
}
