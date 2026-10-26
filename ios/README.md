# Measured for iOS (work in progress)

A piece-by-piece port of the Android app to iOS (SwiftUI). The Android app stays the reference: each
iOS piece is ported from its Kotlin counterpart and keeps the same behavior, wording and storage
formats. One concrete payoff is that **backup files move freely between the two apps**.

## Status

| # | Piece | Android source | iOS approach | Status |
|---|-------|----------------|--------------|--------|
| 1 | Business logic (TDEE engine, coaching, milestones, backups...) | `domain/` | `ios/MeasuredCore` Swift package, tests ported too | **Done**, 109 tests passing |
| 2 | Xcode project + persistence | `data/` (Room, DataStore) | XcodeGen `project.yml`; SwiftData for profile/logs, `UserDefaults` (same key names) for settings | Next |
| 3 | Onboarding + Today check-in | `ui/onboarding`, `ui/today` | SwiftUI + `@Observable` view models | |
| 4 | Journey (milestones, charts, history) | `ui/journey` | Swift Charts | |
| 5 | Settings, edit profile, backup/restore, tips, tutorial | `ui/settings`, `ui/tips`, `ui/tutorial` | `fileExporter`/`fileImporter`, so backups land in the Files app | |
| 6 | Daily reminder | `notifications/` | `UNUserNotificationCenter` repeating calendar trigger (no boot receiver needed: iOS keeps it scheduled) | |
| 7 | Home-screen widgets | `widget/` (Glance) | WidgetKit extension; reads data through an App Group | |
| 8 | Health integration | `health/` (Health Connect) | **HealthKit**, a direct equivalent (body-mass read/write) | |
| 9 | Polish: accent themes + app icon, share card, quick actions | `ui/theme`, `AppIconSwitcher`, `ShareImageRenderer`, `shortcuts.xml` | Alternate app icons (native on iOS), `ImageRenderer` + `ShareLink`, Home Screen quick actions | |

### What doesn't carry over directly

- **Dynamic color (Material You):** iOS has no wallpaper-derived theming. The fixed Sage/Berry/Ocean
  palettes carry over unchanged, and the "use dynamic color" setting is simply dropped on iOS.
- **Health Connect** becomes **HealthKit**. This one ports well: same idea, a better-established API.
- **Supporter perks:** on Android these are an honor-system toggle. That's fine for personal/TestFlight
  builds, but the App Store requires In-App Purchase (StoreKit) for unlocking features, so this needs
  rethinking only if the app is ever submitted there.

## MeasuredCore (piece 1)

`MeasuredCore/` is a plain Swift package with no UIKit/SwiftUI imports, so it builds and tests on
Linux as well as macOS (CI runs it in the `ios-core` job). Each Swift file maps onto one or more
Kotlin files in `app/src/main/java/com/measured/app/domain/`, and the structure is kept close enough
to diff side by side. When the algorithm changes on Android, make the same change here.

Notable porting decisions:

- **`LocalDate`**: Foundation has no date-without-time type, so `LocalDate.swift` provides one
  (days since 1970-01-01, the same value as the Android Room `dateEpochDay` column). Time zones only
  come in at the edges (`LocalDate.today()`, `startOfDay()`).
- **Rounding**: Swift's `.rounded()` rounds ties away from zero, while Kotlin's `roundToLong()` rounds
  them up. All rounding goes through `Double.roundedHalfUp()` so results match the spreadsheet to the
  exact kcal.
- **Enum raw values** are the Kotlin constant names (`"KG"`, `"LESS_THAN_NORMAL"`, ...), since those
  are what both apps persist and write into backups.

Run the tests on a Mac with `cd ios/MeasuredCore && swift test`, or open `Package.swift` in Xcode and
press Cmd+U.

## Getting it onto an iPhone

You need a Mac with Xcode (free from the Mac App Store; an M1 MacBook Air is plenty). Prefer a
personal Mac over a work one, because work machines often have management profiles or policies that
get in the way of developer tooling and personal Apple IDs.

1. **Free Apple ID ("Personal Team")**: sign in under Xcode > Settings > Accounts, plug in the iPhone,
   enable Developer Mode on it (Settings > Privacy & Security), and press Run. The catch is that the
   app stops launching after **7 days** and has to be re-run from Xcode. That's fine for development
   on one or two family phones.
2. **Apple Developer Program ($99/year)**: needed to share with friends via **TestFlight** (up to
   10,000 testers by email/link, builds last 90 days) and for any App Store release later.
