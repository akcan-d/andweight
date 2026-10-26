import XCTest
@testable import MeasuredCore

// Ports of the Android CoachingAdvisorTest, ConfidenceEvaluatorTest and PlateauDetectorTest.

final class CoachingAdvisorTests: XCTestCase {

    private let referenceDate = date(2026, 1, 1)

    // RELAXED at 88 kg (the currentWeightKg used by stateWithWeeks below) gives a weekly rate of
    // 0.528 kg - the ratios used by the individual tests below are chosen relative to that.
    private let loseProfile = UserProfile(weightUnit: .kg, startWeightKg: 90.0, goalWeightKg: 80.0, pace: .relaxed)

    private func weekSummary(_ index: Int, _ deltaKg: Double?) -> WeekSummary {
        WeekSummary(
            weekIndex: index,
            startDate: referenceDate.plusDays((index - 1) * 7),
            endDate: referenceDate.plusDays((index - 1) * 7 + 6),
            hasWeightData: true,
            hasCalorieData: true,
            avgWeightKg: 88.0,
            avgCalories: 2000.0,
            weightDeltaKg: deltaKg,
            tdee: 2200.0
        )
    }

    private func stateWithWeeks(_ weeks: [WeekSummary]) -> TdeeState {
        TdeeState(
            initialTdeeGuess: 2500.0,
            currentWeightKg: 88.0,
            earliestKnownWeightKg: 90.0,
            currentTdee: 2200.0,
            targetDailyDeficit: 500.0,
            recommendedCalories: 1700.0,
            recommendedCaloriesFloorApplied: false,
            weeksToGoal: 10.0,
            goalDate: referenceDate.plusWeeks(10),
            weeklyHistory: weeks
        )
    }

    func testNotEnoughDataYetGivesANeutralEncouragement() {
        let advice = CoachingAdvisor.advise(profile: loseProfile, state: stateWithWeeks([weekSummary(1, -0.5)]))
        XCTAssertEqual(advice.sentiment, .neutral)
    }

    func testLosingRightAroundTheGoalRateIsPositive() {
        let advice = CoachingAdvisor.advise(profile: loseProfile, state: stateWithWeeks([weekSummary(1, -0.5), weekSummary(2, -0.5)]))
        XCTAssertEqual(advice.sentiment, .positive)
    }

    func testBarelyLosingAnyWeightTriggersACautionToEatLess() {
        let advice = CoachingAdvisor.advise(profile: loseProfile, state: stateWithWeeks([weekSummary(1, -0.05), weekSummary(2, -0.05)]))
        XCTAssertEqual(advice.sentiment, .caution)
    }

    func testLosingMuchFasterThanPlannedTriggersACautionToEatMore() {
        let advice = CoachingAdvisor.advise(profile: loseProfile, state: stateWithWeeks([weekSummary(1, -1.5), weekSummary(2, -1.5)]))
        XCTAssertEqual(advice.sentiment, .caution)
    }

    func testMaintainGoalHoldingSteadyIsPositive() {
        let maintain = with(loseProfile) {
            $0.goalWeightKg = $0.startWeightKg
            $0.pace = nil
        }
        let advice = CoachingAdvisor.advise(profile: maintain, state: stateWithWeeks([weekSummary(1, 0.05), weekSummary(2, -0.05)]))
        XCTAssertEqual(advice.sentiment, .positive)
    }

    // Same 0.2 kg/week real trajectory reads as "too slow" under MODERATE and "on track" under
    // RELAXED - the advisor compares against the chosen pace's own target rate.
    func testRealisticSlowBackfillReadsAsBehindAModeratePaceButOnTrackForARelaxedOne() {
        let profile = UserProfile(weightUnit: .kg, startWeightKg: 88.0, goalWeightKg: 75.0, pace: .moderate)
        let logs = [88.0, 87.8, 87.6].enumerated().flatMap { weekIndex, weight in
            (0..<7).map { DailyLog(date: referenceDate.plusDays(weekIndex * 7 + $0), weightKg: weight, calories: 2000.0) }
        }
        let today = referenceDate.plusDays(20)

        let moderateState = TdeeEngine.compute(profile: profile, logs: logs, today: today)
        XCTAssertEqual(CoachingAdvisor.advise(profile: profile, state: moderateState).sentiment, .caution)

        let relaxed = with(profile) { $0.pace = .relaxed }
        let relaxedState = TdeeEngine.compute(profile: relaxed, logs: logs, today: today)
        XCTAssertEqual(CoachingAdvisor.advise(profile: relaxed, state: relaxedState).sentiment, .positive)
    }
}

final class ConfidenceEvaluatorTests: XCTestCase {

    private let today = date(2026, 7, 10)

    private let profile = UserProfile(weightUnit: .kg, startWeightKg: 90.0, goalWeightKg: 80.0, pace: .moderate)

    private func emptyState() -> TdeeState { TdeeEngine.compute(profile: profile, logs: [], today: today) }

    func testNoProfileDetailsAndNoLoggingHistoryIsLowAccuracy() {
        let assessment = ConfidenceEvaluator.assess(profile: profile, state: emptyState(), logs: [], today: today)
        XCTAssertEqual(assessment.level, .low)
        XCTAssertTrue(assessment.promptProfileDetails)
    }

    func testSexUnspecifiedButHeightAndAgePresentIsStillMedium() {
        let partial = with(profile) {
            $0.heightCm = 170.0
            $0.age = 40
        }
        let assessment = ConfidenceEvaluator.assess(profile: partial, state: emptyState(), logs: [], today: today)
        XCTAssertEqual(assessment.level, .medium)
        XCTAssertTrue(assessment.promptProfileDetails)
    }

    func testOnlyOneOfThreeDetailsPresentStaysLowAccuracy() {
        let partial = with(profile) { $0.heightCm = 170.0 }
        XCTAssertEqual(ConfidenceEvaluator.assess(profile: partial, state: emptyState(), logs: [], today: today).level, .low)
    }

    func testAllThreeDetailsPresentIsMediumWithoutThePromptForMoreNudge() {
        let full = with(profile) {
            $0.sex = .female
            $0.heightCm = 170.0
            $0.age = 40
        }
        let assessment = ConfidenceEvaluator.assess(profile: full, state: emptyState(), logs: [], today: today)
        XCTAssertEqual(assessment.level, .medium)
        XCTAssertFalse(assessment.promptProfileDetails)
    }

    func testRealLoggingHistoryTakesPriorityOverProfileDetailCompleteness() {
        let logs = (0..<14).map {
            DailyLog(date: today.minusDays(13 - $0), weightKg: 90.0 - Double($0) * 0.1, calories: 2000.0)
        }
        let state = TdeeEngine.compute(profile: profile, logs: logs, today: today)
        XCTAssertEqual(ConfidenceEvaluator.assess(profile: profile, state: state, logs: logs, today: today).level, .medium)
    }

    func testBodyFatPercentAloneIsEnoughForMediumAccuracy() {
        let bodyFat = with(profile) { $0.bodyFatPercent = 25.0 }
        let assessment = ConfidenceEvaluator.assess(profile: bodyFat, state: emptyState(), logs: [], today: today)
        XCTAssertEqual(assessment.level, .medium)
        XCTAssertFalse(assessment.promptProfileDetails)
    }

    private func completedWeek(_ index: Int) -> WeekSummary {
        WeekSummary(
            weekIndex: index,
            startDate: today.minusWeeks(4 - index),
            endDate: today.minusWeeks(4 - index).plusDays(6),
            hasWeightData: true,
            hasCalorieData: true,
            avgWeightKg: 88.0,
            avgCalories: 2000.0,
            weightDeltaKg: -0.3,
            tdee: 2500.0
        )
    }

    private func recentLogs() -> [DailyLog] {
        (0...2).map { DailyLog(date: today.minusDays($0), weightKg: 88.0, calories: 2000.0) }
    }

    func testAnEstimateThatHasDriftedFarFromItsStartingGuessReadsAsLowAccuracy() {
        let state = with(emptyState()) {
            $0.initialTdeeGuess = 3000.0
            $0.currentTdee = 5000.0
            $0.weeklyHistory = (1...4).map(completedWeek)
        }
        XCTAssertEqual(ConfidenceEvaluator.assess(profile: profile, state: state, logs: recentLogs(), today: today).level, .low)
    }

    func testAModestPlausibleAdjustmentAwayFromTheStartingGuessIsNotTreatedAsDrift() {
        let state = with(emptyState()) {
            $0.initialTdeeGuess = 3000.0
            $0.currentTdee = 3600.0
            $0.weeklyHistory = (1...4).map(completedWeek)
        }
        XCTAssertEqual(ConfidenceEvaluator.assess(profile: profile, state: state, logs: recentLogs(), today: today).level, .high)
    }

    func testLowAccuracyMessageLowercasesTheNextLevelSentence() {
        let message = ConfidenceEvaluator.assess(profile: profile, state: emptyState(), logs: [], today: today).message
        XCTAssertTrue(message.hasSuffix("Otherwise, enter data for about 14 more days to reach the next level."))
    }
}

final class PlateauDetectorTests: XCTestCase {

    private let today = date(2026, 7, 10)

    private let profile = UserProfile(weightUnit: .kg, startWeightKg: 90.0, goalWeightKg: 80.0, pace: .moderate)

    private func flatLogs(_ days: Int, weightKg: Double = 90.0) -> [DailyLog] {
        (0..<days).map { DailyLog(date: today.minusDays(days - 1 - $0), weightKg: weightKg, calories: 2000.0) }
    }

    func testMaintainGoalIsNeverAPlateau() {
        let maintain = with(profile) { $0.goalWeightKg = $0.startWeightKg }
        let state = TdeeEngine.compute(profile: maintain, logs: flatLogs(28), today: today)
        XCTAssertFalse(PlateauDetector.assess(profile: maintain, state: state).isPlateaued)
    }

    func testFewerThan3FlatWeeksIsNotYetAPlateau() {
        let state = TdeeEngine.compute(profile: profile, logs: flatLogs(14), today: today)
        let assessment = PlateauDetector.assess(profile: profile, state: state)
        XCTAssertFalse(assessment.isPlateaued)
        XCTAssertEqual(assessment.weeksFlat, 2)
    }

    func test3OrMoreConsecutiveFlatWeeksIsAPlateau() {
        let state = TdeeEngine.compute(profile: profile, logs: flatLogs(28), today: today)
        let assessment = PlateauDetector.assess(profile: profile, state: state)
        XCTAssertTrue(assessment.isPlateaued)
        XCTAssertEqual(assessment.weeksFlat, 4)
    }

    func testARecentWeekOfRealProgressClearsAnEarlierPlateau() {
        let movingWeek = (0..<7).map { DailyLog(date: today.minusDays(6 - $0), weightKg: 87.0, calories: 2000.0) }
        let state = TdeeEngine.compute(profile: profile, logs: flatLogs(21) + movingWeek, today: today)
        let assessment = PlateauDetector.assess(profile: profile, state: state)
        XCTAssertFalse(assessment.isPlateaued)
        XCTAssertEqual(assessment.weeksFlat, 0)
    }

    func testSteadyOnPaceProgressIsNotAPlateau() {
        let logs = (0..<28).map { DailyLog(date: today.minusDays(27 - $0), weightKg: 90.0 - Double($0) * 0.1, calories: 2000.0) }
        let state = TdeeEngine.compute(profile: profile, logs: logs, today: today)
        XCTAssertFalse(PlateauDetector.assess(profile: profile, state: state).isPlateaued)
    }
}
