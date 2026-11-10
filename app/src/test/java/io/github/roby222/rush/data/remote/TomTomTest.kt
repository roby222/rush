package io.github.roby222.rush.data.remote

import io.github.roby222.rush.home
import io.github.roby222.rush.model.CLOSURE
import io.github.roby222.rush.model.Check
import io.github.roby222.rush.model.TrafficEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TomTomTest {
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
}
