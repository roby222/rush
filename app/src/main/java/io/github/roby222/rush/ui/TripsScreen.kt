package io.github.roby222.rush.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.text.TextUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.roby222.rush.*
import io.github.roby222.rush.R
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** The list of trips: tap one to edit it. */
@Composable
fun TripsScreen(trips: List<Trip>, onEdit: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    if (trips.isEmpty()) {
        Text(stringResource(R.string.no_trips), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
    }
    trips.forEach { t ->
        Card(onClick = { onEdit(t.id) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.trip_schedule, whenLabel(t, locale), "%02d:%02d".format(t.hour, t.minute)),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Text("${t.from?.name?.substringBefore(",").orEmpty()} → ${t.to?.name?.substringBefore(",").orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
    PrimaryButton(stringResource(R.string.add_trip)) { onEdit(0) }
}

/** "Mon, Wed, Fri" for a weekly trip, "Tue 10 Nov" for a one-off one. */
private fun whenLabel(t: Trip, locale: Locale) = t.day()?.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    ?: DayOfWeek.entries.filter { it in t.days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
