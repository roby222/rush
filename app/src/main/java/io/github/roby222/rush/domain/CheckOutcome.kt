package io.github.roby222.rush.domain

import io.github.roby222.rush.data.remote.NoRouteException
import io.github.roby222.rush.data.remote.Route
import io.github.roby222.rush.data.remote.toCheck
import io.github.roby222.rush.model.Check
import io.github.roby222.rush.model.Place
import io.github.roby222.rush.model.Trip
import kotlinx.coroutines.CancellationException

enum class CheckError { NO_FROM, NO_TO, NO_ROUTE, FAILED }

/** What one check produced: a [check], or an [error] with an optional technical [detail]. */
data class Outcome(val notify: Boolean, val check: Check? = null, val error: CheckError? = null, val detail: String? = null)

/**
 * The result of one check and whether to notify. A manual check never notifies: whoever tapped
 * "Check now" sees the result in the app or the widget. Cancellation is rethrown so a check
 * stopped by the system neither saves a result nor notifies a fake error.
 */
suspend fun checkOutcome(
    t: Trip,
    manual: Boolean,
    route: suspend (from: Place, to: Place) -> Route,
): Outcome = try {
    when {
        t.from == null -> Outcome(true, error = CheckError.NO_FROM)
        t.to == null -> Outcome(true, error = CheckError.NO_TO)
        else -> {
            val r = route(t.from, t.to)
            Outcome(r.summary.trafficDelayInSeconds >= t.minDelayMin * 60, r.toCheck(t.to.name.substringBefore(",")))
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: NoRouteException) {
    Outcome(true, error = CheckError.NO_ROUTE)
} catch (e: Exception) {
    Outcome(true, error = CheckError.FAILED, detail = e.message ?: e.javaClass.simpleName)
}.let { if (manual) it.copy(notify = false) else it }
