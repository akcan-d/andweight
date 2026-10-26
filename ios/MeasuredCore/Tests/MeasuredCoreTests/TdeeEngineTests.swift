import XCTest
@testable import MeasuredCore

/// These expected TDEE values (3865 / 4070 / 4245 / 3865) are read directly out of the M column of
/// the original "Beslenme.xlsx" TDEE 3.0 sheet for a real user's first four weeks of data (start
/// weight 122.7 kg) - the same fixture as the Android `TdeeEngineTest.kt`, so both ports are pinned
/// to the spreadsheet's actual behavior.
final class TdeeEngineTests: XCTestCase {

    private let startDate = date(2019, 1, 22)

    private let profile = UserProfile(weightUnit: .kg, startWeightKg: 122.7, goalWeightKg: 88.8, pace: .moderate)

    private let week1Weight = [122.7, 122.3, 121.9, 121.5, 121.1, 120.7, 120.3]
    private let week1Cal: [Double?] = [nil, nil, nil, nil, 1313.0, 1166.0, 1247.0]
    private let week2Weight = [119.9, 119.5, 119.1, 118.7, 118.3, 117.9, 117.5]
    private let week2Cal: [Double?] = [1411.0, 1174.0, 1228.0, 1243.0, 939.0, 1199.0, 1147.0]
    private let week3Weight = [116.6, 116.3, 116.0, 115.8, 115.5, 115.19999999999999, 115.0]
    private let week3Cal: [Double?] = [2004.0, 959.0, 1568.0, 1493.0, 1396.0, 2268.0, 1280.0]
    private let week4Weight = [114.7, 114.4, 114.1, 113.79999999999998, 113.5, 113.2, 113.0]
    private let week4Cal: [Double?] = [965.0, 1950.0, 988.0, 1531.0, 756.0, 687.0, 1031.0]

    private func buildLogs() -> [DailyLog] {
        let weeks = [(week1Weight, week1Cal), (week2Weight, week2Cal), (week3Weight, week3Cal), (week4Weight, week4Cal)]
        var logs: [DailyLog] = []
        for (weekIdx, (weights, cals)) in weeks.enumerated() {
            let weekStart = startDate.plusDays(weekIdx * 7)
            for day in 0..<7 {
                logs.append(DailyLog(date: weekStart.plusDays(day), weightKg: weights[day], calories: cals[day]))
            }
        }
        return logs
    }

    func testMatchesSpreadsheetWeeklyTdeeExactlyForFourRealWeeksOfData() {
        let state = TdeeEngine.compute(profile: profile, logs: buildLogs(), today: startDate.plusDays(27)) // last day of week 4

        XCTAssertEqual(state.weeklyHistory.map(\.tdee), [3865.0, 4070.0, 4245.0, 3865.0])
        XCTAssertEqual(state.currentTdee, 3865.0)
        XCTAssertEqual(state.currentWeightKg, 113.81428571428572, accuracy: 1e-9)
    }

    func testInitialGuessBeforeAnyDataIsWeightTimesMaintenanceConstant() {
        let state = TdeeEngine.compute(profile: profile, logs: [], today: startDate)
        // 122.7 kg * 28.6601 kcal/kg, rounded to nearest 5 = 3515
        XCTAssertEqual(state.initialTdeeGuess, 3515.0)
        XCTAssertEqual(state.currentTdee, 3515.0)
        XCTAssertEqual(state.currentWeightKg, profile.startWeightKg)
    }

    func testAFullySkippedWeekCarriesThePreviousStateForwardUnchanged() {
        let state = TdeeEngine.compute(profile: profile, logs: buildLogs(), today: startDate.plusDays(34))

        XCTAssertEqual(state.currentTdee, 3865.0)
        XCTAssertEqual(state.currentWeightKg, 113.81428571428572, accuracy: 1e-9)
        XCTAssertFalse(state.weeklyHistory.contains { $0.weekIndex == 5 && ($0.hasWeightData || $0.hasCalorieData) })
    }

    func testTargetDailyDeficitConvertsThePacesCurrentWeeklyRateToADailyKcalNumber() {
        let state = TdeeEngine.compute(profile: with(profile) { $0.pace = .fastest }, logs: [], today: startDate)
        // FASTEST at 122.7 kg: 1.5% of bodyweight (1.8405) * 7716.17 kcal/kg / 7, rounded to nearest 5.
        XCTAssertEqual(state.targetDailyDeficit, 2030.0)
    }

    func testRecommendedCaloriesIsCurrentTdeeMinusDeficitWhenLosing() {
        let state = TdeeEngine.compute(profile: with(profile) { $0.pace = .relaxed }, logs: [], today: startDate)
        XCTAssertEqual(state.recommendedCalories, state.currentTdee - state.targetDailyDeficit)
        XCTAssertFalse(state.recommendedCaloriesFloorApplied)
    }

    func testRecommendedCaloriesNeverDropsBelowTheSafetyFloorForWomen() {
        let aggressive = with(profile) {
            $0.startWeightKg = 80.0
            $0.goalWeightKg = 60.0
            $0.pace = .fastest
            $0.sex = .female
        }
        let state = TdeeEngine.compute(profile: aggressive, logs: [], today: startDate)
        XCTAssertGreaterThanOrEqual(state.recommendedCalories, SafetyLimits.femaleFloorKcal)
        XCTAssertTrue(state.recommendedCaloriesFloorApplied)
    }

    func testWeeksToGoalIsNilWhenGoalIsToMaintainWeight() {
        let maintain = with(profile) {
            $0.goalWeightKg = $0.startWeightKg
            $0.pace = nil
        }
        let state = TdeeEngine.compute(profile: maintain, logs: [], today: startDate)
        XCTAssertNil(state.weeksToGoal)
        XCTAssertNil(state.goalDate)
        XCTAssertEqual(state.recommendedCalories, state.currentTdee)
    }

    func testWeeksToGoalAccountsForTheWeeklyRateShrinkingAsWeightApproachesGoal() throws {
        let bigLoss = with(profile) {
            $0.startWeightKg = 150.0
            $0.goalWeightKg = 100.0
            $0.pace = .fastest
        }
        let state = TdeeEngine.compute(profile: bigLoss, logs: [], today: startDate)
        let weeksToGoal = try XCTUnwrap(state.weeksToGoal)

        let startingRateWeeks = (150.0 - 100.0) / PaceGuidance.rateForOption(.fastest, weightKg: 150.0, direction: .lose)
        XCTAssertGreaterThan(weeksToGoal, startingRateWeeks)
    }

    func testEarliestKnownWeightReflectsTheFirstWeekThatActuallyHadAWeightEntry() {
        let state = TdeeEngine.compute(profile: profile, logs: buildLogs(), today: startDate.plusDays(27))
        XCTAssertEqual(state.earliestKnownWeightKg, 121.5, accuracy: 1e-9)
    }

    func testEarliestKnownWeightFallsBackToTheProfilesStartWeightWhenNothingIsLogged() {
        let state = TdeeEngine.compute(profile: profile, logs: [], today: startDate)
        XCTAssertEqual(state.earliestKnownWeightKg, profile.startWeightKg)
    }

    func testEstimatedTdeeNeverGoesNegativeEvenInAnExtremeScenario() {
        let logs = [
            DailyLog(date: startDate, weightKg: 90.0, calories: 500.0),
            DailyLog(date: startDate.plusDays(6), weightKg: 140.0, calories: 500.0),
        ]
        let state = TdeeEngine.compute(profile: with(profile) { $0.startWeightKg = 90.0 }, logs: logs, today: startDate.plusDays(6))
        XCTAssertGreaterThanOrEqual(state.currentTdee, 0.0)
        XCTAssertTrue(state.weeklyHistory.allSatisfy { ($0.tdee ?? 0.0) >= 0.0 })
    }

    func testQuickPickIntakeLevelsOffsetTheCurrentTdeeEstimate() {
        let resolution = IntakeResolver.resolveQuickPick(.lessThanNormal, currentTdeeEstimate: 2500.0, sex: .male)
        XCTAssertEqual(resolution.calories, 2050.0) // 2500 * (1 - 0.18)
        XCTAssertFalse(resolution.floorApplied)
    }

    func testStarvingQuickPickIsClampedToTheSafetyFloorForALowTdee() {
        let resolution = IntakeResolver.resolveQuickPick(.starving, currentTdeeEstimate: 1800.0, sex: .female)
        XCTAssertEqual(resolution.calories, 1200.0) // 1800 * 0.6 = 1080, below the 1200 floor
        XCTAssertTrue(resolution.floorApplied)
    }

    func testExactEntryModeKeepsTheActualValueAndFlagsItInsteadOfClamping() {
        let resolution = IntakeResolver.resolveExact(900.0, sex: .male)
        XCTAssertEqual(resolution.calories, 900.0)
        XCTAssertFalse(resolution.floorApplied)
        XCTAssertTrue(resolution.belowFloor)
    }

    func testExactEntryAboveTheSafetyFloorIsNotFlagged() {
        let resolution = IntakeResolver.resolveExact(1800.0, sex: .male)
        XCTAssertEqual(resolution.calories, 1800.0)
        XCTAssertFalse(resolution.belowFloor)
    }

    func testInitialGuessWithNoDemographicDetailsAtAllUsesTheCrudeBodyweightFormula() {
        let bare = with(profile) {
            $0.sex = .unspecified
            $0.heightCm = nil
            $0.age = nil
        }
        XCTAssertEqual(TdeeEngine.initialTdeeGuess(bare), 3515.0)
    }

    func testASingleDemographicDetailAlreadySharpensTheEstimateAwayFromTheCrudeFormula() {
        let bareGuess = TdeeEngine.initialTdeeGuess(with(profile) { $0.heightCm = nil })
        let heightOnlyGuess = TdeeEngine.initialTdeeGuess(with(profile) { $0.heightCm = 170.0 })
        XCTAssertNotEqual(heightOnlyGuess, bareGuess)
    }

    func testSexLeftUnspecifiedStillUsesMifflinStJeorWithAnAveragedSexTerm() {
        let partial = with(profile) {
            $0.heightCm = 170.0
            $0.age = 40
        }
        let expectedBmr = 10.0 * 122.7 + 6.25 * 170.0 - 5.0 * 40.0 - 78.0
        XCTAssertEqual(TdeeEngine.initialTdeeGuess(partial), TdeeEngine.mround(expectedBmr * ActivityLevel.light.multiplier, 5.0))
    }

    func testFullySpecifiedDemographicsMatchMifflinStJeorExactly() {
        let full = with(profile) {
            $0.sex = .female
            $0.heightCm = 165.0
            $0.age = 30
            $0.activityLevel = .moderate
        }
        let expectedBmr = 10.0 * 122.7 + 6.25 * 165.0 - 5.0 * 30.0 - 161.0
        XCTAssertEqual(TdeeEngine.initialTdeeGuess(full), TdeeEngine.mround(expectedBmr * ActivityLevel.moderate.multiplier, 5.0))
    }

    func testBodyFatPercentUsesKatchMcArdleFromLeanBodyMass() {
        let bodyFat = with(profile) {
            $0.bodyFatPercent = 25.0
            $0.activityLevel = .moderate
        }
        let expectedBmr = 370.0 + 21.6 * (122.7 * 0.75)
        XCTAssertEqual(TdeeEngine.initialTdeeGuess(bodyFat), TdeeEngine.mround(expectedBmr * ActivityLevel.moderate.multiplier, 5.0))
    }

    func testBodyFatPercentTakesPriorityOverSexHeightAndAgeWhenBothArePresent() {
        let both = with(profile) {
            $0.bodyFatPercent = 20.0
            $0.sex = .male
            $0.heightCm = 180.0
            $0.age = 25
        }
        let bodyFatOnly = with(profile) { $0.bodyFatPercent = 20.0 }
        XCTAssertEqual(TdeeEngine.initialTdeeGuess(both), TdeeEngine.initialTdeeGuess(bodyFatOnly))
    }

    // From the Android BackfillAnchorTest: history logged from before the oldest existing entry
    // shifts the weekly anchor earlier instead of silently falling outside every window.
    func testWeeklyWindowsAnchorToTheOldestLoggedDay() {
        let today = date(2026, 2, 1)
        let backfilledDate = today.minusDays(10)
        let logs = [
            DailyLog(date: backfilledDate, weightKg: 91.0, calories: 2300.0),
            DailyLog(date: today, weightKg: 90.0, calories: 2200.0),
        ]
        let anchorProfile = UserProfile(weightUnit: .kg, startWeightKg: 90.0, goalWeightKg: 80.0, pace: .moderate)

        let state = TdeeEngine.compute(profile: anchorProfile, logs: logs, today: today)

        XCTAssertTrue(state.weeklyHistory.contains { $0.hasWeightData })
        XCTAssertEqual(state.weeklyHistory.first?.startDate, backfilledDate)
    }

    // From the Android ReEntryIdempotencyTest: re-saving a day's quick-pick answer must resolve
    // against a baseline that excludes that day's own previous entry, so it doesn't compound.
    func testResolvingTheSameIntakeLevelForADayGivesTheSameResultRegardlessOfWhatWasPreviouslySaved() {
        let referenceDate = date(2026, 1, 1)
        let reEntryProfile = UserProfile(weightUnit: .kg, startWeightKg: 90.0, goalWeightKg: 80.0, pace: .moderate)
        let priorLogs = (0..<6).map {
            DailyLog(date: referenceDate.plusDays($0), weightKg: 90.0 - Double($0) * 0.1, calories: 2200.0)
        }
        let editedDate = referenceDate.plusDays(6)

        let baselineBeforeFirstSave = TdeeEngine.compute(profile: reEntryProfile, logs: priorLogs, today: editedDate).currentTdee
        let firstResult = IntakeResolver.resolveQuickPick(.moreThanAverage, currentTdeeEstimate: baselineBeforeFirstSave, sex: reEntryProfile.sex)

        let logsAfterFirstSave = priorLogs + [DailyLog(date: editedDate, weightKg: 89.5, calories: firstResult.calories)]

        let baselineBeforeSecondSave = TdeeEngine.compute(
            profile: reEntryProfile,
            logs: logsAfterFirstSave.filter { $0.date != editedDate },
            today: editedDate
        ).currentTdee
        XCTAssertEqual(baselineBeforeFirstSave, baselineBeforeSecondSave)

        let secondResult = IntakeResolver.resolveQuickPick(.moreThanAverage, currentTdeeEstimate: baselineBeforeSecondSave, sex: reEntryProfile.sex)
        XCTAssertEqual(firstResult.calories, secondResult.calories)
    }

    func testMroundRoundsTiesUpLikeKotlinNotAwayFromZeroLikeSwift() {
        // -12.5 / 5 = -2.5 -> Kotlin's roundToLong gives -2 (so -10); Swift's .rounded() would give -3.
        XCTAssertEqual(TdeeEngine.mround(-12.5, 5.0), -10.0)
        XCTAssertEqual(TdeeEngine.mround(12.5, 5.0), 15.0)
    }
}
