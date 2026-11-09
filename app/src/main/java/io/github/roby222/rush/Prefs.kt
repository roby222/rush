package io.github.roby222.rush

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

val Context.store by preferencesDataStore("settings")

data class Place(val name: String, val lat: Double, val lon: Double)

data class Settings(
    val from: Place?,
    val to: Place?,
    val days: Set<DayOfWeek>,
    val hour: Int,
    val minute: Int,
    val minDelayMin: Int,
    val lastTitle: String?,
    val lastText: String?,
    val lastAt: Long?,
)

object Keys {
    val days = stringSetPreferencesKey("days")
    val hour = intPreferencesKey("hour")
    val minute = intPreferencesKey("minute")
    val minDelay = intPreferencesKey("min_delay")
    val lastTitle = stringPreferencesKey("last_title")
    val lastText = stringPreferencesKey("last_text")
    val lastAt = longPreferencesKey("last_at")
    fun name(p: String) = stringPreferencesKey("${p}_name")
    fun lat(p: String) = doublePreferencesKey("${p}_lat")
    fun lon(p: String) = doublePreferencesKey("${p}_lon")
}

private fun Preferences.place(p: String): Place? {
    val lat = this[Keys.lat(p)] ?: return null
    val lon = this[Keys.lon(p)] ?: return null
    return Place(this[Keys.name(p)] ?: "", lat, lon)
}

fun Context.settingsFlow(): Flow<Settings> = store.data.map { p ->
    Settings(
        from = p.place("from"),
        to = p.place("to"),
        days = p[Keys.days]?.map { DayOfWeek.valueOf(it) }?.toSet()
            ?: setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        hour = p[Keys.hour] ?: 17,
        minute = p[Keys.minute] ?: 20,
        minDelayMin = p[Keys.minDelay] ?: 0,
        lastTitle = p[Keys.lastTitle],
        lastText = p[Keys.lastText],
        lastAt = p[Keys.lastAt],
    )
}

suspend fun Context.settings() = settingsFlow().first()

suspend fun Context.savePlace(prefix: String, place: Place) = store.edit {
    it[Keys.name(prefix)] = place.name
    it[Keys.lat(prefix)] = place.lat
    it[Keys.lon(prefix)] = place.lon
}
