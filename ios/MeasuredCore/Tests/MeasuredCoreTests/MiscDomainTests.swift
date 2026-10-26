import Foundation
import XCTest
@testable import MeasuredCore

// Ports of the Android BackupSerializerTest, PhrasingTest, PaceGuidanceTest, DataFillRateTest,
// SupporterPromptTest and WeekGroupingTest, plus tests for LocalDate (new on the iOS side).

final class BackupSerializerTests: XCTestCase {

    private let profile = UserProfile(
        weightUnit: .kg,
        startWeightKg: 90.0,
        goalWeightKg: 80.0,
        pace: .moderate,
        sex: .male,
        heightCm: 180.0,
        age: 30,
        activityLevel: .moderate,
        bodyFatPercent: 22.0
    )

    private let settings = BackupSerializer.Settings(themeMode: .dark, tipsEnabled: false, reminderEnabled: true, reminderHour: 8, reminderMinute: 30)

    private let logs = [
        DailyLog(date: date(2026, 1, 1), weightKg: 90.0, calories: 2000.0, intakeLevel: .average),
        DailyLog(date: date(2026, 1, 2), weightKg: 89.5, calories: nil),
    ]

    func testRoundTripsProfileSettingsAndLogsExactly() throws {
        let result = try XCTUnwrap(BackupSerializer.parse(BackupSerializer.build(profile: profile, settings: settings, logs: logs)))

        XCTAssertEqual(result.profile, profile)
        XCTAssertEqual(result.settings, settings)
        XCTAssertEqual(result.skippedLogRowCount, 0)
        XCTAssertEqual(result.logs, logs)
    }

    /// The exact text the Android app writes for the same inputs (derived from BackupSerializer.kt) -
    /// this is what lets a backup move between the Android and iOS apps.
    func testBuildsByteForByteTheSameTextAsTheAndroidApp() {
        let expected = """
        MEASURED_BACKUP v1
        [profile]
        weight_unit=KG
        start_weight_kg=90
        goal_weight_kg=80
        pace=MODERATE
        sex=MALE
        height_cm=180
        age=30
        activity_level=MODERATE
        body_fat_percent=22
        [settings]
        theme_mode=DARK
        tips_enabled=false
        reminder_enabled=true
        reminder_hour=8
        reminder_minute=30
        [logs]
        date,weight_kg,calories,intake_level,exact_entry
        2026-01-01,90,2000,AVERAGE,false
        2026-01-02,89.5,,,false

        """
        XCTAssertEqual(BackupSerializer.build(profile: profile, settings: settings, logs: logs), expected)
    }

    func testAProfileWithNoOptionalFieldsSetRoundTripsToo() throws {
        let bareProfile = UserProfile(weightUnit: .lb, startWeightKg: 150.0, goalWeightKg: 140.0)
        let result = try XCTUnwrap(BackupSerializer.parse(BackupSerializer.build(profile: bareProfile, settings: nil, logs: [])))

        XCTAssertEqual(result.profile, bareProfile)
        XCTAssertNil(result.settings)
        XCTAssertTrue(result.logs.isEmpty)
    }

    func testAFileWithoutTheMagicHeaderIsNotRecognizedAsABackup() {
        XCTAssertNil(BackupSerializer.parse("date,weight_kg,calories\n2026-01-01,90,2000\n"))
    }

    func testABackupFileFromTheAndWeightEraStillRestores() {
        let legacyText = BackupSerializer.build(profile: profile, settings: settings, logs: logs)
            .replacingOccurrences(of: "MEASURED_BACKUP v1", with: "ANDWEIGHT_BACKUP v1")
        let result = BackupSerializer.parse(legacyText)
        XCTAssertEqual(result?.profile, profile)
        XCTAssertEqual(result?.logs.count, 2)
    }

    func testSkipsUnreadableLogRowsButKeepsTheRest() throws {
        let text = BackupSerializer.build(profile: profile, settings: nil, logs: []) + "not,a,valid,row,here\n2026-02-01,88.0,,,false\n"
        let result = try XCTUnwrap(BackupSerializer.parse(text))

        XCTAssertEqual(result.skippedLogRowCount, 1)
        XCTAssertEqual(result.logs.map(\.date), [date(2026, 2, 1)])
    }

    func testWindowsLineEndingsParseTheSameAsUnix() throws {
        let text = BackupSerializer.build(profile: profile, settings: settings, logs: logs).replacingOccurrences(of: "\n", with: "\r\n")
        let result = try XCTUnwrap(BackupSerializer.parse(text))
        XCTAssertEqual(result.profile, profile)
        XCTAssertEqual(result.logs, logs)
    }
}

final class PhrasingTests: XCTestCase {

    func testCalorieTargetSentenceReflectsTheSizeOfTheDeficit() {
        XCTAssertEqual(Phrasing.calorieTargetSentence(recommendedCalories: 2500, tdee: 2500), "Eat about the usual today.")
        XCTAssertEqual(Phrasing.calorieTargetSentence(recommendedCalories: 2200, tdee: 2500), "Eat a bit less today.")
        XCTAssertEqual(Phrasing.calorieTargetSentence(recommendedCalories: 1800, tdee: 2500), "Eat noticeably less today.")
        XCTAssertEqual(Phrasing.calorieTargetSentence(recommendedCalories: 2800, tdee: 2500), "Eat a bit more today.")
        XCTAssertEqual(Phrasing.calorieTargetSentence(recommendedCalories: 3200, tdee: 2500), "Eat noticeably more today.")
    }

    func testGoalCountdownShowsExactDaysUnder60Days() {
        XCTAssertEqual(Phrasing.goalCountdown(daysToGoal: 42), .init(amount: 42, unitLabel: "days", isApproximate: false))
    }

    func testGoalCountdownSingularizesExactlyOneDay() {
        XCTAssertEqual(Phrasing.goalCountdown(daysToGoal: 1).unitLabel, "day")
    }

    func testGoalCountdownSwitchesToWeeksBetween60DaysAnd6Months() {
        XCTAssertEqual(Phrasing.goalCountdown(daysToGoal: 90), .init(amount: 13, unitLabel: "weeks", isApproximate: true))
    }

    func testGoalCountdownSwitchesToMonthsBeyond6Months() {
        XCTAssertEqual(Phrasing.goalCountdown(daysToGoal: 300), .init(amount: 10, unitLabel: "months", isApproximate: true))
    }

    func testGoalCountdownNeverMixesUnits() {
        let countdown = Phrasing.goalCountdown(daysToGoal: 478)
        XCTAssertEqual(countdown.unitLabel, "months")
        XCTAssertTrue((14...16).contains(countdown.amount))
    }

    func testTargetDateIsFormattedWithAFullDateIncludingYear() {
        XCTAssertEqual(Phrasing.formatTargetDate(date(2026, 3, 1)), "Mar 1, 2026")
    }

    func testFormatWeightDropsATrailingZeroButKeepsOneDecimal() {
        XCTAssertEqual(Phrasing.formatWeight(kg: 80.0, unit: .kg), "80 kg")
        XCTAssertEqual(Phrasing.formatWeight(kg: 80.04, unit: .kg), "80 kg")
        XCTAssertEqual(Phrasing.formatWeight(kg: 80.25, unit: .kg), "80.3 kg")
        XCTAssertEqual(Phrasing.formatWeight(kg: 100.0, unit: .lb), "220.5 lb")
    }
}

final class PaceGuidanceTests: XCTestCase {

    func testMaxSafeLossRateIs1Point5PercentOfBodyWeightCappedAt2KgPerWeek() {
        XCTAssertEqual(PaceGuidance.maxSafeWeeklyRateKg(weightKg: 90, direction: .lose), 1.35, accuracy: 1e-9)
        XCTAssertEqual(PaceGuidance.maxSafeWeeklyRateKg(weightKg: 200, direction: .lose), 2.0, accuracy: 1e-9)
    }

    func testMaxSafeGainRateIsSlowerThanLossCappedAt1KgPerWeek() {
        XCTAssertEqual(PaceGuidance.maxSafeWeeklyRateKg(weightKg: 80, direction: .gain), 0.4, accuracy: 1e-9)
        XCTAssertEqual(PaceGuidance.maxSafeWeeklyRateKg(weightKg: 300, direction: .gain), 1.0, accuracy: 1e-9)
    }

    func testMaintainHasNoRateAtAll() {
        XCTAssertEqual(PaceGuidance.maxSafeWeeklyRateKg(weightKg: 90, direction: .maintain), 0.0)
    }

    func testNoPaceOptionEverExceedsTheSafeMaximum() {
        let max = PaceGuidance.maxSafeWeeklyRateKg(weightKg: 90, direction: .lose)
        for option in PaceOption.allCases {
            let rate = PaceGuidance.rateForOption(option, weightKg: 90, direction: .lose)
            XCTAssertLessThanOrEqual(rate, max + 1e-9, "rate for \(option)")
            XCTAssertGreaterThan(rate, 0.0, "rate for \(option)")
        }
    }

    func testRelaxedIsSlowerThanModerateWhichIsSlowerThanFastest() {
        let rates = [PaceOption.relaxed, .moderate, .fastest].map { PaceGuidance.rateForOption($0, weightKg: 90, direction: .lose) }
        XCTAssertEqual(rates, rates.sorted())
        XCTAssertEqual(Set(rates).count, 3)
    }
}

final class DataFillRateTests: XCTestCase {

    private let today = date(2026, 7, 30)

    func testNoLogsAtAllReturnsNil() {
        XCTAssertNil(DataFillRate.compute(logs: [], today: today))
    }

    func testNewUserWithOnlyAFewDaysOfHistoryIsNotPenalizedForTheFull30DayWindow() {
        let start = today.minusDays(4)
        let logs = [
            DailyLog(date: start, weightKg: 90.0, calories: nil),
            DailyLog(date: start.plusDays(1), weightKg: 90.0, calories: nil),
            DailyLog(date: start.plusDays(3), weightKg: nil, calories: 2000.0),
        ]
        XCTAssertEqual(DataFillRate.compute(logs: logs, today: today), DataFillRateResult(loggedDays: 3, totalDays: 5, percent: 60))
    }

    func testLongTimeUserIsCappedToThe30DayTrailingWindow() {
        let logs = (0...29).map { DailyLog(date: today.minusDays($0), weightKg: 80.0, calories: nil) } +
            [DailyLog(date: today.minusDays(200), weightKg: 80.0, calories: nil)]
        XCTAssertEqual(DataFillRate.compute(logs: logs, today: today), DataFillRateResult(loggedDays: 30, totalDays: 30, percent: 100))
    }

    func testADayWithOnlyCaloriesLoggedStillCountsAsALoggedDay() {
        let logs = [DailyLog(date: today, weightKg: nil, calories: 1800.0)]
        XCTAssertEqual(DataFillRate.compute(logs: logs, today: today), DataFillRateResult(loggedDays: 1, totalDays: 1, percent: 100))
    }

    func testDaysAfterTodayAreNeverCountedEvenIfSomehowLogged() {
        let logs = [
            DailyLog(date: today.minusDays(2), weightKg: 80.0, calories: nil),
            DailyLog(date: today.plusDays(1), weightKg: 80.0, calories: nil),
        ]
        let result = DataFillRate.compute(logs: logs, today: today)
        XCTAssertEqual(result?.totalDays, 3)
        XCTAssertEqual(result?.loggedDays, 1)
    }
}

final class SupporterPromptTests: XCTestCase {

    // Well past the install-age/launch-count gate by default, so each test keeps exercising only the
    // condition it's named for.
    private func shouldShow(
        completedWeeks: Int,
        isSupporter: Bool = false,
        shownCount: Int = 0,
        daysSinceLastShown: Int? = nil,
        daysSinceFirstLaunch: Int = 10,
        launchCount: Int = 10
    ) -> Bool {
        SupporterPrompt.shouldShow(
            completedWeeks: completedWeeks,
            isSupporter: isSupporter,
            shownCount: shownCount,
            daysSinceLastShown: daysSinceLastShown,
            daysSinceFirstLaunch: daysSinceFirstLaunch,
            launchCount: launchCount
        )
    }

    func testNeverShowsToAnExistingSupporter() {
        XCTAssertFalse(shouldShow(completedWeeks: 10, isSupporter: true))
    }

    func testNeverShowsBeforeTwoCompletedWeeks() {
        XCTAssertFalse(shouldShow(completedWeeks: 1))
    }

    func testShowsOnceTheMinimumTrackRecordIsThereAndItsNeverBeenShown() {
        XCTAssertTrue(shouldShow(completedWeeks: 2))
    }

    func testStopsAfterTheMaximumNumberOfShowings() {
        XCTAssertFalse(shouldShow(completedWeeks: 20, shownCount: 3, daysSinceLastShown: 365))
    }

    func testRespectsTheMinimumGapBetweenShowings() {
        XCTAssertFalse(shouldShow(completedWeeks: 5, shownCount: 1, daysSinceLastShown: 5))
        XCTAssertTrue(shouldShow(completedWeeks: 5, shownCount: 1, daysSinceLastShown: 14))
    }

    func testNeverShowsBeforeThisInstallIsAtLeastTwoDaysOld() {
        XCTAssertFalse(shouldShow(completedWeeks: 50, daysSinceFirstLaunch: 0))
        XCTAssertTrue(shouldShow(completedWeeks: 50, daysSinceFirstLaunch: 2))
    }

    func testNeverShowsBeforeTheAppHasBeenLaunchedAtLeastThreeTimes() {
        XCTAssertFalse(shouldShow(completedWeeks: 50, launchCount: 2))
        XCTAssertTrue(shouldShow(completedWeeks: 50, launchCount: 3))
    }
}

final class WeekGroupingTests: XCTestCase {

    func testWeekStartForAMondayStartWeek() {
        XCTAssertEqual(WeekGrouping.weekStart(date(2026, 1, 14), firstDayOfWeek: .monday), date(2026, 1, 12))
    }

    func testWeekStartForASundayStartWeek() {
        XCTAssertEqual(WeekGrouping.weekStart(date(2026, 1, 14), firstDayOfWeek: .sunday), date(2026, 1, 11))
    }

    func testADateThatAlreadyIsTheFirstDayOfTheWeekReturnsItself() {
        XCTAssertEqual(WeekGrouping.weekStart(date(2026, 1, 12), firstDayOfWeek: .monday), date(2026, 1, 12))
        XCTAssertEqual(WeekGrouping.weekStart(date(2026, 1, 11), firstDayOfWeek: .sunday), date(2026, 1, 11))
    }

    func testSystemFirstDayOfWeekFollowsTheCalendar() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.firstWeekday = 1
        XCTAssertEqual(WeekGrouping.systemFirstDayOfWeek(calendar: calendar), .sunday)
        calendar.firstWeekday = 2
        XCTAssertEqual(WeekGrouping.systemFirstDayOfWeek(calendar: calendar), .monday)
    }
}

final class LocalDateTests: XCTestCase {

    func testEpochDayMatchesJavaTime() {
        // Values from java.time.LocalDate.toEpochDay(), which is what the Android database stores.
        XCTAssertEqual(date(1970, 1, 1).epochDay, 0)
        XCTAssertEqual(date(2000, 3, 1).epochDay, 11017)
        XCTAssertEqual(date(2026, 1, 1).epochDay, 20454)
        XCTAssertEqual(date(1969, 12, 31).epochDay, -1)
    }

    func testCivilFieldsRoundTripAcrossLeapYears() {
        for epochDay in stride(from: -800_000, through: 800_000, by: 37) {
            let d = LocalDate(epochDay: epochDay)
            XCTAssertEqual(LocalDate(year: d.year, month: d.month, day: d.day), d)
        }
        XCTAssertEqual(date(2024, 2, 28).plusDays(1), date(2024, 2, 29))
        XCTAssertEqual(date(2023, 2, 28).plusDays(1), date(2023, 3, 1))
        XCTAssertEqual(date(2100, 2, 28).plusDays(1), date(2100, 3, 1))
    }

    func testDayOfWeek() {
        XCTAssertEqual(date(1970, 1, 1).dayOfWeek, .thursday)
        XCTAssertEqual(date(2026, 1, 12).dayOfWeek, .monday)
        XCTAssertEqual(date(2026, 1, 11).dayOfWeek, .sunday)
        XCTAssertEqual(date(1969, 12, 29).dayOfWeek, .monday)
    }

    func testIsoStringRoundTripAndStrictParsing() {
        XCTAssertEqual(date(2026, 3, 1).description, "2026-03-01")
        XCTAssertEqual(LocalDate(isoString: "2026-03-01"), date(2026, 3, 1))
        XCTAssertNil(LocalDate(isoString: "2026-02-30"))
        XCTAssertNil(LocalDate(isoString: "2026-3-1"))
        XCTAssertNil(LocalDate(isoString: "not a date"))
        XCTAssertNil(LocalDate(isoString: ""))
    }

    func testTodayUsesTheCalendarsTimeZone() throws {
        // 2026-01-01T23:30:00Z is still Jan 1 in London but already Jan 2 in Tokyo.
        let instant = Date(timeIntervalSince1970: 1_767_310_200)
        var london = Calendar(identifier: .gregorian)
        london.timeZone = try XCTUnwrap(TimeZone(identifier: "Europe/London"))
        var tokyo = Calendar(identifier: .gregorian)
        tokyo.timeZone = try XCTUnwrap(TimeZone(identifier: "Asia/Tokyo"))

        XCTAssertEqual(LocalDate.today(calendar: london, now: instant), date(2026, 1, 1))
        XCTAssertEqual(LocalDate.today(calendar: tokyo, now: instant), date(2026, 1, 2))
        XCTAssertEqual(LocalDate.today(calendar: tokyo, now: date(2026, 1, 2).startOfDay(calendar: tokyo)), date(2026, 1, 2))
    }
}
