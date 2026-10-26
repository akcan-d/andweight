extension Double {
    /// Kotlin's `roundToLong()`/`roundToInt()` and Java's `Math.round` - ties round toward positive
    /// infinity. Swift's own `.rounded()` rounds ties away from zero instead, which only differs for
    /// negative halves (-2.5 -> -2 here, -3 there), but the TDEE numbers are asserted against a
    /// spreadsheet to the exact kcal, so every rounding in this package goes through this one.
    func roundedHalfUp() -> Double {
        let floored = rounded(.down)
        return self - floored >= 0.5 ? floored + 1 : floored
    }

    /// [places]-decimal rounding with a trailing ".0" dropped - "3", "3.5", never "3.0". Matches the
    /// Android side's repeated `formatOneDecimal`/`formatNumber` helpers.
    func trimmedDecimalString(places: Int) -> String {
        var scale = 1.0
        for _ in 0..<places { scale *= 10 }
        let rounded = (self * scale).roundedHalfUp() / scale
        return rounded == rounded.rounded(.towardZero) ? String(Int(rounded)) : String(rounded)
    }
}
