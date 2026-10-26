/// Reasonable population-average fallbacks for whichever of sex/height/age is still missing, so
/// the Mifflin-St Jeor formula can be used - and genuinely sharpened by each detail actually
/// provided - instead of an all-or-nothing switch to a much cruder bodyweight-only estimate.
private let defaultAge = 35.0
private let defaultHeightCm = 168.0

/// Midpoint of Mifflin-St Jeor's male (+5) and female (-161) constants, used when sex is left
/// unspecified rather than assuming either one.
private let averageSexTerm = -78.0

/// Katch-McArdle constants for BMR from lean body mass directly - used whenever body fat % is
/// known, since it measures the thing Mifflin-St Jeor can only approximate from sex/height/age.
private let katchMcArdleBase = 370.0
private let katchMcArdlePerKgLeanMass = 21.6

/// Reimplementation of the "TDEE 3.0" spreadsheet's adaptive-TDEE algorithm - a direct port of the
/// Android `TdeeEngine.kt`, kept structurally identical so the two can be diffed side by side.
///
/// The spreadsheet groups daily weight/calorie entries into fixed calendar weeks (anchored to a
/// start date), forward-fills missing days within a week using the most recently known average,
/// and estimates that week's true maintenance calories as:
///
///     naiveTdee = avgCaloriesThisWeek + (-weightChangeThisWeekKg * kcalPerKg) / 7
///
/// i.e. "how many calories would explain the weight change we actually saw, given what was eaten".
/// This is then smoothed with a rolling average of up to the 6 most recent weeks (this week's naive
/// estimate plus the last 5 already-finalized weekly TDEEs) so a single noisy week can't swing the
/// number, and a week with zero entries is skipped entirely (carries the previous state forward).
///
/// The whole state is recomputed from the full log history on every call rather than incrementally
/// maintained - with realistic data volumes this is fast enough, and it avoids an entire class of
/// "stale incremental state" bugs.
public enum TdeeEngine {

    public static func mround(_ value: Double, _ multiple: Double) -> Double {
        (value / multiple).roundedHalfUp() * multiple
    }

    public static func ceilToHalf(_ value: Double) -> Double { (value * 2.0).rounded(.up) / 2.0 }

    public static func initialTdeeGuess(_ profile: UserProfile) -> Double {
        let hasAnyDemographicDetail = profile.age != nil || profile.heightCm != nil || profile.sex != .unspecified

        let guess: Double
        if let bodyFatPercent = profile.bodyFatPercent {
            // Katch-McArdle from actual lean body mass - takes priority over Mifflin-St Jeor
            // entirely (rather than being blended with sex/height/age) since it measures the thing
            // those fields could only ever approximate.
            let leanMassKg = profile.startWeightKg * (1.0 - bodyFatPercent / 100.0)
            let bmr = katchMcArdleBase + katchMcArdlePerKgLeanMass * leanMassKg
            guess = bmr * profile.activityLevel.multiplier
        } else if !hasAnyDemographicDetail {
            // Nothing at all provided - matches the original spreadsheet's crude bodyweight-only
            // estimate exactly (validated against real spreadsheet numbers - see TdeeEngineTests).
            guess = profile.startWeightKg * EnergyConstants.maintenanceKcalPerKg
        } else {
            // Mifflin-St Jeor, falling back to a population-average default for whichever detail
            // is still missing.
            let age = profile.age.map(Double.init) ?? defaultAge
            let heightCm = profile.heightCm ?? defaultHeightCm
            let sexTerm: Double = switch profile.sex {
            case .male: 5.0
            case .female: -161.0
            case .unspecified: averageSexTerm
            }
            let bmr = 10.0 * profile.startWeightKg + 6.25 * heightCm - 5.0 * age + sexTerm
            guess = bmr * profile.activityLevel.multiplier
        }
        return max(mround(guess, 5.0), 0.0)
    }

    public static func compute(profile: UserProfile, logs: [DailyLog], today: LocalDate) -> TdeeState {
        let initialGuess = initialTdeeGuess(profile)
        // Last entry wins on a duplicate date, same as Kotlin's associateBy.
        let logsByDate = Dictionary(logs.map { ($0.date, $0) }, uniquingKeysWith: { _, last in last })

        var lastKnownAvgWeight = profile.startWeightKg
        var lastKnownAvgCalories = initialGuess
        var earliestKnownWeightKg = profile.startWeightKg
        var sawWeightData = false
        var finalizedTdees: [Double] = []
        var weekSummaries: [WeekSummary] = []

        // Weekly windows are anchored to the oldest logged day, rather than a separately stored
        // "start date" - so backfilling history from before you started using the app just folds
        // naturally into the weekly averages instead of needing a manually-adjusted anchor.
        let anchorDate = logs.map(\.date).min() ?? today

        let lastLoggedDate = logs.map(\.date).max()
        let daysToToday = LocalDate.daysBetween(anchorDate, today)
        let daysToLastLog = lastLoggedDate.map { LocalDate.daysBetween(anchorDate, $0) } ?? 0
        let daysSpan = max(daysToToday, daysToLastLog, 0)
        let weekCount = daysSpan / 7 + 1

        for weekIndex in 0..<weekCount {
            let weekStart = anchorDate.plusDays(weekIndex * 7)
            let weekEnd = weekStart.plusDays(6)

            var hasWeight = false
            var hasCalories = false
            var prevW = lastKnownAvgWeight
            var prevC = lastKnownAvgCalories
            var sumW = 0.0
            var sumC = 0.0

            for dayOffset in 0..<7 {
                let log = logsByDate[weekStart.plusDays(dayOffset)]
                let filledW: Double
                if let weight = log?.weightKg { hasWeight = true; filledW = weight } else { filledW = prevW }
                let filledC: Double
                if let calories = log?.calories { hasCalories = true; filledC = calories } else { filledC = prevC }
                sumW += filledW
                sumC += filledC
                prevW = filledW
                prevC = filledC
            }

            if !hasWeight && !hasCalories { continue }

            let avgWeight: Double? = hasWeight ? sumW / 7.0 : nil
            let avgCalories: Double? = hasCalories ? sumC / 7.0 : nil
            let weightDelta: Double? = avgWeight.map { $0 - lastKnownAvgWeight }

            if let avgWeight, !sawWeightData {
                earliestKnownWeightKg = avgWeight
                sawWeightData = true
            }

            var tdeeThisWeek: Double?
            if let avgCalories, let weightDelta {
                let naiveTdee = avgCalories + (-weightDelta * EnergyConstants.kcalPerKgBodyweight) / 7.0
                let window = ([naiveTdee] + finalizedTdees.reversed()).prefix(6)
                let rolling = window.reduce(0.0, +) / Double(window.count)
                let tdee = max(mround(rolling, 5.0), 0.0)
                tdeeThisWeek = tdee
                finalizedTdees.append(tdee)
            }

            weekSummaries.append(
                WeekSummary(
                    weekIndex: weekIndex + 1,
                    startDate: weekStart,
                    endDate: weekEnd,
                    hasWeightData: hasWeight,
                    hasCalorieData: hasCalories,
                    avgWeightKg: avgWeight,
                    avgCalories: avgCalories,
                    weightDeltaKg: weightDelta,
                    tdee: tdeeThisWeek
                )
            )

            if let avgWeight { lastKnownAvgWeight = avgWeight }
            if let avgCalories { lastKnownAvgCalories = avgCalories }
        }

        let currentWeight = lastKnownAvgWeight
        let currentTdee = finalizedTdees.last ?? initialGuess

        // The weekly rate is a percentage of body weight, so it's re-derived against *current*
        // weight rather than read back as a number fixed at onboarding - someone losing weight eats
        // a little closer to maintenance each week as they get lighter.
        let activePace = profile.goalDirection != .maintain ? profile.pace : nil
        let currentWeeklyRateKg = activePace.map { PaceGuidance.rateForOption($0, weightKg: currentWeight, direction: profile.goalDirection) } ?? 0.0

        let targetDailyDeficit = mround(currentWeeklyRateKg * EnergyConstants.kcalPerKgBodyweight / 7.0, 5.0)

        let rawRecommended: Double = switch profile.goalDirection {
        case .maintain: currentTdee
        case .gain: currentTdee + targetDailyDeficit
        case .lose: currentTdee - targetDailyDeficit
        }
        let floor = SafetyLimits.floor(for: profile.sex)
        let recommended = max(rawRecommended, floor)

        var weeksToGoal: Double?
        var goalDate: LocalDate?
        if let activePace,
           let weeks = simulateWeeksToGoal(startWeightKg: currentWeight, goalWeightKg: profile.goalWeightKg, pace: activePace, direction: profile.goalDirection) {
            let rounded = ceilToHalf(weeks)
            weeksToGoal = rounded
            goalDate = rounded <= 0.0 ? today : today.plusDays(Int((rounded * 7).roundedHalfUp()))
        }

        return TdeeState(
            initialTdeeGuess: initialGuess,
            currentWeightKg: currentWeight,
            earliestKnownWeightKg: earliestKnownWeightKg,
            currentTdee: currentTdee,
            targetDailyDeficit: targetDailyDeficit,
            recommendedCalories: recommended,
            recommendedCaloriesFloorApplied: recommended > rawRecommended + 0.01,
            weeksToGoal: weeksToGoal,
            goalDate: goalDate,
            weeklyHistory: weekSummaries
        )
    }

    /// Projects a goal date week by week rather than with a single division, since the weekly rate
    /// is a percentage of body weight and shrinks (for loss) or grows (for gain) as the simulated
    /// weight moves - a fixed-rate division would over/underestimate how long it actually takes.
    private static func simulateWeeksToGoal(startWeightKg: Double, goalWeightKg: Double, pace: PaceOption, direction: GoalDirection) -> Double? {
        var weight = startWeightKg
        var weeks = 0.0
        for _ in 0..<2000 {
            let remaining = direction == .lose ? weight - goalWeightKg : goalWeightKg - weight
            if remaining <= 0.0 { return weeks }
            let rate = PaceGuidance.rateForOption(pace, weightKg: weight, direction: direction)
            if rate <= 0.0 { return nil }
            if remaining <= rate { return weeks + remaining / rate }
            weight += direction == .lose ? -rate : rate
            weeks += 1.0
        }
        return nil
    }
}

public enum IntakeResolver {

    /// A quick-pick answer is a formula-derived estimate, not a factual report - so it's safe (and
    /// intended) to clamp it up to the safety floor.
    public static func resolveQuickPick(_ level: IntakeLevel, currentTdeeEstimate: Double, sex: Sex) -> CalorieResolution {
        let raw = currentTdeeEstimate * (1.0 + level.offsetPercent)
        let floored = max(raw, SafetyLimits.floor(for: sex))
        return CalorieResolution(calories: floored, floorApplied: floored > raw + 0.01)
    }

    /// An exact entry is what someone actually ate, so it's kept as typed even if it's below the
    /// safety floor - the UI shows a caution instead of silently overriding a real number.
    public static func resolveExact(_ exactCalories: Double, sex: Sex) -> CalorieResolution {
        let floor = SafetyLimits.floor(for: sex)
        return CalorieResolution(calories: exactCalories, floorApplied: false, belowFloor: exactCalories < floor - 0.01)
    }
}
