# AndWeight

An Android app that replaces manual calorie tracking with a 2-minute daily check-in: log your
weight and roughly how much you ate, and the app figures out your real maintenance calories
(TDEE) from what actually happens to your weight - not from food-diary math.

This is a port of a personal "TDEE 3.0" spreadsheet. The spreadsheet's formulas were reverse
engineered cell-by-cell and its adaptive algorithm is reproduced exactly (see
[`TdeeEngine.kt`](app/src/main/java/com/andweight/app/domain/TdeeEngine.kt) and the tests in
[`TdeeEngineTest.kt`](app/src/test/java/com/andweight/app/domain/TdeeEngineTest.kt), which assert
against real numbers taken from that spreadsheet).

## How the algorithm works

Every week, the app compares what you said you ate against what actually happened to your
weight, and backs out an estimate of your true TDEE:

```
impliedTdee = averageCaloriesThatWeek + (weightLostThatWeekInKg x 7716 kcal/kg) / 7
```

If you ate 1800 kcal/day on average and lost 0.5 kg that week, your body actually burned about
2350 kcal/day - regardless of what you intended to eat. That becomes your new baseline.

To stop one noisy week (water weight, a bad measurement) from swinging the estimate, the app
smooths it with a rolling average of the current week plus the last five already-finalized
weeks (6 weeks max). A week with zero entries is skipped entirely and carries the previous
state forward unchanged - so missing a day or two never breaks anything.

Recommended daily calories are then `current TDEE +/- target daily deficit/surplus`, where the
target comes from your chosen weekly rate of weight change. This is **never** allowed to drop
below 1200 kcal (women) or 1500 kcal (men), regardless of how aggressive your goal is.

### The daily check-in

Instead of logging every meal, you pick one of five options for how much you felt like you ate:

| Option | Assumed offset from your current TDEE |
|---|---|
| I was starving | -750 kcal |
| I ate less than normal | -350 kcal |
| I ate an average amount | 0 kcal |
| I ate more than average | +350 kcal |
| I almost bursted | +750 kcal |

There's also an "exact calories" mode for anyone who wants to enter a precise number instead.
Both paths go through the same safety floor before being saved.

## Project structure

```
app/src/main/java/com/andweight/app/
  domain/     Pure Kotlin business logic - no Android dependencies, fully unit-testable.
              TdeeEngine.kt is the entire adaptive-TDEE algorithm.
  data/       Room database (entities, DAOs) and the repository that bridges data <-> domain.
  ui/         Jetpack Compose screens and ViewModels:
                onboarding/  first-run setup (weight, goal, optional sex/height/age)
                today/       the daily check-in + current-status dashboard
                history/     weekly summary list
                nav/         navigation graph + bottom nav
```

There's no backend and no login - everything lives in a local Room (SQLite) database on the
device.

## Opening the project (Android Studio on Windows)

1. Install [Android Studio](https://developer.android.com/studio) if you haven't already.
2. `File > Open`, select this folder, and let Gradle sync (first sync downloads the Gradle
   distribution and Android SDK components it needs - this can take a few minutes).
3. Create a device to run it on: `Tools > Device Manager > Create Device` (any recent phone
   profile, API 34 is a safe default), or plug in an Android phone with USB debugging enabled.
4. Click the green Run button (`Shift+F10`), pick your device, and the app installs and launches.

No API keys, accounts, or backend setup are required - it should run immediately.

### Running the tests

`View > Tool Windows > Gradle`, then `AndWeight > app > Tasks > verification > test`, or from a
terminal: `./gradlew test` (macOS/Linux) or `gradlew.bat test` (Windows).

## Safety note

The 1200/1500 kcal floors are a blunt, general safety net, not medical advice - they exist to
stop the "quick pick" answers or an aggressive goal rate from ever recommending an unsafe
intake. Nothing in this app is a substitute for talking to a doctor about a weight-management
plan, especially for aggressive goals or existing health conditions.
