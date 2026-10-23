package com.measured.app.domain

/** All weights are stored and computed internally in kilograms; this is only for display/input
 * conversion. [ST] (stones) shows as a plain decimal number, same as [KG]/[LB] - not the
 * traditional "X st Y lb" compound notation, since every other weight display in the app (charts,
 * milestones, widgets, the backup format) already works generically off a single [fromKg] number
 * per unit, and a compound display would mean reworking all of them just for this one unit. */
enum class WeightUnit {
    KG,
    LB,
    ST;

    fun toKg(value: Double): Double = when (this) {
        KG -> value
        LB -> value / 2.20462
        ST -> value / 0.157473
    }

    fun fromKg(kg: Double): Double = when (this) {
        KG -> kg
        LB -> kg * 2.20462
        ST -> kg * 0.157473
    }

    /** There's deliberately no separate height-unit setting - picking a weight unit doubles as the
     * app's one Metric/Imperial choice, and height follows it (cm for Kg, feet/inches for Lb/St). */
    val isMetric: Boolean
        get() = this == KG
}

/** All heights are stored and computed internally in centimeters; this is only for display/input
 * conversion, mirroring how [WeightUnit] handles kilograms. */
object HeightConversion {
    private const val CM_PER_INCH = 2.54

    fun cmToInches(cm: Double): Double = cm / CM_PER_INCH
    fun inchesToCm(inches: Double): Double = inches * CM_PER_INCH
    fun feetInchesToCm(feet: Double, inches: Double): Double = inchesToCm(feet * 12.0 + inches)

    /** Splits a height in cm into whole feet plus remaining inches, for display in an imperial field pair. */
    fun cmToFeetAndInches(cm: Double): Pair<Int, Double> {
        val totalInches = cmToInches(cm)
        val feet = (totalInches / 12.0).toInt()
        val remainingInches = totalInches - feet * 12.0
        return feet to remainingInches
    }
}

enum class Sex {
    MALE,
    FEMALE,
    UNSPECIFIED,
}

enum class ActivityLevel(val multiplier: Double, val label: String) {
    SEDENTARY(1.2, "Sedentary (little/no exercise)"),
    LIGHT(1.375, "Lightly active (1-3 days/week)"),
    MODERATE(1.55, "Moderately active (3-5 days/week)"),
    ACTIVE(1.725, "Active (6-7 days/week)"),
    VERY_ACTIVE(1.9, "Very active (physical job/2x training)"),
}

enum class GoalDirection {
    LOSE,
    GAIN,
    MAINTAIN,
}

/** Constants ported directly from the "TDEE 3.0" spreadsheet's unit-conversion table (AQ/AR columns). */
object EnergyConstants {
    /** Energy value of 1 kg of body-weight change, i.e. 3500 kcal/lb converted to kcal/kg. */
    const val KCAL_PER_KG_BODYWEIGHT = 3500.0 * 2.20462

    /** Crude initial maintenance-calorie estimate: ~13 kcal per lb of body weight, in kcal/kg. */
    const val MAINTENANCE_KCAL_PER_KG = 13.0 * 2.20462
}

object SafetyLimits {
    const val FEMALE_FLOOR_KCAL = 1200.0
    const val MALE_FLOOR_KCAL = 1500.0

    /** Sex is optional and defaults to unspecified, so that default has to be a real floor on
     * its own rather than silently assuming either sex - 1200 is the generally-cited minimum
     * regardless of sex, matching what onboarding tells the person before they've said anything
     * about sex at all. It only rises to 1500 once they explicitly say they're male. */
    fun floorFor(sex: Sex): Double = when (sex) {
        Sex.MALE -> MALE_FLOOR_KCAL
        Sex.FEMALE -> FEMALE_FLOOR_KCAL
        Sex.UNSPECIFIED -> FEMALE_FLOOR_KCAL
    }
}

// Enum constant names are persisted in the database (see DailyLogEntity.intakeLevel), so they
// stay fixed even as the user-facing label wording is refined - renaming a constant here would
// orphan any already-saved records referencing the old name.
//
// [direction] is -1/0/+1 (less/usual/more) and [intensity] is 0-2 (how far from usual) - both
// plain data so the UI layer can derive an icon and a neutral (non-judgmental - this app
// deliberately doesn't code "ate more" as bad) visual weight without a parallel when-chain.
//
// [offsetPercent] is a fraction of the person's own current TDEE estimate, not a flat kcal
// number - a flat +/-750 kcal "much more/less" would be a huge swing for someone with a 1500 kcal
// TDEE and a modest one for someone at 3500, so this scales with the person it's being applied to
// instead of meaning something different for everyone.
enum class IntakeLevel(val offsetPercent: Double, val label: String, val shortLabel: String, val direction: Int, val intensity: Int) {
    STARVING(-0.40, "Much less than usual", "Much less", -1, 2),
    LESS_THAN_NORMAL(-0.18, "A bit less than usual", "A bit less", -1, 1),
    AVERAGE(0.0, "About my usual amount", "Usual", 0, 0),
    MORE_THAN_AVERAGE(0.18, "A bit more than usual", "A bit more", 1, 1),
    BURSTED(0.40, "Much more than usual", "Much more", 1, 2),
}
