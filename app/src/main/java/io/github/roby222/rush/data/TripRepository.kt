package io.github.roby222.rush.data

import android.content.Context
import androidx.work.WorkManager
import io.github.roby222.rush.data.local.apiKey
import io.github.roby222.rush.data.local.apiKeyFlow
import io.github.roby222.rush.data.local.deleteTrip
import io.github.roby222.rush.data.local.saveApiKey
import io.github.roby222.rush.data.local.storedApiKey
import io.github.roby222.rush.data.local.saveTrip
import io.github.roby222.rush.data.local.stateFlow
import io.github.roby222.rush.data.remote.MissingKeyException
import io.github.roby222.rush.data.remote.SearchResult
import io.github.roby222.rush.data.remote.TomTom
import io.github.roby222.rush.data.remote.toCheck
import io.github.roby222.rush.domain.nextCheck
import io.github.roby222.rush.model.AppState
import io.github.roby222.rush.model.Check
import io.github.roby222.rush.model.Place
import io.github.roby222.rush.model.Trip
import io.github.roby222.rush.widget.TripWidget
import io.github.roby222.rush.work.Scheduler
import io.github.roby222.rush.work.manualWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

/**
 * The one door the UI uses for trips and their checks. Saving or deleting a trip also updates the
 * alarms and the home-screen widgets, so a screen can never leave them out of date.
 */
class TripRepository(private val ctx: Context) {
    val state: Flow<AppState> = ctx.stateFlow()

    /**
     * Saves [t] and reschedules. An arrival trip needs its usual duration to know when to check: the editor
     * estimates it once the places are chosen; when that failed it is asked here, and a failure fails the save.
     */
    suspend fun save(t: Trip): Trip {
        val toSave = if (!t.arriveBy || t.usualMin != null || t.from == null || t.to == null) t
        else t.copy(usualMin = usualMin(t.from, t.to))
        return ctx.saveTrip(toSave).also {
            Scheduler.scheduleAll(ctx)
            // A one-off arrival saved too late for its checks gets one right away (it notifies like a scheduled one).
            if (it.arriveBy && it.date != null && nextCheck(it, ZonedDateTime.now()) == null) Scheduler.checkNow(ctx, it.id, manual = false)
            TripWidget.refresh(ctx)
        }
    }

    /** A first estimate of the usual duration; the trip's first scheduled check refines it (see [Check.usualMin]). */
    suspend fun usualMin(from: Place, to: Place): Int =
        withContext(Dispatchers.IO) { TomTom.route(key(), from.lat, from.lon, to.lat, to.lon).toCheck("").usualMin }

    suspend fun delete(id: Int) {
        ctx.deleteTrip(id)
        Scheduler.scheduleAll(ctx)
        TripWidget.refresh(ctx) // its widget now says the trip was removed
    }

    suspend fun reschedule() = Scheduler.scheduleAll(ctx)

    fun checkNow(id: Int) = Scheduler.checkNow(ctx, id, manual = true)

    /** Whether a manual check of trip [id] is queued or running. */
    fun checking(id: Int): Flow<Boolean> = WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(manualWork(id))
        .map { infos -> infos.any { !it.state.isFinished } }

    suspend fun suggest(query: String, near: Place?): List<SearchResult> =
        withContext(Dispatchers.IO) { TomTom.suggest(key(), query, near) }

    private suspend fun key() = ctx.apiKey() ?: throw MissingKeyException()

    /** Whether a TomTom key exists (entered by the user, or a debug build's). */
    val hasKey: Flow<Boolean> = ctx.apiKeyFlow().map { it != null }

    /** The last four characters of the key the user entered, to recognise it; null when none was entered. */
    suspend fun storedKeyEnd(): String? = ctx.storedApiKey()?.takeLast(4)

    /**
     * Tries [key] with one search and saves it only if TomTom accepts it. Throws [KeyRefusedException]
     * when TomTom refuses it, and the network error otherwise; nothing is saved then.
     */
    suspend fun verifyAndSaveKey(key: String) {
        withContext(Dispatchers.IO) { TomTom.suggest(key.trim(), "Milano", null) }
        ctx.saveApiKey(key)
    }
}
