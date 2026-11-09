# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
Commuters with a recurring trip at a fixed time (the author's case: home to the gym, Mon/Wed/Fri late
afternoon), deciding how and when to leave. Built first for the author, meant to work for other people too:
it must be understandable at first launch without explanation.

## Product Purpose
Watch a recurring trip so the user does not have to. When traffic is normal, Rush stays silent. When it is
not, Rush says how bad it is and where the problem is, early enough to act: leave earlier, take the
motorbike, or try another road.

Success: the user never opens a map app "just to check", and is never surprised by a jam Rush could have seen.

## Positioning
A scheduled, single-trip watcher rather than a navigator: it speaks only when the usual trip is not usual.

## Operating Context
- Two touchpoints: the scheduled notification (read in a hurry, before leaving) and the app, opened to
  re-check or to understand a problem.
- The decision happens minutes before leaving, often on the move; glanceability beats detail.
- Live data from TomTom Routing (travel time, delay, traffic sections: jams, road works, closures) and
  TomTom Search for places. Free tier, about 2,500 requests/day.

## Capabilities and Constraints
- Scheduled exact alarm on chosen days and time; manual "check now"; threshold in minutes of delay
  (0 = always notify).
- Places are searched once and saved as coordinates; search near the other saved place.
- No backend; data stays on the device. Sideloaded (uses USE_EXACT_ALARM and the direct battery
  exemption request, both restricted on Google Play).
- UI languages: English (fallback) and Italian, chosen by the device language.

## Brand Commitments
- Name: Rush.
- Look requested by the user: similar to Google Maps (neutral surfaces, travel time colored by traffic),
  with a graphite/amber direction already chosen in v0.1.0.
- No specific addresses in code, docs or screenshots; the app stays generic.

## Evidence on Hand
- Real screens (demo routes New York and Milan, English): `docs/screenshots/home-traffic.png`, `home-clear.png`, `settings.png`.
- No users, testimonials or metrics beyond the author.

## Product Principles
1. Silence is the normal state: no news means the usual trip.
2. When something is wrong, say how bad and where, in one glance.
3. Every alert should point to an action: leave earlier, switch vehicle, change road.
4. Configure once, then forget the app exists.

## Accessibility & Inclusion
Traffic state must never rely on color alone (text label always present); readable at a glance outdoors.
