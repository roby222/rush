# Rush

A personal Android app that checks your commutes before you leave. For each trip you save (for example
"Gym", Mon/Wed/Fri 17:20) it asks TomTom for the live-traffic travel time at the chosen time and sends a
notification with the time, the delay and any jams on the way.
No backend: just AlarmManager, WorkManager and DataStore.

<p align="center">
  <img src="docs/screenshots/home-traffic.png" width="250" alt="Home on a slow day: one chip per trip, the jam and its street, how much earlier to leave, the route colored by traffic">
  &nbsp;
  <img src="docs/screenshots/home-clear.png" width="250" alt="Home on a normal day: all clear, travel time, route with no slowdowns">
  &nbsp;
  <img src="docs/screenshots/trips.png" width="250" alt="Trips: one card per trip with days, time, departure and destination">
</p>

<p align="center">
  <img src="docs/screenshots/editor.png" width="250" alt="Trip editor: name, departure and destination found by search">
  &nbsp;
  <img src="docs/screenshots/widget.png" width="400" alt="Home-screen widget: trip name, slowdown with minutes and delay, the street, check time and Check now">
</p>

The UI is in English and Italian, following the device language.

## Features

- **Several trips**: each with its own name, places, days, time and threshold, and its own exact alarm;
  alarms survive reboots, time zone and clock changes. Two trips cannot share a minute on the same day.
- **One-off trips**: an appointment on a given date and time, checked once and then removed on its own.
- **Home**: the trip with a recent problem (or the next one to be checked) in front, the others one chip away;
  a notification opens its own trip.
- **Live traffic**: travel time, delay and traffic sections (jams, road works, closures) from the TomTom
  Routing API. The time is colored by traffic level: green, orange, red.
- **Threshold**: notify only when traffic adds at least N minutes (0 = always). "Controlla ora" (check now)
  and errors always notify.
- **Widget**: a home-screen widget (2x1 to 4x2) with one trip's last result, colored like the Home, and a
  "Check now" button. It never calls TomTom itself, and an old result is shown faded with its time.
- **Local search**: once one place of a trip is set, searching for the other one prefers results within 50 km of it,
  so a bare street name finds the one in your city.
- **System warnings**: if notifications, background running or exact alarms are not allowed, Home shows a
  warning with a one-tap fix; it disappears as soon as the setting is fixed.

## TomTom key

1. Sign up at <https://developer.tomtom.com> (free plan, no card required; about 2,500 requests/day).
2. Dashboard → **API & SDK Keys** → *Create key*, enable **Routing API** and **Search API**.
3. Add it to `local.properties` in the project root (already ignored by git):

   ```properties
   TOMTOM_API_KEY=your_key
   ```
4. Rebuild: the key ends up in `BuildConfig.TOMTOM_API_KEY`.

## Build and install

Open the folder in Android Studio and press Run, or:

```sh
./gradlew installDebug      # phone connected with USB debugging (or wireless debugging) enabled
./gradlew testDebugUnitTest # unit tests
```

On first launch: allow notifications and tap **Add trip**. Give the trip a name, search for the departure and
the destination (check the result and tap **Confirm**), pick days and time, then **Save**. More trips, and
edits, are under the gear. If Home asks to let Rush run in the background, tap **Allow**.

## Testing without waiting

```sh
PKG=io.github.roby222.rush

# scheduled alarms, one "origWhen" per trip
adb shell dumpsys alarm | grep -A1 "walarm\*:$PKG.CHECK"

# run trip 1's check now, as if its alarm fired, and reschedule every trip
adb shell am broadcast -n $PKG/.AlarmReceiver -a $PKG.CHECK --ei trip 1

# reschedule only, as after a reboot (recent Android refuses BOOT_COMPLETED from the shell)
adb shell am broadcast -n $PKG/.AlarmReceiver -a $PKG.RESCHEDULE

# WorkManager job state
adb shell dumpsys jobscheduler | grep -A6 $PKG

# simulate Doze: the alarm must still fire
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle unforce

# logs
adb logcat | grep -iE "rush|WM-"
```

To check a real trigger: edit a trip, set its time 2 minutes from now and include today among the days.

Every trip has its own WorkManager queues, scheduled and manual: "Check now" never cancels a scheduled
check, no trip cancels another, and repeated taps run a single manual check.

## Structure

MVVM: Compose screens → ViewModels → `TripRepository` → pure logic (covered by unit tests).

| File | What it does |
|------|--------------|
| `ui/RushApp.kt` | app shell: top bar, Home → Trips → Editor, system Back |
| `ui/HomeScreen.kt`, `ui/TripsScreen.kt`, `ui/TripEditorScreen.kt` | the three screens |
| `ui/Components.kt`, `ui/Theme.kt` | verdict card, route list, buttons; colors and shapes |
| `ui/MainViewModel.kt`, `ui/TripEditorViewModel.kt` | screen state and actions; the editor draft and its validation |
| `TripRepository.kt` | the UI's only door to trips, checks and search; saving a trip reschedules its alarm |
| `Prefs.kt` | DataStore: trips (JSON), last result per trip |
| `Scheduler.kt` | next trigger, one alarm per trip, time clashes, featured trip, alarm receiver |
| `CheckWorker.kt` | expedited worker: checks one trip, saves its result, notifies |
| `TomTom.kt` | Search/Routing calls, JSON models, slowdowns by street, traffic level |
| `MainActivity.kt` | hosts the UI and opens the trip of a tapped notification |
| `widget/TripWidget.kt`, `widget/TripWidgetConfigActivity.kt` | home-screen widget (Glance) and its trip picker |

Times follow the phone's time zone. The app is meant for sideloading: it uses `USE_EXACT_ALARM` and asks for
the battery optimization exemption directly, both of which Google Play restricts.
