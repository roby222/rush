package io.github.roby222.rush

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json

private const val CHANNEL = "traffic"

class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val manual = inputData.getBoolean("manual", false)
        val o = checkOutcome(ctx.settings(), manual) { from, to ->
            // Interruptible so a cancelled work aborts the HTTP call instead of finishing it.
            runInterruptible(Dispatchers.IO) { TomTom.route(from.lat, from.lon, to.lat, to.lon) }
        }
        val (title, text) = o.check?.let { ctx.resources.describe(it) } ?: (ctx.getString(R.string.error_title) to
            when (o.error) {
                CheckError.NO_FROM -> ctx.getString(R.string.error_no_from)
                CheckError.NO_TO -> ctx.getString(R.string.error_no_to)
                CheckError.NO_ROUTE -> ctx.getString(R.string.error_no_route)
                else -> o.detail.orEmpty()
            })
        ctx.store.edit {
            it[Keys.lastTitle] = title
            it[Keys.lastText] = text
            it[Keys.lastAt] = System.currentTimeMillis()
            // A failed check clears lastCheck: the Home then shows lastText as the error.
            if (o.check != null) it[Keys.lastCheck] = Json.encodeToString(o.check) else it.remove(Keys.lastCheck)
        }
        if (o.notify) notify(ctx, 1, notification(ctx, title, text))
        return Result.success()
    }

    // Expedited work runs as a foreground service below Android 12 and needs this.
    override suspend fun getForegroundInfo() =
        ForegroundInfo(2, notification(applicationContext, applicationContext.getString(R.string.checking_traffic), ""))
}

enum class CheckError { NO_FROM, NO_TO, NO_ROUTE, FAILED }

/** What one check produced: a [check], or an [error] with an optional technical [detail]. */
data class Outcome(val notify: Boolean, val check: Check? = null, val error: CheckError? = null, val detail: String? = null)

/**
 * The result of one check and whether to notify. Cancellation is rethrown so a check
 * stopped by the system neither saves a result nor notifies a fake error.
 */
suspend fun checkOutcome(
    s: Settings,
    manual: Boolean,
    route: suspend (from: Place, to: Place) -> Route,
): Outcome = try {
    when {
        s.from == null -> Outcome(true, error = CheckError.NO_FROM)
        s.to == null -> Outcome(true, error = CheckError.NO_TO)
        else -> {
            val r = route(s.from, s.to)
            Outcome(manual || r.summary.trafficDelayInSeconds >= s.minDelayMin * 60, r.toCheck(s.to.name.substringBefore(",")))
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: NoRouteException) {
    Outcome(true, error = CheckError.NO_ROUTE)
} catch (e: Exception) {
    Outcome(true, error = CheckError.FAILED, detail = e.message ?: e.javaClass.simpleName)
}

fun notification(ctx: Context, title: String, text: String): android.app.Notification {
    ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL, ctx.getString(R.string.channel_traffic), NotificationManager.IMPORTANCE_DEFAULT)
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

/** Notification title and text for a check. */
fun Resources.describe(c: Check): Pair<String, String> {
    val title = getString(R.string.notif_title, c.destination, c.totalMin, c.delayMin)
    val lines = listOf(getString(R.string.notif_summary, c.km, c.totalMin - c.delayMin)) + c.notable.map { e ->
        val line = getString(R.string.notif_event, eventTitle(e), e.delayMin)
        e.speedKmh?.let { getString(R.string.notif_speed, line, it) } ?: line
    }
    return title to lines.joinToString("\n")
}
