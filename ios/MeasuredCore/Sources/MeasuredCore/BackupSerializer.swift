/// Whole-app backup/restore - captures the profile and a few preferences, not just the logged days,
/// so restoring onto a fresh install doesn't mean redoing setup from scratch. Plain `[section]` +
/// `key=value` text rather than JSON - every value here is program-generated, so nothing needs
/// escaping.
///
/// The format is byte-for-byte the same as the Android app's `BackupSerializer.kt`, so a backup
/// file moves freely between the two (e.g. AirDrop/email an Android export to an iPhone).
public enum BackupSerializer {

    public struct Settings: Equatable, Sendable {
        public var themeMode: ThemeMode
        public var tipsEnabled: Bool
        public var reminderEnabled: Bool
        public var reminderHour: Int
        public var reminderMinute: Int

        public init(themeMode: ThemeMode, tipsEnabled: Bool, reminderEnabled: Bool, reminderHour: Int, reminderMinute: Int) {
            self.themeMode = themeMode
            self.tipsEnabled = tipsEnabled
            self.reminderEnabled = reminderEnabled
            self.reminderHour = reminderHour
            self.reminderMinute = reminderMinute
        }
    }

    public struct BackupData: Equatable, Sendable {
        public var profile: UserProfile?
        public var settings: Settings?
        public var logs: [DailyLog]
        public var skippedLogRowCount: Int
    }

    private static let magicHeader = "MEASURED_BACKUP v1"

    // Accepted on restore for anyone with a backup file from before the AndWeight -> Measured
    // rename - the format itself hasn't changed, just the app's name.
    private static let legacyMagicHeader = "ANDWEIGHT_BACKUP v1"

    private static let logsHeader = ["date", "weight_kg", "calories", "intake_level", "exact_entry"]

    public static func build(profile: UserProfile?, settings: Settings?, logs: [DailyLog]) -> String {
        var lines = [magicHeader]

        if let profile {
            lines.append("[profile]")
            lines.append("weight_unit=\(profile.weightUnit.rawValue)")
            lines.append("start_weight_kg=\(formatNumber(profile.startWeightKg))")
            lines.append("goal_weight_kg=\(formatNumber(profile.goalWeightKg))")
            lines.append("pace=\(profile.pace?.rawValue ?? "")")
            lines.append("sex=\(profile.sex.rawValue)")
            lines.append("height_cm=\(profile.heightCm.map(formatNumber) ?? "")")
            lines.append("age=\(profile.age.map(String.init) ?? "")")
            lines.append("activity_level=\(profile.activityLevel.rawValue)")
            lines.append("body_fat_percent=\(profile.bodyFatPercent.map(formatNumber) ?? "")")
        }

        if let settings {
            lines.append("[settings]")
            lines.append("theme_mode=\(settings.themeMode.rawValue)")
            lines.append("tips_enabled=\(settings.tipsEnabled)")
            lines.append("reminder_enabled=\(settings.reminderEnabled)")
            lines.append("reminder_hour=\(settings.reminderHour)")
            lines.append("reminder_minute=\(settings.reminderMinute)")
        }

        lines.append("[logs]")
        lines.append(logsHeader.joined(separator: ","))
        for log in logs.sorted(by: { $0.date < $1.date }) {
            lines.append([
                log.date.description,
                log.weightKg.map(formatNumber) ?? "",
                log.calories.map(formatNumber) ?? "",
                log.intakeLevel?.rawValue ?? "",
                String(log.wasExactEntry),
            ].joined(separator: ","))
        }

        return lines.joined(separator: "\n") + "\n"
    }

    /// Nil means the file isn't recognizable as a Measured backup at all - distinct from a
    /// recognizable-but-partial file, which still restores whatever sections did parse.
    public static func parse(_ text: String) -> BackupData? {
        // Splitting on Character.isNewline handles \n, \r\n (one Character in Swift) and \r alike,
        // like Kotlin's lines().
        let lines = text.split(omittingEmptySubsequences: false, whereSeparator: \.isNewline).map(String.init)
        let header = lines.first?.trimmingWhitespace()
        guard header == magicHeader || header == legacyMagicHeader else { return nil }

        var sections: [String: [String]] = [:]
        var currentSection: String?
        for line in lines.dropFirst() {
            let trimmed = line.trimmingWhitespace()
            if trimmed.hasPrefix("[") && trimmed.hasSuffix("]") {
                let name = String(trimmed.dropFirst().dropLast())
                currentSection = name
                sections[name] = []
            } else if let currentSection, !trimmed.isEmpty {
                sections[currentSection, default: []].append(line)
            }
        }

        let profile = sections["profile"].flatMap(parseProfile)
        let settings = sections["settings"].flatMap(parseSettings)
        let (logs, skipped) = sections["logs"].map(parseLogs) ?? ([], 0)

        return BackupData(profile: profile, settings: settings, logs: logs, skippedLogRowCount: skipped)
    }

    private static func parseProfile(_ lines: [String]) -> UserProfile? {
        let fields = toFieldMap(lines)
        guard let weightUnit = fields["weight_unit"].flatMap(WeightUnit.init(rawValue:)),
              let startWeightKg = fields["start_weight_kg"].flatMap(Double.init),
              let goalWeightKg = fields["goal_weight_kg"].flatMap(Double.init)
        else { return nil }

        return UserProfile(
            weightUnit: weightUnit,
            startWeightKg: startWeightKg,
            goalWeightKg: goalWeightKg,
            pace: fields["pace"].flatMap(PaceOption.init(rawValue:)),
            sex: fields["sex"].flatMap(Sex.init(rawValue:)) ?? .unspecified,
            heightCm: fields["height_cm"].flatMap(Double.init),
            age: fields["age"].flatMap { Int($0) },
            activityLevel: fields["activity_level"].flatMap(ActivityLevel.init(rawValue:)) ?? .light,
            bodyFatPercent: fields["body_fat_percent"].flatMap(Double.init)
        )
    }

    private static func parseSettings(_ lines: [String]) -> Settings? {
        let fields = toFieldMap(lines)
        guard let themeMode = fields["theme_mode"].flatMap(ThemeMode.init(rawValue:)) else { return nil }
        return Settings(
            themeMode: themeMode,
            tipsEnabled: fields["tips_enabled"].map(parseBool) ?? true,
            reminderEnabled: fields["reminder_enabled"].map(parseBool) ?? false,
            reminderHour: fields["reminder_hour"].flatMap { Int($0) } ?? 21,
            reminderMinute: fields["reminder_minute"].flatMap { Int($0) } ?? 0
        )
    }

    private static func parseLogs(_ lines: [String]) -> ([DailyLog], Int) {
        var logs: [DailyLog] = []
        var skipped = 0

        for line in lines.dropFirst() { // first line is the column header
            let columns = line.split(separator: ",", omittingEmptySubsequences: false).map(String.init)
            func column(_ index: Int) -> String? {
                index < columns.count && !columns[index].isEmpty ? columns[index] : nil
            }
            let date = column(0).flatMap(LocalDate.init(isoString:))
            let weightKg = column(1).flatMap(Double.init)
            let calories = column(2).flatMap(Double.init)

            guard let date, weightKg != nil || calories != nil else {
                skipped += 1
                continue
            }

            logs.append(
                DailyLog(
                    date: date,
                    weightKg: weightKg,
                    calories: calories,
                    intakeLevel: column(3).flatMap(IntakeLevel.init(rawValue:)),
                    wasExactEntry: column(4).map(parseBool) ?? false
                )
            )
        }

        return (logs, skipped)
    }

    private static func toFieldMap(_ lines: [String]) -> [String: String] {
        var fields: [String: String] = [:]
        for line in lines {
            guard let index = line.firstIndex(of: "=") else { continue }
            fields[String(line[..<index])] = String(line[line.index(after: index)...])
        }
        return fields
    }

    /// Kotlin's `String.toBoolean()`: "true" in any case is true, anything else is false.
    private static func parseBool(_ text: String) -> Bool { text.lowercased() == "true" }

    private static func formatNumber(_ value: Double) -> String { value.trimmedDecimalString(places: 2) }
}

private extension String {
    func trimmingWhitespace() -> String {
        String(drop(while: \.isWhitespace).reversed().drop(while: \.isWhitespace).reversed())
    }
}
