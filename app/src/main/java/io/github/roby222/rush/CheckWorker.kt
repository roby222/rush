package io.github.roby222.rush

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters

private const val CHANNEL = "traffic"

class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val manual = inputData.getBoolean("manual", false)
        val s = ctx.settings()
        val (title, text, notify) = try {
            val from = s.from ?: error("Imposta la partenza")
            val to = s.to ?: error("Imposta l'arrivo")
            val r = TomTom.route(from.lat, from.lon, to.lat, to.lon)
            val (t, x) = describe(to.name.substringBefore(","), r)
            Triple(t, x, manual || r.summary.trafficDelayInSeconds >= s.minDelayMin * 60)
        } catch (e: Exception) {
            Triple("Traffico: errore", e.message ?: e.javaClass.simpleName, true)
        }
        ctx.store.edit {
            it[Keys.lastTitle] = title
            it[Keys.lastText] = text
            it[Keys.lastAt] = System.currentTimeMillis()
        }
        if (notify) notify(ctx, 1, notification(ctx, title, text))
        return Result.success()
    }

    // Expedited work runs as a foreground service below Android 12 and needs this.
    override suspend fun getForegroundInfo() =
        ForegroundInfo(2, notification(applicationContext, "Controllo traffico…", ""))
}

fun notification(ctx: Context, title: String, text: String): android.app.Notification {
    ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL, "Traffico", NotificationManager.IMPORTANCE_DEFAULT)
    )
    val open = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(ctx, CHANNEL)
        .setSmallIcon(R.drawable.ic_car)
        .setContentTitle(title)
        .setContentText(text.substringBefore("\n"))
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
}

@SuppressLint("MissingPermission") // checked via areNotificationsEnabled, works on all API levels
private fun notify(ctx: Context, id: Int, n: android.app.Notification) {
    val nm = NotificationManagerCompat.from(ctx)
    if (nm.areNotificationsEnabled()) nm.notify(id, n)
}
