import XCTest
@testable import MeasuredCore

// Ports of the Android JourneyMilestonesTest and HealthBenefitsTest.

final class JourneyMilestonesTests: XCTestCase {

    private let referenceDate = date(2026, 1, 1)

    private let loseProfile = UserProfile(weightUnit: .kg, startWeightKg: 100.0, goalWeightKg: 80.0, pace: .moderate)

    private func stateWith(currentWeightKg: Double, earliestKg: Double = 100.0) -> TdeeState {
        TdeeState(
            initialTdeeGuess: 2500.0,
            currentWeightKg: currentWeightKg,
            earliestKnownWeightKg: earliestKg,
            currentTdee: 2200.0,
            targetDailyDeficit: 500.0,
            recommendedCalories: 1700.0,
            recommendedCaloriesFloorApplied: false,
            weeksToGoal: 10.0,
            goalDate: referenceDate.plusWeeks(10),
            weeklyHistory: []
        )
    }

    private func logsOverDays(_ days: Int) -> [DailyLog] {
        (0..<days).map { DailyLog(date: referenceDate.plusDays($0), weightKg: 100.0, calories: 2000.0) }
    }

    func testTimeMilestonesShowEveryThresholdReachedNotJustTheLatest() {
        let milestones = JourneyMilestones.compute(profile: loseProfile, state: stateWith(currentWeightKg: 99.0), logs: logsOverDays(8), today: referenceDate.plusDays(7))
            .filter { $0.category == .time }

        XCTAssertEqual(milestones.filter(\.achieved).map(\.title), ["First check-in", "One week in"])
        let next = milestones.first { !$0.achieved }
        XCTAssertEqual(next?.title, "Two weeks in")
        XCTAssertEqual(next?.hint, "6 days to go")
    }

    func testLongestStreakIsRememberedEvenAfterItIsLaterBroken() {
        let logs = logsOverDays(7) + [DailyLog(date: referenceDate.plusDays(10), weightKg: 98.0, calories: 2000.0)]
        let milestones = JourneyMilestones.compute(profile: loseProfile, state: stateWith(currentWeightKg: 98.0), logs: logs, today: referenceDate.plusDays(10))
            .filter { $0.category == .streak }

        XCTAssertEqual(milestones.filter(\.achieved).map(\.title), ["3-day streak", "7-day streak"])
        XCTAssertEqual(milestones.first { !$0.achieved }?.title, "Two-week streak")
    }

    func testProgressAndAmountMilestonesAreAbsentWhenMaintaining() {
        let maintain = with(loseProfile) {
            $0.goalWeightKg = $0.startWeightKg
            $0.pace = nil
        }
        let milestones = JourneyMilestones.compute(profile: maintain, state: stateWith(currentWeightKg: 100.0), logs: logsOverDays(3), today: referenceDate.plusDays(2))
        XCTAssertFalse(milestones.contains { $0.category == .progress })
        XCTAssertFalse(milestones.contains { $0.category == .amount })
    }

    func testProgressRatioReflectsHowFarCurrentWeightHasMovedTowardTheGoal() {
        let milestones = JourneyMilestones.compute(profile: loseProfile, state: stateWith(currentWeightKg: 90.0), logs: logsOverDays(3), today: referenceDate.plusDays(2))
            .filter { $0.category == .progress }

        XCTAssertEqual(milestones.filter(\.achieved).map(\.title), ["10% of the way there", "A quarter of the way there", "Halfway there"])
        XCTAssertEqual(milestones.first { !$0.achieved }?.title, "Three-quarters there")
    }

    func testAmountMilestoneAchievementIsBasedOnPhysicalWeightMovedNotTheDisplayUnit() throws {
        let state = stateWith(currentWeightKg: 98.0)
        let kgMilestones = JourneyMilestones.compute(profile: loseProfile, state: state, logs: logsOverDays(3), today: referenceDate.plusDays(2))
            .filter { $0.category == .amount }
        let lbMilestones = JourneyMilestones.compute(profile: with(loseProfile) { $0.weightUnit = .lb }, state: state, logs: logsOverDays(3), today: referenceDate.plusDays(2))
            .filter { $0.category == .amount }

        XCTAssertEqual(kgMilestones.filter(\.achieved).count, lbMilestones.filter(\.achieved).count)
        let kgId = try XCTUnwrap(kgMilestones.first { $0.achieved && $0.title == "2 kg down" }).id
        let lbId = try XCTUnwrap(lbMilestones.first { $0.achieved && $0.title == "4 lb down" }).id
        XCTAssertEqual(kgId, lbId)
        // Same id string the Android app produces, so "already seen" state could carry across.
        XCTAssertEqual(kgId, "amount_2.0_LOSE")
    }

    func testAmountMilestoneThresholdsScaleDownForASmallGoalSoTheyStayReachable() {
        let smallGoal = with(loseProfile) {
            $0.startWeightKg = 65.0
            $0.goalWeightKg = 60.0
        }
        let milestones = JourneyMilestones.compute(profile: smallGoal, state: stateWith(currentWeightKg: 64.0, earliestKg: 65.0), logs: logsOverDays(3), today: referenceDate.plusDays(2))
            .filter { $0.category == .amount }

        XCTAssertFalse(milestones.contains { $0.title == "10 kg down" })
        XCTAssertTrue(milestones.contains { $0.title == "1 kg down" || $0.title == "2 kg down" })
    }

    func testMovingTheGoalFartherAwayCorrectlyUnAchievesProgressMilestones() {
        let state = stateWith(currentWeightKg: 90.0)
        let before = JourneyMilestones.compute(profile: loseProfile, state: state, logs: logsOverDays(3), today: referenceDate.plusDays(2))
        XCTAssertTrue(before.contains { $0.category == .progress && $0.achieved && $0.title == "Halfway there" })

        let after = JourneyMilestones.compute(profile: with(loseProfile) { $0.goalWeightKg = 60.0 }, state: state, logs: logsOverDays(3), today: referenceDate.plusDays(2))
        XCTAssertFalse(after.contains { $0.category == .progress && $0.achieved && $0.title == "Halfway there" })
        XCTAssertTrue(after.contains { $0.category == .progress && $0.achieved && $0.title == "A quarter of the way there" })
    }

    func testMovingTheGoalMuchCloserDropsAmountThresholdsThatNoLongerMakeSense() {
        let state = stateWith(currentWeightKg: 90.0)
        let before = JourneyMilestones.compute(profile: loseProfile, state: state, logs: logsOverDays(3), today: referenceDate.plusDays(2))
            .filter { $0.category == .amount }
        XCTAssertTrue(before.contains { $0.achieved && $0.title == "10 kg down" })

        let after = JourneyMilestones.compute(profile: with(loseProfile) { $0.goalWeightKg = 90.0 }, state: state, logs: logsOverDays(3), today: referenceDate.plusDays(2))
            .filter { $0.category == .amount }
        XCTAssertFalse(after.contains { $0.title == "10 kg down" })
    }

    func testNoLogsYetMeansNoTimeOrStreakMilestonesAreComputed() {
        let milestones = JourneyMilestones.compute(profile: loseProfile, state: stateWith(currentWeightKg: 100.0), logs: [], today: referenceDate)
        XCTAssertNil(milestones.first { $0.category == .time })
        XCTAssertFalse(milestones.contains { $0.category == .streak && $0.achieved })
    }
}

final class HealthBenefitsTests: XCTestCase {

    private let profile = UserProfile(weightUnit: .kg, startWeightKg: 100.0, goalWeightKg: 80.0, pace: .moderate)

    private lazy var gainProfile = with(profile) {
        $0.startWeightKg = 50.0
        $0.goalWeightKg = 60.0
    }

    /// An anchor entry at the profile's starting weight, then the "current" entry two weeks later -
    /// mirrors how earliestKnownWeightKg is derived from logged history.
    private func stateAt(_ currentWeightKg: Double, for forProfile: UserProfile? = nil) -> TdeeState {
        let p = forProfile ?? profile
        return TdeeEngine.compute(
            profile: p,
            logs: [
                DailyLog(date: date(2026, 1, 1), weightKg: p.startWeightKg, calories: 2000.0),
                DailyLog(date: date(2026, 1, 15), weightKg: currentWeightKg, calories: 2000.0),
            ],
            today: date(2026, 1, 15)
        )
    }

    private func single(_ result: [HealthBenefit], _ title: String) throws -> HealthBenefit {
        let matches = result.filter { $0.title == title }
        XCTAssertEqual(matches.count, 1, "expected exactly one \"\(title)\"")
        return try XCTUnwrap(matches.first)
    }

    func testNothingUnlockedYetFirstLockedTierHasAHintPointingAt5Percent() throws {
        let result = HealthBenefits.compute(profile: profile, state: stateAt(100.0))
        XCTAssertFalse(result.contains { $0.achieved && $0.title != "Knee joint load" })
        XCTAssertTrue(try single(result, "Blood pressure").hint?.contains("5%") == true)
    }

    func test5PercentLostUnlocksBloodPressureAndCholesterolFacts() throws {
        let result = HealthBenefits.compute(profile: profile, state: stateAt(95.0))
        let achievedTitles = result.filter(\.achieved).map(\.title)
        XCTAssertTrue(achievedTitles.contains("Blood pressure"))
        XCTAssertTrue(achievedTitles.contains("Cholesterol & triglycerides"))
        XCTAssertNotNil(try single(result, "Type 2 diabetes risk").hint)
    }

    func test10PercentLostUnlocksSleepApneaAndBroaderMarkersFacts() {
        let result = HealthBenefits.compute(profile: profile, state: stateAt(90.0))
        let achievedTitles = result.filter(\.achieved).map(\.title)
        XCTAssertTrue(achievedTitles.contains("Sleep apnea"))
        XCTAssertTrue(achievedTitles.contains("Broader health markers"))
        XCTAssertTrue(result.allSatisfy(\.achieved))
    }

    func testKneeLoadFactAppearsWithALiveNumberOnceAnyWeightIsLost() throws {
        let kneeFact = try single(HealthBenefits.compute(profile: profile, state: stateAt(97.0)), "Knee joint load")
        XCTAssertTrue(kneeFact.achieved)
        XCTAssertTrue(kneeFact.body.contains("3 kg"))
        XCTAssertTrue(kneeFact.body.contains("12 kg")) // 3 kg * 4
    }

    func testKneeFactIsLockedBeforeAnyWeightIsLost() throws {
        let kneeFact = try single(HealthBenefits.compute(profile: profile, state: stateAt(100.0)), "Knee joint load")
        XCTAssertFalse(kneeFact.achieved)
        XCTAssertNotNil(kneeFact.hint)
    }

    func testTiersBeyondThePersonsOwnGoalAreLeftOutEntirely() throws {
        let modestGoal = with(profile) { $0.goalWeightKg = 97.0 }
        let result = HealthBenefits.compute(profile: modestGoal, state: stateAt(97.0, for: modestGoal))
        let percentFactTitles: Set = ["Blood pressure", "Cholesterol & triglycerides", "Type 2 diabetes risk", "Sleep apnea", "Broader health markers"]
        XCTAssertFalse(result.contains { percentFactTitles.contains($0.title) })
        XCTAssertTrue(try single(result, "Knee joint load").achieved)
    }

    func testAHintNeverAsksForMoreThanThePersonsOwnGoal() {
        let sixPercentGoal = with(profile) { $0.goalWeightKg = 94.0 }
        let result = HealthBenefits.compute(profile: sixPercentGoal, state: stateAt(100.0, for: sixPercentGoal))
        XCTAssertFalse(result.contains { $0.title == "Type 2 diabetes risk" })
        XCTAssertTrue(result.contains { $0.title == "Blood pressure" })
    }

    func testHiddenEntirelyForAMaintainGoal() {
        let maintain = with(profile) {
            $0.goalWeightKg = $0.startWeightKg
            $0.pace = nil
        }
        let state = TdeeEngine.compute(profile: maintain, logs: [], today: date(2026, 1, 1))
        XCTAssertTrue(HealthBenefits.compute(profile: maintain, state: state).isEmpty)
    }

    func testImperialUnitsAreUsedForTheKneeFactDisplay() throws {
        let lbProfile = with(profile) { $0.weightUnit = .lb }
        let state = TdeeEngine.compute(profile: lbProfile, logs: [DailyLog(date: date(2026, 1, 1), weightKg: 97.0, calories: 2000.0)], today: date(2026, 1, 1))
        XCTAssertTrue(try single(HealthBenefits.compute(profile: lbProfile, state: state), "Knee joint load").body.contains("lb"))
    }

    func testGainGoalUnlocksBoneDensityAndImmuneFunctionFactsAt5Percent() throws {
        let result = HealthBenefits.compute(profile: gainProfile, state: stateAt(52.5, for: gainProfile))
        let achievedTitles = result.filter(\.achieved).map(\.title)
        XCTAssertTrue(achievedTitles.contains("Bone density"))
        XCTAssertTrue(achievedTitles.contains("Immune function"))
        XCTAssertNotNil(try single(result, "Fertility & menstrual health").hint)
    }

    func testGainGoalUnlocksFertilityFactAt10Percent() throws {
        let result = HealthBenefits.compute(profile: gainProfile, state: stateAt(55.0, for: gainProfile))
        XCTAssertTrue(try single(result, "Fertility & menstrual health").achieved)
    }

    func testUnderweightMortalityContextFactShownOnlyWhenStartingBmiIsUnder18Point5() throws {
        let underweight = with(gainProfile) { $0.heightCm = 175.0 } // BMI ~16.3
        let result = HealthBenefits.compute(profile: underweight, state: stateAt(50.0, for: underweight))
        XCTAssertTrue(try single(result, "Why gaining matters").achieved)
    }

    func testNoUnderweightContextFactWhenStartingBmiIsNotUnderweight() {
        let normal = with(gainProfile) { $0.heightCm = 150.0 } // BMI ~22.2
        let result = HealthBenefits.compute(profile: normal, state: stateAt(50.0, for: normal))
        XCTAssertFalse(result.contains { $0.title == "Why gaining matters" })
    }

    func testNoUnderweightContextFactWithoutAHeightOnFile() {
        let result = HealthBenefits.compute(profile: gainProfile, state: stateAt(50.0, for: gainProfile))
        XCTAssertFalse(result.contains { $0.title == "Why gaining matters" })
    }

    func testNoKneeLoadFactForAGainGoal() {
        let result = HealthBenefits.compute(profile: gainProfile, state: stateAt(55.0, for: gainProfile))
        XCTAssertFalse(result.contains { $0.title == "Knee joint load" })
    }
}
