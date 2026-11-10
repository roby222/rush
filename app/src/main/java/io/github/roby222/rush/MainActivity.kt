package io.github.roby222.rush

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import io.github.roby222.rush.ui.MainViewModel
import io.github.roby222.rush.ui.RushApp
import io.github.roby222.rush.ui.theme.RushTheme
import io.github.roby222.rush.work.EXTRA_TRIP

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Opened from a trip's notification: show that trip (only on a fresh start, not after rotation).
        if (savedInstanceState == null) intent.getIntExtra(EXTRA_TRIP, 0).takeIf { it > 0 }?.let(vm::show)
        setContent { RushTheme { RushApp(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}
