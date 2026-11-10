package io.github.roby222.rush.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.roby222.rush.R
import io.github.roby222.rush.data.local.deleteTrip
import io.github.roby222.rush.data.local.result
import io.github.roby222.rush.data.local.saveResult
import io.github.roby222.rush.data.local.setUsualMin
import io.github.roby222.rush.data.local.trips
import io.github.roby222.rush.data.remote.TomTom
import io.github.roby222.rush.domain.CheckError
import io.github.roby222.rush.domain.FIRST_LEAD
import io.github.roby222.rush.domain.arrivalFor
import io.github.roby222.rush.domain.isFollowUp
import io.github.roby222.rush.domain.checkOutcome
import io.github.roby222.rush.domain.doneAfterCheck
import io.github.roby222.rush.model.TripResult
import io.github.roby222.rush.notification.describe
import io.github.roby222.rush.notification.notification
import io.github.roby222.rush.notification.notify
import io.github.roby222.rush.widget.TripWidget
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val manual = inputData.getBoolean("manual", false)
        // The trip may have been deleted while this check waited: nothing to do then.
        val trip = ctx.trips().firstOrNull { it.id == inputData.getInt(EXTRA_TRIP, 0) } ?: return Result.success()
        val now = ZonedDateTime.now()
        // An arrival trip's second check compares with its first, i.e. the result saved shortly before.
        val earlier = if (manual || !isFollowUp(trip, now)) null
        else ctx.result(trip.id)?.takeIf { it.lastAt >= now.minus(FIRST_LEAD).toInstant().toEpochMilli() }?.check
        val o = checkOutcome(trip, manual, earlier) { from, to ->
            // Interruptible so a cancelled work aborts the HTTP call instead of finishing it.
            runInterruptible(Dispatchers.IO) { TomTom.route(from.lat, from.lon, to.lat, to.lon) }
        }
        val leaveBy = o.check?.let { c -> arrivalFor(trip, now)?.minusMinutes(c.totalMin.toLong()) }
        val (title, text) = o.check?.let { ctx.resources.describe(trip.name, it, leaveBy, now) } ?: (ctx.getString(R.string.error_title, trip.name) to
            when (o.error) {
                CheckError.NO_FROM -> ctx.getString(R.string.error_no_from)
                CheckError.NO_TO -> ctx.getString(R.string.error_no_to)
                CheckError.NO_ROUTE -> ctx.getString(R.string.error_no_route)
                else -> o.detail.orEmpty()
            })
        // A failed check is a result without a check: the Home then shows its text as the error.
        ctx.saveResult(trip.id, TripResult(now.toInstant().toEpochMilli(), title, text, o.check, leaveBy?.toInstant()?.toEpochMilli()))
        // One notification per trip, so two trips never overwrite each other's.
        if (o.notify) notify(ctx, 1000 + trip.id, notification(ctx, title, text, trip.id))
        // A one-off trip is done after its scheduled check; the notification carries the result. The time
        // must have come: AlarmReceiver is exported, so a forged early broadcast must not delete the trip.
        if (!manual && doneAfterCheck(trip, now)) ctx.deleteTrip(trip.id)
        // The first check of an arrival runs near the trip's own hour: its usual duration replaces the estimate
        // made when the trip was saved (at any hour), and moves the second check accordingly.
        else if (!manual && trip.arriveBy && !isFollowUp(trip, now) &&
            o.check != null && o.check.usualMin != trip.usualMin) {
            ctx.setUsualMin(trip.id, o.check.usualMin)
            Scheduler.scheduleAll(ctx)
        }
        TripWidget.refresh(ctx) // widgets show the saved result, so redraw them now
        return Result.success()
    }

    // Expedited work runs as a foreground service below Android 12 and needs this.
    override suspend fun getForegroundInfo() =
        ForegroundInfo(2, notification(applicationContext, applicationContext.getString(R.string.checking_traffic), ""))
}
