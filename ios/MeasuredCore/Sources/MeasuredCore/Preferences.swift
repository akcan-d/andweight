// Small preference enums - one file here rather than the Android side's one-per-file
// (`MealTimingMode.kt`, `ThemeMode.kt`, `AccentTheme.kt`), since each is only a few lines.

/// Which day the Today screen's meal section asks about, relative to whatever day is selected.
/// `.sameDay` (the default) suits an evening or before-bed weigh-in, where today's eating is already
/// done; `.previousDay` suits a morning weigh-in, where yesterday's eating is finished and known.
public enum MealTimingMode: String, CaseIterable, Sendable {
    case sameDay = "SAME_DAY"
    case previousDay = "PREVIOUS_DAY"

    public var label: String {
        switch self {
        case .sameDay: "Today's meals"
        case .previousDay: "Yesterday's meals"
        }
    }
}

public enum ThemeMode: String, CaseIterable, Sendable {
    case system = "SYSTEM"
    case light = "LIGHT"
    case dark = "DARK"

    public var label: String {
        switch self {
        case .system: "Match system"
        case .light: "Light"
        case .dark: "Dark"
        }
    }
}

/// The free default, plus two supporter-only alternates - drives both the in-app color palette and
/// the app icon (iOS supports alternate icons natively, via `UIApplication.setAlternateIconName`).
public enum AccentTheme: String, CaseIterable, Sendable {
    case sage = "SAGE"
    case berry = "BERRY"
    case ocean = "OCEAN"

    public var label: String {
        switch self {
        case .sage: "Sage"
        case .berry: "Berry"
        case .ocean: "Ocean"
        }
    }

    public var isSupporterOnly: Bool { self != .sage }
}

/// What counts as "today's check-in is done" - shared by the Status-tab reminder badge and the
/// daily reminder notification so both agree on the answer. Meal info is reported about whichever
/// date is currently due (see `MealTimingMode`), not necessarily today's own date.
public enum CheckInStatus {
    public static func mealDate(for today: LocalDate, mode: MealTimingMode) -> LocalDate {
        mode == .sameDay ? today : today.minusDays(1)
    }

    public static func isComplete(todayLog: DailyLog?, mealLog: DailyLog?) -> Bool {
        todayLog?.weightKg != nil && mealLog?.calories != nil
    }
}

/// Gates the occasional "support the app?" nudge - deliberately rare and tied to a meaningful moment
/// rather than a cold-launch interstitial, and capped at a handful of showings ever.
public enum SupporterPrompt {

    public static let minCompletedWeeks = 2
    public static let maxShownCount = 3
    public static let minDaysBetweenShows = 14

    // Gates on this install's actual age/use, separate from completedWeeks - someone who restores a
    // backup full of old weeks on their very first run shouldn't get this prompt in that same first
    // session just because the *logged history* looks established.
    public static let minDaysSinceFirstLaunch = 2
    public static let minLaunchCount = 3

    public static func shouldShow(
        completedWeeks: Int,
        isSupporter: Bool,
        shownCount: Int,
        daysSinceLastShown: Int?,
        daysSinceFirstLaunch: Int,
        launchCount: Int
    ) -> Bool {
        if isSupporter { return false }
        if shownCount >= maxShownCount { return false }
        if completedWeeks < minCompletedWeeks { return false }
        if let daysSinceLastShown, daysSinceLastShown < minDaysBetweenShows { return false }
        if daysSinceFirstLaunch < minDaysSinceFirstLaunch { return false }
        if launchCount < minLaunchCount { return false }
        return true
    }
}
