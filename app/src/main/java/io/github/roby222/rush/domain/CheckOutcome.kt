package io.github.roby222.rush.domain

import io.github.roby222.rush.data.remote.KeyRefusedException
import io.github.roby222.rush.data.remote.MissingKeyException
import io.github.roby222.rush.data.remote.NoRouteException
import io.github.roby222.rush.data.remote.Route
import io.github.roby222.rush.data.remote.toCheck
import io.github.roby222.rush.model.Check
import io.github.roby222.rush.model.Place
import io.github.roby222.rush.model.Trip
import kotlinx.coroutines.CancellationException

/** How much more delay makes an arrival trip's second check worth a notification after the first one. */
const val FOLLOW_UP_WORSE_MIN = 3

enum class CheckError { NO_FROM, NO_TO, NO_ROUTE, NO_KEY, BAD_KEY, FAILED }

/** What one check produced: a [check], or an [error] with an optional technical [detail]. */
data class Outcome(val notify: Boolean, val check: Check? = null, val error: CheckError? = null, val detail: String? = null)

/**
 * The result of one check and whether to notify. A manual check never notifies: whoever tapped
 * "Check now" sees the result in the app or the widget. Cancellation is rethrown so a check
 * stopped by the system neither saves a result nor notifies a fake error.
 *
 * [earlier] is the first check of the same arrival when this is its follow-up: the follow-up only
 * speaks when it adds something, i.e. the first check stayed quiet or the delay grew by [FOLLOW_UP_WORSE_MIN].
 */
suspend fun checkOutcome(
    t: Trip,
    manual: Boolean,
    earlier: Check? = null,
    route: suspend (from: Place, to: Place) -> Route,
): Outcome = try {
    when {
        t.from == null -> Outcome(true, error = CheckError.NO_FROM)
        t.to == null -> Outcome(true, error = CheckError.NO_TO)
        else -> {
            val r = route(t.from, t.to)
            val c = r.toCheck(t.to.name.substringBefore(","))
            // The earlier check's delay is in whole minutes: close enough to tell whether it notified.
            val news = earlier == null || earlier.delayMin < t.minDelayMin || c.delayMin >= earlier.delayMin + FOLLOW_UP_WORSE_MIN
            Outcome(r.summary.trafficDelayInSeconds >= t.minDelayMin * 60 && news, c)
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: NoRouteException) {
    Outcome(true, error = CheckError.NO_ROUTE)
} catch (e: MissingKeyException) {
    Outcome(true, error = CheckError.NO_KEY)
} catch (e: KeyRefusedException) {
    Outcome(true, error = CheckError.BAD_KEY)
} catch (e: Exception) {
    Outcome(true, error = CheckError.FAILED, detail = e.message ?: e.javaClass.simpleName)
}.let { if (manual) it.copy(notify = false) else it }
