package com.andweight.app.domain

import java.time.LocalDate

/**
 * Whole-app backup/restore - unlike [CsvExport]/[CsvImport], which only round-trip the logged
 * days, this captures the profile (goal, pace, height, age, activity level, units - everything
 * onboarding asks for) and a few preferences too, so restoring onto a fresh install doesn't mean
 * redoing setup from scratch. Plain `[section]` + `key=value` text rather than a real JSON/CSV
 * library, matching how [CsvImport] is hand-rolled elsewhere in this app - every value here is
 * program-generated (a number, an enum name, a boolean, an ISO date), so there's nothing that
 * needs escaping.
 */
object BackupSerializer {

    data class Settings(
        val themeMode: ThemeMode,
        val tipsEnabled: Boolean,
        val reminderEnabled: Boolean,
        val reminderHour: Int,
        val reminderMinute: Int,
    )

    data class BackupData(
        val profile: UserProfile?,
        val settings: Settings?,
        val logs: List<DailyLog>,
        val skippedLogRowCount: Int,
    )

    private const val MAGIC_HEADER = "ANDWEIGHT_BACKUP v1"
    private val logsHeader = listOf("date", "weight_kg", "calories", "intake_level", "exact_entry")

    fun build(profile: UserProfile?, settings: Settings?, logs: List<DailyLog>): String {
        val lines = mutableListOf(MAGIC_HEADER)

        if (profile != null) {
            lines += "[profile]"
            lines += "weight_unit=${profile.weightUnit.name}"
            lines += "start_weight_kg=${formatNumber(profile.startWeightKg)}"
            lines += "goal_weight_kg=${formatNumber(profile.goalWeightKg)}"
            lines += "pace=${profile.pace?.name.orEmpty()}"
            lines += "sex=${profile.sex.name}"
            lines += "height_cm=${profile.heightCm?.let(::formatNumber).orEmpty()}"
            lines += "age=${profile.age?.toString().orEmpty()}"
            lines += "activity_level=${profile.activityLevel.name}"
            lines += "body_fat_percent=${profile.bodyFatPercent?.let(::formatNumber).orEmpty()}"
        }

        if (settings != null) {
            lines += "[settings]"
            lines += "theme_mode=${settings.themeMode.name}"
            lines += "tips_enabled=${settings.tipsEnabled}"
            lines += "reminder_enabled=${settings.reminderEnabled}"
            lines += "reminder_hour=${settings.reminderHour}"
            lines += "reminder_minute=${settings.reminderMinute}"
        }

        lines += "[logs]"
        lines += logsHeader.joinToString(",")
        logs.sortedBy { it.date }.forEach { log ->
            lines += listOf(
                log.date.toString(),
                log.weightKg?.let(::formatNumber) ?: "",
                log.calories?.let(::formatNumber) ?: "",
                log.intakeLevel?.name ?: "",
                log.wasExactEntry.toString(),
            ).joinToString(",")
        }

        return lines.joinToString("\n") + "\n"
    }

    /** Null means the file isn't recognizable as an AndWeight backup at all - distinct from a
     * recognizable-but-partial file, which still restores whatever sections did parse. */
    fun parse(text: String): BackupData? {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != MAGIC_HEADER) return null

        val sections = mutableMapOf<String, MutableList<String>>()
        var currentSection: String? = null
        for (line in lines.drop(1)) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentSection = trimmed.removePrefix("[").removeSuffix("]")
                sections[currentSection] = mutableListOf()
            } else if (currentSection != null && trimmed.isNotEmpty()) {
                sections.getValue(currentSection).add(line)
            }
        }

        val profile = sections["profile"]?.let(::parseProfile)
        val settings = sections["settings"]?.let(::parseSettings)
        val (logs, skipped) = sections["logs"]?.let(::parseLogs) ?: (emptyList<DailyLog>() to 0)

        return BackupData(profile, settings, logs, skipped)
    }

    private fun parseProfile(lines: List<String>): UserProfile? {
        val fields = toFieldMap(lines)
        val weightUnit = fields["weight_unit"]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: return null
        val startWeightKg = fields["start_weight_kg"]?.toDoubleOrNull() ?: return null
        val goalWeightKg = fields["goal_weight_kg"]?.toDoubleOrNull() ?: return null
        val sex = fields["sex"]?.let { runCatching { Sex.valueOf(it) }.getOrNull() } ?: Sex.UNSPECIFIED
        val activityLevel = fields["activity_level"]?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() } ?: ActivityLevel.LIGHT

        return UserProfile(
            weightUnit = weightUnit,
            startWeightKg = startWeightKg,
            goalWeightKg = goalWeightKg,
            pace = fields["pace"]?.takeIf { it.isNotEmpty() }?.let { runCatching { PaceOption.valueOf(it) }.getOrNull() },
            sex = sex,
            heightCm = fields["height_cm"]?.toDoubleOrNull(),
            age = fields["age"]?.toIntOrNull(),
            activityLevel = activityLevel,
            bodyFatPercent = fields["body_fat_percent"]?.toDoubleOrNull(),
        )
    }

    private fun parseSettings(lines: List<String>): Settings? {
        val fields = toFieldMap(lines)
        val themeMode = fields["theme_mode"]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: return null
        return Settings(
            themeMode = themeMode,
            tipsEnabled = fields["tips_enabled"]?.toBoolean() ?: true,
            reminderEnabled = fields["reminder_enabled"]?.toBoolean() ?: false,
            reminderHour = fields["reminder_hour"]?.toIntOrNull() ?: 21,
            reminderMinute = fields["reminder_minute"]?.toIntOrNull() ?: 0,
        )
    }

    private fun parseLogs(lines: List<String>): Pair<List<DailyLog>, Int> {
        val dataRows = lines.drop(1) // first line is the column header
        val logs = mutableListOf<DailyLog>()
        var skipped = 0

        for (line in dataRows) {
            val columns = line.split(",")
            val date = columns.getOrNull(0)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val weightKg = columns.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            val calories = columns.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

            if (date == null || (weightKg == null && calories == null)) {
                skipped++
                continue
            }

            val intakeLevel = columns.getOrNull(3)?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { IntakeLevel.valueOf(it) }.getOrNull() }
            val wasExact = columns.getOrNull(4)?.toBoolean() ?: false

            logs.add(DailyLog(date, weightKg, calories, intakeLevel, wasExact))
        }

        return logs to skipped
    }

    private fun toFieldMap(lines: List<String>): Map<String, String> =
        lines.mapNotNull { line ->
            val index = line.indexOf('=')
            if (index == -1) null else line.substring(0, index) to line.substring(index + 1)
        }.toMap()

    private fun formatNumber(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
