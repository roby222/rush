# Architecture

No backend: AlarmManager fires a WorkManager job that calls TomTom, saves the result in DataStore and maybe notifies.

## Layers

Android's recommended architecture (UI layer → data layer, with an optional domain layer), MVVM in the UI. The package is the layer: a change belongs where its package says. `model` depends on nothing; `domain` on `model` and the TomTom route types; the app's screens reach data only through `TripRepository` (the widget, which cannot host a ViewModel, reads `TripStore` directly).

- `ui/` screens, one package each (`home`, `trips`, `editor`), plus `components` and `theme`, read state from a ViewModel and call its actions; they never touch DataStore or WorkManager. `RushApp` is the shell.
- `ui/MainViewModel`: app state (`HomeUi`: trips, results, the trip in front), system warnings, "check now". `ui/editor/TripEditorViewModel`: the draft (`Draft` and the new-trip step in the `SavedStateHandle`), place suggestions, the usual-duration estimate, save and delete; `validate(draft, trips)` is pure and tested, and tells which steps are done. `NewTripScreen` (steps) and `TripEditorScreen` (one screen) share the fields in `TripForm.kt`.
- `data/TripRepository`: the only door to trips and checks. Saving or deleting a trip also reschedules the alarms, so no screen can leave them stale. Its sources: `data/local/TripStore.kt` (DataStore) and `data/remote/TomTom.kt` (HTTP, JSON DTOs, `Route.toCheck`).
- `model/`: the types every layer shares (`Trip`, `Place`, `TripResult`, `Check`, `TrafficEvent`, `trafficLevel`). They are `@Serializable` and stored as JSON, so renaming a field breaks saved data; moving a class does not.
- `domain/`: pure rules, no Android types. `Schedule.kt` (`nextTrigger`, `nextCheck`, `expired`, `doneAfterCheck`, `alarmPlan`, `timeConflict`, `featuredTrip`), `CheckOutcome.kt` (`checkOutcome`).
- `work/`: background execution. `Scheduler` (exact alarms, WorkManager queue names), `AlarmReceiver`, `CheckWorker`. `notification/`: the notification and its words (`describe`, `eventTitle`), also used by the Home.
- `MainActivity` (root package) only hosts the Compose tree and forwards the notification's trip id and `onResume`.
- `widget/`: the Glance widget (`TripWidget`, its receiver, `CheckNowAction`) and `TripWidgetConfigActivity`. It reads the same DataStore as the Home and never calls TomTom. Its class names are part of the placed widgets: renaming or moving `TripWidgetReceiver` removes the user's widgets.
- Tests mirror the packages (`app/src/test/.../<package>/<Name>Test.kt`); shared fixtures (`at`, `trip`, demo places) live in `TestData.kt`.

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
- A one-off trip is a `Trip` with `date` (ISO day) set; its `days` are empty and ignored.
- An arrival trip has `arriveBy` (hour:minute is the arrival) and `usualMin`; its results carry `leaveBy` (epoch ms).
- Unreadable JSON decodes to nothing (no trips / no result); keep new fields optional or defaulted.

## Check flow

1. Each trip has its own exact alarm: request code = trip id, extra `trip` = id. `AlarmReceiver` enqueues that trip's check, then `scheduleAll` sets every trip's next alarm and cancels stale ones; any other broadcast (boot, clock change, update) only reschedules.
2. `CheckWorker` loads the trip (a deleted trip ends the work silently) and calls `checkOutcome(trip, manual, earlier, route)`: an `Outcome` with notify flag, `Check?` or `CheckError`.
3. The worker turns it into words (`Resources.describe(tripName, check, leaveBy)`), saves `result_<id>` (a failed check is a result without `Check`, and the Home shows its text as the error) and notifies with id `1000 + trip id`, title prefixed by the trip name; tapping the notification opens the Home on that trip.
4. A scheduled check notifies on error or when delay ≥ the trip's threshold; a manual check never notifies (the app or widget that started it shows the result).
5. One-off trips (ticket 08): checked once, at the chosen departure time (not the arrival time: TomTom's `arriveAt` was not needed). After its scheduled check the worker deletes the trip; the notification carries the result. `scheduleAll` first drops one-off trips more than `ONE_OFF_GRACE` (1 h) past their time, e.g. after the phone was off: the grace lets the check started by the alarm still find its trip. A manual check never deletes it, and neither does a check before the trip's last check time (`doneAfterCheck`): `AlarmReceiver` is exported, so a forged early broadcast must not delete the trip.
6. Arrival trips (ticket 10): the time is when to arrive. Live traffic only: TomTom's `arriveAt` predicts from historical traffic, so Rush checks shortly before leaving and computes `leaveBy = arrival − live duration`. Two checks, `FIRST_LEAD` (30 min) and `FOLLOW_UP_LEAD` (10 min) before the usual departure (`arrival − usualMin`); one alarm per trip still, `nextCheck` returns whichever is next. `usualMin` is estimated by one route call when the trip is saved with new places (a failed call fails the save), then replaced by the first scheduled check of each arrival (`Check.usualMin`, near the trip's own hour), which moves the second check. The second check (`isFollowUp`, ±window for late alarms) gets the first one's `Check` as `earlier` and notifies only when the first stayed quiet or the delay grew by `FOLLOW_UP_WORSE_MIN` (3). A one-off arrival saved too late for both checks is checked at once.

Logic stays language-free: `Check` and `Outcome` hold numbers and codes (`TrafficEvent.kind` is TomTom's `simpleCategory`, `CheckError`), and text is produced only from string resources at the edges. Tests assert on data, never on wording.

## Invariants

- `CancellationException` is rethrown in `checkOutcome`: a check stopped by the system saves nothing and notifies nothing. The HTTP call runs in `runInterruptible` so cancellation aborts it.
- Work queues per trip: `check-<id>` (scheduled, REPLACE) and `check-manual-<id>` (KEEP). A manual check never cancels a scheduled one, no trip cancels another, repeated taps run one check.
- Two trips never run in the same minute: the editor refuses a trip with the same hour:minute and the same meaning (check or arrival) as another on a shared day (`timeConflict`). The same time on different days is allowed; a one-off trip shares a day with a weekly one when its date falls on one of the weekly days.
- The Home puts in front the latest trip with a problem (slowdown or failed check) in the last 3 hours (`FEATURED_PROBLEM_WINDOW`), else the trip checked next; a chip or a notification overrides it.

## TomTom

- Routing request: `traffic=true`, `departAt=now`, `computeTravelTimeFor=all`, `sectionType=traffic`, `instructionsType=text`, `language` = device locale.
- A traffic section has `startPointIndex`; its street is the `street` (else first `roadNumbers`) of the last guidance instruction with `pointIndex` ≤ that index. `sections` is absent when the road is clear.
- `trafficDelayInSeconds` is what live traffic adds to the usual time; `noTrafficTravelTimeInSeconds` is an empty road (often far lower). Compare today with `total - delay`, never with the empty road. The same holds for an arrival trip's usual duration: on a city route the empty road said 25 min while the usual time was 32, which would eat a quarter of the margin.
- Back-to-back sections of the same kind and street are merged into one slowdown.
- Many sections carry 0 s delay; `Check.notable` keeps closures and sections that cost time, and only those reach the Home and the notification.
- `trafficLevel` counts minutes first (the user leaves at a fixed time): green up to 2 min of delay, red from 10 min or from +40% over free-flow time, orange between; any closure is red.
- Search: suggestions while typing (`typeahead=true`, `limit=5`), from 3 characters after a 300 ms pause, so about 4–6 requests per field. With the other place saved, `lat`/`lon`/`radius=50000` restrict results near it and each carries `dist` (meters), shown in the list; the first place of a fresh setup is searched globally. The query goes percent-encoded in the URL path. Only a picked suggestion is saved. A place's label is "POI name, address" unless the address already starts with the name (a square).
- As soon as both places are picked the editor estimates the usual duration with one route call (`TripRepository.usualMin`), so the "when" step can show the check times; save reuses it and asks again only when the estimate failed.
- Free tier: about 2,500 non-tile requests/day; a check costs one request, each search (one per typing pause) one.

## Platform choices

Sideloaded on purpose: `USE_EXACT_ALARM` and the direct `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog are both restricted on Google Play. The TomTom key (ticket 12): debug builds get the developer's key from `local.properties` into `BuildConfig` (never in git); release builds carry none (`buildConfigField` is empty for `release`), so a published APK holds nothing to steal and every user spends their own quota. The user's key lives in its own DataStore (`secrets`, `data/local/ApiKeyStore.kt`), excluded from cloud backup and device transfer (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`); `effectiveKey` prefers it over the build's. Without a key the app shows only the key screen; "Verify and save" runs one search and saves only on success. TomTom's 401/403 is `KeyRefusedException` → `CheckError.BAD_KEY`, a missing key `MissingKeyException` → `NO_KEY`; both are told in words. To check a release APK holds no key, search the key's bytes in every zip entry (Python `zipfile`): a plain `grep -r` over the unzipped APK missed it even in the debug build.

`AlarmReceiver` is exported on purpose (decided again on 2026-11-09), so `adb` can fire checks and reschedules in tests. The accepted cost: another app could start checks of existing trips, spending TomTom quota and posting notifications; it cannot read or change trips. If that ever matters, export it only in the debug build.

iOS (ticket 05): an app cannot wake at an exact time to call the network (`BGAppRefreshTask` runs when the system decides), so a scheduled check there needs a backend sending pushes or a Shortcuts automation running an App Intent. Undecided; record the choice here.
