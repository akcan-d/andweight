public enum PaceOption: String, CaseIterable, Sendable {
    case relaxed = "RELAXED"
    case moderate = "MODERATE"
    case fastest = "FASTEST"

    public var label: String {
        switch self {
        case .relaxed: "Slow and easy"
        case .moderate: "Steady and balanced"
        case .fastest: "Faster, within safe limits"
        }
    }
}

/// Turns a qualitative pace choice into an actual weekly rate, instead of letting someone type in an
/// arbitrary number. There's no "unlimited" option on purpose - even the fastest tier is capped at a
/// rate generally considered sustainable for the person's size.
///
/// Cutting guidance follows an "up to ~1.5% of body weight per week" ceiling for the fastest tier,
/// capped in absolute terms at 2 kg/week regardless of size. Gaining guidance is deliberately slower
/// - a "lean bulk" is usually recommended at roughly a third of that rate.
public enum PaceGuidance {

    /// `weightKg` is whatever weight the rate should be evaluated against right now - current weight
    /// in day-to-day use, or the just-entered starting weight during onboarding.
    public static func maxSafeWeeklyRateKg(weightKg: Double, direction: GoalDirection) -> Double {
        switch direction {
        case .lose: min(2.0, weightKg * 0.015)
        case .gain: min(1.0, weightKg * 0.005)
        case .maintain: 0.0
        }
    }

    public static func rateForOption(_ option: PaceOption, weightKg: Double, direction: GoalDirection) -> Double {
        let max = maxSafeWeeklyRateKg(weightKg: weightKg, direction: direction)
        let fraction = switch option {
        case .relaxed: 0.4
        case .moderate: 0.7
        case .fastest: 1.0
        }
        return max * fraction
    }
}
