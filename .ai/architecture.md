# Architecture

No backend: AlarmManager fires a WorkManager job that calls TomTom, saves the result in DataStore and maybe notifies. The README has the file map.

## Check flow

1. `AlarmReceiver` gets the exact alarm (or boot/time change), enqueues the scheduled work and reschedules the next alarm.
2. `CheckWorker` calls `checkOutcome(settings, manual, route)`, a pure function covered by `LogicTest`: it returns an `Outcome` (notification title, text, notify flag, `Check?`).
3. The worker turns the outcome into words (`Resources.describe` in `CheckWorker.kt`) and saves title, text, time and the `Check` as JSON (`Keys.lastCheck`); a failed check removes `lastCheck` and keeps its message in `lastText`, so the Home never shows a stale success.

Logic stays language-free: `Check` and `Outcome` hold numbers and codes (`TrafficEvent.kind` is TomTom's `simpleCategory`, `CheckError`), and text is produced only from string resources at the edges (notification, Home). Tests assert on data, never on wording.
4. Notify when manual, on error, or when delay ≥ threshold minutes.

## Invariants

- `CancellationException` is rethrown in `checkOutcome`: a check stopped by the system saves nothing and notifies nothing. The HTTP call runs in `runInterruptible` so cancellation aborts it.
- Two unique work queues: `check` (scheduled, REPLACE) and `MANUAL_WORK` = `check-manual` (KEEP). A manual check never cancels the scheduled one; repeated taps run one check. The Home observes `MANUAL_WORK` for its progress state.
- Old or unreadable `lastCheck` JSON decodes to null (treated as "no check yet"); keep `Check` fields backward compatible or give them defaults.

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

iOS (ticket 05): an app cannot wake at an exact time to call the network (`BGAppRefreshTask` runs when the system decides), so a scheduled check there needs a backend sending pushes or a Shortcuts automation running an App Intent. Undecided; record the choice here.
