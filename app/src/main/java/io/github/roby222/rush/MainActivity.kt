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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { MaterialTheme { App() } }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { Scheduler.schedule(this@MainActivity) }
    }
}

@Composable
fun App() {
    var editing by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val s = remember { ctx.settingsFlow() }.collectAsState(initial = null).value ?: return
    Scaffold { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editing) {
                BackHandler { editing = false }
                SettingsScreen(s) { editing = false }
            } else {
                HomeScreen(s) { editing = true }
            }
        }
    }
}

@Composable
fun HomeScreen(s: Settings, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    Text("Rush", style = MaterialTheme.typography.headlineMedium)
    Text("${s.from?.name ?: "Partenza?"} → ${s.to?.name ?: "Arrivo?"}")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (s.lastAt == null) {
                Text("Nessun controllo ancora")
            } else {
                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.lastAt)),
                    style = MaterialTheme.typography.labelMedium)
                Text(s.lastTitle ?: "", style = MaterialTheme.typography.titleMedium)
                Text(s.lastText ?: "")
            }
        }
    }
    val next = nextTrigger(ZonedDateTime.now(), s.days, s.hour, s.minute)
    Text("Prossimo controllo: " + (next?.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")) ?: "nessuno"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { Scheduler.checkNow(ctx, manual = true) }) { Text("Controlla ora") }
        OutlinedButton(onClick = onSettings) { Text("Impostazioni") }
    }

    val pm = ctx.getSystemService(PowerManager::class.java)
    if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
        Text("Per non perdere i controlli, escludi l'app dall'ottimizzazione batteria.",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }) { Text("Ottimizzazione batteria") }
    }
    if (Build.VERSION.SDK_INT >= 31 && !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
        Text("Allarmi esatti non consentiti: l'orario sarà approssimativo.",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }) { Text("Consenti allarmi esatti") }
    }
}

@Composable
fun SettingsScreen(s: Settings, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by remember { mutableStateOf(s.days) }
    var time by remember { mutableStateOf("%02d:%02d".format(s.hour, s.minute)) }
    var minDelay by remember { mutableStateOf(s.minDelayMin.toString()) }
    val parsedTime = Regex("""(\d{1,2}):(\d{2})""").matchEntire(time)
        ?.destructured?.let { (h, m) -> h.toInt() to m.toInt() }
        ?.takeIf { (h, m) -> h in 0..23 && m in 0..59 }
    val parsedDelay = minDelay.toIntOrNull()?.takeIf { it >= 0 }

    Text("Impostazioni", style = MaterialTheme.typography.headlineMedium)
    PlaceField("Partenza", "from", s.from)
    PlaceField("Arrivo", "to", s.to)

    Text("Giorni")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { d ->
            FilterChip(
                selected = d in days,
                onClick = { days = if (d in days) days - d else days + d },
                label = { Text(d.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0])) },
            )
        }
    }
    OutlinedTextField(time, { time = it }, label = { Text("Ora (HH:MM)") },
        isError = parsedTime == null, singleLine = true)
    OutlinedTextField(minDelay, { minDelay = it }, label = { Text("Avvisa solo se ritardo ≥ minuti (0 = sempre)") },
        isError = parsedDelay == null, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    Button(enabled = parsedTime != null && parsedDelay != null, onClick = {
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

/** Address field: geocodes once via TomTom Search, saves coordinates only after confirmation. */
@Composable
fun PlaceField(label: String, prefix: String, current: Place?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<SearchResult?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    Text("$label: ${current?.let { "${it.name} (%.5f, %.5f)".format(it.lat, it.lon) } ?: "non impostata"}",
        style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(query, { query = it; found = null }, label = { Text("Cerca indirizzo o luogo") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = query.isNotBlank(), onClick = {
            status = "Cerco…"
            scope.launch {
                status = try {
                    found = withContext(Dispatchers.IO) { TomTom.search(query.trim()) }
                    found?.let { "Trovato: ${it.label} (%.5f, %.5f)".format(it.position.lat, it.position.lon) }
                        ?: "Nessun risultato"
                } catch (e: Exception) {
                    "Errore: ${e.message}"
                }
            }
        }) { Text("Cerca") }
        found?.let { r ->
            Button(onClick = {
                scope.launch {
                    ctx.savePlace(prefix, Place(r.label, r.position.lat, r.position.lon))
                    found = null; query = ""; status = null
                }
            }) { Text("Conferma") }
        }
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
