// Raw values throughout this package are the Kotlin enum constant names on purpose: they're what
// the Android app persists and writes into backup files, so keeping them identical means a backup
// exported on one platform restores on the other.

/// All weights are stored and computed internally in kilograms; this is only for display/input
/// conversion. `.st` (stones) shows as a plain decimal number, same as kg/lb - not the traditional
/// "X st Y lb" compound notation (see the Android `Units.kt` for why).
public enum WeightUnit: String, CaseIterable, Sendable {
    case kg = "KG"
    case lb = "LB"
    case st = "ST"

    public func toKg(_ value: Double) -> Double {
        switch self {
        case .kg: value
        case .lb: value / 2.20462
        case .st: value / 0.157473
        }
    }

    public func fromKg(_ kg: Double) -> Double {
        switch self {
        case .kg: kg
        case .lb: kg * 2.20462
        case .st: kg * 0.157473
        }
    }

    /// There's deliberately no separate height-unit setting - picking a weight unit doubles as the
    /// app's one Metric/Imperial choice, and height follows it (cm for kg, feet/inches for lb/st).
    public var isMetric: Bool { self == .kg }
}

/// All heights are stored and computed internally in centimeters; this is only for display/input
/// conversion, mirroring how `WeightUnit` handles kilograms.
public enum HeightConversion {
    private static let cmPerInch = 2.54

    public static func cmToInches(_ cm: Double) -> Double { cm / cmPerInch }
    public static func inchesToCm(_ inches: Double) -> Double { inches * cmPerInch }
    public static func feetInchesToCm(feet: Double, inches: Double) -> Double { inchesToCm(feet * 12.0 + inches) }

    /// Splits a height in cm into whole feet plus remaining inches, for display in an imperial field pair.
    public static func cmToFeetAndInches(_ cm: Double) -> (feet: Int, inches: Double) {
        let totalInches = cmToInches(cm)
        let feet = Int(totalInches / 12.0)
        return (feet, totalInches - Double(feet) * 12.0)
    }
}

public enum Sex: String, CaseIterable, Sendable {
    case male = "MALE"
    case female = "FEMALE"
    case unspecified = "UNSPECIFIED"
}

public enum ActivityLevel: String, CaseIterable, Sendable {
    case sedentary = "SEDENTARY"
    case light = "LIGHT"
    case moderate = "MODERATE"
    case active = "ACTIVE"
    case veryActive = "VERY_ACTIVE"

    public var multiplier: Double {
        switch self {
        case .sedentary: 1.2
        case .light: 1.375
        case .moderate: 1.55
        case .active: 1.725
        case .veryActive: 1.9
        }
    }

    public var label: String {
        switch self {
        case .sedentary: "Sedentary (little/no exercise)"
        case .light: "Lightly active (1-3 days/week)"
        case .moderate: "Moderately active (3-5 days/week)"
        case .active: "Active (6-7 days/week)"
        case .veryActive: "Very active (physical job/2x training)"
        }
    }
}

public enum GoalDirection: String, CaseIterable, Sendable {
    case lose = "LOSE"
    case gain = "GAIN"
    case maintain = "MAINTAIN"
}

/// Constants ported directly from the "TDEE 3.0" spreadsheet's unit-conversion table (AQ/AR columns).
public enum EnergyConstants {
    /// Energy value of 1 kg of body-weight change, i.e. 3500 kcal/lb converted to kcal/kg.
    public static let kcalPerKgBodyweight = 3500.0 * 2.20462

    /// Crude initial maintenance-calorie estimate: ~13 kcal per lb of body weight, in kcal/kg.
    public static let maintenanceKcalPerKg = 13.0 * 2.20462
}

public enum SafetyLimits {
    public static let femaleFloorKcal = 1200.0
    public static let maleFloorKcal = 1500.0

    /// Sex is optional and defaults to unspecified, so that default has to be a real floor on its
    /// own rather than silently assuming either sex - 1200 is the generally-cited minimum
    /// regardless of sex. It only rises to 1500 once someone explicitly says they're male.
    public static func floor(for sex: Sex) -> Double {
        switch sex {
        case .male: maleFloorKcal
        case .female, .unspecified: femaleFloorKcal
        }
    }
}

/// The five quick-pick meal answers. Raw values are persisted (see the note at the top of this
/// file), so they stay fixed even as the user-facing label wording is refined.
///
/// `direction` is -1/0/+1 (less/usual/more) and `intensity` is 0-2 (how far from usual) - plain
/// data so the UI can derive an icon and a neutral (non-judgmental) visual weight.
///
/// `offsetPercent` is a fraction of the person's own current TDEE estimate, not a flat kcal number,
/// so it scales with the person it's applied to.
public enum IntakeLevel: String, CaseIterable, Sendable {
    case starving = "STARVING"
    case lessThanNormal = "LESS_THAN_NORMAL"
    case average = "AVERAGE"
    case moreThanAverage = "MORE_THAN_AVERAGE"
    case bursted = "BURSTED"

    public var offsetPercent: Double {
        switch self {
        case .starving: -0.40
        case .lessThanNormal: -0.18
        case .average: 0.0
        case .moreThanAverage: 0.18
        case .bursted: 0.40
        }
    }

    public var label: String {
        switch self {
        case .starving: "Much less than usual"
        case .lessThanNormal: "A bit less than usual"
        case .average: "About my usual amount"
        case .moreThanAverage: "A bit more than usual"
        case .bursted: "Much more than usual"
        }
    }

    public var shortLabel: String {
        switch self {
        case .starving: "Much less"
        case .lessThanNormal: "A bit less"
        case .average: "Usual"
        case .moreThanAverage: "A bit more"
        case .bursted: "Much more"
        }
    }

    public var direction: Int {
        switch self {
        case .starving, .lessThanNormal: -1
        case .average: 0
        case .moreThanAverage, .bursted: 1
        }
    }

    public var intensity: Int {
        switch self {
        case .starving, .bursted: 2
        case .lessThanNormal, .moreThanAverage: 1
        case .average: 0
        }
    }
}
