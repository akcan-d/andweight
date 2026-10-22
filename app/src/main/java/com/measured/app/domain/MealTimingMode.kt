package com.measured.app.domain

/**
 * Which day the Today screen's meal section asks about, relative to whatever day is selected.
 * [PREVIOUS_DAY] (the default) suits a morning weigh-in - by then yesterday's eating is finished
 * and known for certain, where today's isn't yet. [SAME_DAY] suits an evening or before-bed
 * weigh-in, where today's eating really is already done by the time of the check-in, so there's
 * no reason to ask about yesterday instead.
 */
enum class MealTimingMode(val label: String) {
    PREVIOUS_DAY("Yesterday's meals"),
    SAME_DAY("Today's meals"),
}
