# Rush

An Android app that checks your commute with TomTom live traffic before you leave, and speaks only when the
trip is not normal: the delay, the jam and its street, how much earlier to go.

https://github.com/user-attachments/assets/faa72bfd-2e94-42f6-8ba4-274551659ac2

<p align="center">
  <img src="docs/screenshots/home-traffic.png" width="250" alt="Home on a slow day: the jam and its street, how much earlier to leave, the route colored by traffic">
  &nbsp;
  <img src="docs/screenshots/new-trip-when.png" width="250" alt="New trip, step 2 of 3: weekly days, the time is the arrival, and when Rush will check">
  &nbsp;
  <img src="docs/screenshots/trips.png" width="250" alt="Trips: one card per trip with days, time, departure and destination">
</p>

More screens: [clear day](docs/screenshots/home-clear.png), [place suggestions](docs/screenshots/new-trip.png),
[widget](docs/screenshots/widget.png). Vertical cut of the video: [download](https://github.com/roby222/rush/releases/download/v0.3.0/rush-promo-9x16.mp4).

## Features

- **Trips**: weekly or one-off, each with its own exact alarm (survives reboots and clock changes).
- **Arrival time**: say when to be there ("gym by 18:50"); Rush checks 30 and 10 min before you usually leave
  and tells you when to go.
- **Only when it matters**: a notification when traffic adds at least N minutes; "Check now" shows the result
  without notifying.
- **Widget** with a trip's last result and a Check now button.
- **Guided setup** in three steps, places picked from suggestions while you type.
- English and Italian. No backend: AlarmManager, WorkManager, DataStore.

## Install

Download the APK from the [latest release](https://github.com/roby222/rush/releases/latest) and open it on the
phone (Android 8+). On first launch paste **your own TomTom key**: free at <https://my.tomtom.com/keys>, with
**Search** and **Routing** enabled. It stays on the phone and out of backups.

## Development

```sh
./gradlew installDebug       # phone with USB or wireless debugging
./gradlew testDebugUnitTest  # unit tests
```

Debug builds read the key from `local.properties` (ignored by git), so they skip the key screen:

```properties
TOMTOM_API_KEY=your_key
```

Architecture, packages and adb recipes for testing alarms: [.ai/](.ai/AGENTS.md).

Rush is meant for sideloading: it uses `USE_EXACT_ALARM` and asks for the battery optimization exemption
directly, both restricted on Google Play.
