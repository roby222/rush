package io.github.roby222.rush.domain

import io.github.roby222.rush.data.remote.Route
import io.github.roby222.rush.data.remote.Summary
import io.github.roby222.rush.data.remote.TomTom
import io.github.roby222.rush.gym
import io.github.roby222.rush.home
import io.github.roby222.rush.model.Place
import io.github.roby222.rush.model.Trip
import io.github.roby222.rush.mwf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckOutcomeTest {
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
        assertFalse(outcome(settings(minDelay = 0), manual = true) { _, _ -> delay(30) }.notify)
        assertFalse(outcome(settings(from = null), manual = true) { _, _ -> error("not called") }.notify)
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
}
