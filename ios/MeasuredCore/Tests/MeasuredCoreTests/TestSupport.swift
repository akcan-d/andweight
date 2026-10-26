@testable import MeasuredCore

/// Stand-in for Kotlin's data-class `copy(...)`: `with(profile) { $0.pace = nil }`.
func with<T>(_ value: T, _ change: (inout T) -> Void) -> T {
    var copy = value
    change(&copy)
    return copy
}

func date(_ year: Int, _ month: Int, _ day: Int) -> LocalDate {
    LocalDate(year: year, month: month, day: day)
}
