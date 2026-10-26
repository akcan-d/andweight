/// General, population-level findings from published weight research - not personalized
/// predictions or guarantees. Percentage-based facts are gated on the percentage of *starting* body
/// weight changed, matching how the cited studies actually measured their results.
///
/// Every percentage tier is also capped to what's reachable within the person's *own stated goal* -
/// a fact that would require going past their goal is left out entirely rather than shown as a
/// locked target, since this app should never imply "keep going past what you actually want".
public struct HealthBenefit: Equatable, Sendable {
    public var title: String
    public var body: String
    public var source: String
    public var achieved: Bool
    /// How much more change it takes to unlock this one - nil once `achieved`.
    public var hint: String?
}

public enum HealthBenefits {

    private struct Tier {
        let thresholdPercent: Double
        let title: String
        let body: String
        let source: String
    }

    private static let loseTiers = [
        Tier(
            thresholdPercent: 5.0,
            title: "Blood pressure",
            body: "Losing 5-10% of body weight is linked to about a 5 mmHg drop in blood pressure, on average.",
            source: "Ma et al., Diabetes Care (American Diabetes Association), 2011"
        ),
        Tier(
            thresholdPercent: 5.0,
            title: "Cholesterol & triglycerides",
            body: "Losing 5-10% of body weight is linked to roughly a 5-point rise in HDL (\"good\") " +
                "cholesterol and about a 40 mg/dL drop in triglycerides, on average.",
            source: "Ma et al., Diabetes Care (American Diabetes Association), 2011"
        ),
        Tier(
            thresholdPercent: 7.0,
            title: "Type 2 diabetes risk",
            body: "In the NIH's Diabetes Prevention Program, people who lost about 7% of their body " +
                "weight and stayed active cut their risk of developing type 2 diabetes by 58% over 3 years.",
            source: "Diabetes Prevention Program, New England Journal of Medicine, 2002"
        ),
        Tier(
            thresholdPercent: 10.0,
            title: "Sleep apnea",
            body: "In people with obstructive sleep apnea, losing 10% or more of body weight was linked " +
                "to roughly a 49% reduction in breathing interruptions during sleep, on average.",
            source: "Systematic review & meta-analysis, Sleep Medicine, 2024; Sleep AHEAD trial"
        ),
        Tier(
            thresholdPercent: 10.0,
            title: "Broader health markers",
            body: "Research reviews describe 10% or more body weight loss as \"disease-modifying\" for " +
                "several weight-related conditions, with benefits continuing to build up to around 15%.",
            source: "Ryan & Yockey, Current Obesity Reports, 2017"
        ),
    ]

    private static let gainTiers = [
        Tier(
            thresholdPercent: 5.0,
            title: "Bone density",
            body: "In a large cohort of women who were underweight in their 20s, those who later gained " +
                "weight had a markedly lower rate of low bone density (osteopenia) after midlife than " +
                "those who stayed underweight.",
            source: "The KOBE Study, Journal of Epidemiology, 2016"
        ),
        Tier(
            thresholdPercent: 5.0,
            title: "Immune function",
            body: "Being underweight is linked to reduced production of white blood cells and antibodies; " +
                "restoring adequate body weight and protein intake supports a healthier immune response, " +
                "per nutrition research reviews.",
            source: "Nutritional immunology review, Journal of Nutrition"
        ),
        Tier(
            thresholdPercent: 10.0,
            title: "Fertility & menstrual health",
            body: "Being underweight is linked to a higher risk of irregular or absent periods and " +
                "fertility difficulties - restoring body weight is the recommended first step in treating " +
                "weight-related menstrual irregularity, per endocrine clinical guidelines.",
            source: "Gordon et al., Endocrine Society Clinical Practice Guideline, Journal of Clinical " +
                "Endocrinology & Metabolism, 2017"
        ),
    ]

    private static let kneeLoadMultiplier = 4.0
    private static let kneeSource = "Messier et al., Arthritis & Rheumatism, 2005 (Wake Forest University)"

    private static let underweightBmiThreshold = 18.5
    private static let mortalitySource = "Global BMI Mortality Collaboration, The Lancet, 2016"

    public static func compute(profile: UserProfile, state: TdeeState) -> [HealthBenefit] {
        let direction = profile.goalDirection
        let startWeightKg = state.earliestKnownWeightKg
        if direction == .maintain || startWeightKg <= 0.0 { return [] }

        let isLosing = direction == .lose
        let changeKg = max(isLosing ? startWeightKg - state.currentWeightKg : state.currentWeightKg - startWeightKg, 0.0)
        let goalChangeKg = max(isLosing ? startWeightKg - profile.goalWeightKg : profile.goalWeightKg - startWeightKg, 0.0)
        let percentChanged = changeKg / startWeightKg * 100.0
        let goalPercent = goalChangeKg / startWeightKg * 100.0

        let unit = profile.weightUnit
        let unitLabel = unit.isMetric ? "kg" : "lb"

        let reachableTiers = (isLosing ? loseTiers : gainTiers).filter { $0.thresholdPercent <= goalPercent }
        let percentFacts = reachableTiers.map { tier in
            let achieved = percentChanged >= tier.thresholdPercent
            var hint: String?
            if !achieved {
                let neededKg = max(startWeightKg * tier.thresholdPercent / 100.0 - changeKg, 0.0)
                hint = "Unlocks at \(Int(tier.thresholdPercent))% of your starting weight - about " +
                    "\(unit.fromKg(neededKg).trimmedDecimalString(places: 1)) \(unitLabel) more to go"
            }
            return HealthBenefit(title: tier.title, body: tier.body, source: tier.source, achieved: achieved, hint: hint)
        }

        let contextFacts = isLosing
            ? [kneeLoadFact(profile: profile, lostKg: changeKg)]
            : [underweightMortalityFact(profile: profile, startWeightKg: startWeightKg)].compactMap { $0 }

        return contextFacts + percentFacts
    }

    // The knee-load finding is a flat rate rather than a percentage threshold, so it unlocks as soon
    // as any weight has actually been lost - not capped to the goal, since it only describes change
    // already achieved.
    private static func kneeLoadFact(profile: UserProfile, lostKg: Double) -> HealthBenefit {
        let unit = profile.weightUnit
        let unitLabel = unit.isMetric ? "kg" : "lb"
        let achieved = lostKg > 0.05
        let lostInUnit = unit.fromKg(lostKg)
        return HealthBenefit(
            title: "Knee joint load",
            body: "Research on knee osteoarthritis found that each \(unitLabel) of weight lost was linked " +
                "to roughly \(Int(kneeLoadMultiplier)) \(unitLabel) less load on the knees with every " +
                "step. You've lost \(lostInUnit.trimmedDecimalString(places: 1)) \(unitLabel) so far - roughly " +
                "\((lostInUnit * kneeLoadMultiplier).trimmedDecimalString(places: 1)) \(unitLabel) less load per step.",
            source: kneeSource,
            achieved: achieved,
            hint: achieved ? nil : "Unlocks as soon as you log any weight lost"
        )
    }

    // Only shown at all if the person's starting BMI was actually in the underweight range; this
    // describes their starting situation rather than a target, so there's nothing to "unlock".
    private static func underweightMortalityFact(profile: UserProfile, startWeightKg: Double) -> HealthBenefit? {
        guard let heightCm = profile.heightCm else { return nil }
        let heightM = heightCm / 100.0
        let bmi = startWeightKg / (heightM * heightM)
        if bmi >= underweightBmiThreshold { return nil }

        return HealthBenefit(
            title: "Why gaining matters",
            body: "Large population studies link being underweight (BMI under 18.5) to a meaningfully " +
                "higher risk of early death compared to being at a healthy weight - working back toward " +
                "a healthier weight addresses that risk directly.",
            source: mortalitySource,
            achieved: true
        )
    }
}
