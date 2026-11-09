package io.github.roby222.rush

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
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
        Settings(from, to, mwf, 17, 20, minDelay, null, null, null, null)
    private fun delay(min: Int) = Route(Summary(6400, 840 + min * 60, min * 60, 840))
    private fun outcome(s: Settings, manual: Boolean = false, route: suspend (Place, Place) -> Route) =
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
}
