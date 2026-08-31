package com.example.runningbeat.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val MIN_BPM_KEY = intPreferencesKey("min_bpm")
        val MAX_BPM_KEY = intPreferencesKey("max_bpm")
        val STARTING_BPM_KEY = intPreferencesKey("starting_bpm")
        val ALLOW_SKIPPING_KEY = booleanPreferencesKey("allow_skipping_bpm_change")
        val BPM_DIFF_SWITCH_KEY = intPreferencesKey("bpm_diff_switch")
        val SWITCH_DELAY_SECONDS_KEY = intPreferencesKey("switch_delay_seconds")
        val USE_FALLBACK_TRACKS_KEY = booleanPreferencesKey("use_fallback_tracks_when_missing")
    }

    val minBpmFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[MIN_BPM_KEY] ?: 130
    }

    val maxBpmFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[MAX_BPM_KEY] ?: 165
    }

    val startingBpmFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[STARTING_BPM_KEY] ?: 155
    }

    val allowSkippingFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ALLOW_SKIPPING_KEY] ?: true
    }

    val bpmDiffSwitchFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[BPM_DIFF_SWITCH_KEY] ?: 4
    }

    val switchDelaySecondsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SWITCH_DELAY_SECONDS_KEY] ?: 7
    }

    val useFallbackTracksFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[USE_FALLBACK_TRACKS_KEY] ?: true
    }

    suspend fun saveBpmWindow(minBpm: Int, maxBpm: Int) {
        context.dataStore.edit { prefs ->
            prefs[MIN_BPM_KEY] = minBpm
            prefs[MAX_BPM_KEY] = maxBpm
        }
    }

    suspend fun saveStartingBpm(bpm: Int) {
        context.dataStore.edit { prefs -> prefs[STARTING_BPM_KEY] = bpm }
    }

    suspend fun saveAllowSkipping(allow: Boolean) {
        context.dataStore.edit { prefs -> prefs[ALLOW_SKIPPING_KEY] = allow }
    }

    suspend fun saveBpmDiffSwitch(diff: Int) {
        context.dataStore.edit { prefs -> prefs[BPM_DIFF_SWITCH_KEY] = diff }
    }

    suspend fun saveSwitchDelaySeconds(seconds: Int) {
        context.dataStore.edit { prefs -> prefs[SWITCH_DELAY_SECONDS_KEY] = seconds }
    }

    suspend fun saveUseFallbackTracks(useFallback: Boolean) {
        context.dataStore.edit { prefs -> prefs[USE_FALLBACK_TRACKS_KEY] = useFallback }
    }
}