package io.github.roby222.rush.widget

import io.github.roby222.rush.at
import io.github.roby222.rush.data.local.result
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripWidgetTest {
    @Test fun widgetResultTurnsStale() {
        val now = at(2026, 11, 10, 18, 0)
        fun ago(h: Long, m: Long = 0) = now.minusHours(h).minusMinutes(m).toInstant().toEpochMilli()
        assertFalse(isStale(ago(1), now))
        assertFalse(isStale(ago(2), now))                          // exactly the limit
        assertTrue(isStale(ago(2, 1), now))
        val justAfterMidnight = at(2026, 11, 10, 0, 30)
        assertTrue(isStale(justAfterMidnight.minusHours(1).toInstant().toEpochMilli(), justAfterMidnight)) // yesterday
    }
    @Test fun widgetCheckingEndsWithANewerResult() {
        val tap = 1_000_000L
        assertTrue(isChecking(tap, lastAt = tap - 60_000, now = tap + 5_000))   // older result: still checking
        assertTrue(isChecking(tap, lastAt = null, now = tap + 5_000))           // never checked before
        assertFalse(isChecking(tap, lastAt = tap + 20_000, now = tap + 21_000)) // result arrived
        assertFalse(isChecking(tap, lastAt = null, now = tap + 4 * 60_000))     // gave up after 3 minutes
        assertFalse(isChecking(0, lastAt = null, now = tap))                    // never tapped
    }
}
