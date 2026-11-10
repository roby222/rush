package io.github.roby222.rush.ui

import android.app.AlarmManager
import android.app.Application
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.roby222.rush.data.TripRepository
import io.github.roby222.rush.data.remote.KeyRefusedException
import io.github.roby222.rush.domain.featuredTrip
import io.github.roby222.rush.model.AppState
import io.github.roby222.rush.model.Trip
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** System settings the checks depend on; each false one shows a warning on Home. */
data class SystemStatus(val notifications: Boolean = true, val battery: Boolean = true, val exactAlarms: Boolean = true)

/** What the Home shows: every trip and result, and the one trip in front. */
data class HomeUi(val state: AppState, val trip: Trip?)

private const val SHOWN = "shown"

/** Where "Verify and save" of a TomTom key stands. */
sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Checking : KeyCheck
    data object Refused : KeyCheck
    data class Failed(val message: String) : KeyCheck
}

class MainViewModel(app: Application, private val handle: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = TripRepository(app)

    /** Trip picked on the Home (chip or notification); 0 lets [featuredTrip] choose. */
    private val shown = handle.getStateFlow(SHOWN, 0)

    val home: StateFlow<HomeUi?> = combine(repo.state, shown) { s, id ->
        HomeUi(s, s.trips.firstOrNull { it.id == id } ?: featuredTrip(s.trips, s.results, ZonedDateTime.now()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _status = MutableStateFlow(SystemStatus())
    val status: StateFlow<SystemStatus> = _status

    /** Whether a TomTom key exists; null until known. Without one the app asks for it first. */
    val hasKey: StateFlow<Boolean?> = repo.hasKey.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _keyCheck = MutableStateFlow<KeyCheck>(KeyCheck.Idle)
    val keyCheck: StateFlow<KeyCheck> = _keyCheck

    suspend fun storedKeyEnd(): String? = repo.storedKeyEnd()

    /** Tries [key] and saves it only if TomTom accepts it; [onSaved] runs then. */
    fun saveKey(key: String, onSaved: () -> Unit) {
        _keyCheck.value = KeyCheck.Checking
        viewModelScope.launch {
            _keyCheck.value = try {
                repo.verifyAndSaveKey(key)
                onSaved()
                KeyCheck.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: KeyRefusedException) {
                KeyCheck.Refused
            } catch (e: Exception) {
                KeyCheck.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun show(tripId: Int) { handle[SHOWN] = tripId }

    fun checkNow(tripId: Int) = repo.checkNow(tripId)

    fun checking(tripId: Int): Flow<Boolean> = repo.checking(tripId)

    /** Called on every resume: the user may have fixed a setting, or the clock may have moved. */
    fun onResume() {
        val app = getApplication<Application>()
        _status.value = SystemStatus(
            notifications = NotificationManagerCompat.from(app).areNotificationsEnabled(),
            battery = app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName),
            exactAlarms = Build.VERSION.SDK_INT < 31 || app.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        )
        viewModelScope.launch { repo.reschedule() }
    }
}
