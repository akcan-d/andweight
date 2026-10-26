package com.measured.app.domain

/**
 * Which day the Today screen's meal section asks about, relative to whatever day is selected.
 * [SAME_DAY] (the default) is the least surprising reading of a panel titled "Today" - it suits an
 * evening or before-bed weigh-in, where today's eating is already done by the time of the
 * check-in. [PREVIOUS_DAY] suits a morning weigh-in instead, where yesterday's eating is finished
 * and known for certain but today's isn't yet - someone who checks in that way opts into it
 * explicitly in Settings rather than being defaulted into a mismatch between what the screen says
 * and what it's actually asking for.
 */
enum class MealTimingMode(val label: String) {
    SAME_DAY("Today's meals"),
    PREVIOUS_DAY("Yesterday's meals"),
}
