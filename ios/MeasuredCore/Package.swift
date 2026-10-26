// swift-tools-version:6.0
import PackageDescription

// The iOS port's pure business logic - a line-for-line Swift port of the Android app's `domain/`
// package (app/src/main/java/com/measured/app/domain). No UIKit/SwiftUI dependencies, so it builds
// and tests on Linux too (see the "ios-core" job in .github/workflows/tests.yml), not just on a Mac.
let package = Package(
    name: "MeasuredCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "MeasuredCore", targets: ["MeasuredCore"]),
    ],
    targets: [
        .target(name: "MeasuredCore"),
        .testTarget(name: "MeasuredCoreTests", dependencies: ["MeasuredCore"]),
    ]
)
