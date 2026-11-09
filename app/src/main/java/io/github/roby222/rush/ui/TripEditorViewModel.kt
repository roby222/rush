package io.github.roby222.rush.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.roby222.rush.Place
import io.github.roby222.rush.SearchResult
import io.github.roby222.rush.Trip
import io.github.roby222.rush.TripRepository
import io.github.roby222.rush.timeConflict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/** A trip as typed in the editor, before it is valid. Serializable: it lives in the SavedStateHandle. */
data class Draft(
    val id: Int = 0,
    val name: String = "",
    val from: Place? = null,
    val to: Place? = null,
    val days: Set<DayOfWeek> = emptySet(),
    val time: String = "",
    val minDelay: String = "",
) : java.io.Serializable {
    companion object {
        fun of(t: Trip) = Draft(t.id, t.name, t.from, t.to, t.days, "%02d:%02d".format(t.hour, t.minute), t.minDelayMin.toString())
    }
}

enum class Missing { NAME, PLACES }

/** The editor's verdict on a draft: the trip it describes (when the time parses) and what stops a save. */
data class Validation(val trip: Trip?, val badTime: Boolean, val badDelay: Boolean, val conflict: Trip?, val missing: Missing?) {
    val canSave get() = trip != null && !badDelay && conflict == null && missing == null
}

fun validate(d: Draft, trips: List<Trip>): Validation {
    val time = Regex("""(\d{1,2}):(\d{2})""").matchEntire(d.time.trim())
        ?.destructured?.let { (h, m) -> h.toInt() to m.toInt() }
        ?.takeIf { (h, m) -> h in 0..23 && m in 0..59 }
    val delay = d.minDelay.trim().toIntOrNull()?.takeIf { it >= 0 }
    val trip = time?.let { (h, m) -> Trip(d.id, d.name.trim(), d.from, d.to, d.days, h, m, delay ?: 0) }
    val missing = when {
        d.name.isBlank() -> Missing.NAME
        d.from == null || d.to == null -> Missing.PLACES
        else -> null
    }
    return Validation(trip, time == null, delay == null, trip?.let { timeConflict(it, trips) }, missing)
}

/** Which end of the trip a place search is for. */
enum class End { FROM, TO }

sealed interface SearchState {
    data object Searching : SearchState
    data class Found(val result: SearchResult) : SearchState
    data object NoResults : SearchState
    data class Failed(val message: String?) : SearchState
}

private const val DRAFT = "draft"

class TripEditorViewModel(app: Application, private val handle: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = TripRepository(app)
    private val trips = repo.state.map { it.trips }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val draft: StateFlow<Draft> = handle.getStateFlow(DRAFT, Draft())
    val validation: StateFlow<Validation> = combine(draft, trips, ::validate)
        .stateIn(viewModelScope, SharingStarted.Eagerly, validate(draft.value, emptyList()))

    private val _search = MutableStateFlow<Map<End, SearchState>>(emptyMap())
    val search: StateFlow<Map<End, SearchState>> = _search

    /** Starts editing [trip] (id 0: a new one), dropping any previous draft. */
    fun open(trip: Trip) {
        handle[DRAFT] = Draft.of(trip)
        _search.value = emptyMap()
    }

    fun update(change: (Draft) -> Draft) { handle[DRAFT] = change(draft.value) }

    /** Searches near the other end of the trip, so a bare street name finds the one in the same city. */
    fun search(end: End, query: String) {
        if (query.isBlank()) return
        val near = if (end == End.FROM) draft.value.to else draft.value.from
        _search.value += end to SearchState.Searching
        viewModelScope.launch {
            val result = try {
                repo.search(query.trim(), near)?.let { SearchState.Found(it) } ?: SearchState.NoResults
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchState.Failed(e.message)
            }
            _search.value += end to result
        }
    }

    fun clearSearch(end: End) { _search.value -= end }

    /** Puts the found place into the draft; a new trip takes its name from the destination. */
    fun confirm(end: End) {
        val r = (_search.value[end] as? SearchState.Found)?.result ?: return
        val place = Place(r.label, r.position.lat, r.position.lon)
        update {
            if (end == End.FROM) it.copy(from = place)
            else it.copy(to = place, name = it.name.ifBlank { place.name.substringBefore(",") })
        }
        clearSearch(end)
    }

    fun save(onSaved: (Int) -> Unit) {
        val v = validation.value
        val trip = v.trip?.takeIf { v.canSave } ?: return
        viewModelScope.launch { onSaved(repo.save(trip).id) }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = draft.value.id.takeIf { it != 0 } ?: return
        viewModelScope.launch {
            repo.delete(id)
            onDeleted()
        }
    }
}
