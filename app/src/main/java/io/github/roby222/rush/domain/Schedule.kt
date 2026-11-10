package io.github.roby222.rush.domain

import io.github.roby222.rush.model.Trip
import io.github.roby222.rush.model.TripResult
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

/** A problem result stays in front on the Home for this long. */
val FEATURED_PROBLEM_WINDOW: Duration = Duration.ofHours(3)

/** First occurrence strictly after [now] on one of [days] at hour:minute, or null if no days. */
fun nextTrigger(now: ZonedDateTime, days: Set<DayOfWeek>, hour: Int, minute: Int): ZonedDateTime? =
    (0L..7L).asSequence()
        .map { now.toLocalDate().plusDays(it) }
        .filter { it.dayOfWeek in days }
        .map { it.atTime(hour, minute).atZone(now.zone) }
        .firstOrNull { it.isAfter(now) }

/** A one-off trip stays this long after its time, so the check its alarm started can still find it. */
val ONE_OFF_GRACE: Duration = Duration.ofHours(1)

/** When a one-off trip is checked, in [now]'s zone; null for a weekly trip. */
fun oneOffAt(t: Trip, now: ZonedDateTime): ZonedDateTime? = t.day()?.atTime(t.hour, t.minute)?.atZone(now.zone)

/** The next check of [t] after [now]: the next weekly occurrence, or the one-off time while still ahead. */
fun nextCheck(t: Trip, now: ZonedDateTime): ZonedDateTime? =
    if (t.date == null) nextTrigger(now, t.days, t.hour, t.minute) else oneOffAt(t, now)?.takeIf { it.isAfter(now) }

/** A one-off trip whose time passed more than [ONE_OFF_GRACE] ago, e.g. with the phone off: it is removed. */
fun expired(t: Trip, now: ZonedDateTime): Boolean = oneOffAt(t, now)?.isBefore(now.minus(ONE_OFF_GRACE)) == true

/** Whether a scheduled check at [now] is the last one of [t]: a one-off trip whose time has come. */
fun doneAfterCheck(t: Trip, now: ZonedDateTime): Boolean = oneOffAt(t, now)?.isAfter(now) == false

/** Next alarm time per trip id; weekly trips without days and past one-off trips have none. */
fun alarmPlan(trips: List<Trip>, now: ZonedDateTime): Map<Int, ZonedDateTime> =
    trips.mapNotNull { t -> nextCheck(t, now)?.let { t.id to it } }.toMap()

/** Whether two trips can run on the same calendar day. */
private fun sharesADay(a: Trip, b: Trip): Boolean {
    val da = a.day()
    val db = b.day()
    return when {
        da != null && db != null -> da == db
        da != null -> da.dayOfWeek in b.days
        db != null -> db.dayOfWeek in a.days
        else -> a.days.any { it in b.days }
    }
}

/** Another trip checked at the same hour:minute on a shared day: two checks in one minute make no sense. */
fun timeConflict(draft: Trip, trips: List<Trip>): Trip? = trips.firstOrNull {
    it.id != draft.id && it.hour == draft.hour && it.minute == draft.minute && sharesADay(it, draft)
}

/**
 * The trip the Home shows first: the latest one with a problem (slowdown or failed check) in the last
 * [FEATURED_PROBLEM_WINDOW], otherwise the one checked next, otherwise the first.
 */
fun featuredTrip(trips: List<Trip>, results: Map<Int, TripResult>, now: ZonedDateTime): Trip? {
    val since = now.toInstant().minus(FEATURED_PROBLEM_WINDOW)
    val problem = trips.mapNotNull { t -> results[t.id]?.let { t to it } }
        .filter { (_, r) -> (r.check == null || r.check.level > 0) && !Instant.ofEpochMilli(r.lastAt).isBefore(since) }
        .maxByOrNull { (_, r) -> r.lastAt }?.first
    val plan = alarmPlan(trips, now)
    return problem ?: trips.filter { it.id in plan }.minByOrNull { plan.getValue(it.id) } ?: trips.firstOrNull()
}
