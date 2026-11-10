package io.github.roby222.rush.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.roby222.rush.MainActivity
import io.github.roby222.rush.R
import io.github.roby222.rush.model.CLOSURE
import io.github.roby222.rush.model.Check
import io.github.roby222.rush.model.TrafficEvent
import io.github.roby222.rush.work.EXTRA_TRIP

private const val CHANNEL = "traffic"

/** A traffic notification; tapping it opens the Home on [tripId] (0: whatever the Home picks). */
fun notification(ctx: Context, title: String, text: String, tripId: Int = 0): android.app.Notification {
    ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL, ctx.getString(R.string.channel_traffic), NotificationManager.IMPORTANCE_DEFAULT)
    )
    val open = PendingIntent.getActivity(
        ctx, tripId,
        Intent(ctx, MainActivity::class.java).putExtra(EXTRA_TRIP, tripId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
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
fun notify(ctx: Context, id: Int, n: android.app.Notification) {
    val nm = NotificationManagerCompat.from(ctx)
    if (nm.areNotificationsEnabled()) nm.notify(id, n)
}

/** Words for a slowdown kind; unknown TomTom categories fall back to a readable form of the code. */
fun Resources.kindLabel(kind: String): String = when (kind) {
    "JAM" -> getString(R.string.kind_jam)
    "ROAD_WORK" -> getString(R.string.kind_road_work)
    CLOSURE -> getString(R.string.kind_road_closure)
    "TRAFFIC" -> getString(R.string.kind_traffic)
    else -> kind.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** "Coda in Corso Magenta" / "Jam on Corso Magenta", or just the kind when the street is unknown. */
fun Resources.eventTitle(e: TrafficEvent): String =
    e.street?.let { getString(R.string.event_on_street, kindLabel(e.kind), it) } ?: kindLabel(e.kind)

/** Notification title and text for a check of the trip named [tripName]. */
fun Resources.describe(tripName: String, c: Check): Pair<String, String> {
    // No "(+0)": a check without delay shows only the time.
    val title = if (c.delayMin > 0) getString(R.string.notif_title, tripName, c.totalMin, c.delayMin)
    else getString(R.string.notif_title_clear, tripName, c.totalMin)
    val lines = listOf(getString(R.string.notif_summary, c.km, c.totalMin - c.delayMin)) + c.notable.map { e ->
        val line = if (e.delayMin > 0) getString(R.string.notif_event, eventTitle(e), e.delayMin) else eventTitle(e)
        e.speedKmh?.let { getString(R.string.notif_speed, line, it) } ?: line
    }
    return title to lines.joinToString("\n")
}
