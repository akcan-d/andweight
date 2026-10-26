public enum MilestoneCategory: Sendable {
    case time
    case streak
    case progress
    case amount

    public var label: String {
        switch self {
        case .time: "Time"
        case .streak: "Consistency"
        case .progress: "Progress"
        case .amount: "Weight moved"
        }
    }
}

public struct Milestone: Equatable, Sendable, Identifiable {
    public var category: MilestoneCategory
    /// Stable, unit- and locale-independent identity - this is what "already seen/notified"
    /// tracking keys off of, kept entirely separate from `title`. `title` for amount milestones
    /// embeds a number converted to the person's display unit, so using title text as identity
    /// would make a kg/lb switch alone look like a fresh set of milestones. Same id strings as the
    /// Android app.
    public var id: String
    public var title: String
    public var achieved: Bool
    /// Only set when `achieved` is false - a short "how much further" hint.
    public var hint: String?

    public init(category: MilestoneCategory, id: String, title: String, achieved: Bool, hint: String? = nil) {
        self.category = category
        self.id = id
        self.title = title
        self.achieved = achieved
        self.hint = hint
    }
}

/// Generic, non-medical milestones for the Journey screen - deliberately built only from things this
/// app can actually verify (elapsed time, logging consistency, progress toward the stated goal,
/// weight moved), rather than asserting specific physiological outcomes.
///
/// Each category returns every threshold already reached, in order, plus the single next one coming
/// up. Everything here is computed fresh on every call rather than cached, so it stays correct if
/// the goal weight changes.
public enum JourneyMilestones {

    private static let timeThresholds: [(Int, String)] = [
        (1, "First check-in"),
        (7, "One week in"),
        (14, "Two weeks in"),
        (30, "One month in"),
        (90, "Three months in"),
        (180, "Six months in"),
        (365, "One year in"),
    ]

    private static let streakThresholds: [(Int, String)] = [
        (3, "3-day streak"),
        (7, "7-day streak"),
        (14, "Two-week streak"),
        (30, "30-day streak"),
        (60, "60-day streak"),
        (100, "100-day streak"),
    ]

    private static let progressThresholds: [(Double, String)] = [
        (0.10, "10% of the way there"),
        (0.25, "A quarter of the way there"),
        (0.50, "Halfway there"),
        (0.75, "Three-quarters there"),
        (1.00, "Goal reached"),
    ]

    // A single round-number progression shared by every goal size; amountFractionRange then picks
    // the slice that's meaningful for a given goal's total distance. Always in kg regardless of the
    // display unit, so switching units can never flip which thresholds count as reached.
    private static let amountProgressionKg = [0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0]
    private static let amountFractionRange = 0.05...0.95

    public static func compute(profile: UserProfile, state: TdeeState, logs: [DailyLog], today: LocalDate) -> [Milestone] {
        var milestones: [Milestone] = []

        if let firstLoggedDate = logs.map(\.date).min() {
            let daysSinceStart = LocalDate.daysBetween(firstLoggedDate, today) + 1
            milestones += timeMilestones(daysSinceStart: daysSinceStart)
        }

        milestones += streakMilestones(longestStreak: longestLoggingStreak(logs))

        if profile.goalDirection != .maintain {
            let totalNeededKg = abs(profile.goalWeightKg - state.earliestKnownWeightKg)
            if totalNeededKg > 0.01 {
                let movedKg = abs(state.currentWeightKg - state.earliestKnownWeightKg)
                milestones += progressMilestones(rawRatio: movedKg / totalNeededKg)
                milestones += amountMilestones(movedKg: movedKg, totalNeededKg: totalNeededKg, unit: profile.weightUnit, direction: profile.goalDirection)
            }
        }

        return milestones
    }

    /// Every rung up to and including the last one reached, plus the next one with a hint. Shared by
    /// all four categories (the Kotlin original repeats this loop per category).
    private static func ladder<T>(
        _ thresholds: [T],
        isReached: (T) -> Bool,
        milestone: (T, _ achieved: Bool) -> Milestone
    ) -> [Milestone] {
        let reachedCount = (thresholds.lastIndex(where: isReached) ?? -1) + 1
        var result = thresholds.prefix(reachedCount).map { milestone($0, true) }
        if reachedCount < thresholds.count {
            result.append(milestone(thresholds[reachedCount], false))
        }
        return result
    }

    private static func timeMilestones(daysSinceStart: Int) -> [Milestone] {
        ladder(timeThresholds, isReached: { daysSinceStart >= $0.0 }) { threshold, achieved in
            Milestone(
                category: .time,
                id: "time_\(threshold.0)",
                title: threshold.1,
                achieved: achieved,
                hint: achieved ? nil : "\(dayWord(threshold.0 - daysSinceStart)) to go"
            )
        }
    }

    private static func streakMilestones(longestStreak: Int) -> [Milestone] {
        ladder(streakThresholds, isReached: { longestStreak >= $0.0 }) { threshold, achieved in
            Milestone(
                category: .streak,
                id: "streak_\(threshold.0)",
                title: threshold.1,
                achieved: achieved,
                hint: achieved ? nil : "\(dayWord(threshold.0 - longestStreak)) more in a row"
            )
        }
    }

    private static func progressMilestones(rawRatio: Double) -> [Milestone] {
        let ratio = min(max(rawRatio, 0.0), 1.0)
        return ladder(progressThresholds, isReached: { ratio >= $0.0 - 0.001 }) { threshold, achieved in
            let remainingPercent = Int(((threshold.0 - ratio) * 100).roundedHalfUp())
            return Milestone(
                category: .progress,
                id: "progress_\(threshold.0)",
                title: threshold.1,
                achieved: achieved,
                hint: achieved ? nil : "\(remainingPercent)% to go"
            )
        }
    }

    private static func amountMilestones(movedKg: Double, totalNeededKg: Double, unit: WeightUnit, direction: GoalDirection) -> [Milestone] {
        let thresholdsInKg = amountProgressionKg.filter { amountFractionRange.contains($0 / totalNeededKg) }
        if thresholdsInKg.isEmpty { return [] }

        let unitLabel = unit.rawValue.lowercased()
        let verb = direction == .lose ? "down" : "up"
        // Rounded to a whole number in lb (values there tend to be larger, where a decimal reads as
        // noise) but kept to one decimal in kg or stones, where small thresholds would round to "0".
        func formatForUnit(_ valueInUnit: Double) -> String {
            unit == .lb ? String(Int(valueInUnit.roundedHalfUp())) : valueInUnit.trimmedDecimalString(places: 1)
        }

        return ladder(thresholdsInKg, isReached: { movedKg >= $0 - 0.01 }) { thresholdKg, achieved in
            Milestone(
                category: .amount,
                // The threshold's kg value and goal direction, never the display unit - so a unit
                // switch alone can't change a milestone's identity.
                id: "amount_\(thresholdKg)_\(direction.rawValue)",
                title: "\(formatForUnit(unit.fromKg(thresholdKg))) \(unitLabel) \(verb)",
                achieved: achieved,
                hint: achieved ? nil : "\(formatForUnit(unit.fromKg(thresholdKg - movedKg))) \(unitLabel) to go"
            )
        }
    }

    /// Longest run of consecutive calendar days with at least a weight or calorie entry, anywhere in
    /// the history - once reached, a streak milestone stays earned even if a later streak breaks.
    private static func longestLoggingStreak(_ logs: [DailyLog]) -> Int {
        let loggedDates = Set(logs.filter { $0.weightKg != nil || $0.calories != nil }.map(\.date)).sorted()
        guard !loggedDates.isEmpty else { return 0 }

        var longest = 1
        var current = 1
        for i in 1..<loggedDates.count {
            current = loggedDates[i] == loggedDates[i - 1].plusDays(1) ? current + 1 : 1
            longest = max(longest, current)
        }
        return longest
    }

    private static func dayWord(_ days: Int) -> String { days == 1 ? "1 day" : "\(days) days" }
}
