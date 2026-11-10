package io.github.roby222.rush.domain

import io.github.roby222.rush.model.Trip
import io.github.roby222.rush.model.TripResult
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/** A problem result stays in front on the Home for this long. */
val FEATURED_PROBLEM_WINDOW: Duration = Duration.ofHours(3)

/** A one-off trip stays this long after its time, so the check its alarm started can still find it. */
val ONE_OFF_GRACE: Duration = Duration.ofHours(1)

/** An arrival trip is checked twice, this long before its usual departure (arrival − [Trip.usualMin])... */
val FIRST_LEAD: Duration = Duration.ofMinutes(30)
/** ...and again this long before it, to catch traffic that built up after the first check. */
val FOLLOW_UP_LEAD: Duration = Duration.ofMinutes(10)

/** The time a one-off trip is set for (its check or its arrival), in [now]'s zone; null for a weekly trip. */
fun oneOffAt(t: Trip, now: ZonedDateTime): ZonedDateTime? = t.day()?.atTime(t.hour, t.minute)?.atZone(now.zone)

/** The times [t] is set for around [now]: its one-off time, or each weekly one from yesterday to a week ahead. */
private fun occurrences(t: Trip, now: ZonedDateTime): List<ZonedDateTime> =
    oneOffAt(t, now)?.let(::listOf) ?: (-1L..8L).map { now.toLocalDate().plusDays(it) }
        .filter { it.dayOfWeek in t.days }.map { it.atTime(t.hour, t.minute).atZone(now.zone) }

/** The checks for one occurrence [at]: at that time, or twice before the usual departure of an arrival trip. */
private fun checksFor(t: Trip, at: ZonedDateTime): List<ZonedDateTime> {
    if (!t.arriveBy) return listOf(at)
    val usualDeparture = at.minusMinutes((t.usualMin ?: 0).toLong())
    return listOf(usualDeparture.minus(FIRST_LEAD), usualDeparture.minus(FOLLOW_UP_LEAD))
}

/** The next check of [t] strictly after [now]; null for a weekly trip without days or a one-off trip past its checks. */
fun nextCheck(t: Trip, now: ZonedDateTime): ZonedDateTime? =
    occurrences(t, now).flatMap { checksFor(t, it) }.sorted().firstOrNull { it.isAfter(now) }

/** The arrival a check at [now] is for (the next one not yet reached); null for a trip with a check time. */
fun arrivalFor(t: Trip, now: ZonedDateTime): ZonedDateTime? =
    if (!t.arriveBy) null else occurrences(t, now).firstOrNull { !it.isBefore(now) }

/** Whether a scheduled check at [now] is the second check of an arrival trip (alarms may run a few minutes late). */
fun isFollowUp(t: Trip, now: ZonedDateTime): Boolean = t.arriveBy && occurrences(t, now).any {
    val second = checksFor(t, it).last()
    !now.isBefore(second.minusMinutes(5)) && now.isBefore(second.plusMinutes(15))
}

/** A one-off trip whose time passed more than [ONE_OFF_GRACE] ago, e.g. with the phone off: it is removed. */
fun expired(t: Trip, now: ZonedDateTime): Boolean = oneOffAt(t, now)?.isBefore(now.minus(ONE_OFF_GRACE)) == true

/** Whether a scheduled check at [now] is the last one of [t]: a one-off trip whose last check time has come. */
fun doneAfterCheck(t: Trip, now: ZonedDateTime): Boolean =
    oneOffAt(t, now)?.let { checksFor(t, it).last().isAfter(now) } == false

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

/**
 * Another trip set for the same hour:minute on a shared day, with the same meaning (check or arrival):
 * two checks in one minute make no sense.
 */
fun timeConflict(draft: Trip, trips: List<Trip>): Trip? = trips.firstOrNull {
    it.id != draft.id && it.arriveBy == draft.arriveBy && it.hour == draft.hour && it.minute == draft.minute &&
        sharesADay(it, draft)
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
