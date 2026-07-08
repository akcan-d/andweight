package com.andweight.app.domain

/** All weights are stored and computed internally in kilograms; this is only for display/input conversion. */
enum class WeightUnit {
    KG,
    LB;

    fun toKg(value: Double): Double = if (this == KG) value else value / 2.20462
    fun fromKg(kg: Double): Double = if (this == KG) kg else kg * 2.20462
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

    /** When sex is unspecified we use the more conservative (higher) floor. */
    fun floorFor(sex: Sex): Double = when (sex) {
        Sex.MALE -> MALE_FLOOR_KCAL
        Sex.FEMALE -> FEMALE_FLOOR_KCAL
        Sex.UNSPECIFIED -> MALE_FLOOR_KCAL
    }
}

enum class IntakeLevel(val offsetKcal: Double, val label: String) {
    STARVING(-750.0, "I was starving"),
    LESS_THAN_NORMAL(-350.0, "I ate less than normal"),
    AVERAGE(0.0, "I ate an average amount"),
    MORE_THAN_AVERAGE(350.0, "I ate more than average"),
    BURSTED(750.0, "I almost bursted"),
}
