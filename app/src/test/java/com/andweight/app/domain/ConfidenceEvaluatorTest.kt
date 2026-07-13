package com.andweight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ConfidenceEvaluatorTest {

    private val today = LocalDate.of(2026, 7, 10)

    private val profile = UserProfile(
        weightUnit = WeightUnit.KG,
        startWeightKg = 90.0,
        goalWeightKg = 80.0,
        pace = PaceOption.MODERATE,
    )

    private fun emptyState() = TdeeEngine.compute(profile, emptyList(), today)

    @Test
    fun `no profile details and no logging history is low accuracy`() {
        val assessment = ConfidenceEvaluator.assess(profile, emptyState(), emptyList(), today)
        assertEquals(ConfidenceLevel.LOW, assessment.level)
        assertTrue(assessment.promptProfileDetails)
    }

    @Test
    fun `sex unspecified but height and age present is still medium - matches the reported bug`() {
        // Exactly the reported scenario: sex left as "prefer not to say" but height + age filled in.
        val partialProfile = profile.copy(sex = Sex.UNSPECIFIED, heightCm = 170.0, age = 40)
        val assessment = ConfidenceEvaluator.assess(partialProfile, emptyState(), emptyList(), today)
        assertEquals(ConfidenceLevel.MEDIUM, assessment.level)
        assertTrue(assessment.promptProfileDetails)
    }

    @Test
    fun `only one of three details present stays low accuracy`() {
        val partialProfile = profile.copy(sex = Sex.UNSPECIFIED, heightCm = 170.0, age = null)
        val assessment = ConfidenceEvaluator.assess(partialProfile, emptyState(), emptyList(), today)
        assertEquals(ConfidenceLevel.LOW, assessment.level)
    }

    @Test
    fun `all three details present is medium without the prompt-for-more nudge`() {
        val fullProfile = profile.copy(sex = Sex.FEMALE, heightCm = 170.0, age = 40)
        val assessment = ConfidenceEvaluator.assess(fullProfile, emptyState(), emptyList(), today)
        assertEquals(ConfidenceLevel.MEDIUM, assessment.level)
        assertTrue(!assessment.promptProfileDetails)
    }

    @Test
    fun `real logging history takes priority over profile detail completeness`() {
        val logs = (0 until 14).map { offset ->
            DailyLog(today.minusDays((13 - offset).toLong()), weightKg = 90.0 - offset * 0.1, calories = 2000.0)
        }
        val state = TdeeEngine.compute(profile, logs, today)
        val assessment = ConfidenceEvaluator.assess(profile.copy(sex = Sex.UNSPECIFIED), state, logs, today)
        assertEquals(ConfidenceLevel.MEDIUM, assessment.level)
    }

    @Test
    fun `body fat percent alone is enough for medium accuracy, no other details needed`() {
        val bodyFatProfile = profile.copy(bodyFatPercent = 25.0, sex = Sex.UNSPECIFIED, heightCm = null, age = null)
        val assessment = ConfidenceEvaluator.assess(bodyFatProfile, emptyState(), emptyList(), today)
        assertEquals(ConfidenceLevel.MEDIUM, assessment.level)
        assertTrue(!assessment.promptProfileDetails)
    }
}
