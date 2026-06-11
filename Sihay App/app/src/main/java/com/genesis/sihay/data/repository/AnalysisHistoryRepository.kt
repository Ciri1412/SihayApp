package com.genesis.sihay.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.genesis.sihay.data.model.EggAnalysisRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.historyDataStore by preferencesDataStore("sihay_history")

class AnalysisHistoryRepository(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {

    private val historyKey = stringSetPreferencesKey("analysis_events")

    val historyStream: Flow<List<EggAnalysisRecord>> =
        context.historyDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[historyKey]
                    ?.mapNotNull { runCatching { json.decodeFromString<EggAnalysisRecord>(it) }.getOrNull() }
                    ?.sortedByDescending(EggAnalysisRecord::timestamp)
                    ?: emptyList()
            }

    suspend fun add(record: EggAnalysisRecord) {
        context.historyDataStore.edit { prefs ->
            val current = prefs[historyKey]?.toMutableList() ?: mutableListOf()
            current.add(json.encodeToString(record))
            // keep only most recent 200 entries
            if (current.size > 200) {
                val dropCount = current.size - 200
                repeat(dropCount) { current.removeAt(0) }
            }
            prefs[historyKey] = current.toSet()
        }
    }

    suspend fun delete(ids: Set<String>) {
        if (ids.isEmpty()) return
        context.historyDataStore.edit { prefs ->
            val current = prefs[historyKey]?.toMutableList() ?: mutableListOf()
            val filtered = current.mapNotNull { encoded ->
                val record = runCatching { json.decodeFromString<EggAnalysisRecord>(encoded) }.getOrNull()
                if (record != null && record.id in ids) {
                    null
                } else {
                    encoded
                }
            }
            prefs[historyKey] = filtered.toSet()
        }
    }

    suspend fun clear() {
        context.historyDataStore.edit { prefs ->
            prefs.remove(historyKey)
        }
    }
}

