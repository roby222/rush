package io.github.roby222.rush

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
