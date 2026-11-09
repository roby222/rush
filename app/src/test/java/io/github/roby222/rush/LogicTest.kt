package io.github.roby222.rush

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import io.github.roby222.rush.ui.Draft
import io.github.roby222.rush.ui.Missing
import io.github.roby222.rush.ui.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.ZoneId
import java.time.ZonedDateTime

class LogicTest {
    private val rome = ZoneId.of("Europe/Rome")
    private val mwf = setOf(MONDAY, WEDNESDAY, FRIDAY)
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, rome)

    @Test fun nextTrigger() {
        // 2026-11-09 is a Monday
        assertEquals(at(2026, 11, 9, 17, 20), nextTrigger(at(2026, 11, 9, 10, 0), mwf, 17, 20))
        assertEquals(at(2026, 11, 11, 17, 20), nextTrigger(at(2026, 11, 9, 17, 20), mwf, 17, 20))
        assertEquals(at(2026, 11, 16, 17, 20), nextTrigger(at(2026, 11, 13, 18, 0), mwf, 17, 20))
        assertEquals(at(2026, 11, 16, 17, 20), nextTrigger(at(2026, 11, 9, 18, 0), setOf(MONDAY), 17, 20))
        assertNull(nextTrigger(at(2026, 11, 9, 10, 0), emptySet(), 17, 20))
        // DST end in Rome: 2026-10-25, the local time stays 17:20
        assertEquals(at(2026, 10, 26, 17, 20), nextTrigger(at(2026, 10, 24, 18, 0), mwf, 17, 20))
    }

    @Test fun describeRoute() {
        val r = TomTom.parseRoute("""
            {"formatVersion":"0.0.12","routes":[{"summary":{"lengthInMeters":6400,"travelTimeInSeconds":840,
            "trafficDelayInSeconds":300,"noTrafficTravelTimeInSeconds":540,"extra":1},
            "sections":[{"sectionType":"TRAVEL_MODE"},
            {"sectionType":"TRAFFIC","simpleCategory":"JAM","delayInSeconds":240,"effectiveSpeedInKmh":11},
            {"sectionType":"TRAFFIC","simpleCategory":"ROAD_WORK","delayInSeconds":60}]}]}
        """)
        val c = r.toCheck("Palestra")
        assertEquals(Check("Palestra", 14, 5, 9, 6.4,
            listOf(TrafficEvent("JAM", null, 4, 11), TrafficEvent("ROAD_WORK", null, 1))), c)
    }

    private val home = Place("Casa", 45.0, 9.0)
    private val gym = Place("Palestra, Via Roma 1", 45.1, 9.1)
    private fun settings(from: Place? = home, to: Place? = gym, minDelay: Int = 10) =
        Trip(1, "Palestra", from, to, mwf, 17, 20, minDelay)
    private fun delay(min: Int) = Route(Summary(6400, 840 + min * 60, min * 60, 840))
    private fun outcome(s: Trip, manual: Boolean = false, route: suspend (Place, Place) -> Route) =
        runBlocking { checkOutcome(s, manual, route) }

    @Test fun outcomeThreshold() {
        assertFalse(outcome(settings()) { _, _ -> delay(5) }.notify)
        val o = outcome(settings()) { _, _ -> delay(10) }
        assertTrue(o.notify)
        assertEquals(24, o.check?.totalMin)
        assertEquals(10, o.check?.delayMin)
        assertTrue(outcome(settings(minDelay = 0)) { _, _ -> delay(0) }.notify)
        assertTrue(outcome(settings(), manual = true) { _, _ -> delay(0) }.notify)
    }

    @Test fun outcomeErrors() {
        assertEquals(Outcome(true, error = CheckError.NO_FROM),
            outcome(settings(from = null)) { _, _ -> error("not called") })
        assertEquals(Outcome(true, error = CheckError.NO_TO),
            outcome(settings(to = null)) { _, _ -> error("not called") })
        assertEquals(Outcome(true, error = CheckError.FAILED, detail = "HTTP 403: Forbidden"),
            outcome(settings()) { _, _ -> error("HTTP 403: Forbidden") })
        assertEquals(Outcome(true, error = CheckError.NO_ROUTE),
            outcome(settings()) { _, _ -> TomTom.parseRoute("""{"routes":[]}""") })
    }

    @Test fun outcomeCancellationRethrown() {
        assertThrows(CancellationException::class.java) {
            outcome(settings()) { _, _ -> throw CancellationException("stopped") }
        }
    }

    @Test fun searchUrl() {
        val global = TomTom.searchUrl("Piazza Università 1", null)
        assertTrue(global.encodedPath.endsWith("/search/2/search/Piazza%20Universit%C3%A0%201.json"))
        assertNull(global.queryParameter("lat"))
        assertNull(global.queryParameter("lon"))
        assertNull(global.queryParameter("radius"))
        val near = TomTom.searchUrl("Via Roma", home)
        assertEquals("45.0", near.queryParameter("lat"))
        assertEquals("9.0", near.queryParameter("lon"))
        assertEquals(NEAR_RADIUS_M.toString(), near.queryParameter("radius"))
    }

    @Test fun slowdownsNamedByStreet() {
        val c = TomTom.parseRoute("""
            {"routes":[{"summary":{"lengthInMeters":1500,"travelTimeInSeconds":960,"trafficDelayInSeconds":480,
            "noTrafficTravelTimeInSeconds":480},
            "sections":[{"sectionType":"TRAFFIC","simpleCategory":"ROAD_WORK","delayInSeconds":120,"startPointIndex":40},
            {"sectionType":"TRAFFIC","simpleCategory":"JAM","delayInSeconds":360,"effectiveSpeedInKmh":9,"startPointIndex":12}],
            "guidance":{"instructions":[{"pointIndex":0,"street":"Piazza del Duomo"},
            {"pointIndex":10,"street":"Corso Magenta"},{"pointIndex":35,"street":"","roadNumbers":["SS11"]}]}}]}
        """).toCheck("Via Torino")
        // Sorted in driving order; the street is the last one named before the slowdown starts.
        assertEquals(listOf("JAM" to "Corso Magenta", "ROAD_WORK" to "SS11"), c.events.map { it.kind to it.street })
        assertEquals(2, c.level)
        assertEquals(2, c.copy(delayMin = 0, events = listOf(TrafficEvent(CLOSURE, delayMin = 0))).level)
        val quiet = c.copy(events = listOf(TrafficEvent("JAM", "Via X", 0), TrafficEvent(CLOSURE, "Via Y", 0)))
        assertEquals(listOf("Via Y"), quiet.notable.map { it.street })  // zero-delay jams stay silent
    }

    @Test fun backToBackSectionsMerge() {
        val c = TomTom.parseRoute("""
            {"routes":[{"summary":{"lengthInMeters":30000,"travelTimeInSeconds":3780,"trafficDelayInSeconds":300},
            "sections":[{"sectionType":"TRAFFIC","simpleCategory":"JAM","delayInSeconds":180,"effectiveSpeedInKmh":20,"startPointIndex":50},
            {"sectionType":"TRAFFIC","simpleCategory":"JAM","delayInSeconds":120,"effectiveSpeedInKmh":27,"startPointIndex":70}],
            "guidance":{"instructions":[{"pointIndex":40,"street":"Long Island Expy E"}]}}]}
        """).toCheck("JFK")
        assertEquals(listOf(TrafficEvent("JAM", "Long Island Expy E", 5, 20)), c.events)
    }

    @Test fun trafficLevels() {
        assertEquals(0, trafficLevel(14, 2))   // up to 2 min is normal
        assertEquals(1, trafficLevel(63, 5))   // +5 min is a slowdown even on a long trip
        assertEquals(1, trafficLevel(14, 3))   // 27% on a short trip
        assertEquals(2, trafficLevel(14, 5))   // 56%: a short trip turns intense sooner
        assertEquals(2, trafficLevel(70, 10))  // 10 min is intense on any trip
    }

    // --- multiple trips (ticket 07) ---

    private fun trip(id: Int, days: Set<java.time.DayOfWeek> = mwf, hour: Int = 17, minute: Int = 20) =
        Trip(id, "T$id", home, gym, days, hour, minute, 0)

    @Test fun tripJsonRoundTrip() {
        val p = mutablePreferencesOf()
        val saved = p.putTrip(trip(0, days = setOf(TUESDAY, THURSDAY), hour = 8, minute = 0))
        assertEquals(listOf(saved), p.trips())
    }

    @Test fun tripIdsGrowAndAreNeverReused() {
        val p = mutablePreferencesOf(stringPreferencesKey("from_name") to "old", intPreferencesKey("hour") to 9)
        val a = p.putTrip(trip(0))
        val b = p.putTrip(trip(0, hour = 8))
        assertEquals(1 to 2, a.id to b.id)
        assertTrue("v0.2.0 keys are dropped", p.asMap().keys.none { it.name == "from_name" || it.name == "hour" })
        p[Keys.result(b.id)] = "{}"
        p.removeTrip(b.id)
        assertNull(p[Keys.result(b.id)])
        assertEquals(3, p.putTrip(trip(0, hour = 9)).id)
        p.putTrip(a.copy(name = "Palestra"))
        assertEquals(listOf("Palestra", "T0"), p.trips().map { it.name })
    }

    @Test fun oneAlarmPerTrip() {
        val now = at(2026, 11, 9, 10, 0) // Monday
        val plan = alarmPlan(listOf(trip(1), trip(2, days = setOf(TUESDAY), hour = 8, minute = 0), trip(3, days = emptySet())), now)
        assertEquals(mapOf(1 to at(2026, 11, 9, 17, 20), 2 to at(2026, 11, 10, 8, 0)), plan)
        assertTrue(scheduledWork(1) != scheduledWork(2) && manualWork(1) != scheduledWork(1))
    }

    @Test fun sameMinuteOnASharedDayConflicts() {
        val gymTrip = trip(1).copy(name = "Palestra")
        assertEquals(gymTrip, timeConflict(trip(0, days = setOf(FRIDAY)), listOf(gymTrip)))
        assertNull(timeConflict(trip(0, days = setOf(TUESDAY)), listOf(gymTrip)))  // same time, other days
        assertNull(timeConflict(trip(0, minute = 21), listOf(gymTrip)))
        assertNull(timeConflict(gymTrip, listOf(gymTrip)))                          // editing itself
    }

    @Test fun featuredTripPrefersARecentProblem() {
        val now = at(2026, 11, 9, 10, 0)
        val morning = trip(1, days = setOf(MONDAY), hour = 11, minute = 0)          // next check: today 11:00
        val evening = trip(2)                                                        // next check: today 17:20
        fun result(hoursAgo: Long, delay: Int?) = TripResult(now.minusHours(hoursAgo).toInstant().toEpochMilli(), "", "",
            delay?.let { Check("X", 20 + it, it, 20, 5.0) })
        val trips = listOf(evening, morning)
        assertEquals(morning, featuredTrip(trips, emptyMap(), now))                  // no results: next check
        assertEquals(evening, featuredTrip(trips, mapOf(2 to result(1, 8)), now))    // slowdown 1 h ago
        assertEquals(evening, featuredTrip(trips, mapOf(2 to result(1, null)), now)) // failed check 1 h ago
        assertEquals(morning, featuredTrip(trips, mapOf(2 to result(4, 8)), now))    // too old
        assertEquals(morning, featuredTrip(trips, mapOf(2 to result(1, 0)), now))    // all clear is not a problem
        assertNull(featuredTrip(emptyList(), emptyMap(), now))
    }

    @Test fun editorValidation() {
        val gymTrip = trip(1).copy(name = "Palestra")
        val ok = Draft(0, "Ufficio", home, gym, setOf(TUESDAY), "8:05", "3")
        val v = validate(ok, listOf(gymTrip))
        assertTrue(v.canSave)
        assertEquals(Trip(0, "Ufficio", home, gym, setOf(TUESDAY), 8, 5, 3), v.trip)
        assertEquals(Missing.NAME, validate(ok.copy(name = " "), emptyList()).missing)
        assertEquals(Missing.PLACES, validate(ok.copy(to = null), emptyList()).missing)
        assertTrue(validate(ok.copy(time = "25:00"), emptyList()).badTime)
        assertTrue(validate(ok.copy(minDelay = "-1"), emptyList()).badDelay)
        val clash = validate(ok.copy(days = setOf(WEDNESDAY), time = "17:20"), listOf(gymTrip))
        assertEquals(gymTrip, clash.conflict)
        assertFalse(clash.canSave)
    }
}
