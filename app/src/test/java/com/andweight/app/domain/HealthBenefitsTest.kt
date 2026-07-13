package com.andweight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HealthBenefitsTest {

    private val profile = UserProfile(
        weightUnit = WeightUnit.KG,
        startWeightKg = 100.0,
        goalWeightKg = 80.0,
        pace = PaceOption.MODERATE,
    )

    private fun stateAt(currentWeightKg: Double): TdeeState =
        TdeeEngine.compute(profile, listOf(DailyLog(LocalDate.of(2026, 1, 1), currentWeightKg, 2000.0)), LocalDate.of(2026, 1, 1))

    @Test
    fun `nothing unlocked yet and next hint points at the first 5 percent tier`() {
        val result = HealthBenefits.compute(profile, stateAt(100.0))
        assertTrue(result.achieved.isEmpty())
        assertEquals("Blood pressure", result.next?.title)
        assertTrue(result.nextHint!!.contains("5%"))
    }

    @Test
    fun `5 percent lost unlocks blood pressure and cholesterol facts`() {
        val result = HealthBenefits.compute(profile, stateAt(95.0)) // exactly 5% of 100kg
        val titles = result.achieved.map { it.title }
        assertTrue(titles.contains("Blood pressure"))
        assertTrue(titles.contains("Cholesterol & triglycerides"))
        assertEquals("Type 2 diabetes risk", result.next?.title)
    }

    @Test
    fun `10 percent lost unlocks sleep apnea and broader markers facts`() {
        val result = HealthBenefits.compute(profile, stateAt(90.0))
        val titles = result.achieved.map { it.title }
        assertTrue(titles.contains("Sleep apnea"))
        assertTrue(titles.contains("Broader health markers"))
        assertNull(result.next) // all percent-based tiers unlocked
    }

    @Test
    fun `knee load fact appears with a live number once any weight is lost`() {
        val result = HealthBenefits.compute(profile, stateAt(97.0)) // 3 kg lost, below the 5% tier
        val kneeFact = result.achieved.single { it.title == "Knee joint load" }
        assertTrue(kneeFact.body.contains("3 kg"))
        assertTrue(kneeFact.body.contains("12 kg")) // 3 kg * 4
    }

    @Test
    fun `no facts at all before any weight is lost`() {
        val result = HealthBenefits.compute(profile, stateAt(100.0))
        assertTrue(result.achieved.none { it.title == "Knee joint load" })
    }

    @Test
    fun `hidden entirely for a maintain goal`() {
        val maintainProfile = profile.copy(goalWeightKg = profile.startWeightKg, pace = null)
        val result = HealthBenefits.compute(maintainProfile, TdeeEngine.compute(maintainProfile, emptyList(), LocalDate.of(2026, 1, 1)))
        assertTrue(result.achieved.isEmpty())
        assertNull(result.next)
    }

    @Test
    fun `hidden entirely for a gain goal`() {
        val gainProfile = profile.copy(goalWeightKg = profile.startWeightKg + 10.0, pace = PaceOption.MODERATE)
        val result = HealthBenefits.compute(gainProfile, TdeeEngine.compute(gainProfile, emptyList(), LocalDate.of(2026, 1, 1)))
        assertTrue(result.achieved.isEmpty())
        assertNull(result.next)
    }

    @Test
    fun `imperial units are used for the knee fact display`() {
        val lbProfile = profile.copy(weightUnit = WeightUnit.LB)
        val state = TdeeEngine.compute(lbProfile, listOf(DailyLog(LocalDate.of(2026, 1, 1), 97.0, 2000.0)), LocalDate.of(2026, 1, 1))
        val result = HealthBenefits.compute(lbProfile, state)
        val kneeFact = result.achieved.single { it.title == "Knee joint load" }
        assertTrue(kneeFact.body.contains("lb"))
    }
}
