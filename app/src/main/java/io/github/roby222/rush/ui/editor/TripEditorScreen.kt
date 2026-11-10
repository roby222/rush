package io.github.roby222.rush.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.roby222.rush.R
import io.github.roby222.rush.model.Place
import io.github.roby222.rush.ui.components.PrimaryButton
import io.github.roby222.rush.ui.components.Section
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Edits the draft in [vm]; nothing is written until Save. */
@Composable
fun TripEditorScreen(vm: TripEditorViewModel, onSaved: (Int) -> Unit, onDeleted: () -> Unit) {
    val d by vm.draft.collectAsStateWithLifecycle()
    val v by vm.validation.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(d.name, { name -> vm.update { it.copy(name = name) } }, label = { Text(stringResource(R.string.trip_name)) },
        singleLine = true, modifier = Modifier.fillMaxWidth())

    Section(stringResource(R.string.section_route))
    PlaceField(stringResource(R.string.departure), d.from, search[End.FROM],
        onSearch = { vm.search(End.FROM, it) }, onClear = { vm.clearSearch(End.FROM) }, onConfirm = { vm.confirm(End.FROM) })
    HorizontalDivider()
    PlaceField(stringResource(R.string.destination), d.to, search[End.TO],
        onSearch = { vm.search(End.TO, it) }, onClear = { vm.clearSearch(End.TO) }, onConfirm = { vm.confirm(End.TO) })

    Section(stringResource(R.string.section_when))
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
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(false, true).forEachIndexed { i, option ->
            SegmentedButton(selected = d.arriveBy == option, onClick = { vm.update { it.copy(arriveBy = option) } },
                shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                Text(stringResource(if (option) R.string.mode_arrive else R.string.mode_check))
            }
        }
    }
    val conflict = v.conflict
    OutlinedTextField(d.time, { time -> vm.update { it.copy(time = time) } },
        label = { Text(stringResource(if (d.arriveBy) R.string.arrival_time else R.string.time)) },
        isError = v.badTime || conflict != null || v.past, singleLine = true, modifier = Modifier.fillMaxWidth(),
        supportingText = {
            Text(when {
                v.badTime -> stringResource(R.string.time_error)
                conflict != null -> stringResource(if (once) R.string.time_conflict_once else R.string.time_conflict,
                    conflict.name, "%02d:%02d".format(conflict.hour, conflict.minute))
                v.past -> stringResource(R.string.time_past)
                d.arriveBy -> stringResource(R.string.arrive_help)
                else -> stringResource(R.string.time_help)
            })
        })

    Section(stringResource(R.string.section_alerts))
    OutlinedTextField(d.minDelay, { delay -> vm.update { it.copy(minDelay = delay) } }, label = { Text(stringResource(R.string.min_delay)) },
        isError = v.badDelay, singleLine = true, modifier = Modifier.fillMaxWidth(),
        supportingText = { Text(stringResource(if (v.badDelay) R.string.min_delay_error else R.string.min_delay_help)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    val saveError by vm.saveError.collectAsStateWithLifecycle()
    PrimaryButton(stringResource(R.string.save), enabled = v.canSave) { vm.save(onSaved) }
    saveError?.let {
        Text(stringResource(R.string.save_failed, it), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
    }
    v.missing?.let {
        Text(stringResource(if (it == Missing.NAME) R.string.trip_name_error else R.string.places_required),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
    }
    if (d.id != 0) {
        TextButton(onClick = { confirmDelete = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.delete_trip)) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title, d.name)) },
            text = { Text(stringResource(R.string.delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onDeleted) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
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

/** One end of the trip: the saved place, a search field, the result and Confirm. The search runs in the ViewModel. */
@Composable
fun PlaceField(label: String, current: Place?, search: SearchState?, onSearch: (String) -> Unit, onClear: () -> Unit, onConfirm: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    fun submit() {
        if (query.isBlank()) return
        focus.clearFocus() // closes the keyboard, which would otherwise cover the Confirm button
        onSearch(query)
    }

    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(current?.name ?: stringResource(R.string.not_set), style = MaterialTheme.typography.bodyLarge)
        current?.let {
            Text("%.5f, %.5f".format(it.lat, it.lon), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    OutlinedTextField(query, { query = it; onClear() },
        label = { Text(stringResource(if (current == null) R.string.search_place else R.string.change_place)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }))
    when (search) {
        null -> {}
        SearchState.Searching -> Text(stringResource(R.string.searching), style = MaterialTheme.typography.bodyMedium)
        SearchState.NoResults -> Text(stringResource(R.string.no_results), style = MaterialTheme.typography.bodyMedium)
        is SearchState.Found -> Text(stringResource(R.string.found, search.result.label), style = MaterialTheme.typography.bodyMedium)
        is SearchState.Failed -> Text(stringResource(R.string.search_error, search.message.orEmpty()),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = query.isNotBlank(), onClick = { submit() }) { Text(stringResource(R.string.search)) }
        if (search is SearchState.Found) {
            Button(onClick = {
                onConfirm()
                focus.clearFocus()
                query = ""
            }) { Text(stringResource(R.string.confirm)) }
        }
    }
}
