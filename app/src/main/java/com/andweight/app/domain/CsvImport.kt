package com.andweight.app.domain

import java.time.LocalDate

/**
 * Parses the same CSV format [CsvExport] produces - reads whichever weight column is present
 * (weight_kg or weight_lb) so a file exported while Imperial was selected still imports correctly
 * even if the unit has since changed. Malformed rows (unparseable date, or neither a weight nor a
 * calorie value) are skipped rather than aborting the whole import, since a personal export/import
 * round trip only needs to be resilient to the file it itself produces, not arbitrary CSVs.
 */
object CsvImport {

    data class ImportResult(val logs: List<DailyLog>, val skippedRowCount: Int)

    fun parse(csv: String): ImportResult {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ImportResult(emptyList(), 0)

        val header = lines.first().split(",")
        val dateIndex = header.indexOf("date")
        val weightColumnIndex = header.indexOfFirst { it == "weight_kg" || it == "weight_lb" }
        val isImperial = header.getOrNull(weightColumnIndex) == "weight_lb"
        val caloriesIndex = header.indexOf("calories")
        val intakeIndex = header.indexOf("intake_level")
        val exactIndex = header.indexOf("exact_entry")

        val dataRows = lines.drop(1)
        if (dateIndex == -1 || weightColumnIndex == -1) return ImportResult(emptyList(), dataRows.size)

        val logs = mutableListOf<DailyLog>()
        var skipped = 0

        for (line in dataRows) {
            val columns = line.split(",")
            val date = columns.getOrNull(dateIndex)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val weightKg = columns.getOrNull(weightColumnIndex)?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                ?.let { if (isImperial) WeightUnit.LB.toKg(it) else it }
            val calories = columns.getOrNull(caloriesIndex)?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

            if (date == null || (weightKg == null && calories == null)) {
                skipped++
                continue
            }

            val intakeLevel = columns.getOrNull(intakeIndex)?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { IntakeLevel.valueOf(it) }.getOrNull() }
            val wasExact = columns.getOrNull(exactIndex)?.trim()?.toBoolean() ?: false

            logs.add(DailyLog(date, weightKg, calories, intakeLevel, wasExact))
        }

        return ImportResult(logs, skipped)
    }
}
