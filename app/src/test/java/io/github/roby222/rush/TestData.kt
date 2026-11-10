package io.github.roby222.rush

import io.github.roby222.rush.model.Place
import io.github.roby222.rush.model.Trip
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.ZoneId
import java.time.ZonedDateTime

// Shared test fixtures: public demo places and times in Rome's zone.
val rome: ZoneId = ZoneId.of("Europe/Rome")
val mwf = setOf(MONDAY, WEDNESDAY, FRIDAY)
fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): ZonedDateTime = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, rome)

val home = Place("Casa", 45.0, 9.0)
val gym = Place("Palestra, Via Roma 1", 45.1, 9.1)

fun trip(id: Int, days: Set<DayOfWeek> = mwf, hour: Int = 17, minute: Int = 20) =
    Trip(id, "T$id", home, gym, days, hour, minute, 0)
