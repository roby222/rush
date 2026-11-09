# Architecture

No backend: AlarmManager fires a WorkManager job that calls TomTom, saves the result in DataStore and maybe notifies. The README has the file map.

## Layers

MVVM, as Google recommends for Android: screens → ViewModel → repository → pure logic.

- `ui/` screens (`RushApp` shell, `HomeScreen`, `TripsScreen`, `TripEditorScreen`, shared `Components`, `Theme`) read state from a ViewModel and call its actions; they never touch DataStore or WorkManager.
- `ui/MainViewModel`: app state (`HomeUi`: trips, results, the trip in front), system warnings, "check now". `ui/TripEditorViewModel`: the draft (`Draft` in the `SavedStateHandle`), place search, save and delete; `validate(draft, trips)` is pure and tested.
- `TripRepository`: the only door to trips and checks. Saving or deleting a trip also reschedules the alarms, so no screen can leave them stale.
- Pure logic, covered by `LogicTest`: `TomTom.kt` (route → `Check`, levels), `Scheduler.kt` (`nextTrigger`, `alarmPlan`, `timeConflict`, `featuredTrip`), `Prefs.kt` (`putTrip`/`removeTrip` on `MutablePreferences`), `checkOutcome`.
- `MainActivity` only hosts the Compose tree and forwards the notification's trip id and `onResume`.
- `widget/`: the Glance widget (`TripWidget`, its receiver, `CheckNowAction`) and `TripWidgetConfigActivity`. It reads the same DataStore as the Home and never calls TomTom.

## Widget refresh

The widget only redraws saved data; who triggers a redraw:
- `CheckWorker`, after saving any result (scheduled or manual);
- `TripRepository`, after saving or deleting a trip (a deleted trip's widget says so);
- `AlarmReceiver`, after every reschedule (app update, boot, clock change): an updated app leaves widgets on the loading layout otherwise;
- the system, every 30 minutes (`updatePeriodMillis`), so a result can turn stale (`isStale`: older than 2 h or from an earlier day);
- `CheckNowAction`, which stores the tap time in the widget's own state: the widget says "checking" until a newer result lands (`isChecking`, 3-minute cap). WorkManager's state cannot tell, because the worker redraws while its job still counts as running.

Glance gotcha: while a session is open, an update recomposes the content without calling `provideGlance` again, so the widget reads DataStore (`collectAsState`) and its own state (`currentState`) inside `provideContent`, never before it.

## Storage

- `trips`: the list of `Trip` as JSON in one DataStore key; one atomic write per change.
- `result_<id>`: the last `TripResult` of each trip (title, text, time, `Check?`), separate from the configuration so a finishing check never overwrites an edit in progress.
- `next_trip_id`: ids start at 1 and are never reused, so an old alarm or result cannot land on a new trip.
- `alarm_ids`: trip ids with an alarm set, so alarms of deleted trips get cancelled.
- No migration from v0.2.0 (one user, who re-entered the trip): its keys are dropped on the first save, and its alarm (request code 0) is cancelled by `scheduleAll`.
- Unreadable JSON decodes to nothing (no trips / no result); keep new fields optional or defaulted.

## Check flow

1. Each trip has its own exact alarm: request code = trip id, extra `trip` = id. `AlarmReceiver` enqueues that trip's check, then `scheduleAll` sets every trip's next alarm and cancels stale ones; any other broadcast (boot, clock change, update) only reschedules.
2. `CheckWorker` loads the trip (a deleted trip ends the work silently) and calls `checkOutcome(trip, manual, route)`: an `Outcome` with notify flag, `Check?` or `CheckError`.
3. The worker turns it into words (`Resources.describe(tripName, check)`), saves `result_<id>` (a failed check is a result without `Check`, and the Home shows its text as the error) and notifies with id `1000 + trip id`, title prefixed by the trip name; tapping the notification opens the Home on that trip.
4. Notify when manual, on error, or when delay ≥ the trip's threshold.

Logic stays language-free: `Check` and `Outcome` hold numbers and codes (`TrafficEvent.kind` is TomTom's `simpleCategory`, `CheckError`), and text is produced only from string resources at the edges. Tests assert on data, never on wording.

## Invariants

- `CancellationException` is rethrown in `checkOutcome`: a check stopped by the system saves nothing and notifies nothing. The HTTP call runs in `runInterruptible` so cancellation aborts it.
- Work queues per trip: `check-<id>` (scheduled, REPLACE) and `check-manual-<id>` (KEEP). A manual check never cancels a scheduled one, no trip cancels another, repeated taps run one check.
- Two trips never run in the same minute: the editor refuses a trip with the same hour:minute as another on a shared day (`timeConflict`). The same time on different days is allowed.
- The Home puts in front the latest trip with a problem (slowdown or failed check) in the last 3 hours (`FEATURED_PROBLEM_WINDOW`), else the trip checked next; a chip or a notification overrides it.

## TomTom

- Routing request: `traffic=true`, `departAt=now`, `computeTravelTimeFor=all`, `sectionType=traffic`, `instructionsType=text`, `language` = device locale.
- A traffic section has `startPointIndex`; its street is the `street` (else first `roadNumbers`) of the last guidance instruction with `pointIndex` ≤ that index. `sections` is absent when the road is clear.
- `trafficDelayInSeconds` is what live traffic adds to the usual time; `noTrafficTravelTimeInSeconds` is an empty road (often far lower). Compare today with `total - delay`, never with the empty road.
- Back-to-back sections of the same kind and street are merged into one slowdown.
- Many sections carry 0 s delay; `Check.notable` keeps closures and sections that cost time, and only those reach the Home and the notification.
- `trafficLevel` counts minutes first (the user leaves at a fixed time): green up to 2 min of delay, red from 10 min or from +40% over free-flow time, orange between; any closure is red.
- Search: with the other place saved, `lat`/`lon`/`radius=50000` restrict results near it (`searchUrl`), so the first place of a fresh setup is searched globally. The query goes percent-encoded in the URL path; the user sees and confirms the result before it is saved.
- Free tier: about 2,500 non-tile requests/day; a check costs one request, a search one.

## Platform choices

Sideloaded on purpose: `USE_EXACT_ALARM` and the direct `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog are both restricted on Google Play. The API key comes from `local.properties` into `BuildConfig` and never enters git. It is still readable from the APK and every install shares its daily quota: fine for personal sideloading, not for handing the app to other people (ticket 06).

`AlarmReceiver` is exported on purpose (decided again on 2026-11-09), so `adb` can fire checks and reschedules in tests. The accepted cost: another app could start checks of existing trips, spending TomTom quota and posting notifications; it cannot read or change trips. If that ever matters, export it only in the debug build.

iOS (ticket 05): an app cannot wake at an exact time to call the network (`BGAppRefreshTask` runs when the system decides), so a scheduled check there needs a backend sending pushes or a Shortcuts automation running an App Intent. Undecided; record the choice here.
