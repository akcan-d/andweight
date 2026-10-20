# Changelog

All notable changes to Measured (formerly AndWeight) are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/), and versions follow
[Semantic Versioning](https://semver.org/) - while pre-1.0, a minor version bump
can still include a behavior change, not just new features.

## [Unreleased]

## [0.22.0] - 2026-10-20

### Changed
- Renamed the app from AndWeight to Measured everywhere user-facing: app name, onboarding and
  tutorial copy, the supporter prompt, backup-file messages, feedback email subject, widget text,
  the share-progress image, and the privacy policy page
- Also changed the Android applicationId/package from `com.andweight.app` to `com.measured.app`
  to match - the last point this was safely possible, since it's permanent once a real release
  goes out. An existing sideloaded install under the old id needs a fresh install rather than an
  in-place update (back up first if you want to keep your history)
- The launcher icon, notification icon, and notification's large icon are now an old-style balance
  scale instead of a partial progress-ring glyph - ties back to the app's name and the idea of
  weight and balance, and reads as more distinct than the previous abstract shape
- Primary, secondary, and accent colors are noticeably more saturated (less washed-out), while
  staying dark enough to clear the same ~4.5:1 contrast target as before
- The Today screen's coaching line is now a short, icon-led pill instead of a full sentence, and
  Settings' daily-tip row is a switch matching the reminder row instead of a checkbox with a
  sentence

## [0.21.0] - 2026-10-19

### Added
- A "Report a bug or send feedback" button in Settings > Help, opening an email pre-filled with
  the app version and device details
- The Today screen now leads with the check-in card (instead of the status card) whenever that
  day's weight or meal is still missing, so a new user sees what to do first instead of having to
  scroll past a stats card

### Fixed
- Theme switching could leave several differently-colored launcher icons enabled at once if you
  switched more than once in a session, and in some cases disable the exact icon you were
  launching through - closing the app immediately and removing it from the launcher entirely. The
  icon-switching logic now correctly tracks which single icon the running app depends on and only
  ever touches the others

## [0.20.0] - 2026-10-19

### Added
- A privacy policy, linked from Settings > Help, and published at
  https://akcan-d.github.io/andweight/privacy-policy.html for the Play Store listing
- High contrast mode: a free accessibility toggle in Settings > Appearance
- Input bounds on exact-calorie entry and onboarding/Edit Profile's weight, height, and age
  fields, to catch an obvious typo before it feeds a nonsensical number into the TDEE math

### Changed
- The quick-pick "a bit/much more or less than usual" options now scale as a percentage of your
  own current TDEE estimate (+-18%/40%) instead of a flat +-350/750 kcal for everyone

### Fixed
- Journey's empty state told new users to check in from the "Today" tab - it's actually called
  "Status"
- Dropped the unused SCHEDULE_EXACT_ALARM permission (reduces Play Store review risk with no
  functional loss - the reminder already falls back to an inexact alarm in practice)

## [0.19.0] - 2026-10-19

### Changed
- Home-screen widget redesigned: now styled with the exact same color theme as the app (accent
  and light/dark) instead of generic default colors, and shows a goal-progress ring plus a short
  weight-trend sparkline and current weight instead of an exact calorie count. Dropped the
  "Ate usual" quick-log button - the widget is now purely informative, and tapping anywhere on it
  opens the app
- The tutorial's first-launch pages are now centered in the available space instead of leaving a
  large empty gap on taller screens, with a small icon per page

### Fixed
- Switching the accent theme no longer crashes the app a few seconds later (disabling the
  launcher-icon alias the app was actually running under was causing Android to force-finish it)
- The "allow reminders to run in the background" warning in Settings now rechecks the battery
  optimization setting when you return to the app, instead of staying stuck on screen forever
  after you've already granted it

## [0.18.0] - 2026-10-19

### Added
- Supporter tier: a one-time "Support the app" unlock in Settings for two extra color themes
  (Berry, Ocean - these also recolor the app icon) and an extra calorie-trend chart on the
  Journey page. The core app stays completely free. A rare, consent-based "want to support?"
  prompt shows at most a handful of times, only after a few weeks of consistent use, never as a
  recurring interruption
- Predictive back gesture support
- A proper notification icon matching the app's logo, instead of a generic one

### Changed
- Darkened several light-theme colors (primary, accent, secondary, soft/faint text, lines,
  destructive) for better contrast and readability
- Onboarding's safety-floor explanation no longer mentions sex by default - it just says 1200,
  bumping to 1500 only if male is selected; the enforced floor for unspecified sex was lowered to
  1200 to match what the screen actually says
- Release builds now use R8 code shrinking and resource shrinking (smaller, obfuscated APK)
- Accessibility: screen-reader descriptions for the weight/calorie trend charts, merged labels on
  switches and checkboxes so they read as one control instead of two, and locked theme chips are
  now properly disabled instead of silently ignoring a tap

### Removed
- CSV import/export, superseded by full backup/restore

## [0.17.0] - 2026-10-19

### Added
- A full visual redesign: a warm cream/sage/coral/sky palette (with a matching dark theme),
  Quicksand + Figtree fonts, rounder cards and chips, a custom ring for the Status page's goal
  countdown instead of a linear bar, a floating pill-shaped bottom nav, color-coded tinted icons
  throughout, and a small mascot illustration on the Milestones screen
- Full backup & restore in Settings - unlike CSV, this also captures your profile/goal and a few
  preferences, so restoring onto a fresh install doesn't mean redoing onboarding
- Share progress as an image - a "Share progress" button next to Edit profile renders a simple
  branded summary card (weight change, days tracked) and opens the system share sheet

## [0.16.0] - 2026-08-13

### Added
- Journey: Milestones and "What the research says" are now their own
  screens, reached from a compact summary card on the main page, instead
  of living inline
- Journey: a "History" title above the daily/weekly toggle
- First day of the week (Monday for metric users, Sunday for imperial)
  as a real concept, used to page Journey's daily history one calendar
  week at a time via a "Previous week" button, replacing the flat
  20-item "Show more"

### Changed
- Settings: dropped the "Your profile" title and description text above
  the Edit profile button

## [0.15.0] - 2026-07-23

### Added
- Calendar date-picker button on the Status page to jump straight to any date
- Horizontal swipe navigation between the Status, Journey, and Settings tabs,
  in sync with the bottom nav
- "Show more" pagination on Journey's daily history list instead of loading
  the entire history at once

### Changed
- Export and import CSV are now a single combined section in Settings
- Editing a day from Journey now switches to the Status tab with that day
  open, exactly like a normal tab switch - no more separate "Editing day X"
  page to back out of
- The "jump to today" icon on Status is now visually distinct from the new
  calendar button
- Journey's Milestones and research ("What the research says") sections are
  now compact, tappable chips instead of stacked wordy cards, and the page
  is reordered so both sit together near the top
- The accuracy popup now says how many more days of logging it takes to
  reach the next confidence level

### Fixed
- Research-benefit facts (Journey) could show nothing unlocked even after
  real progress, because they were measured against your onboarding start
  weight instead of your earliest logged weight (the same baseline the
  Status page's "down X kg" uses) - both now agree

## [0.14.0] - 2026-07-13

### Added
- CSV import in Settings, matching the export format - merges by date
  (imported days overwrite matching existing entries, other days are left
  alone), converts kg/lb automatically, and reports any unreadable rows

### Changed
- Status tab (renamed from Today) is now more dashboard-like: target date
  and today's calorie target are always visible instead of hidden behind
  "Show details"
- Goal countdown now shows months (>6 months out), weeks (60 days-6
  months), or days (<60 days) - always a single unit, never mixed, and
  never falsely precise about a many-months-out estimate
- Removed the redundant "weeks to go" forecast row from the details view
  (replaced by the always-visible target date)
- Shortened the early-stage coaching message
- Removed the "Raised to a safe minimum for this day." message after a
  quick-pick meal save (the safety floor itself still applies)

## [0.13.0] - 2026-07-13

### Added
- Research-backed health benefit facts for weight-gain goals too (bone
  density, immune function, fertility/menstrual health, and an underweight-
  BMI mortality-risk context fact when height is on file)

### Changed
- Research-benefit facts (both lose and gain) are now capped to what's
  reachable within your own stated goal - a fact that would require going
  past your goal is left out entirely rather than dangled as a locked target
- Onboarding intro and the closing safety note no longer presuppose a
  weight-loss goal; both now read correctly for Gain too
- Onboarding pace explanation reads correctly for a Gain goal instead of
  always describing weight loss

### Fixed
- Journey's "edit this day" action could occasionally land back on the
  wrong day, because it reused the bottom-tab save/restore-state navigation
  behavior; it's now a plain, always-correct jump to the requested date,
  with a back arrow to return to Journey

## [0.12.0] - 2026-07-13

### Added
- Optional body fat % field in onboarding and Edit Profile - when known, it
  drives the starting TDEE estimate on its own via the Katch-McArdle
  formula (actual lean body mass, rather than approximating it from sex/
  height/age), and counts as full accuracy in the confidence badge
- Daily entries on the Journey tab are now editable - tapping the edit icon
  jumps straight to that day on the Today tab
- "What the research says" can now be expanded to show every upcoming
  finding, not just the next one
- "Reset tip history" option in Settings, to start the tip rotation and
  seen-tip checkmarks over

### Changed
- Removed the "matching the original spreadsheet this app was built from"
  line from the "How your estimate works" info screen

## [0.11.0] - 2026-07-13

### Added
- Journey tab now shows "What the research says": general, non-personalized
  health-benefit facts from published weight-loss research (blood pressure,
  cholesterol/triglycerides, type 2 diabetes risk, sleep apnea, knee joint
  load), each unlocked as you lose the percentage of body weight the cited
  study measured, with the source named on every fact
- Journey tab now shows a check-in rate stat ("you logged X% of the last N
  days"), capped to the days since your first entry so new users aren't
  penalized for the full 30-day window
- "How your estimate works" info screen (Settings > Help) explaining where
  the starting calorie estimate comes from and how the adaptive TDEE
  algorithm corrects itself from your real results over time
- Units (Metric/Imperial) can now be changed directly from Settings, not
  just from Edit Profile
- Already-seen tips are marked with a checkmark in the full tips list

### Changed
- Reminder notification is back to a single tap-to-open notification,
  without in-notification quick actions - the reply/quick-action buttons
  were unreliable on-device (a stuck "sending" state on the weight reply),
  and opening the app to log takes barely any extra time

## [0.10.0] - 2026-07-10

### Changed
- Unit-selection labels now read "Metric (kg, cm)" / "Imperial (lb, ft/in)"
  instead of "Kg" / "Lb"
- The reminder time picker now shows a 24-hour clock when Metric units are
  selected, and a 12-hour am/pm clock for Imperial - matching the same
  Metric/Imperial choice used everywhere else
- Starting TDEE estimate and the accuracy badge now treat sex, height, and
  age as independently useful: each one you provide sharpens the formula a
  little (using a population-average fallback for whichever is still
  missing), instead of only using the full formula once all three are
  filled in - so leaving sex as "prefer not to say" no longer drops you all
  the way back to the crude, bodyweight-only estimate if height and age are
  already filled in

### Added
- Export your full weight/calorie history as a CSV file, from Settings
- Reminder notification now has quick actions: log today's weight by
  replying with a number, or log calories as "usual" with one tap - both
  without opening the app
- Home screen widget showing today's calorie target and whether weight/
  meals are logged yet, with a matching "Ate usual" quick action and a
  button to open the app

## [0.9.0] - 2026-07-10

### Changed
- The age-under-18 warning now only appears if you try to save with an age
  under 18, and blocks saving until it's changed or cleared - instead of
  showing live as you type
- Stats renamed to Journey

### Added
- Journey now opens with a milestones section: time in the app, logging
  streaks, and (for a lose/gain goal) progress toward your target and total
  weight moved - each shown as unlocked achievements plus what's coming up
  next, using generic, non-medical language rather than specific health
  claims tied to exact weights or dates

## [0.8.0] - 2026-07-10

### Fixed
- Daily reminder permission was only ever requested from the Settings toggle,
  which never fires if the reminder is left at its default (enabled) state -
  notifications silently never appeared unless the user manually turned the
  toggle off and back on
- Reminders could fail to fire at all when the app wasn't running, on phones
  with aggressive battery optimization

### Added
- One-time opt-in prompt for the daily reminder, shown after the first day is
  logged, with the time picker built in
- In-app guidance for excluding the app from battery optimization, for
  reliable reminder delivery on phones that need it
- Warning shown for an entered age under 18, recommending medical guidance

## [0.7.0] - 2026-07-10

### Added
- Android Auto Backup for the Room database and DataStore preferences
- Opt-in daily check-in reminder notification (default 9 PM, configurable and
  can be turned off)
- "Erase all data and start over" option in Settings

### Changed
- A blank day now shows one combined Save button instead of two separate ones
- The low-accuracy confidence explanation links directly to Edit Profile

## [0.6.0] - 2026-07-10

### Changed
- Weekly pace is re-derived from current weight instead of a fixed rate set at
  onboarding, so it tapers as you approach your goal - the projected goal date
  reflects that taper too
- Confidence indicator factors in filled-in profile details (sex/height/age),
  and no longer shows a "gone stale" warning on day one

### Fixed
- Exact calorie entries are kept as typed instead of being silently raised to
  the safety floor
- Start date removed as a stored/editable field - now derived from the oldest
  logged weight

## [0.5.0] - 2026-07-09

### Added
- Explicit lose/maintain/gain goal selector at onboarding
- Editable profile in Settings (goal, pace, height, age, sex, activity level,
  units)
- Confidence/accuracy indicator with a tap-to-explain dialog

### Changed
- Weight and meal entries split into independent, edit-in-place fields
- History renamed to Stats, with a weight-over-time trend chart
- Long "what you ate" sentences replaced with compact icon+label chips
- Units unified into one Metric/Imperial choice; fixed height unit bugs

### Fixed
- Dark-mode white flash on launch
- Contradictory "no need to restrict" vs "eat less" messaging

## [0.4.0] - 2026-07-09

### Changed
- Health-focused reframing across onboarding, tutorial, and tips copy
- Replaced free-text weekly rate entry with three guarded pace options
- Reworded daily intake quick-pick options for clarity

### Added
- Delete-record button for a logged day
- Daily entry list alongside the weekly summary in History

### Fixed
- TDEE could go negative in extreme scenarios
- Contradictory eat-more/eat-less advice
- Wrong sign on "up/down since you started" with backfilled data

## [0.3.0] - 2026-07-09

### Added
- Settings screen (theme mode, tips toggle)
- Tip-of-the-day popup

### Changed
- Today screen dashboard redesign
- Day navigation: removed backward cap, added jump-to-today
- Single-line input fields throughout

### Fixed
- kg/lb auto-conversion for already-entered onboarding values
- Morning-weight/night-calories check-in workflow

## [0.2.0] - 2026-07-09

### Added
- Day navigation (previous/next) on the check-in card
- Summary/edit view for already-logged days
- Word-based coaching advice
- Tips tab
- First-launch tutorial

## [0.1.1] - 2026-07-08

### Fixed
- Crash right after finishing onboarding
- Crash on cold launch when a profile already existed

## [0.1.0] - 2026-07-08

### Added
- Initial release: adaptive TDEE engine ported from the original spreadsheet,
  Room-backed data layer, and the first Compose UI (onboarding, daily
  check-in, dashboard, history)
