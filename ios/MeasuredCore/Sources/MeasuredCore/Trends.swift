import Foundation

// Week grouping, data fill rate and plateau detection - the smaller "read the history" helpers
// (Android: `WeekGrouping.kt`, `DataFillRate.kt`, `PlateauDetector.kt`).

/// Calendar-week boundaries for display purposes (e.g. paging through Journey's daily history week
/// by week) - distinct from `TdeeEngine`'s internal weekly windows, which are anchored to the first
/// logged day instead.
public enum WeekGrouping {

    /// The most recent date on or before `date` that falls on `firstDayOfWeek`.
    public static func weekStart(_ date: LocalDate, firstDayOfWeek: DayOfWeek) -> LocalDate {
        let daysSinceWeekStart = (date.dayOfWeek.rawValue - firstDayOfWeek.rawValue + 7) % 7
        return date.minusDays(daysSinceWeekStart)
    }

    /// The phone's own notion of which day starts the week (Settings > General > Language & Region
    /// on iOS) - no separate in-app setting, same as on Android.
    public static func systemFirstDayOfWeek(calendar: Calendar = .current) -> DayOfWeek {
        // Foundation numbers weekdays Sunday = 1 ... Saturday = 7.
        calendar.firstWeekday == 1 ? .sunday : DayOfWeek(rawValue: calendar.firstWeekday - 1)!
    }
}

public struct DataFillRateResult: Equatable, Sendable {
    public var loggedDays: Int
    public var totalDays: Int
    public var percent: Int
}

/// What fraction of recent days actually got a check-in, over a fixed trailing window - but capped
/// to how long the person has actually been using the app, so a brand-new user doesn't see an
/// artificially low percentage.
public enum DataFillRate {

    private static let windowDays = 30

    public static func compute(logs: [DailyLog], today: LocalDate) -> DataFillRateResult? {
        guard let firstLoggedDate = logs.map(\.date).min() else { return nil }

        let windowStart = max(today.minusDays(windowDays - 1), firstLoggedDate)
        let totalDays = LocalDate.daysBetween(windowStart, today) + 1

        let loggedDays = logs.filter {
            $0.date >= windowStart && $0.date <= today && ($0.weightKg != nil || $0.calories != nil)
        }.count

        let percent = Int((Double(loggedDays) / Double(totalDays) * 100.0).roundedHalfUp())
        return DataFillRateResult(loggedDays: loggedDays, totalDays: totalDays, percent: percent)
    }
}

public struct PlateauAssessment: Equatable, Sendable {
    public var isPlateaued: Bool
    public var weeksFlat: Int
}

/// Flags a sustained stretch of weeks where almost no net progress happened, despite consistent
/// logging - distinct from `CoachingAdvisor`'s single-week "progress has been slow lately" message.
public enum PlateauDetector {

    private static let minConsecutiveWeeks = 3

    /// A week counts as "flat" when its net change was within a quarter of the planned weekly rate
    /// in either direction.
    private static let flatRatioRange = -0.25...0.25

    public static func assess(profile: UserProfile, state: TdeeState) -> PlateauAssessment {
        guard profile.goalDirection != .maintain, let pace = profile.pace else {
            return PlateauAssessment(isPlateaued: false, weeksFlat: 0)
        }

        let targetWeeklyRateKg = PaceGuidance.rateForOption(pace, weightKg: state.currentWeightKg, direction: profile.goalDirection)
        guard targetWeeklyRateKg > 0.0 else { return PlateauAssessment(isPlateaued: false, weeksFlat: 0) }
        let signedTarget = profile.goalDirection == .lose ? -targetWeeklyRateKg : targetWeeklyRateKg

        let completedWeeks = state.weeklyHistory.filter { $0.hasWeightData && $0.hasCalorieData }

        // Counts backward from the most recent completed week, stopping at the first week that
        // isn't flat - so a plateau has to be the *current* state.
        var weeksFlat = 0
        for week in completedWeeks.reversed() {
            guard let delta = week.weightDeltaKg, flatRatioRange.contains(delta / signedTarget) else { break }
            weeksFlat += 1
        }

        return PlateauAssessment(isPlateaued: weeksFlat >= minConsecutiveWeeks, weeksFlat: weeksFlat)
    }
}
