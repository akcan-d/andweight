package com.andweight.app.domain

/**
 * General, population-level findings from published weight-loss research - not personalized
 * predictions or guarantees. Each fact is gated on the percentage of *starting* body weight lost,
 * matching how the cited studies actually measured their results (a 5 kg loss means something very
 * different for a 60 kg person than a 150 kg person, so percentage - not an absolute amount - is
 * what the research tracks). Only shown for a weight-loss goal; none of this research is about
 * weight gain. See each fact's [HealthBenefit.source] for the underlying study or review.
 */
data class HealthBenefit(
    val title: String,
    val body: String,
    val source: String,
)

data class HealthBenefitsResult(
    val achieved: List<HealthBenefit>,
    /** The next fact still to unlock, with a hint of how much more weight loss it takes - null once
     * everything is unlocked. */
    val next: HealthBenefit?,
    val nextHint: String?,
)

object HealthBenefits {

    private data class Tier(val thresholdPercent: Double, val fact: HealthBenefit)

    private val TIERS = listOf(
        Tier(
            5.0,
            HealthBenefit(
                title = "Blood pressure",
                body = "Losing 5-10% of body weight is linked to about a 5 mmHg drop in blood pressure, on average.",
                source = "Ma et al., Diabetes Care (American Diabetes Association), 2011",
            ),
        ),
        Tier(
            5.0,
            HealthBenefit(
                title = "Cholesterol & triglycerides",
                body = "Losing 5-10% of body weight is linked to roughly a 5-point rise in HDL (\"good\") " +
                    "cholesterol and about a 40 mg/dL drop in triglycerides, on average.",
                source = "Ma et al., Diabetes Care (American Diabetes Association), 2011",
            ),
        ),
        Tier(
            7.0,
            HealthBenefit(
                title = "Type 2 diabetes risk",
                body = "In the NIH's Diabetes Prevention Program, people who lost about 7% of their body " +
                    "weight and stayed active cut their risk of developing type 2 diabetes by 58% over 3 years.",
                source = "Diabetes Prevention Program, New England Journal of Medicine, 2002",
            ),
        ),
        Tier(
            10.0,
            HealthBenefit(
                title = "Sleep apnea",
                body = "In people with obstructive sleep apnea, losing 10% or more of body weight was linked " +
                    "to roughly a 49% reduction in breathing interruptions during sleep, on average.",
                source = "Systematic review & meta-analysis, Sleep Medicine, 2024; Sleep AHEAD trial",
            ),
        ),
        Tier(
            10.0,
            HealthBenefit(
                title = "Broader health markers",
                body = "Research reviews describe 10% or more body weight loss as \"disease-modifying\" for " +
                    "several weight-related conditions, with benefits continuing to build up to around 15%.",
                source = "Ryan & Yockey, Current Obesity Reports, 2017",
            ),
        ),
    )

    private const val KNEE_LOAD_MULTIPLIER = 4.0
    private const val KNEE_SOURCE = "Messier et al., Arthritis & Rheumatism, 2005 (Wake Forest University)"

    fun compute(profile: UserProfile, state: TdeeState): HealthBenefitsResult {
        if (profile.goalDirection != GoalDirection.LOSE || profile.startWeightKg <= 0.0) {
            return HealthBenefitsResult(emptyList(), null, null)
        }

        val lostKg = (profile.startWeightKg - state.currentWeightKg).coerceAtLeast(0.0)
        val percentLost = (lostKg / profile.startWeightKg) * 100.0

        val achieved = TIERS.filter { percentLost >= it.thresholdPercent }.map { it.fact }.toMutableList()
        val nextTier = TIERS.filter { percentLost < it.thresholdPercent }.minByOrNull { it.thresholdPercent }

        // The knee-load finding is a flat rate rather than a threshold, so it's shown as soon as any
        // weight has actually been lost, with the numbers scaled to what's really been logged.
        if (lostKg > 0.05) {
            val unit = profile.weightUnit
            val lostDisplay = formatOneDecimal(unit.fromKg(lostKg))
            val loadDisplay = formatOneDecimal(unit.fromKg(lostKg) * KNEE_LOAD_MULTIPLIER)
            val unitLabel = if (unit.isMetric) "kg" else "lb"
            achieved.add(
                HealthBenefit(
                    title = "Knee joint load",
                    body = "Research on knee osteoarthritis found that each $unitLabel of weight lost was " +
                        "linked to roughly ${KNEE_LOAD_MULTIPLIER.toInt()} $unitLabel less load on the knees " +
                        "with every step. You've lost $lostDisplay $unitLabel so far - roughly $loadDisplay " +
                        "$unitLabel less load per step.",
                    source = KNEE_SOURCE,
                ),
            )
        }

        val nextHint = nextTier?.let {
            val neededKg = (profile.startWeightKg * it.thresholdPercent / 100.0) - lostKg
            val unit = profile.weightUnit
            val neededDisplay = formatOneDecimal(unit.fromKg(neededKg.coerceAtLeast(0.0)))
            val unitLabel = if (unit.isMetric) "kg" else "lb"
            "Unlocks at ${it.thresholdPercent.toInt()}% of your starting weight - about $neededDisplay $unitLabel more to go"
        }

        return HealthBenefitsResult(achieved, nextTier?.fact, nextHint)
    }

    private fun formatOneDecimal(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
