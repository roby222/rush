package io.github.roby222.rush.ui.editor

import io.github.roby222.rush.at
import io.github.roby222.rush.gym
import io.github.roby222.rush.home
import io.github.roby222.rush.model.Trip
import io.github.roby222.rush.trip
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripEditorValidationTest {
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

    @Test fun oneOffValidation() {
        val now = at(2026, 11, 10, 12, 0)
        val d = Draft(0, "Dentista", home, gym, setOf(MONDAY), "15:00", "0", java.time.LocalDate.of(2026, 11, 10))
        val v = validate(d, emptyList(), now)
        assertTrue(v.canSave)
        assertEquals(Trip(0, "Dentista", home, gym, emptySet(), 15, 0, 0, "2026-11-10"), v.trip)
        val past = validate(d.copy(time = "11:59"), emptyList(), now)
        assertTrue(past.past)
        assertFalse(past.canSave)
        // An arrival trip keeps its mode; only the arrival itself must be ahead (its checks need the duration).
        val arrival = validate(d.copy(arriveBy = true, time = "12:10"), emptyList(), now)
        assertTrue(arrival.canSave)
        assertTrue(arrival.trip!!.arriveBy)
    }
}
