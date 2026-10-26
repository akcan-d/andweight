public enum ConfidenceLevel: Sendable {
    case low
    case medium
    case high

    public var label: String {
        switch self {
        case .low: "Low accuracy"
        case .medium: "Medium accuracy"
        case .high: "High accuracy"
        }
    }
}

public struct ConfidenceAssessment: Equatable, Sendable {
    public var level: ConfidenceLevel
    public var message: String
    /// True only for the specific message that suggests filling in profile details - lets the UI
    /// offer a direct link to the profile editor instead of just saying "in Settings".
    public var promptProfileDetails: Bool = false
}

/// How much to trust the current TDEE estimate and target - based on how much real data has gone
/// into it so far. Unlike a one-time score, this can also go back down: if logging lapses for a
/// while after a real track record has been built up, the estimate is running on stale information.
/// That staleness check is gated behind already having some completed weeks, so nobody is greeted
/// with a red warning on day one.
public enum ConfidenceEvaluator {

    private static let recentWindowDays = 7
    private static let minRecentDaysLogged = 3
    private static let minWeeksBeforeStalenessApplies = 2

    /// How far the estimate can drift from its starting formula-based guess before treating that
    /// drift itself as a reason to doubt it. A 60%+ swing either way usually means recent quick-pick
    /// choices don't track real results closely enough for the number to mean much yet.
    private static let driftRatioThreshold = 1.6

    /// Out of 3 (sex, height, age) - each independently sharpens `TdeeEngine.initialTdeeGuess`.
    private static let detailsNeededForMedium = 2

    public static func assess(profile: UserProfile, state: TdeeState, logs: [DailyLog], today: LocalDate) -> ConfidenceAssessment {
        let completedWeeks = state.weeklyHistory.filter { $0.hasWeightData && $0.hasCalorieData }.count

        // Body fat % alone drives the formula (Katch-McArdle bypasses sex/height/age entirely), so
        // it counts as full accuracy on its own.
        let hasBodyFat = profile.bodyFatPercent != nil
        let profileDetailCount = [profile.age != nil, profile.heightCm != nil, profile.sex != .unspecified].filter { $0 }.count
        let hasFullProfileDetails = hasBodyFat || profileDetailCount == 3
        let hasEnoughProfileDetails = hasBodyFat || profileDetailCount >= detailsNeededForMedium

        let recentWindowStart = today.minusDays(recentWindowDays - 1)
        let recentDaysLogged = logs.filter {
            $0.date >= recentWindowStart && $0.date <= today && ($0.weightKg != nil || $0.calories != nil)
        }.count
        let isStale = completedWeeks >= minWeeksBeforeStalenessApplies && recentDaysLogged < minRecentDaysLogged

        let driftRatio = state.currentTdee / state.initialTdeeGuess
        let hasDrifted = completedWeeks >= minWeeksBeforeStalenessApplies &&
            state.initialTdeeGuess > 0.0 &&
            (driftRatio > driftRatioThreshold || driftRatio < 1.0 / driftRatioThreshold)

        // "N more days" assumes logging daily from here on - a completed week only needs one day of
        // each kind of entry somewhere in it.
        func daysToNextLevel(_ targetWeeks: Int) -> String {
            let days = max(targetWeeks - completedWeeks, 1) * 7
            return "Enter data for about \(days) more day\(days == 1 ? "" : "s") to reach the next level."
        }

        if isStale {
            let daysNeeded = max(minRecentDaysLogged - recentDaysLogged, 1)
            return ConfidenceAssessment(
                level: .low,
                message: "You haven't logged much in the past week, so this estimate is going stale. Enter " +
                    "data for \(daysNeeded) more day\(daysNeeded == 1 ? "" : "s") this week to bring it back up."
            )
        }
        if hasDrifted {
            return ConfidenceAssessment(
                level: .low,
                message: "This estimate has moved a lot from where it started - usually that means recent " +
                    "quick-pick choices (\"a bit less\", \"much more\", etc.) don't quite match what " +
                    "actually happened on the scale. Logging exact calories for a few days can help it " +
                    "settle on the right number faster."
            )
        }
        if completedWeeks >= 4 {
            return ConfidenceAssessment(
                level: .high,
                message: "Built from several weeks of your actual results - this is a solid, personalized estimate."
            )
        }
        if completedWeeks >= 2 {
            return ConfidenceAssessment(
                level: .medium,
                message: "Based on a couple of weeks of your data - getting more accurate, but still settling in. " +
                    daysToNextLevel(4)
            )
        }
        if hasBodyFat {
            return ConfidenceAssessment(
                level: .medium,
                message: "Starting estimate uses your body fat percentage for the most accurate formula available. " +
                    daysToNextLevel(4)
            )
        }
        if hasFullProfileDetails {
            return ConfidenceAssessment(
                level: .medium,
                message: "Starting estimate uses your sex, height, and age for a fully personalized formula. " +
                    daysToNextLevel(4)
            )
        }
        if hasEnoughProfileDetails {
            return ConfidenceAssessment(
                level: .medium,
                message: "Starting estimate uses most of what you've told us about yourself - filling in the last " +
                    "detail (sex, height, or age), or your body fat percentage, would sharpen it further. " +
                    daysToNextLevel(4),
                promptProfileDetails: true
            )
        }
        let nextLevel = daysToNextLevel(2)
        return ConfidenceAssessment(
            level: .low,
            message: "This is still a rough, formula-based starting estimate. Add your sex, height, and age " +
                "(or your body fat percentage, if you know it) to sharpen it up right away. Otherwise, " +
                nextLevel.prefix(1).lowercased() + nextLevel.dropFirst(),
            promptProfileDetails: true
        )
    }
}
