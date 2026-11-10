# Design

Rush looks like a map app: neutral surfaces, travel time colored by traffic, one brand accent. Mode: operate (a working screen read in a hurry, minutes before leaving). Material 3 components only; the brand lives in `ui/theme/Theme.kt`.

## Tokens (`ui/theme/Theme.kt`)

- Surfaces: white (light) / graphite `Ink` #1C1F24 (dark); cards `surfaceContainerHighest` #F1F3F4 / #2A2E35.
- Accent: `Amber` #FFB300 with `Ink` text. Reserved for the primary action ("Controlla ora", "Salva", "Aggiungi tragitto", via `PrimaryButton`) and the destination dot.
- `primary` is Ink in light and Amber in dark: it colors section headings, focus and text buttons.
- Traffic: `trafficColor(level)` for text, dots and the route line, `statusContainer(level)` for the verdict card ground; level 0 green, 1 orange (#A04D00 light / #FCAD70 dark, kept clear of the brand Amber), 2 red. Contrast ≥4.5:1 on their grounds.
- Errors use the same red family (`error`, `errorContainer` set in both schemes), never the Material baseline.
- Shapes: cards 24dp, the verdict card 28dp, buttons are full pills 52dp high.
- No dynamic color: the fixed palette is the identity.

## Home: the verdict

The Home is the notification at full size: one verdict, the sentence naming the problem and its street, the actions under it.

0. With more than one trip, a scrollable row of filter chips with the trip names; each chip's dot has the color of the trip's last result (gray: never checked, red family: failed). With one trip the row is hidden. With no trips, the verdict card says so and the primary button adds the first one.
1. Verdict card, tinted by level: headline naming the problem and its street ("Coda in Corso Magenta", or "Tutto regolare"), beside it a filled pill with the level in sentence case (Rallentamento, Traffico intenso, Strada chiusa) shown only when level > 0, one sentence with minutes and how much earlier to leave (for an arrival trip: "Per essere a X alle 18:50, parti entro le **18:14**", or "**Parti subito**" when that time had passed at the check), "Apri in Maps" when there is a problem, update time. Headline, pill and ground crossfade together when a new check changes the state (600 ms). Error and never-checked states reuse the same card.
2. "Sul percorso": the trip as a directions list on a 4dp route line colored like a map: green where traffic flows, the slowdown's color around each slowdown, gray before the first check. Departure (hollow dot), each notable slowdown in driving order, destination (amber dot).
3. "Controlla ora" (shows "Controllo in corso…" while the manual work runs), next automatic check, then system warnings.

## Trips and editor

- The gear opens "Tragitti": one card per trip (name, days · time or "Tue 10 Nov · 15:00" for a one-off trip, "arrivo 18:50" for an arrival trip, departure → destination), tap to edit, primary "Aggiungi tragitto" at the bottom.
- A new trip is three steps, one question each ("Da dove a dove?", "Quando?", "Quando ti avviso?"): "Passo 1 di 3" over a linear progress bar, the question as a headline, the fields, then the primary "Avanti", enabled only when the step is valid; the last step ends with a summary card and "Salva tragitto". The bar's back arrow and the system Back go one step back, and leave from the first. No swipe or carousel: Material keeps carousels for browsing content.
- Editing an existing trip is one screen with the same fields under section headings, primary "Salva", then "Elimina tragitto" as an error-colored text button behind a Material confirmation dialog.
- Places: departure and destination share one card, with the route's hollow and amber dots. A chosen place shows its name and "Cambia"; otherwise a field whose suggestions (name in bold, address and distance below) open under it while typing. Only a suggestion becomes the place; there is no Search or Confirm button. A new trip takes its name from the destination when the name is still empty; in the steps the name field appears once there is a destination.
- When: segmented "Ogni settimana / Una volta", the day chips or a date button opening the Material date picker (past days disabled), "L'ora è quella di…" with a segmented "Partenza / Arrivo", the time ("Ora di partenza" / "Arriva entro le"); for an arrival the line under it says the usual duration and the two check times.
- Alerts: radio choices "Sempre", "Con almeno 5 min di ritardo", "Con almeno 10 min", "Altro…" (which shows the minutes field).
- Errors sit under their field: a time clash names the other trip ("C'è già «Palestra» alle 17:20 in uno di questi giorni") and keeps Salva disabled; a one-off time already passed says so under the time field; a missing name or place is said in one line under Salva.

## TomTom key

Shown alone, with no back arrow, while there is no key (a fresh release install); later from "Chiave TomTom" under the trips list. Headline "La tua chiave TomTom", one paragraph on why (own free key, quota), three numbered steps, an outlined "Apri my.tomtom.com/keys", the key field (plain text: the user checks what they pasted; under it the last 4 characters of the key in use), primary "Verifica e salva" ("Verifica in corso…" while checking), and a muted line that the key stays on the phone. A refused key or a network error is said under the field and nothing is saved.

## Widget (`widget/TripWidget.kt`, Glance)

One trip per widget, chosen when the widget is added (skipped with a single trip) and changeable from the launcher's pencil. Same palette as the app through `ui/theme/Palette` (Glance cannot read the Compose theme), day/night colors, 24dp corners.

- 4x2: trip name; "level · minutes (+delay)" in the traffic color, followed by "parti alle 18:14" for an arrival trip, the "(+delay)" left out when it is 0 (in the notification title too); the first notable slowdown with its street; "Checked at … · Next …"; the amber "Check now" pill, which reads "Checking…" until a newer result lands.
- 2x1: name and minutes on one line, the level in words, only the check time.
- Ground: the verdict card's tint for a fresh result, the error tint for a failed check, neutral otherwise.
- A result older than 2 hours or from an earlier day is never shown as current: neutral ground, everything muted except the time line, which turns bold ("Last check Mon 23:32").
- No trip yet / trip removed: one sentence saying what to do. Tapping anywhere opens the app on the widget's trip.

## Rules

- Traffic state is always written in words next to its color (the headline at level 0, the pill beside it above level 0).
- Text and controls on a tinted card take their tint from the card's ink or content color, not gray.
- Headings carry their own weight; labels go below a value or as a field label, never as a small caps line above a heading.
- System warnings are inline cards with one fixing action, re-checked on resume so they vanish once fixed; dialogs only for the system's own permission prompts and for confirming a deletion.
- Icons are vector drawables in `res/drawable` (`ic_settings`, `ic_back`); dots and lines are drawn in Compose.
- Launcher icon: white ground, an Ink road entering from the bottom edge with its centre line colored by traffic (green, yellow, red) up to an Ink arrival dot; monochrome is the road and the dot with a hole.
- Screen state survives rotation and theme changes: navigation in `rememberSaveable`, the editor draft and the shown trip in the ViewModels' `SavedStateHandle`.
