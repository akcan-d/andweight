/// Turns the engine's raw numbers into short, plain-language sentences. The exact figures are still
/// available (see the "show details" views) - this is just what people see by default, since the
/// point of the app is "am I on track", not a precise calorie count.
public enum Phrasing {

    /// How today's recommended intake compares to the person's current TDEE - which is itself an
    /// estimate of what they've actually been eating and burning lately, so "usual" here means
    /// "what you've genuinely been eating lately", and it updates as that changes.
    public static func calorieTargetSentence(recommendedCalories: Double, tdee: Double) -> String {
        let delta = recommendedCalories - tdee
        if delta <= -600 { return "Eat noticeably less today." }
        if delta <= -150 { return "Eat a bit less today." }
        if delta < 150 { return "Eat about the usual today." }
        if delta < 600 { return "Eat a bit more today." }
        return "Eat noticeably more today."
    }

    /// A whole number plus a matching, already-pluralized unit word ("months"/"weeks"/"days") -
    /// never a mix of units, and never falsely precise about a many-months-out estimate.
    public struct GoalCountdown: Equatable, Sendable {
        public var amount: Int
        public var unitLabel: String
        public var isApproximate: Bool
    }

    private static let monthThresholdDays = 183
    private static let weekThresholdDays = 60
    private static let daysPerMonth = 30.44
    private static let daysPerWeek = 7.0

    public static func goalCountdown(daysToGoal: Int) -> GoalCountdown {
        if daysToGoal > monthThresholdDays {
            let months = Int((Double(daysToGoal) / daysPerMonth).roundedHalfUp())
            return GoalCountdown(amount: months, unitLabel: months == 1 ? "month" : "months", isApproximate: true)
        }
        if daysToGoal >= weekThresholdDays {
            let weeks = Int((Double(daysToGoal) / daysPerWeek).roundedHalfUp())
            return GoalCountdown(amount: weeks, unitLabel: weeks == 1 ? "week" : "weeks", isApproximate: true)
        }
        return GoalCountdown(amount: daysToGoal, unitLabel: daysToGoal == 1 ? "day" : "days", isApproximate: false)
    }

    private static let monthAbbreviations = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

    /// "Mar 1, 2026". Spelled out by hand rather than through `DateFormatter` so it's identical on
    /// every device and in tests - all of the app's other copy is English-only anyway.
    public static func formatTargetDate(_ date: LocalDate) -> String {
        "\(monthAbbreviations[date.month - 1]) \(date.day), \(date.year)"
    }

    public static func formatWeight(kg: Double, unit: WeightUnit) -> String {
        let unitLabel = switch unit {
        case .kg: "kg"
        case .lb: "lb"
        case .st: "st"
        }
        return "\(unit.fromKg(kg).trimmedDecimalString(places: 1)) \(unitLabel)"
    }
}
