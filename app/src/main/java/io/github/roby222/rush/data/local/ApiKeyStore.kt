package io.github.roby222.rush.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.roby222.rush.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// A store of its own, so the backup rules can leave the key on this phone (res/xml/backup_rules.xml).
private val Context.secrets by preferencesDataStore("secrets")
private val TOMTOM_KEY = stringPreferencesKey("tomtom_key")

/** The key in use: the one the user entered, else the build's (debug builds only); null when neither exists. */
fun effectiveKey(stored: String?, build: String): String? = stored?.trim()?.ifBlank { null } ?: build.ifBlank { null }

fun Context.apiKeyFlow(): Flow<String?> = secrets.data.map { effectiveKey(it[TOMTOM_KEY], BuildConfig.TOMTOM_API_KEY) }

suspend fun Context.apiKey(): String? = apiKeyFlow().first()

/** The key the user entered, if any (not the build's). */
suspend fun Context.storedApiKey(): String? = secrets.data.first()[TOMTOM_KEY]

suspend fun Context.saveApiKey(key: String) = secrets.edit { it[TOMTOM_KEY] = key.trim() }
