package io.github.roby222.rush.ui.trips

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.roby222.rush.R
import io.github.roby222.rush.model.Trip
import io.github.roby222.rush.ui.components.PrimaryButton
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The list of trips: tap one to edit it. */
@Composable
fun TripsScreen(trips: List<Trip>, onEdit: (Int) -> Unit, onKey: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    if (trips.isEmpty()) {
        Text(stringResource(R.string.no_trips), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
    }
    trips.forEach { t ->
        Card(onClick = { onEdit(t.id) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium)
                Text(scheduleText(t),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Text("${t.from?.name?.substringBefore(",").orEmpty()} → ${t.to?.name?.substringBefore(",").orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
    PrimaryButton(stringResource(R.string.add_trip)) { onEdit(0) }
    TextButton(onClick = onKey) { Text(stringResource(R.string.key_title)) }
}

/** "Mon, Wed, Fri · 17:20", "Tue 10 Nov · 15:00" or "Mon, Wed, Fri · arrive 18:50". */
@Composable
internal fun scheduleText(t: Trip): String {
    val time = "%02d:%02d".format(t.hour, t.minute)
    return stringResource(R.string.trip_schedule, whenLabel(t, LocalConfiguration.current.locales[0]),
        if (t.arriveBy) stringResource(R.string.arrive_at, time) else time)
}

/** "Mon, Wed, Fri" for a weekly trip, "Tue 10 Nov" for a one-off one. */
private fun whenLabel(t: Trip, locale: Locale) = t.day()?.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    ?: DayOfWeek.entries.filter { it in t.days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
