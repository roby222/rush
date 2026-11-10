package io.github.roby222.rush.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.roby222.rush.data.TripRepository
import io.github.roby222.rush.data.remote.SearchResult
import io.github.roby222.rush.domain.nextCheck
import io.github.roby222.rush.domain.timeConflict
import io.github.roby222.rush.model.Place
import io.github.roby222.rush.model.Trip
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A trip as typed in the editor, before it is valid. Serializable: it lives in the SavedStateHandle. */
data class Draft(
    val id: Int = 0,
    val name: String = "",
    val from: Place? = null,
    val to: Place? = null,
    val days: Set<DayOfWeek> = emptySet(),
    val time: String = "",
    val minDelay: String = "",
    /** The day of a one-off trip; null for a weekly one. */
    val date: LocalDate? = null,
    /** The time is the arrival, not the check. */
    val arriveBy: Boolean = false,
    /** Usual duration of the chosen places, estimated as soon as both are set; null while unknown. */
    val usualMin: Int? = null,
) : java.io.Serializable {
    companion object {
        fun of(t: Trip) = Draft(t.id, t.name, t.from, t.to, t.days, "%02d:%02d".format(t.hour, t.minute),
            t.minDelayMin.toString(), t.day(), t.arriveBy, t.usualMin)
    }
}

enum class Missing { NAME, PLACES }

/** The editor's verdict on a draft: the trip it describes (when the time parses) and what stops a save. */
data class Validation(
    val trip: Trip?, val badTime: Boolean, val badDelay: Boolean, val conflict: Trip?, val missing: Missing?, val past: Boolean = false,
) {
    val canSave get() = trip != null && !badDelay && conflict == null && missing == null && !past
    /** The new-trip steps: "where" needs a name and both places, "when" a valid, free, future time. */
    val whereDone get() = missing == null
    val whenDone get() = trip != null && conflict == null && !past
}

/** [now] decides whether a one-off trip's date and time have already passed. */
fun validate(d: Draft, trips: List<Trip>, now: ZonedDateTime = ZonedDateTime.now()): Validation {
    val time = Regex("""(\d{1,2}):(\d{2})""").matchEntire(d.time.trim())
        ?.destructured?.let { (h, m) -> h.toInt() to m.toInt() }
        ?.takeIf { (h, m) -> h in 0..23 && m in 0..59 }
    val delay = d.minDelay.trim().toIntOrNull()?.takeIf { it >= 0 }
    val trip = time?.let { (h, m) ->
        Trip(d.id, d.name.trim(), d.from, d.to, if (d.date == null) d.days else emptySet(), h, m, delay ?: 0,
            d.date?.toString(), d.arriveBy, d.usualMin)
    }
    val missing = when {
        d.name.isBlank() -> Missing.NAME
        d.from == null || d.to == null -> Missing.PLACES
        else -> null
    }
    // An arrival trip's checks depend on its usual duration, still unknown here: the arrival itself must be ahead.
    val past = trip?.date != null && nextCheck(trip.copy(arriveBy = false), now) == null
    return Validation(trip, time == null, delay == null, trip?.let { timeConflict(it, trips) }, missing, past)
}

/** Which end of the trip a place field is for. */
enum class End { FROM, TO }

/** Suggestions start from this many typed characters, after [SUGGEST_PAUSE_MS] without typing. */
const val MIN_QUERY = 3
const val SUGGEST_PAUSE_MS = 300L

fun shouldSuggest(query: String) = query.trim().length >= MIN_QUERY

/** A place field while the user types: the text, and what TomTom suggests for it. */
data class PlaceSearch(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val none get() = !loading && error == null && results.isEmpty() && shouldSuggest(query)
}

private const val DRAFT = "draft"
private const val STEP = "step"

/** The last step of a new trip: 0 where, 1 when, 2 alerts. */
const val LAST_STEP = 2

class TripEditorViewModel(app: Application, private val handle: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = TripRepository(app)
    private val trips = repo.state.map { it.trips }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val draft: StateFlow<Draft> = handle.getStateFlow(DRAFT, Draft())
    val validation: StateFlow<Validation> = combine(draft, trips) { d, t -> validate(d, t) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, validate(draft.value, emptyList()))

    /** The step of a new trip being created; an existing trip is edited on one screen. */
    val step: StateFlow<Int> = handle.getStateFlow(STEP, 0)

    private val _saveError = MutableStateFlow<String?>(null)
    /** Why the last save failed (an arrival trip's duration could not be estimated), or null. */
    val saveError: StateFlow<String?> = _saveError

    private val _search = MutableStateFlow<Map<End, PlaceSearch>>(emptyMap())
    /** The place fields being typed in; an end missing here shows its chosen place. */
    val search: StateFlow<Map<End, PlaceSearch>> = _search
    private val suggestJobs = mutableMapOf<End, Job>()

    /** Starts editing [trip] (id 0: a new one, from its first step), dropping any previous draft. */
    fun open(trip: Trip) {
        handle[DRAFT] = Draft.of(trip)
        handle[STEP] = 0
        suggestJobs.values.forEach { it.cancel() }
        _search.value = if (trip.id == 0) mapOf(End.FROM to PlaceSearch(), End.TO to PlaceSearch()) else emptyMap()
        _saveError.value = null
    }

    fun update(change: (Draft) -> Draft) { handle[DRAFT] = change(draft.value) }

    fun next() { handle[STEP] = (step.value + 1).coerceAtMost(LAST_STEP) }

    /** Goes one step back; false on the first step, where Back leaves the new trip. */
    fun back(): Boolean = (step.value > 0).also { if (it) handle[STEP] = step.value - 1 }

    /**
     * Switches between weekly and one-off. A one-off trip starts today, from the departure of the
     * last saved trip when it has none yet (an appointment usually starts where the other trips do).
     */
    fun setOnce(once: Boolean) {
        val from = draft.value.from
        update {
            if (!once) it.copy(date = null)
            else it.copy(date = it.date ?: LocalDate.now(), from = it.from ?: trips.value.maxByOrNull { t -> t.id }?.from)
        }
        if (from == null && draft.value.from != null) {
            _search.value -= End.FROM
            estimate()
        }
    }

    /** Opens the field of [end] for a new search, starting from what it holds. */
    fun change(end: End) {
        _search.value += end to PlaceSearch()
    }

    /** The text of [end] changed: suggestions follow once the user pauses, searched near the other end. */
    fun type(end: End, query: String) {
        suggestJobs[end]?.cancel()
        _search.value += end to PlaceSearch(query, loading = shouldSuggest(query))
        if (!shouldSuggest(query)) return
        val near = if (end == End.FROM) draft.value.to else draft.value.from
        suggestJobs[end] = viewModelScope.launch {
            delay(SUGGEST_PAUSE_MS)
            val next = try {
                // TomTom may return several stretches of one street: show each place once.
                PlaceSearch(query, repo.suggest(query.trim(), near).distinctBy { it.label })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlaceSearch(query, error = e.message ?: e.javaClass.simpleName)
            }
            _search.value += end to next
        }
    }

    /** Closes the field of [end] without changing its place (only when it has one). */
    fun cancelSearch(end: End) {
        if ((if (end == End.FROM) draft.value.from else draft.value.to) == null) return
        suggestJobs[end]?.cancel()
        _search.value -= end
    }

    /** Puts a suggestion into the draft; a new trip takes its name from the destination. */
    fun pick(end: End, r: SearchResult) {
        suggestJobs[end]?.cancel()
        val place = Place(r.label, r.position.lat, r.position.lon)
        update {
            if (end == End.FROM) it.copy(from = place)
            else it.copy(to = place, name = it.name.ifBlank { place.name.substringBefore(",") })
        }
        _search.value -= end
        estimate()
    }

    /** Estimates the usual duration of the chosen places, so the "when" step can say when Rush checks. */
    private fun estimate() {
        val d = draft.value
        val from = d.from ?: return
        val to = d.to ?: return
        update { it.copy(usualMin = null) }
        viewModelScope.launch {
            // A failure leaves it unknown: an arrival trip asks again on save.
            val min = try { repo.usualMin(from, to) } catch (e: CancellationException) { throw e } catch (e: Exception) { return@launch }
            update { if (it.from == from && it.to == to) it.copy(usualMin = min) else it }
        }
    }

    fun save(onSaved: (Int) -> Unit) {
        val v = validation.value
        val trip = v.trip?.takeIf { v.canSave } ?: return
        _saveError.value = null
        viewModelScope.launch {
            val saved = try {
                repo.save(trip)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveError.value = e.message ?: e.javaClass.simpleName
                return@launch
            }
            onSaved(saved.id)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = draft.value.id.takeIf { it != 0 } ?: return
        viewModelScope.launch {
            repo.delete(id)
            onDeleted()
        }
    }
}
