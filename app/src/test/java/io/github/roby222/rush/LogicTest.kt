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
        val (title, text) = describe("Palestra", r)
        assertEquals("Palestra: 14 min (+5 traffico)", title)
        assertEquals("6.4 km · senza traffico 9 min\nCoda: +4 min (11 km/h)\nLavori: +1 min", text.replace(',', '.'))
    }

    private val home = Place("Casa", 45.0, 9.0)
    private val gym = Place("Palestra, Via Roma 1", 45.1, 9.1)
    private fun settings(from: Place? = home, to: Place? = gym, minDelay: Int = 10) =
        Settings(from, to, mwf, 17, 20, minDelay, null, null, null)
    private fun delay(min: Int) = Route(Summary(6400, 840 + min * 60, min * 60, 840))
    private fun outcome(s: Settings, manual: Boolean = false, route: suspend (Place, Place) -> Route) =
        runBlocking { checkOutcome(s, manual, route) }

    @Test fun outcomeThreshold() {
        assertFalse(outcome(settings()) { _, _ -> delay(5) }.third)
        val (title, _, notify) = outcome(settings()) { _, _ -> delay(10) }
        assertEquals("Palestra: 24 min (+10 traffico)", title)
        assertTrue(notify)
        assertTrue(outcome(settings(minDelay = 0)) { _, _ -> delay(0) }.third)
        assertTrue(outcome(settings(), manual = true) { _, _ -> delay(0) }.third)
    }

    @Test fun outcomeErrors() {
        assertEquals(Triple("Traffico: errore", "Imposta la partenza", true),
            outcome(settings(from = null)) { _, _ -> error("not called") })
        assertEquals(Triple("Traffico: errore", "Imposta l'arrivo", true),
            outcome(settings(to = null)) { _, _ -> error("not called") })
        assertEquals(Triple("Traffico: errore", "HTTP 403: Forbidden", true),
            outcome(settings()) { _, _ -> error("HTTP 403: Forbidden") })
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

    @Test fun titleAndTrafficLevel() {
        assertEquals(Triple("Palestra", 24, 10), parseTitle(describe("Palestra", delay(10)).first))
        assertNull(parseTitle("Traffico: errore"))
        assertEquals(0, trafficLevel(14, 2))  // small absolute delay
        assertEquals(0, trafficLevel(40, 4))  // 11% of free flow
        assertEquals(1, trafficLevel(14, 3))  // 27%
        assertEquals(2, trafficLevel(14, 5))  // 56%
    }
}
