# Workflow

## Running and testing

- Unit tests: `./gradlew testDebugUnitTest`. New pure logic (parsing, levels, outcomes) gets a case in the test of its package (e.g. `domain/ScheduleTest`).
- Test on the user's phone (Pixel, wireless adb) by default; the emulator (`Pixel_3a_API_32_arm64-v8a`) is slow, so use it when the phone is locked or the test needs demo trips. Set `ANDROID_SERIAL` to the device from `adb devices` (zsh does not split `$ADB -s x` in a variable). `./gradlew assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Fire a trip's scheduled check without waiting for the alarm: `adb shell am broadcast -n io.github.roby222.rush/.work.AlarmReceiver -a io.github.roby222.rush.CHECK --ei trip <id>` (also reschedules). Any other action (e.g. `-a io.github.roby222.rush.RESCHEDULE`) only reschedules; the shell may not send `BOOT_COMPLETED` on recent Android.
- List the app's alarms: `adb shell dumpsys alarm | grep -A1 'walarm\*:io.github.roby222.rush.CHECK'` (one `origWhen` per trip).
- Doze: `adb shell dumpsys deviceidle force-idle` (then `unforce`); the alarm must still fire. WorkManager state: `adb shell dumpsys jobscheduler | grep -A6 io.github.roby222.rush`. A real trigger: set a trip 2 minutes from now with today among its days.
- The phone is often locked; check the screen first (`adb exec-out screencap -p`) and ask the user to unlock before driving the UI.
- Drive the UI by element, not coordinates: `uiautomator dump` and tap the bounds of a `text=` or `content-desc=`. Space dumps at least 3 s apart: dumping every second on the slow emulator caused an ANR (the main thread busy answering accessibility requests). `input keycombination` (select all) does not work there; clear a field with `KEYCODE_MOVE_END` and repeated `DEL`.
- The phone holds the user's real trips. Before demo data: back up `files/datastore/settings.preferences_pb` with `adb exec-out run-as io.github.roby222.rush cat …`, and afterwards restore it (push to `/data/local/tmp`, `run-as … cp`) and compare MD5s. Leave the user's system settings (dark mode, font scale) as found.
- Screenshots for docs: demo route only, status bar cropped (it shows the user's notifications).
- Traffic to test against: Italian roads are mostly clear in the evening; US cities at their midday (e.g. Times Square → JFK) have live slowdowns.

## Git and releases

- Work lands on `main`, pushed to `github.com/roby222/rush` (public repo).
- Tags are `vX.Y.Z` and match `versionName` in `app/build.gradle.kts`; bump it in a commit before tagging.
- `local.properties` (API key, release signing passwords) is never committed.
- Release APK: `./gradlew assembleRelease` signs with `~/.android/rush-release.jks` (created 2026-11-10, alias `rush`; path and passwords in `local.properties`). Every update must be signed with this same key, or phones refuse it as an update: the user keeps a backup. Before publishing, check the APK holds no TomTom key (see `architecture.md`, Platform choices) and verify the signature with `apksigner verify`.
- Publish: tag `vX.Y.Z`, then a GitHub release with the APK attached as `rush-X.Y.Z.apk` (`gh release create`); the README's Install section links to the latest release.

## Local issue tracker

No GitHub Issues. One ticket per file, `NN-slug.md`, numbered in dependency order (blockers first):

- `docs/tickets/todo/`: open work. A ticket is ready when every ticket in its "Blocked by" is in `done/`.
- `docs/tickets/done/`: finished work. Whoever implements a ticket ticks its checkboxes, sets `**Status:** done`, and moves the file here with `git mv` in the same change.
