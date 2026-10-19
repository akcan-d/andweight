package com.andweight.app.domain

import java.time.DayOfWeek

/** All weights are stored and computed internally in kilograms; this is only for display/input conversion. */
enum class WeightUnit {
    KG,
    LB;

    fun toKg(value: Double): Double = if (this == KG) value else value / 2.20462
    fun fromKg(kg: Double): Double = if (this == KG) kg else kg * 2.20462

    /** There's deliberately no separate height-unit setting - picking Kg or Lb doubles as the
     * app's one Metric/Imperial choice, and height follows it (cm for Kg, feet/inches for Lb). */
    val isMetric: Boolean
        get() = this == KG

    /** No separate first-day-of-week setting yet either - it follows the same Metric/Imperial
     * choice (Monday-start is the common convention in most metric-using countries, Sunday-start
     * in the US, which is also where "Imperial" units are otherwise still in everyday use). */
    val firstDayOfWeek: DayOfWeek
        get() = if (isMetric) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
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
enum class IntakeLevel(val offsetKcal: Double, val label: String, val shortLabel: String, val direction: Int, val intensity: Int) {
    STARVING(-750.0, "Much less than usual", "Much less", -1, 2),
    LESS_THAN_NORMAL(-350.0, "A bit less than usual", "A bit less", -1, 1),
    AVERAGE(0.0, "About my usual amount", "Usual", 0, 0),
    MORE_THAN_AVERAGE(350.0, "A bit more than usual", "A bit more", 1, 1),
    BURSTED(750.0, "Much more than usual", "Much more", 1, 2),
}
