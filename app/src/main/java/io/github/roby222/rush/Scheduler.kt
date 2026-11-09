package io.github.roby222.rush

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZonedDateTime

const val ACTION_CHECK = "io.github.roby222.rush.CHECK"

/** First occurrence strictly after [now] on one of [days] at hour:minute, or null if no days. */
fun nextTrigger(now: ZonedDateTime, days: Set<DayOfWeek>, hour: Int, minute: Int): ZonedDateTime? =
    (0L..7L).asSequence()
        .map { now.toLocalDate().plusDays(it) }
        .filter { it.dayOfWeek in days }
        .map { it.atTime(hour, minute).atZone(now.zone) }
        .firstOrNull { it.isAfter(now) }

object Scheduler {
    suspend fun schedule(ctx: Context) {
        val s = ctx.settings()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val next = nextTrigger(ZonedDateTime.now(), s.days, s.hour, s.minute)
        if (next == null) return am.cancel(pi)
        val at = next.toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) // inexact fallback
        }
    }

    fun checkNow(ctx: Context, manual: Boolean) {
        val req = OneTimeWorkRequestBuilder<CheckWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setInputData(workDataOf("manual" to manual))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("check", ExistingWorkPolicy.REPLACE, req)
    }
}

/** Fires the check on alarm; reschedules on alarm, boot, clock/timezone change and app update. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_CHECK) Scheduler.checkNow(ctx, manual = false)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Scheduler.schedule(ctx) } finally { pending.finish() }
        }
    }
}
