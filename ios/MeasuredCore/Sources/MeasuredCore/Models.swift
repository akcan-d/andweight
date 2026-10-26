public struct UserProfile: Equatable, Sendable {
    public var weightUnit: WeightUnit
    public var startWeightKg: Double
    public var goalWeightKg: Double
    /// Nil when maintaining. Kept as the qualitative choice rather than a fixed kg/week number, so
    /// the actual weekly rate can be re-derived against *current* weight (see `PaceGuidance`) every
    /// time it's needed - a percentage-of-bodyweight pace naturally tapers as you approach your goal.
    public var pace: PaceOption?
    public var sex: Sex
    public var heightCm: Double?
    public var age: Int?
    public var activityLevel: ActivityLevel
    /// When known, this alone drives the starting TDEE estimate (see `TdeeEngine.initialTdeeGuess`)
    /// since actual lean body mass is a more direct measure than approximating it from sex/height/age.
    public var bodyFatPercent: Double?

    public init(
        weightUnit: WeightUnit,
        startWeightKg: Double,
        goalWeightKg: Double,
        pace: PaceOption? = nil,
        sex: Sex = .unspecified,
        heightCm: Double? = nil,
        age: Int? = nil,
        activityLevel: ActivityLevel = .light,
        bodyFatPercent: Double? = nil
    ) {
        self.weightUnit = weightUnit
        self.startWeightKg = startWeightKg
        self.goalWeightKg = goalWeightKg
        self.pace = pace
        self.sex = sex
        self.heightCm = heightCm
        self.age = age
        self.activityLevel = activityLevel
        self.bodyFatPercent = bodyFatPercent
    }

    public var goalDirection: GoalDirection {
        if goalWeightKg > startWeightKg + 0.01 { return .gain }
        if goalWeightKg < startWeightKg - 0.01 { return .lose }
        return .maintain
    }
}

/// One day's check-in. Both fields are optional - the engine forward-fills gaps.
public struct DailyLog: Equatable, Sendable {
    public var date: LocalDate
    public var weightKg: Double?
    public var calories: Double?
    /// Which quick-pick answer produced `calories`, or nil if `wasExactEntry` is true.
    public var intakeLevel: IntakeLevel?
    public var wasExactEntry: Bool

    public init(date: LocalDate, weightKg: Double?, calories: Double?, intakeLevel: IntakeLevel? = nil, wasExactEntry: Bool = false) {
        self.date = date
        self.weightKg = weightKg
        self.calories = calories
        self.intakeLevel = intakeLevel
        self.wasExactEntry = wasExactEntry
    }
}

public struct WeekSummary: Equatable, Sendable {
    public var weekIndex: Int
    public var startDate: LocalDate
    public var endDate: LocalDate
    public var hasWeightData: Bool
    public var hasCalorieData: Bool
    public var avgWeightKg: Double?
    public var avgCalories: Double?
    public var weightDeltaKg: Double?
    public var tdee: Double?

    public init(
        weekIndex: Int,
        startDate: LocalDate,
        endDate: LocalDate,
        hasWeightData: Bool,
        hasCalorieData: Bool,
        avgWeightKg: Double?,
        avgCalories: Double?,
        weightDeltaKg: Double?,
        tdee: Double?
    ) {
        self.weekIndex = weekIndex
        self.startDate = startDate
        self.endDate = endDate
        self.hasWeightData = hasWeightData
        self.hasCalorieData = hasCalorieData
        self.avgWeightKg = avgWeightKg
        self.avgCalories = avgCalories
        self.weightDeltaKg = weightDeltaKg
        self.tdee = tdee
    }
}

public struct TdeeState: Equatable, Sendable {
    public var initialTdeeGuess: Double
    public var currentWeightKg: Double
    /// The earliest weight actually known for this person - the first real logged entry if one
    /// exists (which may predate the profile's start date via backfilling), otherwise the weight
    /// entered at onboarding. Use this, not the onboarding snapshot, as the "since you started"
    /// reference point.
    public var earliestKnownWeightKg: Double
    public var currentTdee: Double
    public var targetDailyDeficit: Double
    public var recommendedCalories: Double
    public var recommendedCaloriesFloorApplied: Bool
    /// Nil when the goal is to maintain weight, or the goal rate is zero.
    public var weeksToGoal: Double?
    public var goalDate: LocalDate?
    public var weeklyHistory: [WeekSummary]

    public init(
        initialTdeeGuess: Double,
        currentWeightKg: Double,
        earliestKnownWeightKg: Double,
        currentTdee: Double,
        targetDailyDeficit: Double,
        recommendedCalories: Double,
        recommendedCaloriesFloorApplied: Bool,
        weeksToGoal: Double?,
        goalDate: LocalDate?,
        weeklyHistory: [WeekSummary]
    ) {
        self.initialTdeeGuess = initialTdeeGuess
        self.currentWeightKg = currentWeightKg
        self.earliestKnownWeightKg = earliestKnownWeightKg
        self.currentTdee = currentTdee
        self.targetDailyDeficit = targetDailyDeficit
        self.recommendedCalories = recommendedCalories
        self.recommendedCaloriesFloorApplied = recommendedCaloriesFloorApplied
        self.weeksToGoal = weeksToGoal
        self.goalDate = goalDate
        self.weeklyHistory = weeklyHistory
    }
}

public struct CalorieResolution: Equatable, Sendable {
    public var calories: Double
    /// True if `calories` was raised up to the safety floor - only ever happens for a quick-pick
    /// estimate, never for an exact manual entry (see `belowFloor`).
    public var floorApplied: Bool
    /// True if `calories` sits below the safety floor for this person's sex. Quick-pick estimates
    /// are always clamped so this is never true for them; an exact entry is kept exactly as typed
    /// even when it's below the floor, so the UI just shows a caution instead.
    public var belowFloor: Bool

    public init(calories: Double, floorApplied: Bool, belowFloor: Bool = false) {
        self.calories = calories
        self.floorApplied = floorApplied
        self.belowFloor = belowFloor
    }
}
