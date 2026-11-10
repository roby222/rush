package io.github.roby222.rush.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.roby222.rush.R
import io.github.roby222.rush.domain.arrivalCheckTimes
import io.github.roby222.rush.ui.components.PrimaryButton
import io.github.roby222.rush.ui.components.Section
import io.github.roby222.rush.ui.trips.scheduleText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Edits an existing trip on one screen; nothing is written until Save. */
@Composable
fun TripEditorScreen(vm: TripEditorViewModel, onSaved: (Int) -> Unit, onDeleted: () -> Unit) {
    val d by vm.draft.collectAsStateWithLifecycle()
    val v by vm.validation.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    NameField(vm, d)
    Section(stringResource(R.string.section_route))
    PlacesFields(vm, d, search)
    Section(stringResource(R.string.section_when))
    WhenFields(vm, d, v)
    Section(stringResource(R.string.section_alerts))
    AlertFields(vm, d, v)
    SaveButton(vm, v, stringResource(R.string.save), onSaved)
    v.missing?.let {
        Text(stringResource(if (it == Missing.NAME) R.string.trip_name_error else R.string.places_required),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
    }
    TextButton(onClick = { confirmDelete = true },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) { Text(stringResource(R.string.delete_trip)) }
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

/**
 * A new trip, one question per step: where, when, alerts. Next opens only on a valid step; Back (the bar's
 * or the system's) goes one step back through [TripEditorViewModel.back].
 */
@Composable
fun NewTripScreen(vm: TripEditorViewModel, onSaved: (Int) -> Unit) {
    val d by vm.draft.collectAsStateWithLifecycle()
    val v by vm.validation.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val step by vm.step.collectAsStateWithLifecycle()

    StepProgress(step)
    Text(stringResource(listOf(R.string.wizard_where, R.string.wizard_when, R.string.wizard_alerts)[step]),
        style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    when (step) {
        0 -> {
            PlacesFields(vm, d, search)
            // The name fills in from the destination: ask for it only once there is one.
            if (d.to != null) NameField(vm, d, help = true)
        }
        1 -> WhenFields(vm, d, v)
        else -> {
            AlertFields(vm, d, v)
            Summary(d, v)
        }
    }
    if (step < LAST_STEP) {
        PrimaryButton(stringResource(R.string.next), enabled = if (step == 0) v.whereDone else v.whenDone) { vm.next() }
    } else {
        SaveButton(vm, v, stringResource(R.string.save_trip), onSaved)
    }
}

/** What is about to be saved, as the trips list will show it. */
@Composable
private fun Summary(d: Draft, v: Validation) {
    val t = v.trip ?: return
    val checks = d.usualMin?.takeIf { t.arriveBy }?.let { arrivalCheckTimes(t.hour, t.minute, it) }
        ?.map { it.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.summary), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(t.name, style = MaterialTheme.typography.titleMedium)
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Text(scheduleText(t), style = MaterialTheme.typography.bodyMedium, color = muted)
            Text("${t.from?.name?.substringBefore(",").orEmpty()} → ${t.to?.name?.substringBefore(",").orEmpty()}",
                style = MaterialTheme.typography.bodyMedium, color = muted)
            checks?.let { Text(stringResource(R.string.summary_checks, it[0], it[1]), style = MaterialTheme.typography.bodyMedium, color = muted) }
        }
    }
}

@Composable
private fun SaveButton(vm: TripEditorViewModel, v: Validation, label: String, onSaved: (Int) -> Unit) {
    val saveError by vm.saveError.collectAsStateWithLifecycle()
    PrimaryButton(label, enabled = v.canSave) { vm.save(onSaved) }
    saveError?.let {
        Text(stringResource(R.string.save_failed, it), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
    }
}
