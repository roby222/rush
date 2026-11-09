package io.github.roby222.rush

import android.content.Context
import androidx.work.WorkManager
import io.github.roby222.rush.widget.TripWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The one door the UI uses for trips and their checks. Saving or deleting a trip also updates the
 * alarms and the home-screen widgets, so a screen can never leave them out of date.
 */
class TripRepository(private val ctx: Context) {
    val state: Flow<AppState> = ctx.stateFlow()

    suspend fun save(t: Trip): Trip = ctx.saveTrip(t).also {
        Scheduler.scheduleAll(ctx)
        TripWidget.refresh(ctx)
    }

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

    suspend fun search(query: String, near: Place?): SearchResult? =
        withContext(Dispatchers.IO) { TomTom.search(query, near) }
}
