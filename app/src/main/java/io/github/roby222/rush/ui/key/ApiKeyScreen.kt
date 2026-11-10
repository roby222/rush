package io.github.roby222.rush.ui.key

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.roby222.rush.R
import io.github.roby222.rush.ui.KeyCheck
import io.github.roby222.rush.ui.MainViewModel
import io.github.roby222.rush.ui.components.PrimaryButton

const val KEYS_URL = "https://my.tomtom.com/keys"

/** Asks for the user's own TomTom key, how to get one, and saves it only once TomTom accepts it. */
@Composable
fun ApiKeyScreen(vm: MainViewModel, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val check by vm.keyCheck.collectAsStateWithLifecycle()
    val end by produceState<String?>(null) { value = vm.storedKeyEnd() }
    var key by rememberSaveable { mutableStateOf("") }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Text(stringResource(R.string.key_headline), style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(top = 8.dp))
    Text(stringResource(R.string.key_intro), style = MaterialTheme.typography.bodyLarge)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(R.string.key_step_1, R.string.key_step_2, R.string.key_step_3).forEachIndexed { i, s ->
            Text("${i + 1}. ${stringResource(s)}", style = MaterialTheme.typography.bodyLarge)
        }
    }
    OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KEYS_URL))) },
        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.key_open_site)) }
    OutlinedTextField(key, { key = it }, label = { Text(stringResource(R.string.key_field)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(), isError = check is KeyCheck.Refused || check is KeyCheck.Failed,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
        supportingText = {
            when (val c = check) {
                KeyCheck.Refused -> Text(stringResource(R.string.key_refused))
                is KeyCheck.Failed -> Text(stringResource(R.string.key_failed, c.message))
                else -> end?.let { Text(stringResource(R.string.key_current, it), color = muted) }
            }
        })
    PrimaryButton(stringResource(if (check == KeyCheck.Checking) R.string.key_checking else R.string.key_save),
        enabled = key.isNotBlank() && check != KeyCheck.Checking) { vm.saveKey(key, onSaved) }
    Text(stringResource(R.string.key_privacy), style = MaterialTheme.typography.bodyMedium, color = muted)
}
