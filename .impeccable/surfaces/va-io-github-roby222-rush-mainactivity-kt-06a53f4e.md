---
version: 1
slug: "va-io-github-roby222-rush-mainactivity-kt-06a53f4e"
primary_target: "app/src/main/java/io/github/roby222/rush/MainActivity.kt"
related_targets: []
---

# Surface: Home (MainActivity.kt HomeScreen)

Mode: operate. Task: decide in one glance whether today's trip is normal and, if not, how bad and where, then act (leave earlier, switch vehicle, open another road in Maps). States: never checked, regular, slowed, heavy/closed, error, checking.

## Direction contract

THESIS: the Home is the notification at full size: one stamped verdict, one sentence naming the problem and its street, the actions under it. Refuses the dashboard of numbers (minutes card + route card + settings list) that every traffic app ships.

OWN-WORLD: graphite ground #1C1F24 / white in light; one status card tinted by severity (green, amber, red containers) with an uppercase stamp in the traffic color; amber #FFB300 only for the primary action and the destination dot; Material 3 cards 28dp, chips, Roboto.

STORY: the user sees the stamp and the sentence, understands whether today differs from usual and where, and either does nothing or taps "Apri in Maps" / leaves earlier by the stated minutes.

FIRST VIEWPORT: top app bar; status card first and largest: stamp (REGOLARE / RALLENTAMENTO / TRAFFICO INTENSO / STRADA CHIUSA), headline (e.g. "Coda in Corso Magenta" or "Tutto regolare"), sentence "Verso X oggi servono 16 min invece di 8", "Parti 8 min prima", chip "Apri in Maps". Then "Sul percorso" events list with street names, route card with origin/destination dots, full-width amber "Controlla ora", updated/next line.

FORM: dealt structure "Il verdetto", position 7 on the ordered list. seed:2adaeb85a69bc74a
Signature move: the severity stamp, which changes with a crossfade when a new check lands.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
