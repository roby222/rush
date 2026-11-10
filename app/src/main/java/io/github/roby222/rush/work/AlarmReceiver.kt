package io.github.roby222.rush.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.roby222.rush.widget.TripWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires a trip's check on its alarm; reschedules every trip and redraws the widgets on alarm, boot, clock/timezone change and app update. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val tripId = intent.getIntExtra(EXTRA_TRIP, 0)
        // The v0.2.0 alarm has no trip: it only triggers the rescheduling that cancels it.
        if (intent.action == ACTION_CHECK && tripId > 0) Scheduler.checkNow(ctx, tripId, manual = false)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Scheduler.scheduleAll(ctx)
                // After an app update, reboot or clock change the widgets need a redraw too (and may turn stale).
                TripWidget.refresh(ctx)
            } finally {
                pending.finish()
            }
        }
    }
}
