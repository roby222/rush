package io.github.roby222.rush.model

import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.Serializable

val DEFAULT_DAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

// java.io.Serializable so the editor can keep a draft place across rotation (rememberSaveable).
@Serializable data class Place(val name: String, val lat: Double, val lon: Double) : java.io.Serializable

/**
 * A trip: weekly on [days], or once on [date] (ISO, e.g. "2026-11-10"; then [days] is ignored).
 * [id] 0 means "not saved yet"; saved ids start at 1 and are never reused.
 */
@Serializable data class Trip(
    val id: Int = 0,
    val name: String = "",
    val from: Place? = null,
    val to: Place? = null,
    val days: Set<DayOfWeek> = DEFAULT_DAYS,
    val hour: Int = 17,
    val minute: Int = 20,
    val minDelayMin: Int = 0,
    val date: String? = null,
) {
    /** The day of a one-off trip, null for a weekly one. */
    fun day(): LocalDate? = date?.let(LocalDate::parse)
}

/** The last check of a trip: the notification's words, and the [check] itself (null when it failed). */
@Serializable data class TripResult(val lastAt: Long, val title: String, val text: String, val check: Check? = null)

/** Everything the screens show, read in one go. */
data class AppState(val trips: List<Trip>, val results: Map<Int, TripResult>)
