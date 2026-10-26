public enum AdviceSentiment: Sendable {
    case positive
    case neutral
    case caution
}

public struct Advice: Equatable, Sendable {
    public var message: String
    public var sentiment: AdviceSentiment
}

/// Compares how the person has actually been trending against their stated goal and produces a
/// single, self-contained piece of guidance - deliberately the *only* "what should I eat today"
/// message shown, so it can never contradict a separately-computed line.
///
/// Deliberately conservative early on - with only a week or two of data the TDEE estimate is still
/// finding its footing, so advice before then would just be noise.
public enum CoachingAdvisor {

    private static let minWeeksForAdvice = 2

    public static func advise(profile: UserProfile, state: TdeeState) -> Advice {
        let completedWeeks = state.weeklyHistory.filter { $0.hasWeightData && $0.hasCalorieData }
        let targetSentence = Phrasing.calorieTargetSentence(recommendedCalories: state.recommendedCalories, tdee: state.currentTdee)

        if completedWeeks.count < minWeeksForAdvice {
            return Advice(message: "Still an early estimate - keep logging to sharpen it up.", sentiment: .neutral)
        }

        let recentDeltaKg = completedWeeks.last?.weightDeltaKg ?? 0.0

        if profile.goalDirection == .maintain {
            if abs(recentDeltaKg) < 0.3 {
                return Advice(message: "Holding steady - keep eating normally.", sentiment: .positive)
            }
            if recentDeltaKg > 0 {
                return Advice(message: "You've been drifting upward lately. Try scaling back your portions a little.", sentiment: .caution)
            }
            return Advice(message: "You've been drifting downward lately. Try adding a little more to your meals.", sentiment: .caution)
        }

        // Re-derived against current weight, not a number fixed back at onboarding - "on track" has
        // to mean on track for what the plan calls for *now*.
        let currentWeeklyRateKg = profile.pace.map {
            PaceGuidance.rateForOption($0, weightKg: state.currentWeightKg, direction: profile.goalDirection)
        } ?? 0.0
        let targetWeeklyDeltaKg: Double = switch profile.goalDirection {
        case .lose: -currentWeeklyRateKg
        case .gain: currentWeeklyRateKg
        case .maintain: 0.0
        }
        if targetWeeklyDeltaKg == 0.0 {
            return Advice(message: "You're on track. \(targetSentence)", sentiment: .positive)
        }

        let progressRatio = recentDeltaKg / targetWeeklyDeltaKg
        let isLosing = profile.goalDirection == .lose

        if progressRatio < 0.3 {
            let verb = isLosing ? "eating a bit less" : "eating a bit more"
            return Advice(message: "Progress has been slow lately. Try \(verb) than you have been the past couple of weeks.", sentiment: .caution)
        }
        if progressRatio > 1.8 {
            let verb = isLosing ? "eating a little more" : "eating a little less"
            return Advice(
                message: "You're moving faster than planned, which isn't necessarily healthier - consider \(verb) " +
                    "to keep it sustainable and protect your energy and muscle mass.",
                sentiment: .caution
            )
        }
        // No imperative here - targetSentence always reads as "eat less" for a lose goal, which
        // would read as a correction right after telling someone they're doing exactly what's needed.
        return Advice(message: "You're doing great - right on pace for your goal. Keep it up.", sentiment: .positive)
    }
}
