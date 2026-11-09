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

/** The severity color of the verdict card, for the controls inside it. */
internal val LocalVerdictInk = compositionLocalOf { Color.Unspecified }

/**
 * The status card: the headline with an optional severity [badge] beside it, then one sentence.
 * The ground crossfades when a new check changes the level.
 */
@Composable
internal fun Verdict(
    ground: Color, ink: Color, badge: String?, headline: String, sentence: AnnotatedString, updated: String?,
    actions: @Composable () -> Unit = {},
) {
    val bg by animateColorAsState(ground, tween(600), label = "verdict")
    Card(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Crossfade(headline, Modifier.weight(1f), tween(600), label = "headline") { text ->
                    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                }
                // Animated even when the badge appears or leaves, so calm → problem is one transition.
                AnimatedContent(badge, transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) },
                    label = "badge") { text ->
                    if (text != null) {
                        Text(text, color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp).background(ink, RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                }
            }
            Text(sentence, style = MaterialTheme.typography.bodyLarge)
            CompositionLocalProvider(LocalVerdictInk provides ink) { actions() }
            if (updated != null) {
                // Tinted from the card's own text, not gray, so it reads on every severity ground.
                Text(stringResource(R.string.updated_at, updated), style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = 0.72f))
            }
        }
    }
}

/** The trip as a directions list: departure, each slowdown where it happens, destination. */
@Composable
internal fun RouteCard(trip: Trip, c: Check?) {
    val cs = MaterialTheme.colorScheme
    val res = LocalContext.current.resources
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.on_route), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp))
            // Like a map route: green where traffic flows, the slowdown's color around each slowdown.
            val flow = if (c == null) cs.outline else trafficColor(0)
            Stop(Modifier.border(2.dp, cs.onSurfaceVariant, CircleShape), trip.from?.name?.substringBefore(",") ?: stringResource(R.string.departure_unset),
                stringResource(R.string.departure), above = null, below = flow)
            when {
                c == null -> {}
                c.notable.isEmpty() -> Stop(Modifier.background(flow, CircleShape),
                    stringResource(R.string.no_slowdowns), stringResource(R.string.route_summary, c.km, c.totalMin), flow, flow)
                else -> c.notable.forEach { e ->
                    val color = trafficColor(if (e.severe) 2 else 1)
                    Stop(Modifier.background(color, CircleShape), res.eventTitle(e),
                        listOfNotNull(if (e.delayMin > 0) res.getString(R.string.delay_min, e.delayMin) else null,
                            e.speedKmh?.let { res.getString(R.string.speed_kmh, it) }).joinToString(" · "), color, color)
                }
            }
            Stop(Modifier.background(Amber, CircleShape), trip.to?.name?.substringBefore(",") ?: stringResource(R.string.destination_unset),
                c?.let { stringResource(R.string.arrival_in, it.totalMin) } ?: stringResource(R.string.destination), above = flow, below = null)
        }
    }
}

/** One row of the directions list; the route line runs [above] and [below] its dot (null at the ends). */
@Composable
internal fun Stop(dot: Modifier, title: String, detail: String, above: Color?, below: Color?) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.width(12.dp).fillMaxHeight().drawBehind {
            val x = size.width / 2
            // The line meets the dot's edges, so the hollow departure ring stays empty.
            val dotTop = 4.dp.toPx()
            val dotBottom = dotTop + 12.dp.toPx()
            if (above != null) drawLine(above, Offset(x, 0f), Offset(x, dotTop), 4.dp.toPx())
            if (below != null) drawLine(below, Offset(x, dotBottom), Offset(x, size.height), 4.dp.toPx())
        }) {
            Box(Modifier.padding(top = 4.dp).size(12.dp).then(dot))
        }
        Column(Modifier.padding(bottom = if (below == null) 0.dp else 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A system setting the app needs, with a button that opens it. */
@Composable
internal fun Warning(text: String, action: String, critical: Boolean, intent: Intent) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (critical) c.errorContainer else c.secondaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { ctx.startActivity(intent) }, modifier = Modifier.align(Alignment.End),
                colors = if (critical) ButtonDefaults.textButtonColors(contentColor = c.error)
                    else ButtonDefaults.textButtonColors()) { Text(action) }
        }
    }
}

@Composable
internal fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp))
}

/** The amber full-width button reserved for a screen's main action. */
@Composable
internal fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
    ) { Text(text, style = MaterialTheme.typography.titleSmall) }
}
