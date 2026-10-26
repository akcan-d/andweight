# Measured

An Android app that replaces manual calorie tracking with a 2-minute daily check-in: log your
weight and roughly how much you ate, and the app figures out your real maintenance calories
(TDEE) from what actually happens to your weight - not from food-diary math.

This is a port of a personal "TDEE 3.0" spreadsheet. The spreadsheet's formulas were reverse
engineered cell-by-cell and its adaptive algorithm is reproduced exactly (see
[`TdeeEngine.kt`](app/src/main/java/com/measured/app/domain/TdeeEngine.kt) and the tests in
[`TdeeEngineTest.kt`](app/src/test/java/com/measured/app/domain/TdeeEngineTest.kt), which assert
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
below 1200 kcal by default, or 1500 kcal once sex is set to male (sex is optional and only
raises the floor if explicitly provided), regardless of how aggressive your goal is.

### The daily check-in

The weight you log is for today, but by default the meal you log is for **yesterday** - by the
time you're checking in (typically a morning weigh-in), yesterday's eating is finished and known
for certain, where today's isn't yet. This lets one check-in finalize both without needing to come
back once today is actually over. If you weigh in in the evening or before bed instead, after
you've already finished eating for the day, a setting under Settings &gt; Check-in switches the
meal question to be about today instead (`MealTimingMode` in
[`TodayViewModel.kt`](app/src/main/java/com/measured/app/ui/today/TodayViewModel.kt)).

A small dot on the Status tab's nav icon shows up whenever today's check-in - whichever date's
weight and meal that currently means - isn't fully logged yet, so there's a reminder without
needing to rearrange or highlight anything on the Today screen itself
(`CheckInReminderViewModel`).

Instead of logging every meal, you pick one of five options for how much you felt like you ate:

| Option | Assumed offset from your current TDEE |
|---|---|
| Much less than usual | -40% |
| A bit less than usual | -18% |
| About my usual amount | 0% |
| A bit more than usual | +18% |
| Much more than usual | +40% |

The offset is a percentage of your own TDEE estimate rather than a flat number of calories -
a flat +/-750 kcal "much more/less" would be a huge swing for someone with a 1500 kcal TDEE and a
modest one for someone at 3500, so this scales with the person instead of meaning something
different for everyone.

There's also an "exact calories" mode for anyone who wants to enter a precise number instead.
Both paths go through the same safety floor before being saved.

### Plateau detection

Separately from the week-by-week coaching message above, `PlateauDetector` looks for a
*sustained* stall: 3 or more of the most recent consecutive completed weeks where net weight
change landed within a quarter of the planned weekly rate of zero, for an active lose/gain goal.
It counts backward from the most recent week and stops at the first week that isn't flat, so a
plateau has to be the current state, not just something that happened at some point in the
history. This is intentionally kept separate from `CoachingAdvisor`, which reacts to a single
recent week and is the only "what should I eat today" message - a plateau is a longer-run
pattern worth naming and reassuring about on its own, not something to react to by cutting
further after just one quiet week.

## Backups

Settings has an export/import for a plain-text backup file (profile, preferences, and the full
log history). Restoring one asks for confirmation first, then merges it into whatever's already
on the device rather than blindly overwriting: each day's weight is merged on its own, and
calories/intake-level/exact-entry are merged together as one group (since intake level and the
exact-entry flag only mean anything alongside a specific calories value). A field the backup
doesn't carry for a given day - say, an older export taken before that day's meal was logged -
leaves whatever's already saved for that field alone instead of wiping it out. Days the device
has that the file doesn't are untouched either way. See `AndWeightRepository.restoreLog()`.

The onboarding flow also has a "restore instead" button at the bottom of the form, for anyone
who already has a backup file and would rather not re-type their starting numbers. It runs
through the same merge-safe restore logic (`OnboardingViewModel.restoreFromBackup()`), which
matters less there since there's nothing yet on the device to merge against, but keeps restore
behaving the same regardless of where it's triggered from.

Android's own automatic backup (tied to the user's Google account) is also enabled for the app's
database and settings, but it only restores through the Play Store install flow or a device's
"restore from backup" setup step - not when sideloading an APK directly, which is normally how
this app gets tested during development.

## Health Connect

Settings has an optional Health Connect section (`HealthConnectManager`, `health/`) - off by
default, nothing is requested until the user taps "Connect to Health Connect" there. Once
connected:

- "Share logged weight automatically" mirrors every weight logged in the app into Health
  Connect, through the single `AndWeightRepository.saveLog()` choke point every logging path
  already goes through.
- "Import weight readings now" is a manual, user-triggered pull of recent Health Connect weight
  readings (e.g. from a smart scale's own app) that only fills days with no weight logged yet -
  it never overwrites a day already logged by hand.

Both permission and sync state can change outside the app at any time (Health Connect's own
"App permissions" screen), so the Settings UI re-checks the permission grant on every resume
rather than caching it. The app also declares an activity that responds to Health Connect's
`SHOW_PERMISSIONS_RATIONALE` intent (required of every client app, regardless of which record
types it uses) - handled in `MainActivity` by opening this same privacy policy and finishing.

The project uses the stable `androidx.health.connect:connect-client:1.1.0` release, which needs
`compileSdk 36` and AGP 8.9.1+. Stable requires every written record to carry `Metadata`, so
weights mirrored into Health Connect are tagged `Metadata.manualEntry()` (they're typed in by
hand, not measured by a device).

## Project structure

```
app/src/main/java/com/measured/app/
  domain/        Pure Kotlin business logic - no Android dependencies, fully unit-testable.
                 TdeeEngine.kt is the entire adaptive-TDEE algorithm.
  data/          Room database (entities, DAOs) and the repository that bridges data <-> domain.
  health/        Health Connect integration (HealthConnectManager).
  notifications/ Daily reminder scheduling and the notification itself.
  widget/        Home-screen Glance widget.
  ui/            Jetpack Compose screens and ViewModels:
                   onboarding/ first-run setup (weight, goal, optional sex/height/age)
                   tutorial/   first-launch walkthrough
                   today/      the daily check-in + current-status dashboard
                   journey/    milestones, research, and daily/weekly history
                   settings/   preferences, edit profile, backup/restore, tips
                   tips/       the tips list screen
                   nav/        navigation graph + bottom nav
```

There's no backend and no login - everything lives in a local Room (SQLite) database on the
device.

## Opening the project (Android Studio on Windows)

1. Install [Android Studio](https://developer.android.com/studio) if you haven't already.
2. `File > Open`, select this folder, and let Gradle sync (first sync downloads the Gradle
   distribution and Android SDK components it needs - this can take a few minutes).
3. Create a device to run it on: `Tools > Device Manager > Create Device` (any recent phone
   profile, API 36 matches the app's target SDK), or plug in an Android phone with USB debugging enabled.
4. Click the green Run button (`Shift+F10`), pick your device, and the app installs and launches.

No API keys, accounts, or backend setup are required - it should run immediately.

### Running the tests

`View > Tool Windows > Gradle`, then `Measured > app > Tasks > verification > test`, or from a
terminal: `./gradlew test` (macOS/Linux) or `gradlew.bat test` (Windows).

## Releasing to Google Play

The app targets API 36 (`targetSdk`/`compileSdk` in `app/build.gradle.kts`), which is what Play
requires for new apps and updates from August 31, 2026. Play raises this roughly once a year, so
check [the current requirement](https://developer.android.com/google/play/requirements/target-sdk)
before each release.

Play takes an Android App Bundle (`.aab`), not an APK, and uses Play App Signing: Google holds the
key that signs what users install, and you sign each upload with your own **upload key**.

1. **Create the upload key (once).** Either use Android Studio's
   `Build > Generate Signed App Bundle or APK > Android App Bundle > Create new...`, or from a
   terminal (on Windows, `keytool` is in `C:\Program Files\Android\Android Studio\jbr\bin\`):

   ```
   keytool -genkeypair -v -keystore measured-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```

   Keep the `.jks` file **outside this repo** and back it up together with its passwords (e.g. a
   password manager). If it's lost, Play can reset the upload key, but only after a support
   request and a waiting period.

2. **Point the build at it.** Create `keystore.properties` in the project root (gitignored):

   ```
   storeFile=C:/Users/you/keys/measured-upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```

   Use an absolute path with forward slashes. Relative paths resolve against `app/`, not the
   project root.

3. **Build the bundle.** `gradlew.bat bundleRelease` (Windows) or `./gradlew bundleRelease`, or
   use the Android Studio wizard. The signed bundle is at
   `app/build/outputs/bundle/release/app-release.aab`. Raise `versionCode` before every upload,
   because Play rejects a version code it has already seen.

4. **Upload** it to a testing track in Play Console and opt in to Play App Signing when asked.

The store listing text (title, descriptions, contact details) is drafted in
[`store/play-listing.md`](store/play-listing.md).

APKs signed with any earlier local testing key can't be updated in place by the Play build
(Android refuses an update signed with a different key). Export a backup in Settings first,
uninstall, install from Play, then restore.

## Safety note

The 1200/1500 kcal floors are a blunt, general safety net, not medical advice - they exist to
stop the "quick pick" answers or an aggressive goal rate from ever recommending an unsafe
intake. Nothing in this app is a substitute for talking to a doctor about a weight-management
plan, especially for aggressive goals or existing health conditions.
