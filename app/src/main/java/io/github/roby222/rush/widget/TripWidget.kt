package io.github.roby222.rush.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.roby222.rush.MainActivity
import io.github.roby222.rush.R
import io.github.roby222.rush.data.local.result
import io.github.roby222.rush.data.local.store
import io.github.roby222.rush.data.local.trips
import io.github.roby222.rush.domain.nextCheck
import io.github.roby222.rush.model.CLOSURE
import io.github.roby222.rush.model.Trip
import io.github.roby222.rush.model.TripResult
import io.github.roby222.rush.notification.clock
import io.github.roby222.rush.notification.eventTitle
import io.github.roby222.rush.notification.kindLabel
import io.github.roby222.rush.ui.theme.Amber
import io.github.roby222.rush.ui.theme.Ink
import io.github.roby222.rush.ui.theme.Palette
import io.github.roby222.rush.work.EXTRA_TRIP
import io.github.roby222.rush.work.Scheduler
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

/** Per-widget state: the trip it shows (0 = not chosen yet). */
val WIDGET_TRIP = intPreferencesKey("trip")

/** Per-widget state: when its "Check now" was tapped. */
private val WIDGET_REQUESTED = longPreferencesKey("requested")

/** Longest a widget says "checking" without a newer result (a failed or lost check must not stick). */
val CHECKING_TIMEOUT: Duration = Duration.ofMinutes(3)

/**
 * A widget is checking from its "Check now" tap until a newer result lands. WorkManager's state cannot tell:
 * the worker redraws the widget while its own job still counts as running.
 */
fun isChecking(requestedAt: Long, lastAt: Long?, now: Long): Boolean =
    requestedAt > (lastAt ?: 0) && now - requestedAt < CHECKING_TIMEOUT.toMillis()

/** A result older than this, or from an earlier day, is shown faded: never as if it were current. */
val STALE_AFTER: Duration = Duration.ofHours(2)

fun isStale(lastAt: Long, now: ZonedDateTime): Boolean {
    val at = Instant.ofEpochMilli(lastAt).atZone(now.zone)
    return Duration.between(at, now) > STALE_AFTER || at.toLocalDate() != now.toLocalDate()
}

private val SMALL = DpSize(110.dp, 48.dp)
private val LARGE = DpSize(250.dp, 110.dp)

/**
 * Home-screen widget with a trip's last result. It never calls TomTom: it shows what the last check saved
 * and is redrawn when a check ends, when a trip changes, and by the system every 30 minutes (for staleness).
 */
class TripWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = context.store.data.first()
        provideContent {
            // Read inside the content: while a Glance session is open, updates recompose this block
            // instead of calling provideGlance again, so values read outside it would go stale.
            val prefs by context.store.data.collectAsState(initial)
            val widgetState = currentState<Preferences>()
            val chosen = widgetState[WIDGET_TRIP] ?: 0
            val trips = prefs.trips()
            // Not configured yet but only one trip: that is the trip.
            val trip = trips.firstOrNull { it.id == chosen } ?: trips.singleOrNull()?.takeIf { chosen == 0 }
            val result = trip?.let { prefs.result(it.id) }
            val now = System.currentTimeMillis()
            val checking = trip != null && isChecking(widgetState[WIDGET_REQUESTED] ?: 0, result?.lastAt, now)
            Content(context, WidgetUi.of(context, trip, removed = trip == null && chosen != 0, result, checking, ZonedDateTime.now()))
        }
    }

    companion object {
        /** Redraws every Rush widget; never fails the caller (a check or a save). */
        suspend fun refresh(context: Context) {
            runCatching { TripWidget().updateAll(context) }
        }
    }
}

class TripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TripWidget()
}

private val TRIP_PARAM = ActionParameters.Key<Int>("trip")

/** "Check now" on the widget: the same manual queue as the app, then a redraw showing "checking". */
class CheckNowAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val tripId = parameters[TRIP_PARAM] ?: return
        Scheduler.checkNow(context, tripId, manual = true)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) {
            it.toMutablePreferences().apply { this[WIDGET_REQUESTED] = System.currentTimeMillis() }
        }
        TripWidget().update(context, glanceId)
    }
}

/** Everything the widget draws, already in words. */
private data class WidgetUi(
    val tripId: Int,
    val title: String,
    val headline: String,
    val minutes: String?,   // "24 min (+6)", only for a successful check
    val leave: String?,     // "leave by 18:14", an arrival trip's successful check (4x2 only)
    val detail: String?,
    val time: String?,      // full line for 4x2: "Checked at 17:20 · Next Wed 17:20"
    val shortTime: String?, // 2x1: just the check time, or the next one before any check
    val level: Int?,      // null: no colored state (never checked, stale, failed, removed)
    val failed: Boolean,
    val stale: Boolean,
    val checking: Boolean,
) {
    companion object {
        fun of(ctx: Context, trip: Trip?, removed: Boolean, r: TripResult?, checking: Boolean, now: ZonedDateTime): WidgetUi {
            val res = ctx.resources
            if (trip == null) {
                val text = res.getString(if (removed) R.string.widget_trip_removed else R.string.widget_no_trip)
                return WidgetUi(0, "Rush", text, null, null, null, null, null, null, failed = false, stale = false, checking = false)
            }
            val nextAt = nextCheck(trip, now)?.format(DateTimeFormatter.ofPattern("EEE HH:mm"))
            val next = nextAt?.let { res.getString(R.string.widget_next, it) }
            if (r == null) {
                return WidgetUi(trip.id, trip.name, res.getString(R.string.no_check_yet), null, null, null, next, next,
                    null, false, false, checking)
            }
            val at = Instant.ofEpochMilli(r.lastAt).atZone(now.zone)
            val stale = isStale(r.lastAt, now)
            val checkedAt = at.format(DateTimeFormatter.ofPattern(if (stale) "EEE HH:mm" else "HH:mm"))
            val checked = res.getString(if (stale) R.string.widget_checked_day else R.string.widget_checked_at, checkedAt)
            val time = listOfNotNull(checked, next).joinToString(" · ")
            val c = r.check ?: return WidgetUi(trip.id, trip.name, res.getString(R.string.check_failed), null, null, r.text, time,
                checkedAt, null, failed = true, stale = stale, checking = checking)
            val level = c.level
            val word = if (c.events.any { it.kind == CLOSURE }) res.kindLabel(CLOSURE) else res.getString(LEVELS[level])
            val worst = c.notable.maxByOrNull { if (it.kind == CLOSURE) Int.MAX_VALUE else it.delayMin }
            val minutes = if (c.delayMin > 0) res.getString(R.string.widget_minutes, c.totalMin, c.delayMin)
            else res.getString(R.string.widget_minutes_clear, c.totalMin)
            val leave = r.leaveBy?.let { Instant.ofEpochMilli(it).atZone(now.zone) }?.let {
                if (it.isAfter(at)) res.getString(R.string.widget_leave_by, clock(it, at)) else res.getString(R.string.widget_leave_now)
            }
            return WidgetUi(trip.id, trip.name, word, minutes, leave, worst?.let { res.eventTitle(it) }, time, checkedAt,
                level.takeUnless { stale }, failed = false, stale = stale, checking = checking)
        }
    }
}

private val LEVELS = listOf(R.string.level_normal, R.string.level_slow, R.string.level_heavy)

private fun dayNight(light: Color, dark: Color) = ColorProvider(day = light, night = dark)

@Composable
private fun Content(ctx: Context, ui: WidgetUi) {
    val large = LocalSize.current.height >= LARGE.height
    val ground = when {
        ui.failed && !ui.stale -> dayNight(Palette.statusLight[2], Palette.statusDark[2])
        ui.level != null -> dayNight(Palette.statusLight[ui.level], Palette.statusDark[ui.level])
        else -> dayNight(Palette.cardLight, Palette.cardDark)
    }
    val text = dayNight(Palette.textLight, Palette.textDark)
    val muted = dayNight(Palette.mutedLight, Palette.mutedDark)
    // Stale results are muted everywhere except the time, which says how old they are.
    val headlineColor = when {
        ui.stale -> muted
        ui.level != null -> dayNight(Palette.trafficLight[ui.level], Palette.trafficDark[ui.level])
        ui.failed -> dayNight(Palette.trafficLight[2], Palette.trafficDark[2])
        else -> text
    }
    val open = Intent(ctx, MainActivity::class.java).putExtra(EXTRA_TRIP, ui.tripId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    Column(
        GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(ground).padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(actionStartActivity(open)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (large) {
            Text(ui.title, maxLines = 1, style = TextStyle(color = muted, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            Text(listOfNotNull(ui.headline, ui.minutes, ui.leave).joinToString(" · "), maxLines = 2,
                style = TextStyle(color = headlineColor, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            ui.detail?.let { Text(it, maxLines = 2, style = TextStyle(color = if (ui.stale) muted else text, fontSize = 14.sp)) }
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.defaultWeight()) {
                    ui.time?.let {
                        Text(it, maxLines = 2, style = TextStyle(color = if (ui.stale) text else muted, fontSize = 12.sp,
                            fontWeight = if (ui.stale) FontWeight.Bold else FontWeight.Normal))
                    }
                }
                if (ui.tripId != 0) {
                    Spacer(GlanceModifier.width(8.dp))
                    CheckButton(ctx, ui)
                }
            }
        } else {
            // Two cells: name and minutes, the level in words, and only the time of the check.
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(ui.title, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = muted, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                ui.minutes?.let {
                    Text(it, maxLines = 1, style = TextStyle(color = if (ui.stale) muted else text, fontSize = 12.sp,
                        fontWeight = FontWeight.Medium))
                }
            }
            Text(ui.headline, maxLines = 1, style = TextStyle(color = headlineColor, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            ui.shortTime?.let {
                Text(it, maxLines = 1, style = TextStyle(color = if (ui.stale) text else muted, fontSize = 11.sp,
                    fontWeight = if (ui.stale) FontWeight.Bold else FontWeight.Normal))
            }
        }
    }
}

/** The amber "Check now" pill; while the manual check runs it reads "Checking…" and does nothing. */
@Composable
private fun CheckButton(ctx: Context, ui: WidgetUi) {
    val label = ctx.getString(if (ui.checking) R.string.checking else R.string.check_now)
    var modifier = GlanceModifier.cornerRadius(18.dp).background(ColorProvider(Amber)).padding(horizontal = 14.dp, vertical = 8.dp)
    if (!ui.checking) modifier = modifier.clickable(actionRunCallback<CheckNowAction>(actionParametersOf(TRIP_PARAM to ui.tripId)))
    Box(modifier) {
        Text(label, style = TextStyle(color = ColorProvider(Ink), fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}
