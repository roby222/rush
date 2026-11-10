package io.github.roby222.rush.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import io.github.roby222.rush.R
import io.github.roby222.rush.data.local.trips
import io.github.roby222.rush.model.Trip
import io.github.roby222.rush.ui.theme.RushTheme
import kotlinx.coroutines.launch

/** Shown when a widget is added (or reconfigured): picks its trip; with a single trip it picks it alone. */
class TripWidgetConfigActivity : ComponentActivity() {
    private val widgetId by lazy {
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Leaving without a choice must not place the widget.
        setResult(RESULT_CANCELED, result())
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        enableEdgeToEdge()
        lifecycleScope.launch {
            val trips = trips()
            if (trips.size <= 1) return@launch choose(trips.firstOrNull())
            setContent { RushTheme { Picker(trips) } }
        }
    }

    private fun result() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    /** Stores the trip in the widget's state (null: none yet, the widget invites to add one), draws it, closes. */
    private fun choose(trip: Trip?) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@TripWidgetConfigActivity).getGlanceIdBy(widgetId)
            updateAppWidgetState(this@TripWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) {
                it.toMutablePreferences().apply { this[WIDGET_TRIP] = trip?.id ?: 0 }
            }
            TripWidget().update(this@TripWidgetConfigActivity, glanceId)
            setResult(RESULT_OK, result())
            finish()
        }
    }

    @androidx.compose.runtime.Composable
    private fun Picker(trips: List<Trip>) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.widget_pick_trip), style = MaterialTheme.typography.headlineSmall)
                trips.forEach { t ->
                    Card(onClick = { choose(t) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text("${t.from?.name?.substringBefore(",").orEmpty()} → ${t.to?.name?.substringBefore(",").orEmpty()}",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
