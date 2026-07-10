package com.andweight.app.domain

import java.time.format.DateTimeFormatter

/**
 * Builds a plain CSV of the full logged history, in the user's chosen display unit rather than
 * the internally-stored kilograms - so opening it in a spreadsheet shows the same numbers the
 * person actually saw in the app.
 */
object CsvExport {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun buildCsv(profile: UserProfile?, logs: List<DailyLog>): String {
        val unit = profile?.weightUnit ?: WeightUnit.KG
        val weightHeader = if (unit == WeightUnit.KG) "weight_kg" else "weight_lb"

        // Every field is program-generated (ISO date, a number, an enum name, or a boolean), so
        // none of them can ever contain a comma or quote - no CSV escaping needed.
        val header = listOf("date", weightHeader, "calories", "intake_level", "exact_entry").joinToString(",")
        val rows = logs.sortedBy { it.date }.map { log ->
            listOf(
                log.date.format(dateFormatter),
                log.weightKg?.let { unit.fromKg(it) }?.let(::formatNumber) ?: "",
                log.calories?.let(::formatNumber) ?: "",
                log.intakeLevel?.name ?: "",
                log.wasExactEntry.toString(),
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n") + "\n"
    }

    private fun formatNumber(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
