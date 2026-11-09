package io.github.roby222.rush

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import android.text.TextUtils
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.work.WorkManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date

/** System settings the checks depend on; each false one shows a warning on Home. */
data class SystemStatus(val notifications: Boolean, val battery: Boolean, val exactAlarms: Boolean)

class MainActivity : ComponentActivity() {
    // Refreshed on resume so a warning hides as soon as the user fixes it in system settings.
    private val status = mutableStateOf(SystemStatus(notifications = true, battery = true, exactAlarms = true))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { RushTheme { App(status.value) } }
    }

    override fun onResume() {
        super.onResume()
        status.value = SystemStatus(
            notifications = NotificationManagerCompat.from(this).areNotificationsEnabled(),
            battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
            exactAlarms = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        )
        lifecycleScope.launch { Scheduler.schedule(this@MainActivity) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(status: SystemStatus) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val s = remember { ctx.settingsFlow() }.collectAsState(initial = null).value ?: return
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (editing) stringResource(R.string.settings) else "Rush") },
            navigationIcon = {
                if (editing) IconButton(onClick = { editing = false }) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                if (!editing) IconButton(onClick = { editing = true }) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                }
            },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).consumeWindowInsets(pad).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editing) {
                BackHandler { editing = false }
                SettingsScreen(s) { editing = false }
            } else {
                HomeScreen(s, status)
            }
        }
    }
}

@Composable
fun HomeScreen(s: Settings, status: SystemStatus) {
    val ctx = LocalContext.current
    val checking = remember { WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(MANUAL_WORK) }
        .collectAsState(emptyList()).value.any { !it.state.isFinished }
    val updated = s.lastAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
    val c = s.lastCheck

    when {
        // A failed check clears lastCheck and leaves its message in lastText.
        s.lastAt != null && c == null && !s.lastText.isNullOrBlank() ->
            Verdict(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, null,
                stringResource(R.string.check_failed), AnnotatedString(s.lastText), updated)
        c == null -> {
            val noRoute = s.from == null || s.to == null
            Verdict(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, null,
                stringResource(if (noRoute) R.string.setup_route else R.string.no_check_yet),
                AnnotatedString(stringResource(if (noRoute) R.string.setup_route_hint else R.string.no_check_hint)), updated)
        }
        else -> VerdictFor(c, s, updated)
    }

    RouteCard(s, c)

    Button(
        onClick = { Scheduler.checkNow(ctx, manual = true) },
        enabled = !checking,
        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(stringResource(if (checking) R.string.checking else R.string.check_now), style = MaterialTheme.typography.titleSmall) }
    val next = nextTrigger(ZonedDateTime.now(), s.days, s.hour, s.minute)
    Text(stringResource(R.string.next_check,
        next?.format(DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm")) ?: stringResource(R.string.no_days)),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp))

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

/** The severity color of the verdict card, for the controls inside it. */
private val LocalVerdictInk = compositionLocalOf { Color.Unspecified }

private val LEVELS = listOf(R.string.level_normal, R.string.level_slow, R.string.level_heavy)

/** The verdict for a successful check: what is happening, where, and what to do about it. */
@Composable
private fun VerdictFor(c: Check, s: Settings, updated: String?) {
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
        if (level > 0 && s.from != null && s.to != null) {
            AssistChip(
                onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://www.google.com/maps/dir/?api=1&travelmode=driving" +
                            "&origin=${s.from.lat},${s.from.lon}&destination=${s.to.lat},${s.to.lon}")))
                },
                label = { Text(stringResource(R.string.open_maps)) },
                border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = LocalVerdictInk.current.copy(alpha = 0.5f)),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The status card: the headline with an optional severity [badge] beside it, then one sentence.
 * The ground crossfades when a new check changes the level.
 */
@Composable
private fun Verdict(
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
private fun RouteCard(s: Settings, c: Check?) {
    val cs = MaterialTheme.colorScheme
    val res = LocalContext.current.resources
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.on_route), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp))
            // Like a map route: green where traffic flows, the slowdown's color around each slowdown.
            val flow = if (c == null) cs.outline else trafficColor(0)
            Stop(Modifier.border(2.dp, cs.onSurfaceVariant, CircleShape), s.from?.name?.substringBefore(",") ?: stringResource(R.string.departure_unset),
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
            Stop(Modifier.background(Amber, CircleShape), s.to?.name?.substringBefore(",") ?: stringResource(R.string.destination_unset),
                c?.let { stringResource(R.string.arrival_in, it.totalMin) } ?: stringResource(R.string.destination), above = flow, below = null)
        }
    }
}

/** One row of the directions list; the route line runs [above] and [below] its dot (null at the ends). */
@Composable
private fun Stop(dot: Modifier, title: String, detail: String, above: Color?, below: Color?) {
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
private fun Warning(text: String, action: String, critical: Boolean, intent: Intent) {
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
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun SettingsScreen(s: Settings, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by rememberSaveable { mutableStateOf(s.days) }
    var time by rememberSaveable { mutableStateOf("%02d:%02d".format(s.hour, s.minute)) }
    var minDelay by rememberSaveable { mutableStateOf(s.minDelayMin.toString()) }
    val parsedTime = Regex("""(\d{1,2}):(\d{2})""").matchEntire(time)
        ?.destructured?.let { (h, m) -> h.toInt() to m.toInt() }
        ?.takeIf { (h, m) -> h in 0..23 && m in 0..59 }
    val parsedDelay = minDelay.toIntOrNull()?.takeIf { it >= 0 }

    Section(stringResource(R.string.section_route))
    PlaceField(stringResource(R.string.departure), "from", s.from, near = s.to)
    HorizontalDivider()
    PlaceField(stringResource(R.string.destination), "to", s.to, near = s.from)

    Section(stringResource(R.string.section_when))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { d ->
            FilterChip(
                selected = d in days,
                onClick = { days = if (d in days) days - d else days + d },
                label = { Text(d.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0])) },
            )
        }
    }
    OutlinedTextField(time, { time = it }, label = { Text(stringResource(R.string.time)) },
        isError = parsedTime == null, singleLine = true, modifier = Modifier.fillMaxWidth(),
        supportingText = { Text(stringResource(if (parsedTime == null) R.string.time_error else R.string.time_help)) })

    Section(stringResource(R.string.section_alerts))
    OutlinedTextField(minDelay, { minDelay = it }, label = { Text(stringResource(R.string.min_delay)) },
        isError = parsedDelay == null, singleLine = true, modifier = Modifier.fillMaxWidth(),
        supportingText = {
            Text(stringResource(if (parsedDelay == null) R.string.min_delay_error else R.string.min_delay_help))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    Button(enabled = parsedTime != null && parsedDelay != null, modifier = Modifier.fillMaxWidth(), onClick = {
        scope.launch {
            ctx.store.edit {
                it[Keys.days] = days.map { d -> d.name }.toSet()
                it[Keys.hour] = parsedTime!!.first
                it[Keys.minute] = parsedTime.second
                it[Keys.minDelay] = parsedDelay!!
            }
            Scheduler.schedule(ctx)
            onDone()
        }
    }) { Text(stringResource(R.string.save)) }
}

/** Address field: geocodes once via TomTom Search (near the other saved place, if any), saves only after confirmation. */
@Composable
fun PlaceField(label: String, prefix: String, current: Place?, near: Place?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var found by remember { mutableStateOf<SearchResult?>(null) }
    val res = ctx.resources
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    fun search() {
        if (query.isBlank()) return
        status = res.getString(R.string.searching)
        failed = false
        scope.launch {
            status = try {
                found = withContext(Dispatchers.IO) { TomTom.search(query.trim(), near) }
                found?.let { res.getString(R.string.found, it.label) } ?: res.getString(R.string.no_results)
            } catch (e: Exception) {
                failed = true
                res.getString(R.string.search_error, e.message)
            }
        }
    }

    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(current?.name ?: stringResource(R.string.not_set), style = MaterialTheme.typography.bodyLarge)
        current?.let {
            Text("%.5f, %.5f".format(it.lat, it.lon), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    OutlinedTextField(query, { query = it; found = null; status = null },
        label = { Text(stringResource(if (current == null) R.string.search_place else R.string.change_place)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { search() }))
    status?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium,
            color = if (failed) MaterialTheme.colorScheme.error else Color.Unspecified)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = query.isNotBlank(), onClick = { search() }) { Text(stringResource(R.string.search)) }
        found?.let { r ->
            Button(onClick = {
                scope.launch {
                    ctx.savePlace(prefix, Place(r.label, r.position.lat, r.position.lon))
                    found = null; query = ""; status = null
                }
            }) { Text(stringResource(R.string.confirm)) }
        }
    }
}
