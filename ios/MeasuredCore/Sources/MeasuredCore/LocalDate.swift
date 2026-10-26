import Foundation

/// ISO day of week, numbered the same way as `java.time.DayOfWeek` (Monday = 1 ... Sunday = 7).
public enum DayOfWeek: Int, CaseIterable, Sendable {
    case monday = 1, tuesday, wednesday, thursday, friday, saturday, sunday
}

/// A calendar date with no time or time zone - the Swift stand-in for `java.time.LocalDate`, which
/// the Android domain layer is built on. Foundation only has `Date` (an instant in time), and doing
/// day arithmetic on instants drags in time zones and DST, which is exactly the class of bug a
/// "one entry per calendar day" app shouldn't have to think about. Stored as days since
/// 1970-01-01 - the same representation as the Android Room column (`dateEpochDay`), so the two
/// stay trivially convertible.
public struct LocalDate: Hashable, Comparable, Sendable, CustomStringConvertible {
    public let epochDay: Int

    public init(epochDay: Int) {
        self.epochDay = epochDay
    }

    /// Traps on an impossible date (e.g. Feb 30), like `LocalDate.of` throwing on the Android side.
    public init(year: Int, month: Int, day: Int) {
        guard let date = LocalDate.validated(year: year, month: month, day: day) else {
            preconditionFailure("Invalid date \(year)-\(month)-\(day)")
        }
        self = date
    }

    /// Parses strict ISO `yyyy-MM-dd` (what `LocalDate.toString()` writes into backup files).
    public init?(isoString: String) {
        let parts = isoString.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3,
              parts[0].count == 4, parts[1].count == 2, parts[2].count == 2,
              parts.allSatisfy({ $0.allSatisfy(\.isASCII) && $0.allSatisfy(\.isNumber) }),
              let year = Int(parts[0]), let month = Int(parts[1]), let day = Int(parts[2]),
              let date = LocalDate.validated(year: year, month: month, day: day)
        else { return nil }
        self = date
    }

    private static func validated(year: Int, month: Int, day: Int) -> LocalDate? {
        guard (1...12).contains(month), day >= 1 else { return nil }
        let date = LocalDate(epochDay: daysFromCivil(year: year, month: month, day: day))
        // Round-tripping rejects overflowed days (Apr 31 would otherwise land on May 1).
        return date.year == year && date.month == month && date.day == day ? date : nil
    }

    public var year: Int { LocalDate.civilFromDays(epochDay).year }
    public var month: Int { LocalDate.civilFromDays(epochDay).month }
    public var day: Int { LocalDate.civilFromDays(epochDay).day }

    public var dayOfWeek: DayOfWeek {
        // 1970-01-01 was a Thursday (ISO 4).
        DayOfWeek(rawValue: ((epochDay + 3) % 7 + 7) % 7 + 1)!
    }

    public func plusDays(_ days: Int) -> LocalDate { LocalDate(epochDay: epochDay + days) }
    public func minusDays(_ days: Int) -> LocalDate { LocalDate(epochDay: epochDay - days) }
    public func plusWeeks(_ weeks: Int) -> LocalDate { plusDays(weeks * 7) }
    public func minusWeeks(_ weeks: Int) -> LocalDate { minusDays(weeks * 7) }

    /// Same sign convention as `ChronoUnit.DAYS.between(start, end)`: positive when [end] is later.
    public static func daysBetween(_ start: LocalDate, _ end: LocalDate) -> Int {
        end.epochDay - start.epochDay
    }

    public static func < (lhs: LocalDate, rhs: LocalDate) -> Bool { lhs.epochDay < rhs.epochDay }

    public var description: String {
        let civil = LocalDate.civilFromDays(epochDay)
        return "\(pad(civil.year, 4))-\(pad(civil.month, 2))-\(pad(civil.day, 2))"
    }

    private func pad(_ value: Int, _ width: Int) -> String {
        let digits = String(value)
        return String(repeating: "0", count: max(0, width - digits.count)) + digits
    }

    // MARK: - Bridging to Foundation (the only place time zones come into it)

    /// The calendar date [instant] falls on in [calendar]'s time zone - the equivalent of
    /// `LocalDate.now()`, which also uses the device's current zone.
    public static func today(calendar: Calendar = .current, now instant: Date = Date()) -> LocalDate {
        let parts = calendar.dateComponents([.year, .month, .day], from: instant)
        return LocalDate(year: parts.year!, month: parts.month!, day: parts.day!)
    }

    /// Midnight at the start of this date in [calendar]'s time zone - for handing a date to
    /// Foundation/SwiftUI APIs (date pickers, HealthKit queries, notification triggers).
    public func startOfDay(calendar: Calendar = .current) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day))!
    }

    // MARK: - Civil calendar conversion (Howard Hinnant's algorithms, proleptic Gregorian)

    private static func daysFromCivil(year: Int, month: Int, day: Int) -> Int {
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yearOfEra = y - era * 400
        let dayOfYear = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1
        let dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097 + dayOfEra - 719_468
    }

    private static func civilFromDays(_ epochDay: Int) -> (year: Int, month: Int, day: Int) {
        let z = epochDay + 719_468
        let era = (z >= 0 ? z : z - 146_096) / 146_097
        let dayOfEra = z - era * 146_097
        let yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146_096) / 365
        let dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        let mp = (5 * dayOfYear + 2) / 153
        let day = dayOfYear - (153 * mp + 2) / 5 + 1
        let month = mp < 10 ? mp + 3 : mp - 9
        return (yearOfEra + era * 400 + (month <= 2 ? 1 : 0), month, day)
    }
}
