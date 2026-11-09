package io.github.roby222.rush

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
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

class MainActivity : ComponentActivity() {
    // Refreshed on resume so the warning hides after the user re-enables notifications.
    private val notificationsOn = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val dark = isSystemInDarkTheme()
            // Material You (colori dallo sfondo) da Android 12; prima, palette standard
            val colors = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(this)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) { App(notificationsOn.value) }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationsOn.value = NotificationManagerCompat.from(this).areNotificationsEnabled()
        lifecycleScope.launch { Scheduler.schedule(this@MainActivity) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(notificationsOn: Boolean) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val s = remember { ctx.settingsFlow() }.collectAsState(initial = null).value ?: return
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (editing) "Impostazioni" else "Rush") },
            navigationIcon = {
                if (editing) IconButton(onClick = { editing = false }) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "Indietro")
                }
            },
            actions = {
                if (!editing) IconButton(onClick = { editing = true }) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "Impostazioni")
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
                HomeScreen(s, notificationsOn)
            }
        }
    }
}

@Composable
fun HomeScreen(s: Settings, notificationsOn: Boolean) {
    val ctx = LocalContext.current
    val checking = remember { WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(MANUAL_WORK) }
        .collectAsState(emptyList()).value.any { !it.state.isFinished }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledValue("Partenza", s.from?.name?.substringBefore(",") ?: "Non impostata")
            LabeledValue("Arrivo", s.to?.name?.substringBefore(",") ?: "Non impostato")
        }
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (s.lastAt == null) {
                Text("Nessun controllo ancora", style = MaterialTheme.typography.titleMedium)
                Text("Premi \"Controlla ora\" per vedere il tempo di percorrenza.")
            } else {
                Text("Ultimo controllo · " +
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.lastAt)),
                    style = MaterialTheme.typography.labelMedium)
                Text(s.lastTitle ?: "", style = MaterialTheme.typography.headlineSmall)
                Text(s.lastText ?: "", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    val next = nextTrigger(ZonedDateTime.now(), s.days, s.hour, s.minute)
    LabeledValue("Prossimo controllo", next?.format(DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm")) ?: "Nessun giorno selezionato")
    Button(
        onClick = { Scheduler.checkNow(ctx, manual = true) },
        enabled = !checking,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (checking) "Controllo in corso…" else "Controlla ora") }

    if (!notificationsOn) {
        Warning("Notifiche disattivate: non riceverai i risultati dei controlli.", "Attiva notifiche", critical = true,
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
    }
    val pm = ctx.getSystemService(PowerManager::class.java)
    if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
        Warning("Per non perdere i controlli, escludi l'app dall'ottimizzazione batteria.", "Ottimizzazione batteria",
            critical = false, Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
    if (Build.VERSION.SDK_INT >= 31 && !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
        Warning("Allarmi esatti non consentiti: l'orario sarà approssimativo.", "Consenti allarmi esatti",
            critical = false, Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
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

    Section("Percorso")
    PlaceField("Partenza", "from", s.from, near = s.to)
    HorizontalDivider()
    PlaceField("Arrivo", "to", s.to, near = s.from)

    Section("Quando")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { d ->
            FilterChip(
                selected = d in days,
                onClick = { days = if (d in days) days - d else days + d },
                label = { Text(d.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0])) },
            )
        }
    }
    OutlinedTextField(time, { time = it }, label = { Text("Ora") },
        isError = parsedTime == null, singleLine = true, modifier = Modifier.fillMaxWidth(),
        supportingText = { Text(if (parsedTime == null) "Usa il formato HH:MM, es. 17:20" else "Formato HH:MM") })

    Section("Avvisi")
    OutlinedTextField(minDelay, { minDelay = it }, label = { Text("Ritardo minimo (minuti)") },
        isError = parsedDelay == null, singleLine = true, modifier = Modifier.fillMaxWidth(),
        supportingText = {
            Text(if (parsedDelay == null) "Inserisci un numero intero, 0 o più"
                else "Notifica solo se il traffico aggiunge almeno questi minuti. 0 = sempre.")
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
    }) { Text("Salva") }
}

/** Address field: geocodes once via TomTom Search (near the other saved place, if any), saves only after confirmation. */
@Composable
fun PlaceField(label: String, prefix: String, current: Place?, near: Place?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var found by remember { mutableStateOf<SearchResult?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    fun search() {
        if (query.isBlank()) return
        status = "Cerco…"
        scope.launch {
            status = try {
                found = withContext(Dispatchers.IO) { TomTom.search(query.trim(), near) }
                found?.let { "Trovato: ${it.label}" } ?: "Nessun risultato"
            } catch (e: Exception) {
                "Errore: ${e.message}"
            }
        }
    }

    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(current?.name ?: "Non impostata", style = MaterialTheme.typography.bodyLarge)
        current?.let {
            Text("%.5f, %.5f".format(it.lat, it.lon), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    OutlinedTextField(query, { query = it; found = null; status = null },
        label = { Text(if (current == null) "Cerca indirizzo o luogo" else "Cambia: cerca indirizzo o luogo") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { search() }))
    status?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium,
            color = if (it.startsWith("Errore")) MaterialTheme.colorScheme.error else Color.Unspecified)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = query.isNotBlank(), onClick = { search() }) { Text("Cerca") }
        found?.let { r ->
            Button(onClick = {
                scope.launch {
                    ctx.savePlace(prefix, Place(r.label, r.position.lat, r.position.lon))
                    found = null; query = ""; status = null
                }
            }) { Text("Conferma") }
        }
    }
}
