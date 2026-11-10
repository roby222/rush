package io.github.roby222.rush.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.roby222.rush.R
import io.github.roby222.rush.data.remote.SearchResult
import io.github.roby222.rush.domain.arrivalCheckTimes
import io.github.roby222.rush.model.Place
import io.github.roby222.rush.ui.theme.Amber
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

// The fields of a trip, shared by the one-screen editor and the new-trip steps.

@Composable
internal fun NameField(vm: TripEditorViewModel, d: Draft, help: Boolean = false) {
    OutlinedTextField(d.name, { name -> vm.update { it.copy(name = name) } }, label = { Text(stringResource(R.string.trip_name)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        supportingText = if (help) ({ Text(stringResource(R.string.name_from_destination)) }) else null)
}

/** Departure and destination on one card, like the route on the Home: a hollow dot, then the amber one. */
@Composable
internal fun PlacesFields(vm: TripEditorViewModel, d: Draft, search: Map<End, PlaceSearch>) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            PlaceField(vm, End.FROM, stringResource(R.string.departure), d.from, search[End.FROM],
                Modifier.border(2.5.dp, cs.onSurfaceVariant, CircleShape))
            HorizontalDivider(Modifier.padding(start = 26.dp), color = cs.outline)
            PlaceField(vm, End.TO, stringResource(R.string.destination), d.to, search[End.TO],
                Modifier.background(Amber, CircleShape))
        }
    }
}

/**
 * One end of the trip: its chosen place (tap to change), or a field whose suggestions open below it while
 * typing. Only a suggestion becomes the place, so a saved place always has real coordinates.
 */
@Composable
private fun PlaceField(vm: TripEditorViewModel, end: End, label: String, place: Place?, s: PlaceSearch?, dot: Modifier) {
    val cs = MaterialTheme.colorScheme
    if (s == null && place != null) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClickLabel = stringResource(R.string.change)) { vm.change(end) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(12.dp).then(dot))
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Text(place.name, style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(R.string.change), style = MaterialTheme.typography.labelLarge, color = cs.primary)
        }
        return
    }
    val search = s ?: PlaceSearch()
    val focus = remember { FocusRequester() }
    // Reopened to change a place: the keyboard comes up at once.
    if (place != null) LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(search.query, { vm.type(end, it) }, label = { Text(label) },
            placeholder = { Text(stringResource(R.string.search_place)) },
            singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus),
            trailingIcon = if (place != null) ({ TextButton(onClick = { vm.cancelSearch(end) }) { Text(stringResource(R.string.cancel)) } }) else null,
            supportingText = when {
                search.error != null -> ({ Text(stringResource(R.string.search_error, search.error), color = cs.error) })
                search.loading -> ({ Text(stringResource(R.string.searching)) })
                search.none -> ({ Text(stringResource(R.string.no_results)) })
                else -> null
            })
        if (search.results.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 3.dp, tonalElevation = 0.dp, color = cs.surface) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    search.results.forEach { r -> Suggestion(r) { vm.pick(end, r) } }
                }
            }
        }
    }
}

/** A suggested place: its name in bold, then the rest of the address and how far it is from the other end. */
@Composable
private fun Suggestion(r: SearchResult, onPick: () -> Unit) {
    val address = r.address.freeformAddress
    val title = r.poi?.name ?: address.substringBefore(",")
    val rest = if (r.poi != null && !address.startsWith(title)) address else address.substringAfter(", ", "")
    val detail = listOfNotNull(rest.ifBlank { null }, r.dist?.let { stringResource(R.string.distance_km, it / 1000) }).joinToString(" · ")
    Column(Modifier.fillMaxWidth().clickable(onClick = onPick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Weekly days or a date, whether the time is the departure or the arrival, and the time itself. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WhenFields(vm: TripEditorViewModel, d: Draft, v: Validation) {
    val once = d.date != null
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(false, true).forEachIndexed { i, option ->
            SegmentedButton(selected = once == option, onClick = { vm.setOnce(option) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                Text(stringResource(if (option) R.string.repeat_once else R.string.repeat_weekly))
            }
        }
    }
    val date = d.date
    if (date != null) {
        DateField(date) { picked -> vm.update { it.copy(date = picked) } }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in d.days,
                    onClick = { vm.update { it.copy(days = if (day in it.days) it.days - day else it.days + day) } },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0])) },
                )
            }
        }
    }
    Text(stringResource(R.string.time_is), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(false, true).forEachIndexed { i, option ->
            SegmentedButton(selected = d.arriveBy == option, onClick = { vm.update { it.copy(arriveBy = option) } },
                shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                Text(stringResource(if (option) R.string.mode_arrive else R.string.mode_check))
            }
        }
    }
    val conflict = v.conflict
    val checks = v.trip?.takeIf { d.arriveBy }?.let { t -> d.usualMin?.let { arrivalCheckTimes(t.hour, t.minute, it) } }
    OutlinedTextField(d.time, { time -> vm.update { it.copy(time = time) } },
        label = { Text(stringResource(if (d.arriveBy) R.string.arrival_time else R.string.time)) },
        isError = v.badTime || conflict != null || v.past, singleLine = true, modifier = Modifier.fillMaxWidth(),
        supportingText = {
            Text(when {
                v.badTime -> stringResource(R.string.time_error)
                conflict != null -> stringResource(if (once) R.string.time_conflict_once else R.string.time_conflict,
                    conflict.name, "%02d:%02d".format(conflict.hour, conflict.minute))
                v.past -> stringResource(R.string.time_past)
                checks != null -> stringResource(R.string.arrive_help_usual, d.usualMin!!, clock(checks[0]), clock(checks[1]))
                d.arriveBy -> stringResource(R.string.arrive_help)
                else -> stringResource(R.string.time_help)
            })
        })
}

private fun clock(t: LocalTime): String = t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** The delay that makes Rush speak: a few common choices, or any number under "Other". */
@Composable
internal fun AlertFields(vm: TripEditorViewModel, d: Draft, v: Validation) {
    val presets = listOf("0", "5", "10")
    var other by rememberSaveable { mutableStateOf(d.minDelay.trim() !in presets) }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        presets.forEach { value ->
            AlertOption(selected = !other && d.minDelay.trim() == value,
                text = if (value == "0") stringResource(R.string.alert_always) else stringResource(R.string.alert_min, value.toInt()),
                hint = if (value == "0") stringResource(R.string.alert_always_hint) else null,
            ) { other = false; vm.update { it.copy(minDelay = value) } }
        }
        AlertOption(selected = other, text = stringResource(R.string.alert_other), hint = null) { other = true }
    }
    if (other) {
        OutlinedTextField(d.minDelay, { delay -> vm.update { it.copy(minDelay = delay) } }, label = { Text(stringResource(R.string.min_delay)) },
            isError = v.badDelay, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(stringResource(if (v.badDelay) R.string.min_delay_error else R.string.min_delay_help)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
}

@Composable
private fun AlertOption(selected: Boolean, text: String, hint: String?, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** "1 of 3" and a bar that fills step by step. */
@Composable
internal fun StepProgress(step: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(progress = { (step + 1f) / (LAST_STEP + 1) }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
        Text(stringResource(R.string.wizard_step, step + 1, LAST_STEP + 1), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The day of a one-off trip, picked in a Material date picker that starts today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.date_label, date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))))
    }
    if (!open) return
    // The picker speaks UTC midnights.
    val today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today
        },
    )
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                open = false
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state) }
}
