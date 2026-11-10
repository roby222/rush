package io.github.roby222.rush.data.local

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.roby222.rush.domain.expired
import io.github.roby222.rush.model.AppState
import io.github.roby222.rush.model.Trip
import io.github.roby222.rush.model.TripResult
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

val Context.store by preferencesDataStore("settings")
private val json = Json { ignoreUnknownKeys = true }

object Keys {
    val trips = stringPreferencesKey("trips")
    val nextId = intPreferencesKey("next_trip_id")
    /** Trip ids with an alarm set, so alarms of deleted trips can be cancelled. */
    val alarmIds = stringSetPreferencesKey("alarm_ids")
    fun result(id: Int) = stringPreferencesKey("result_$id")
}

// v0.2.0 stored a single trip under these keys; it is not converted (one user, who re-enters it).
private val LEGACY_KEYS = setOf(
    "days", "hour", "minute", "min_delay", "last_title", "last_text", "last_at", "last_check",
    "from_name", "from_lat", "from_lon", "to_name", "to_lat", "to_lon",
)

fun Preferences.trips(): List<Trip> =
    this[Keys.trips]?.let { runCatching { json.decodeFromString<List<Trip>>(it) }.getOrNull() }.orEmpty()

fun Preferences.result(id: Int): TripResult? =
    this[Keys.result(id)]?.let { runCatching { json.decodeFromString<TripResult>(it) }.getOrNull() }

/** Adds [t] when its id is 0, otherwise replaces the trip with the same id; returns the saved trip. */
fun MutablePreferences.putTrip(t: Trip): Trip {
    val list = trips()
    val saved = if (t.id == 0) t.copy(id = this[Keys.nextId] ?: 1) else t
    if (t.id == 0) this[Keys.nextId] = saved.id + 1
    this[Keys.trips] = json.encodeToString(if (t.id == 0) list + saved else list.map { if (it.id == t.id) saved else it })
    asMap().keys.filter { it.name in LEGACY_KEYS }.forEach { remove(it) }
    return saved
}

fun MutablePreferences.removeTrip(id: Int) {
    this[Keys.trips] = json.encodeToString(trips().filterNot { it.id == id })
    remove(Keys.result(id))
}

fun Context.stateFlow(): Flow<AppState> = store.data.map { p ->
    val trips = p.trips()
    AppState(trips, trips.mapNotNull { t -> p.result(t.id)?.let { t.id to it } }.toMap())
}

suspend fun Context.trips(): List<Trip> = store.data.first().trips()

suspend fun Context.saveTrip(t: Trip): Trip {
    var saved = t
    store.edit { saved = it.putTrip(t) }
    return saved
}

suspend fun Context.deleteTrip(id: Int) = store.edit { it.removeTrip(id) }

/** Removes the one-off trips whose time is long past (see [expired]). */
suspend fun Context.pruneExpired(now: ZonedDateTime) = store.edit { p ->
    p.trips().filter { expired(it, now) }.forEach { p.removeTrip(it.id) }
}

/** Saves a check result, unless the trip was deleted while the check ran. */
suspend fun Context.saveResult(id: Int, r: TripResult) = store.edit {
    if (it.trips().any { t -> t.id == id }) it[Keys.result(id)] = json.encodeToString(r)
}
