package com.dnsspeedtest.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dnsspeedtest.app.dns.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsStore

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        val raw = prefs[KEY] ?: return@map UserSettings()
        runCatching { AppJson.decodeFromString<UserSettings>(raw) }.getOrDefault(UserSettings())
    }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { prefs ->
            val current = prefs[KEY]?.let { raw ->
                runCatching { AppJson.decodeFromString<UserSettings>(raw) }.getOrNull()
            } ?: UserSettings()
            prefs[KEY] = AppJson.encodeToString(UserSettings.serializer(), transform(current))
        }
    }

    companion object {
        private val KEY = stringPreferencesKey("user_settings")
    }
}
