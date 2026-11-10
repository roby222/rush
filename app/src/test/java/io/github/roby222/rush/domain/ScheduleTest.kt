package io.github.roby222.rush.domain

import androidx.datastore.preferences.core.mutablePreferencesOf
import io.github.roby222.rush.at
import io.github.roby222.rush.data.local.putTrip
import io.github.roby222.rush.data.local.result
import io.github.roby222.rush.data.local.trips
import io.github.roby222.rush.model.Check
import io.github.roby222.rush.model.TripResult
import io.github.roby222.rush.mwf
import io.github.roby222.rush.trip
import io.github.roby222.rush.work.manualWork
import io.github.roby222.rush.work.scheduledWork
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {
    @Test fun nextWeeklyCheck() {
        // 2026-11-09 is a Monday
        assertEquals(at(2026, 11, 9, 17, 20), nextCheck(trip(1), at(2026, 11, 9, 10, 0)))
        assertEquals(at(2026, 11, 11, 17, 20), nextCheck(trip(1), at(2026, 11, 9, 17, 20)))
        assertEquals(at(2026, 11, 16, 17, 20), nextCheck(trip(1), at(2026, 11, 13, 18, 0)))
        assertEquals(at(2026, 11, 16, 17, 20), nextCheck(trip(1, days = setOf(MONDAY)), at(2026, 11, 9, 18, 0)))
        assertNull(nextCheck(trip(1, days = emptySet()), at(2026, 11, 9, 10, 0)))
        // DST end in Rome: 2026-10-25, the local time stays 17:20
        assertEquals(at(2026, 10, 26, 17, 20), nextCheck(trip(1), at(2026, 10, 24, 18, 0)))
    }

    // --- arrival time (ticket 10) ---

    /** Be at the gym at 18:50 on Mon/Wed/Fri; 22 min without traffic: usual departure 18:28. */
    private val arrive = trip(7, hour = 18, minute = 50).copy(arriveBy = true, usualMin = 22)

    @Test fun arrivalTripIsCheckedTwiceBeforeTheUsualDeparture() {
        assertEquals(at(2026, 11, 9, 17, 58), nextCheck(arrive, at(2026, 11, 9, 10, 0)))
        assertEquals(at(2026, 11, 9, 18, 18), nextCheck(arrive, at(2026, 11, 9, 17, 58)))
        assertEquals(at(2026, 11, 11, 17, 58), nextCheck(arrive, at(2026, 11, 9, 18, 18)))
        assertEquals(at(2026, 11, 9, 18, 50), arrivalFor(arrive, at(2026, 11, 9, 17, 58)))
        assertNull(arrivalFor(trip(1), at(2026, 11, 9, 17, 58)))
        assertFalse(isFollowUp(arrive, at(2026, 11, 9, 17, 58)))
        assertTrue(isFollowUp(arrive, at(2026, 11, 9, 18, 18)))
        assertTrue(isFollowUp(arrive, at(2026, 11, 9, 18, 25)))        // a late alarm is still the second check
        assertFalse(isFollowUp(trip(1), at(2026, 11, 9, 17, 20)))
        // An arrival just after midnight is checked the evening before.
        val early = arrive.copy(hour = 0, minute = 10, days = setOf(TUESDAY))
        assertEquals(at(2026, 11, 9, 23, 18), nextCheck(early, at(2026, 11, 9, 20, 0)))
    }

    @Test fun arrivalCheckTimesForTheEditor() {
        assertEquals(listOf(LocalTime.of(17, 58), LocalTime.of(18, 18)), arrivalCheckTimes(18, 50, 22))
        assertEquals(listOf(LocalTime.of(23, 18), LocalTime.of(23, 38)), arrivalCheckTimes(0, 10, 22))  // the evening before
    }

    @Test fun oneOffArrivalEndsAfterItsSecondCheck() {
        val dentist = arrive.copy(days = emptySet(), date = "2026-11-10", hour = 15, minute = 0)   // checks 14:08, 14:28
        assertEquals(at(2026, 11, 10, 14, 8), nextCheck(dentist, at(2026, 11, 10, 9, 0)))
        assertFalse(doneAfterCheck(dentist, at(2026, 11, 10, 14, 8)))
        assertTrue(doneAfterCheck(dentist, at(2026, 11, 10, 14, 28)))
        assertNull(nextCheck(dentist, at(2026, 11, 10, 14, 28)))
    }

    @Test fun checkAndArrivalTimesDoNotConflict() {
        assertNull(timeConflict(arrive.copy(id = 0, hour = 17, minute = 20), listOf(trip(1))))
        assertEquals(arrive, timeConflict(arrive.copy(id = 0, days = setOf(FRIDAY)), listOf(arrive)))
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

    @Test fun oneOffTripIsCheckedOnceThenExpires() {
        // 2026-11-10 is a Tuesday
        val t = trip(5, days = emptySet(), hour = 15, minute = 0).copy(date = "2026-11-10")
        assertEquals(at(2026, 11, 10, 15, 0), nextCheck(t, at(2026, 11, 10, 9, 0)))
        assertNull(nextCheck(t, at(2026, 11, 10, 15, 0)))                 // time reached: no further check
        assertEquals(mapOf(5 to at(2026, 11, 10, 15, 0)), alarmPlan(listOf(t), at(2026, 11, 10, 9, 0)))
        assertFalse(expired(t, at(2026, 11, 10, 15, 30)))                 // its check may still be running
        assertTrue(expired(t, at(2026, 11, 10, 16, 1)))                   // e.g. the phone was off
        assertFalse(expired(trip(1), at(2026, 11, 10, 16, 1)))            // weekly trips never expire
        assertFalse(doneAfterCheck(t, at(2026, 11, 10, 9, 0)))            // early (forged) check keeps it
        assertTrue(doneAfterCheck(t, at(2026, 11, 10, 15, 0)))
        assertFalse(doneAfterCheck(trip(1), at(2026, 11, 10, 18, 0)))
        val json = mutablePreferencesOf().apply { putTrip(t.copy(id = 0)) }
        assertEquals("2026-11-10", json.trips().single().date)
    }

    @Test fun oneOffTripConflicts() {
        val gymMwf = trip(1)                                               // Mon/Wed/Fri 17:20
        val wed = trip(0, days = emptySet()).copy(date = "2026-11-11")    // a Wednesday, 17:20
        val tue = wed.copy(date = "2026-11-10")
        assertEquals(gymMwf, timeConflict(wed, listOf(gymMwf)))
        assertNull(timeConflict(tue, listOf(gymMwf)))
        assertEquals(wed.copy(id = 2), timeConflict(gymMwf, listOf(wed.copy(id = 2))))
        assertNull(timeConflict(tue.copy(id = 3), listOf(tue.copy(id = 4, date = "2026-11-17"))))
    }
}
