package com.dnsspeedtest.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dnsspeedtest.app.dns.HistorySession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

private val Context.historyStore by preferencesDataStore(name = "history")

class HistoryRepository(context: Context) {
    private val dataStore = context.applicationContext.historyStore

    val sessions: Flow<List<HistorySession>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY] ?: return@map emptyList()
        runCatching {
            AppJson.decodeFromString(ListSerializer(HistorySession.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun add(session: HistorySession) {
        dataStore.edit { prefs ->
            val current = prefs[KEY]?.let { raw ->
                runCatching {
                    AppJson.decodeFromString(ListSerializer(HistorySession.serializer()), raw)
                }.getOrDefault(emptyList())
            } ?: emptyList()
            val next = (listOf(session) + current).take(40)
            prefs[KEY] = AppJson.encodeToString(ListSerializer(HistorySession.serializer()), next)
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs[KEY] = AppJson.encodeToString(
                ListSerializer(HistorySession.serializer()),
                emptyList(),
            )
        }
    }

    companion object {
        private val KEY = stringPreferencesKey("sessions")
    }
}
