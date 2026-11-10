package io.github.roby222.rush.data.local

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.roby222.rush.trip
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripStoreTest {
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
}
