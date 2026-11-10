package io.github.roby222.rush.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.roby222.rush.R
import io.github.roby222.rush.model.Trip
import io.github.roby222.rush.ui.editor.NewTripScreen
import io.github.roby222.rush.ui.editor.TripEditorScreen
import io.github.roby222.rush.ui.editor.TripEditorViewModel
import io.github.roby222.rush.ui.home.HomeScreen
import io.github.roby222.rush.ui.key.ApiKeyScreen
import io.github.roby222.rush.ui.trips.TripsScreen

private enum class Screen { HOME, TRIPS, EDITOR, KEY }

/** The app shell: top bar and the three screens (Home → Trips → Editor), with system Back walking up. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RushApp(vm: MainViewModel, editor: TripEditorViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val home = vm.home.collectAsStateWithLifecycle().value ?: return
    // Without a TomTom key (a fresh release install) nothing works: the key comes first, with no way around it.
    val noKey = vm.hasKey.collectAsStateWithLifecycle().value == false
    val draft by editor.draft.collectAsStateWithLifecycle()
    val creating = screen == Screen.EDITOR && draft.id == 0
    // Back inside a new trip walks its steps first, then leaves it.
    val up = {
        screen = when {
            creating && editor.back() -> Screen.EDITOR
            screen == Screen.EDITOR || screen == Screen.KEY -> Screen.TRIPS
            else -> Screen.HOME
        }
    }
    val edit = { id: Int ->
        editor.open(home.state.trips.firstOrNull { it.id == id } ?: Trip())
        screen = Screen.EDITOR
    }
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(when {
                    noKey -> stringResource(R.string.key_title)
                    else -> when (screen) {
                    Screen.HOME -> "Rush"
                    Screen.TRIPS -> stringResource(R.string.trips_title)
                    Screen.EDITOR -> home.state.trips.firstOrNull { it.id == draft.id && draft.id != 0 }?.name
                        ?: stringResource(R.string.new_trip)
                    Screen.KEY -> stringResource(R.string.key_title)
                    }
                })
            },
            navigationIcon = {
                if (screen != Screen.HOME && !noKey) IconButton(onClick = up) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                if (screen == Screen.HOME && !noKey) IconButton(onClick = { screen = Screen.TRIPS }) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.trips_title))
                }
            },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).consumeWindowInsets(pad).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (noKey) {
                ApiKeyScreen(vm, onSaved = { screen = Screen.HOME })
                return@Column
            }
            when (screen) {
                Screen.HOME -> HomeScreen(vm, home, onAdd = { edit(0) })
                Screen.TRIPS -> {
                    BackHandler { screen = Screen.HOME }
                    TripsScreen(home.state.trips, onEdit = edit, onKey = { screen = Screen.KEY })
                }
                Screen.KEY -> {
                    BackHandler(onBack = up)
                    ApiKeyScreen(vm, onSaved = { screen = Screen.TRIPS })
                }
                Screen.EDITOR -> {
                    BackHandler(onBack = up)
                    val saved = { id: Int -> vm.show(id); screen = Screen.HOME }
                    if (creating) NewTripScreen(editor, onSaved = saved)
                    else TripEditorScreen(editor, onSaved = saved, onDeleted = { screen = Screen.TRIPS })
                }
            }
        }
    }
}
