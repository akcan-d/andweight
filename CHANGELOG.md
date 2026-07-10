# Changelog

All notable changes to AndWeight are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/), and versions follow
[Semantic Versioning](https://semver.org/) - while pre-1.0, a minor version bump
can still include a behavior change, not just new features.

## [Unreleased]

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
