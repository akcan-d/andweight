package com.andweight.app.domain

/**
 * General, population-level findings from published weight-loss research - not personalized
 * predictions or guarantees. Each fact is gated on the percentage of *starting* body weight lost,
 * matching how the cited studies actually measured their results (a 5 kg loss means something very
 * different for a 60 kg person than a 150 kg person, so percentage - not an absolute amount - is
 * what the research tracks). Only shown for a weight-loss goal; none of this research is about
 * weight gain. See each fact's [source] for the underlying study or review.
 */
data class HealthBenefit(
    val title: String,
    val body: String,
    val source: String,
    val achieved: Boolean,
    /** How much more weight loss it takes to unlock this one - null once [achieved]. */
    val hint: String? = null,
)

object HealthBenefits {

    private data class Tier(val thresholdPercent: Double, val title: String, val body: String, val source: String)

    private val TIERS = listOf(
        Tier(
            5.0,
            title = "Blood pressure",
            body = "Losing 5-10% of body weight is linked to about a 5 mmHg drop in blood pressure, on average.",
            source = "Ma et al., Diabetes Care (American Diabetes Association), 2011",
        ),
        Tier(
            5.0,
            title = "Cholesterol & triglycerides",
            body = "Losing 5-10% of body weight is linked to roughly a 5-point rise in HDL (\"good\") " +
                "cholesterol and about a 40 mg/dL drop in triglycerides, on average.",
            source = "Ma et al., Diabetes Care (American Diabetes Association), 2011",
        ),
        Tier(
            7.0,
            title = "Type 2 diabetes risk",
            body = "In the NIH's Diabetes Prevention Program, people who lost about 7% of their body " +
                "weight and stayed active cut their risk of developing type 2 diabetes by 58% over 3 years.",
            source = "Diabetes Prevention Program, New England Journal of Medicine, 2002",
        ),
        Tier(
            10.0,
            title = "Sleep apnea",
            body = "In people with obstructive sleep apnea, losing 10% or more of body weight was linked " +
                "to roughly a 49% reduction in breathing interruptions during sleep, on average.",
            source = "Systematic review & meta-analysis, Sleep Medicine, 2024; Sleep AHEAD trial",
        ),
        Tier(
            10.0,
            title = "Broader health markers",
            body = "Research reviews describe 10% or more body weight loss as \"disease-modifying\" for " +
                "several weight-related conditions, with benefits continuing to build up to around 15%.",
            source = "Ryan & Yockey, Current Obesity Reports, 2017",
        ),
    )

    private const val KNEE_LOAD_MULTIPLIER = 4.0
    private const val KNEE_SOURCE = "Messier et al., Arthritis & Rheumatism, 2005 (Wake Forest University)"

    fun compute(profile: UserProfile, state: TdeeState): List<HealthBenefit> {
        if (profile.goalDirection != GoalDirection.LOSE || profile.startWeightKg <= 0.0) {
            return emptyList()
        }

        val lostKg = (profile.startWeightKg - state.currentWeightKg).coerceAtLeast(0.0)
        val percentLost = (lostKg / profile.startWeightKg) * 100.0
        val unit = profile.weightUnit
        val unitLabel = if (unit.isMetric) "kg" else "lb"

        // The knee-load finding is a flat rate rather than a percentage threshold, so it unlocks as
        // soon as any weight has actually been lost, first in the list since it's the earliest one
        // to reach - with the numbers scaled to what's really been logged once achieved.
        val kneeAchieved = lostKg > 0.05
        val kneeFact = HealthBenefit(
            title = "Knee joint load",
            body = "Research on knee osteoarthritis found that each $unitLabel of weight lost was linked " +
                "to roughly ${KNEE_LOAD_MULTIPLIER.toInt()} $unitLabel less load on the knees with every " +
                "step. You've lost ${formatOneDecimal(unit.fromKg(lostKg))} $unitLabel so far - roughly " +
                "${formatOneDecimal(unit.fromKg(lostKg) * KNEE_LOAD_MULTIPLIER)} $unitLabel less load per step.",
            source = KNEE_SOURCE,
            achieved = kneeAchieved,
            hint = if (kneeAchieved) null else "Unlocks as soon as you log any weight lost",
        )

        val percentFacts = TIERS.map { tier ->
            val achieved = percentLost >= tier.thresholdPercent
            val hint = if (achieved) {
                null
            } else {
                val neededKg = ((profile.startWeightKg * tier.thresholdPercent / 100.0) - lostKg).coerceAtLeast(0.0)
                "Unlocks at ${tier.thresholdPercent.toInt()}% of your starting weight - about " +
                    "${formatOneDecimal(unit.fromKg(neededKg))} $unitLabel more to go"
            }
            HealthBenefit(tier.title, tier.body, tier.source, achieved, hint)
        }

        return listOf(kneeFact) + percentFacts
    }

    private fun formatOneDecimal(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
