package com.andweight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HealthBenefitsTest {

    private val profile = UserProfile(
        weightUnit = WeightUnit.KG,
        startWeightKg = 100.0,
        goalWeightKg = 80.0, // 20% of starting weight - comfortably covers every LOSE tier
        pace = PaceOption.MODERATE,
    )

    private fun stateAt(currentWeightKg: Double, forProfile: UserProfile = profile): TdeeState =
        TdeeEngine.compute(forProfile, listOf(DailyLog(LocalDate.of(2026, 1, 1), currentWeightKg, 2000.0)), LocalDate.of(2026, 1, 1))

    @Test
    fun `nothing unlocked yet - first locked tier has a hint pointing at 5 percent`() {
        val result = HealthBenefits.compute(profile, stateAt(100.0))
        assertTrue(result.none { it.achieved && it.title != "Knee joint load" })
        val bloodPressure = result.single { it.title == "Blood pressure" }
        assertTrue(bloodPressure.hint!!.contains("5%"))
    }

    @Test
    fun `5 percent lost unlocks blood pressure and cholesterol facts`() {
        val result = HealthBenefits.compute(profile, stateAt(95.0)) // exactly 5% of 100kg
        val achievedTitles = result.filter { it.achieved }.map { it.title }
        assertTrue(achievedTitles.contains("Blood pressure"))
        assertTrue(achievedTitles.contains("Cholesterol & triglycerides"))
        assertTrue(result.single { it.title == "Type 2 diabetes risk" }.hint != null)
    }

    @Test
    fun `10 percent lost unlocks sleep apnea and broader markers facts`() {
        val result = HealthBenefits.compute(profile, stateAt(90.0))
        val achievedTitles = result.filter { it.achieved }.map { it.title }
        assertTrue(achievedTitles.contains("Sleep apnea"))
        assertTrue(achievedTitles.contains("Broader health markers"))
        assertTrue(result.all { it.achieved }) // all percent-based tiers + the knee fact unlocked
    }

    @Test
    fun `knee load fact appears with a live number once any weight is lost`() {
        val result = HealthBenefits.compute(profile, stateAt(97.0)) // 3 kg lost, below the 5% tier
        val kneeFact = result.single { it.title == "Knee joint load" }
        assertTrue(kneeFact.achieved)
        assertTrue(kneeFact.body.contains("3 kg"))
        assertTrue(kneeFact.body.contains("12 kg")) // 3 kg * 4
    }

    @Test
    fun `knee fact is locked before any weight is lost`() {
        val result = HealthBenefits.compute(profile, stateAt(100.0))
        val kneeFact = result.single { it.title == "Knee joint load" }
        assertTrue(!kneeFact.achieved)
        assertTrue(kneeFact.hint != null)
    }

    @Test
    fun `tiers beyond the person's own goal are left out entirely, not shown as locked`() {
        // Goal is only 3% of starting weight - below every LOSE tier's threshold (lowest is 5%).
        val modestGoalProfile = profile.copy(goalWeightKg = 97.0)
        val result = HealthBenefits.compute(modestGoalProfile, stateAt(97.0, modestGoalProfile))
        // None of the percent-gated facts should appear at all, locked or achieved.
        val percentFactTitles = setOf("Blood pressure", "Cholesterol & triglycerides", "Type 2 diabetes risk", "Sleep apnea", "Broader health markers")
        assertTrue(result.none { it.title in percentFactTitles })
        // The knee fact is unaffected since it just describes change already made, not a target.
        assertTrue(result.single { it.title == "Knee joint load" }.achieved)
    }

    @Test
    fun `a hint never asks for more than the person's own goal`() {
        // Goal is exactly 6% - reaches the 5% tier but not the 7% one, so the 7% fact should be
        // left out entirely rather than shown as a locked target beyond the goal.
        val sixPercentGoalProfile = profile.copy(goalWeightKg = 94.0)
        val result = HealthBenefits.compute(sixPercentGoalProfile, stateAt(100.0, sixPercentGoalProfile))
        assertTrue(result.none { it.title == "Type 2 diabetes risk" })
        assertTrue(result.any { it.title == "Blood pressure" })
    }

    @Test
    fun `hidden entirely for a maintain goal`() {
        val maintainProfile = profile.copy(goalWeightKg = profile.startWeightKg, pace = null)
        val result = HealthBenefits.compute(maintainProfile, TdeeEngine.compute(maintainProfile, emptyList(), LocalDate.of(2026, 1, 1)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `imperial units are used for the knee fact display`() {
        val lbProfile = profile.copy(weightUnit = WeightUnit.LB)
        val state = TdeeEngine.compute(lbProfile, listOf(DailyLog(LocalDate.of(2026, 1, 1), 97.0, 2000.0)), LocalDate.of(2026, 1, 1))
        val result = HealthBenefits.compute(lbProfile, state)
        val kneeFact = result.single { it.title == "Knee joint load" }
        assertTrue(kneeFact.body.contains("lb"))
    }

    // --- Gain goal ---

    private val gainProfile = profile.copy(startWeightKg = 50.0, goalWeightKg = 60.0, pace = PaceOption.MODERATE) // 20% goal

    @Test
    fun `gain goal unlocks bone density and immune function facts at 5 percent`() {
        val result = HealthBenefits.compute(gainProfile, stateAt(52.5, gainProfile)) // 5% of 50kg
        val achievedTitles = result.filter { it.achieved }.map { it.title }
        assertTrue(achievedTitles.contains("Bone density"))
        assertTrue(achievedTitles.contains("Immune function"))
        assertTrue(result.single { it.title == "Fertility & menstrual health" }.hint != null)
    }

    @Test
    fun `gain goal unlocks fertility fact at 10 percent`() {
        val result = HealthBenefits.compute(gainProfile, stateAt(55.0, gainProfile))
        assertTrue(result.single { it.title == "Fertility & menstrual health" }.achieved)
    }

    @Test
    fun `underweight mortality context fact shown only when starting BMI is under 18_5`() {
        // 50 kg at 175 cm is a BMI of about 16.3 - underweight.
        val underweightProfile = gainProfile.copy(heightCm = 175.0)
        val result = HealthBenefits.compute(underweightProfile, stateAt(50.0, underweightProfile))
        assertTrue(result.single { it.title == "Why gaining matters" }.achieved)
    }

    @Test
    fun `no underweight context fact when starting BMI is not underweight`() {
        // 50 kg at 150 cm is a BMI of about 22.2 - not underweight.
        val normalWeightProfile = gainProfile.copy(heightCm = 150.0)
        val result = HealthBenefits.compute(normalWeightProfile, stateAt(50.0, normalWeightProfile))
        assertTrue(result.none { it.title == "Why gaining matters" })
    }

    @Test
    fun `no underweight context fact without a height on file`() {
        val result = HealthBenefits.compute(gainProfile, stateAt(50.0, gainProfile))
        assertTrue(result.none { it.title == "Why gaining matters" })
    }

    @Test
    fun `no knee load fact for a gain goal`() {
        val result = HealthBenefits.compute(gainProfile, stateAt(55.0, gainProfile))
        assertTrue(result.none { it.title == "Knee joint load" })
    }
}
