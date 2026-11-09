package io.github.roby222.rush.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.text.TextUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.roby222.rush.*
import io.github.roby222.rush.R
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** The Home: the trip in front (chips switch it), its verdict and route, and the system warnings. */
@Composable
fun HomeScreen(vm: MainViewModel, home: HomeUi, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val status by vm.status.collectAsStateWithLifecycle()
    val trip = home.trip

    if (trip == null) {
        Verdict(cs.surfaceContainerHighest, cs.onSurfaceVariant, null, stringResource(R.string.no_trips),
            AnnotatedString(stringResource(R.string.no_trips_hint)), null)
        PrimaryButton(stringResource(R.string.add_trip), onClick = onAdd)
    } else {
        if (home.state.trips.size > 1) TripChips(home.state, trip.id, vm::show)
        val r = home.state.results[trip.id]
        val updated = r?.lastAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
        when {
            r == null -> Verdict(cs.surfaceContainerHighest, cs.onSurfaceVariant, null, stringResource(R.string.no_check_yet),
                AnnotatedString(stringResource(R.string.no_check_hint)), null)
            // A failed check is saved without a check; its text says what went wrong.
            r.check == null -> Verdict(cs.errorContainer, cs.error, null, stringResource(R.string.check_failed),
                AnnotatedString(r.text), updated)
            else -> VerdictFor(r.check, trip, updated)
        }
        RouteCard(trip, r?.check)
        val checking by remember(trip.id) { vm.checking(trip.id) }.collectAsStateWithLifecycle(false)
        PrimaryButton(stringResource(if (checking) R.string.checking else R.string.check_now), enabled = !checking) {
            vm.checkNow(trip.id)
        }
        val next = nextTrigger(ZonedDateTime.now(), trip.days, trip.hour, trip.minute)
        Text(stringResource(R.string.next_check,
            next?.format(DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm")) ?: stringResource(R.string.no_days)),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
    }

    val pkg = Uri.parse("package:${ctx.packageName}")
    if (!status.notifications) {
        Warning(stringResource(R.string.warn_notifications), stringResource(R.string.action_enable_notifications), critical = true,
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
    }
    if (!status.battery) {
        // Opens the system "allow in background" dialog directly (allowed: app is not on Play Store).
        Warning(stringResource(R.string.warn_battery), stringResource(R.string.action_allow),
            critical = false, Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
    }
    if (!status.exactAlarms) {
        Warning(stringResource(R.string.warn_alarms), stringResource(R.string.action_allow_alarms),
            critical = false, Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
    }
}
/** One chip per trip; its dot carries the color of the trip's last result. */
@Composable
private fun TripChips(state: AppState, selected: Int, onShow: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.trips.forEach { t ->
            val r = state.results[t.id]
            val dot = when {
                r == null -> MaterialTheme.colorScheme.outline
                r.check == null -> MaterialTheme.colorScheme.error
                else -> trafficColor(r.check.level)
            }
            FilterChip(
                selected = t.id == selected, onClick = { onShow(t.id) }, label = { Text(t.name) },
                leadingIcon = { Box(Modifier.size(8.dp).background(dot, CircleShape)) },
            )
        }
    }
}

private val LEVELS = listOf(R.string.level_normal, R.string.level_slow, R.string.level_heavy)

/** The verdict for a successful check: what is happening, where, and what to do about it. */
@Composable
private fun VerdictFor(c: Check, trip: Trip, updated: String?) {
    val ctx = LocalContext.current
    val res = ctx.resources
    val level = c.level
    val worst = c.notable.maxByOrNull { if (it.kind == CLOSURE) Int.MAX_VALUE else it.delayMin }
    val headline = when {
        level == 0 && c.notable.isEmpty() -> stringResource(R.string.headline_clear)
        level == 0 -> stringResource(R.string.headline_minor)
        worst?.kind == CLOSURE -> res.eventTitle(worst)
        worst != null && worst.delayMin > 0 -> res.eventTitle(worst)
        else -> stringResource(R.string.headline_traffic)
    }
    // The strings carry <b> for the figures to emphasise; the destination is escaped before it meets the markup.
    val dest = TextUtils.htmlEncode(c.destination)
    val sentence = AnnotatedString.fromHtml(when {
        level == 0 && c.delayMin == 0 -> stringResource(R.string.verdict_clear, dest, c.totalMin)
        level == 0 -> stringResource(R.string.verdict_minor, dest, c.totalMin, c.delayMin)
        // TomTom's delay is what today's traffic adds, so compare with the time without it, not with an empty road.
        else -> stringResource(R.string.verdict_problem, dest, c.totalMin, c.totalMin - c.delayMin, c.delayMin)
    })
    // At level 0 the headline already says it; a problem keeps its level in words, not only in color.
    val badge = when {
        c.events.any { it.kind == CLOSURE } -> res.kindLabel(CLOSURE)
        level > 0 -> stringResource(LEVELS[level])
        else -> null
    }
    Verdict(statusContainer(level), trafficColor(level), badge, headline, sentence, updated) {
        if (level > 0 && trip.from != null && trip.to != null) {
            AssistChip(
                onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://www.google.com/maps/dir/?api=1&travelmode=driving" +
                            "&origin=${trip.from.lat},${trip.from.lon}&destination=${trip.to.lat},${trip.to.lon}")))
                },
                label = { Text(stringResource(R.string.open_maps)) },
                border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = LocalVerdictInk.current.copy(alpha = 0.5f)),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
