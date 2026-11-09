# Workflow

## Running and testing

- Unit tests: `./gradlew testDebugUnitTest`. New pure logic (parsing, levels, outcomes) gets a case in `LogicTest`.
- Test on the user's phone (Pixel, wireless adb), never the emulator: set `ANDROID_SERIAL` to the phone's serial from `adb devices` (zsh does not split `$ADB -s x` in a variable). `./gradlew assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Fire a scheduled check without waiting for the alarm: `adb shell am broadcast -a io.github.roby222.rush.CHECK -n io.github.roby222.rush/.AlarmReceiver` (also reschedules the next alarm).
- The phone is often locked; check the screen first (`adb exec-out screencap -p`) and ask the user to unlock before driving the UI.
- Drive the UI by element, not coordinates: `uiautomator dump` and tap the bounds of a `text=` or `content-desc=`.
- The phone holds the user's real route. Before demo data: back up `files/datastore/settings.preferences_pb` with `adb exec-out run-as io.github.roby222.rush cat …`, and afterwards restore it (push to `/data/local/tmp`, `run-as … cp`) and compare MD5s. Leave the user's system settings (dark mode, font scale) as found.
- Screenshots for docs: demo route only, status bar cropped (it shows the user's notifications).
- Traffic to test against: Italian roads are mostly clear in the evening; US cities at their midday (e.g. Times Square → JFK) have live slowdowns.

## Git and releases

- Work lands on `main`, pushed to `github.com/roby222/rush` (public repo).
- Tags are `vX.Y.Z` and match `versionName` in `app/build.gradle.kts`; bump it in a commit before tagging.
- `local.properties` (API key) is never committed.

## Local issue tracker

No GitHub Issues. One ticket per file, `NN-slug.md`, numbered in dependency order (blockers first):

- `docs/tickets/todo/`: open work. A ticket is ready when every ticket in its "Blocked by" is in `done/`.
- `docs/tickets/done/`: finished work. Whoever implements a ticket ticks its checkboxes, sets `**Status:** done`, and moves the file here with `git mv` in the same change.
