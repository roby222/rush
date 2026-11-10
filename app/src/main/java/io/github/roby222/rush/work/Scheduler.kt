package io.github.roby222.rush.work

import android.app.AlarmManager
import android.app.PendingIntent
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
import io.github.roby222.rush.data.local.Keys
import io.github.roby222.rush.data.local.pruneExpired
import io.github.roby222.rush.data.local.store
import io.github.roby222.rush.data.local.trips
import io.github.roby222.rush.domain.alarmPlan
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

const val ACTION_CHECK = "io.github.roby222.rush.CHECK"
const val EXTRA_TRIP = "trip"

fun scheduledWork(tripId: Int) = "check-$tripId"
fun manualWork(tripId: Int) = "check-manual-$tripId"

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
