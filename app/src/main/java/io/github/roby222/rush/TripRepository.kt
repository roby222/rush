package io.github.roby222.rush

import android.content.Context
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The one door the UI uses for trips and their checks. Saving or deleting a trip also updates the
 * alarms, so a screen can never leave the schedule out of date.
 */
class TripRepository(private val ctx: Context) {
    val state: Flow<AppState> = ctx.stateFlow()

    suspend fun save(t: Trip): Trip = ctx.saveTrip(t).also { Scheduler.scheduleAll(ctx) }

    suspend fun delete(id: Int) {
        ctx.deleteTrip(id)
        Scheduler.scheduleAll(ctx)
    }

    suspend fun reschedule() = Scheduler.scheduleAll(ctx)

    fun checkNow(id: Int) = Scheduler.checkNow(ctx, id, manual = true)

    /** Whether a manual check of trip [id] is queued or running. */
    fun checking(id: Int): Flow<Boolean> = WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(manualWork(id))
        .map { infos -> infos.any { !it.state.isFinished } }

    suspend fun search(query: String, near: Place?): SearchResult? =
        withContext(Dispatchers.IO) { TomTom.search(query, near) }
}
