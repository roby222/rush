package io.github.roby222.rush

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.roby222.rush.widget.TripWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

const val ACTION_CHECK = "io.github.roby222.rush.CHECK"
const val EXTRA_TRIP = "trip"

/** A problem result stays in front on the Home for this long. */
val FEATURED_PROBLEM_WINDOW: Duration = Duration.ofHours(3)

fun scheduledWork(tripId: Int) = "check-$tripId"
fun manualWork(tripId: Int) = "check-manual-$tripId"

/** First occurrence strictly after [now] on one of [days] at hour:minute, or null if no days. */
fun nextTrigger(now: ZonedDateTime, days: Set<DayOfWeek>, hour: Int, minute: Int): ZonedDateTime? =
    (0L..7L).asSequence()
        .map { now.toLocalDate().plusDays(it) }
        .filter { it.dayOfWeek in days }
        .map { it.atTime(hour, minute).atZone(now.zone) }
        .firstOrNull { it.isAfter(now) }

/** A one-off trip stays this long after its time, so the check its alarm started can still find it. */
val ONE_OFF_GRACE: Duration = Duration.ofHours(1)

/** When a one-off trip is checked, in [now]'s zone; null for a weekly trip. */
fun oneOffAt(t: Trip, now: ZonedDateTime): ZonedDateTime? = t.day()?.atTime(t.hour, t.minute)?.atZone(now.zone)

/** The next check of [t] after [now]: the next weekly occurrence, or the one-off time while still ahead. */
fun nextCheck(t: Trip, now: ZonedDateTime): ZonedDateTime? =
    if (t.date == null) nextTrigger(now, t.days, t.hour, t.minute) else oneOffAt(t, now)?.takeIf { it.isAfter(now) }

/** A one-off trip whose time passed more than [ONE_OFF_GRACE] ago, e.g. with the phone off: it is removed. */
fun expired(t: Trip, now: ZonedDateTime): Boolean = oneOffAt(t, now)?.isBefore(now.minus(ONE_OFF_GRACE)) == true

/** Whether a scheduled check at [now] is the last one of [t]: a one-off trip whose time has come. */
fun doneAfterCheck(t: Trip, now: ZonedDateTime): Boolean = oneOffAt(t, now)?.isAfter(now) == false

/** Next alarm time per trip id; weekly trips without days and past one-off trips have none. */
fun alarmPlan(trips: List<Trip>, now: ZonedDateTime): Map<Int, ZonedDateTime> =
    trips.mapNotNull { t -> nextCheck(t, now)?.let { t.id to it } }.toMap()

/** Whether two trips can run on the same calendar day. */
private fun sharesADay(a: Trip, b: Trip): Boolean {
    val da = a.day()
    val db = b.day()
    return when {
        da != null && db != null -> da == db
        da != null -> da.dayOfWeek in b.days
        db != null -> db.dayOfWeek in a.days
        else -> a.days.any { it in b.days }
    }
}

/** Another trip checked at the same hour:minute on a shared day: two checks in one minute make no sense. */
fun timeConflict(draft: Trip, trips: List<Trip>): Trip? = trips.firstOrNull {
    it.id != draft.id && it.hour == draft.hour && it.minute == draft.minute && sharesADay(it, draft)
}

/**
 * The trip the Home shows first: the latest one with a problem (slowdown or failed check) in the last
 * [FEATURED_PROBLEM_WINDOW], otherwise the one checked next, otherwise the first.
 */
fun featuredTrip(trips: List<Trip>, results: Map<Int, TripResult>, now: ZonedDateTime): Trip? {
    val since = now.toInstant().minus(FEATURED_PROBLEM_WINDOW)
    val problem = trips.mapNotNull { t -> results[t.id]?.let { t to it } }
        .filter { (_, r) -> (r.check == null || r.check.level > 0) && !Instant.ofEpochMilli(r.lastAt).isBefore(since) }
        .maxByOrNull { (_, r) -> r.lastAt }?.first
    val plan = alarmPlan(trips, now)
    return problem ?: trips.filter { it.id in plan }.minByOrNull { plan.getValue(it.id) } ?: trips.firstOrNull()
}

object Scheduler {
    // Same action and component for every trip: the request code (the trip id) tells the alarms apart.
    private fun alarm(ctx: Context, tripId: Int) = PendingIntent.getBroadcast(
        ctx, tripId,
        Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_CHECK).putExtra(EXTRA_TRIP, tripId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Drops expired one-off trips, sets one exact alarm per trip and cancels the alarms of trips that no longer exist. */
    suspend fun scheduleAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = ZonedDateTime.now()
        ctx.pruneExpired(now)
        val prefs = ctx.store.data.first()
        val plan = alarmPlan(prefs.trips(), now)
        // 0 is the single alarm of v0.2.0.
        val stale = prefs[Keys.alarmIds].orEmpty().mapNotNull { it.toIntOrNull() }.toSet() + 0 - plan.keys
        stale.forEach { am.cancel(alarm(ctx, it)) }
        plan.forEach { (id, next) ->
            val at = next.toInstant().toEpochMilli()
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(ctx, id))
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(ctx, id)) // inexact fallback
            }
        }
        ctx.store.edit { it[Keys.alarmIds] = plan.keys.map(Int::toString).toSet() }
    }

    fun checkNow(ctx: Context, tripId: Int, manual: Boolean) {
        val req = OneTimeWorkRequestBuilder<CheckWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setInputData(workDataOf("manual" to manual, EXTRA_TRIP to tripId))
            .build()
        // Queues per trip and per kind: a manual check never cancels a scheduled one, no trip cancels
        // another, and repeated taps keep the manual check already queued or running.
        val wm = WorkManager.getInstance(ctx)
        if (manual) wm.enqueueUniqueWork(manualWork(tripId), ExistingWorkPolicy.KEEP, req)
        else wm.enqueueUniqueWork(scheduledWork(tripId), ExistingWorkPolicy.REPLACE, req)
    }
}

/** Fires a trip's check on its alarm; reschedules every trip and redraws the widgets on alarm, boot, clock/timezone change and app update. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val tripId = intent.getIntExtra(EXTRA_TRIP, 0)
        // The v0.2.0 alarm has no trip: it only triggers the rescheduling that cancels it.
        if (intent.action == ACTION_CHECK && tripId > 0) Scheduler.checkNow(ctx, tripId, manual = false)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Scheduler.scheduleAll(ctx)
                // After an app update, reboot or clock change the widgets need a redraw too (and may turn stale).
                TripWidget.refresh(ctx)
            } finally {
                pending.finish()
            }
        }
    }
}
