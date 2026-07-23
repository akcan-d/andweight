package com.andweight.app.domain

/**
 * General, population-level findings from published weight research - not personalized
 * predictions or guarantees. Percentage-based facts are gated on the percentage of *starting* body
 * weight changed, matching how the cited studies actually measured their results (a 5 kg change
 * means something very different for a 60 kg person than a 150 kg person, so percentage - not an
 * absolute amount - is what the research tracks).
 *
 * Every percentage tier is also capped to what's reachable within the person's *own stated goal* -
 * a fact that would require going past their goal to unlock is left out entirely rather than shown
 * as a locked target, since this app should never imply "keep going past what you actually want" to
 * chase a research fact. Someone whose whole goal is a modest 3% of body weight simply won't see
 * facts that need 5%+ - which is also honest, since that research doesn't say much about changes
 * that small anyway.
 *
 * See each fact's [source] for the underlying study, trial, or clinical guideline.
 */
data class HealthBenefit(
    val title: String,
    val body: String,
    val source: String,
    val achieved: Boolean,
    /** How much more change it takes to unlock this one - null once [achieved]. */
    val hint: String? = null,
)

object HealthBenefits {

    private data class Tier(val thresholdPercent: Double, val title: String, val body: String, val source: String)

    private val LOSE_TIERS = listOf(
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

    private val GAIN_TIERS = listOf(
        Tier(
            5.0,
            title = "Bone density",
            body = "In a large cohort of women who were underweight in their 20s, those who later gained " +
                "weight had a markedly lower rate of low bone density (osteopenia) after midlife than " +
                "those who stayed underweight.",
            source = "The KOBE Study, Journal of Epidemiology, 2016",
        ),
        Tier(
            5.0,
            title = "Immune function",
            body = "Being underweight is linked to reduced production of white blood cells and antibodies; " +
                "restoring adequate body weight and protein intake supports a healthier immune response, " +
                "per nutrition research reviews.",
            source = "Nutritional immunology review, Journal of Nutrition",
        ),
        Tier(
            10.0,
            title = "Fertility & menstrual health",
            body = "Being underweight is linked to a higher risk of irregular or absent periods and " +
                "fertility difficulties - restoring body weight is the recommended first step in treating " +
                "weight-related menstrual irregularity, per endocrine clinical guidelines.",
            source = "Gordon et al., Endocrine Society Clinical Practice Guideline, Journal of Clinical " +
                "Endocrinology & Metabolism, 2017",
        ),
    )

    private const val KNEE_LOAD_MULTIPLIER = 4.0
    private const val KNEE_SOURCE = "Messier et al., Arthritis & Rheumatism, 2005 (Wake Forest University)"

    private const val UNDERWEIGHT_BMI_THRESHOLD = 18.5
    private const val MORTALITY_SOURCE = "Global BMI Mortality Collaboration, The Lancet, 2016"

    fun compute(profile: UserProfile, state: TdeeState): List<HealthBenefit> {
        val direction = profile.goalDirection
        val startWeightKg = state.earliestKnownWeightKg
        if (direction == GoalDirection.MAINTAIN || startWeightKg <= 0.0) return emptyList()

        val changeKg = when (direction) {
            GoalDirection.LOSE -> (startWeightKg - state.currentWeightKg).coerceAtLeast(0.0)
            GoalDirection.GAIN -> (state.currentWeightKg - startWeightKg).coerceAtLeast(0.0)
            GoalDirection.MAINTAIN -> 0.0
        }
        val goalChangeKg = when (direction) {
            GoalDirection.LOSE -> (startWeightKg - profile.goalWeightKg).coerceAtLeast(0.0)
            GoalDirection.GAIN -> (profile.goalWeightKg - startWeightKg).coerceAtLeast(0.0)
            GoalDirection.MAINTAIN -> 0.0
        }
        val percentChanged = (changeKg / startWeightKg) * 100.0
        val goalPercent = (goalChangeKg / startWeightKg) * 100.0

        val unit = profile.weightUnit
        val unitLabel = if (unit.isMetric) "kg" else "lb"

        val tiers = if (direction == GoalDirection.LOSE) LOSE_TIERS else GAIN_TIERS
        val reachableTiers = tiers.filter { it.thresholdPercent <= goalPercent }
        val percentFacts = reachableTiers.map { tier ->
            val achieved = percentChanged >= tier.thresholdPercent
            val hint = if (achieved) {
                null
            } else {
                val neededKg = ((startWeightKg * tier.thresholdPercent / 100.0) - changeKg).coerceAtLeast(0.0)
                "Unlocks at ${tier.thresholdPercent.toInt()}% of your starting weight - about " +
                    "${formatOneDecimal(unit.fromKg(neededKg))} $unitLabel more to go"
            }
            HealthBenefit(tier.title, tier.body, tier.source, achieved, hint)
        }

        val contextFacts = when (direction) {
            GoalDirection.LOSE -> listOf(kneeLoadFact(profile, changeKg))
            GoalDirection.GAIN -> listOfNotNull(underweightMortalityFact(profile, startWeightKg))
            GoalDirection.MAINTAIN -> emptyList()
        }

        return contextFacts + percentFacts
    }

    // The knee-load finding is a flat rate rather than a percentage threshold, so it unlocks as
    // soon as any weight has actually been lost - not capped to the goal, since it only describes
    // change already achieved rather than a target to reach.
    private fun kneeLoadFact(profile: UserProfile, lostKg: Double): HealthBenefit {
        val unit = profile.weightUnit
        val unitLabel = if (unit.isMetric) "kg" else "lb"
        val achieved = lostKg > 0.05
        return HealthBenefit(
            title = "Knee joint load",
            body = "Research on knee osteoarthritis found that each $unitLabel of weight lost was linked " +
                "to roughly ${KNEE_LOAD_MULTIPLIER.toInt()} $unitLabel less load on the knees with every " +
                "step. You've lost ${formatOneDecimal(unit.fromKg(lostKg))} $unitLabel so far - roughly " +
                "${formatOneDecimal(unit.fromKg(lostKg) * KNEE_LOAD_MULTIPLIER)} $unitLabel less load per step.",
            source = KNEE_SOURCE,
            achieved = achieved,
            hint = if (achieved) null else "Unlocks as soon as you log any weight lost",
        )
    }

    // Only relevant - and only shown at all - if the person's starting BMI was actually in the
    // underweight range; this describes their starting situation rather than a target, so there's
    // nothing to "unlock" and no goal-size cap needed.
    private fun underweightMortalityFact(profile: UserProfile, startWeightKg: Double): HealthBenefit? {
        val heightCm = profile.heightCm ?: return null
        val heightM = heightCm / 100.0
        val bmi = startWeightKg / (heightM * heightM)
        if (bmi >= UNDERWEIGHT_BMI_THRESHOLD) return null

        return HealthBenefit(
            title = "Why gaining matters",
            body = "Large population studies link being underweight (BMI under 18.5) to a meaningfully " +
                "higher risk of early death compared to being at a healthy weight - working back toward " +
                "a healthier weight addresses that risk directly.",
            source = MORTALITY_SOURCE,
            achieved = true,
        )
    }

    private fun formatOneDecimal(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
