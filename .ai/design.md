# Design

Rush looks like a map app: neutral surfaces, travel time colored by traffic, one brand accent. Mode: operate (a working screen read in a hurry, minutes before leaving). Material 3 components only; the brand lives in `Theme.kt`.

## Tokens (`Theme.kt`)

- Surfaces: white (light) / graphite `Ink` #1C1F24 (dark); cards `surfaceContainerHighest` #F1F3F4 / #2A2E35.
- Accent: `Amber` #FFB300 with `Ink` text. Reserved for the primary action ("Controlla ora", "Salva") and the destination dot.
- `primary` is Ink in light and Amber in dark: it colors section headings, focus and text buttons.
- Traffic: `trafficColor(level)` for text, dots and the route line, `statusContainer(level)` for the verdict card ground; level 0 green, 1 orange (#A04D00 light / #FCAD70 dark, kept clear of the brand Amber), 2 red. Contrast ≥4.5:1 on their grounds.
- Errors use the same red family (`error`, `errorContainer` set in both schemes), never the Material baseline.
- Shapes: cards 24dp, the verdict card 28dp, buttons are full pills 52dp high.
- No dynamic color: the fixed palette is the identity.

## Home: the verdict

The Home is the notification at full size (direction contract in `.impeccable/surfaces/`).

1. Verdict card, tinted by level: headline naming the problem and its street ("Coda in Corso Magenta", or "Tutto regolare"), beside it a filled pill with the level in sentence case (Rallentamento, Traffico intenso, Strada chiusa) shown only when level > 0, one sentence with minutes and how much earlier to leave, "Apri in Maps" when there is a problem, update time. Headline, pill and ground crossfade together when a new check changes the state (600 ms). Error and never-checked states reuse the same card.
2. "Sul percorso": the trip as a directions list on a 4dp route line colored like a map: green where traffic flows, the slowdown's color around each slowdown, gray before the first check. Departure (hollow dot), each notable slowdown in driving order, destination (amber dot).
3. "Controlla ora" (shows "Controllo in corso…" while the manual work runs), next automatic check, then system warnings.

## Rules

- Traffic state is always written in words next to its color (the headline at level 0, the pill beside it above level 0).
- Text and controls on a tinted card take their tint from the card's ink or content color, not gray.
- Headings carry their own weight; labels go below a value or as a field label, never as a small caps line above a heading.
- System warnings are inline cards with one fixing action, re-checked on resume so they vanish once fixed; dialogs only for the system's own permission prompts.
- Icons are vector drawables in `res/drawable` (`ic_settings`, `ic_back`); dots and lines are drawn in Compose.
- Launcher icon: white ground, an Ink road entering from the bottom edge with its centre line colored by traffic (green, yellow, red) up to an Ink arrival dot; monochrome is the road and the dot with a hole.
- Screen state survives rotation and theme changes (`rememberSaveable`).
