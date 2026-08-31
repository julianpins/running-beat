package com.example.runningbeat.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("runningbeat_settings", Context.MODE_PRIVATE)

    private val _minBpmFlow = MutableStateFlow(prefs.getInt("min_bpm", 145))
    val minBpmFlow: StateFlow<Int> = _minBpmFlow.asStateFlow()

    private val _maxBpmFlow = MutableStateFlow(prefs.getInt("max_bpm", 165))
    val maxBpmFlow: StateFlow<Int> = _maxBpmFlow.asStateFlow()

    private val _startingBpmFlow = MutableStateFlow(prefs.getInt("starting_bpm", 155))
    val startingBpmFlow: StateFlow<Int> = _startingBpmFlow.asStateFlow()

    private val _allowSkippingFlow = MutableStateFlow(prefs.getBoolean("allow_skipping", true))
    val allowSkippingFlow: StateFlow<Boolean> = _allowSkippingFlow.asStateFlow()

    private val _bpmDiffSwitchFlow = MutableStateFlow(prefs.getInt("bpm_diff_switch", 4))
    val bpmDiffSwitchFlow: StateFlow<Int> = _bpmDiffSwitchFlow.asStateFlow()

    private val _switchDelaySecondsFlow = MutableStateFlow(prefs.getInt("switch_delay_seconds", 7))
    val switchDelaySecondsFlow: StateFlow<Int> = _switchDelaySecondsFlow.asStateFlow()

    private val _useFallbackTracksFlow = MutableStateFlow(prefs.getBoolean("use_fallback_tracks", true))
    val useFallbackTracksFlow: StateFlow<Boolean> = _useFallbackTracksFlow.asStateFlow()

    private val _isCadenceOnlyModeFlow = MutableStateFlow(prefs.getBoolean("is_cadence_only", false))
    val isCadenceOnlyModeFlow: StateFlow<Boolean> = _isCadenceOnlyModeFlow.asStateFlow()

    fun saveBpmWindow(min: Int, max: Int) {
        prefs.edit().putInt("min_bpm", min).putInt("max_bpm", max).apply()
        _minBpmFlow.value = min
        _maxBpmFlow.value = max
    }

    fun saveStartingBpm(value: Int) {
        prefs.edit().putInt("starting_bpm", value).apply()
        _startingBpmFlow.value = value
    }

    fun saveAllowSkipping(value: Boolean) {
        prefs.edit().putBoolean("allow_skipping", value).apply()
        _allowSkippingFlow.value = value
    }

    fun saveBpmDiffSwitch(value: Int) {
        prefs.edit().putInt("bpm_diff_switch", value).apply()
        _bpmDiffSwitchFlow.value = value
    }

    fun saveSwitchDelaySeconds(value: Int) {
        prefs.edit().putInt("switch_delay_seconds", value).apply()
        _switchDelaySecondsFlow.value = value
    }

    fun saveUseFallbackTracks(value: Boolean) {
        prefs.edit().putBoolean("use_fallback_tracks", value).apply()
        _useFallbackTracksFlow.value = value
    }

    fun saveIsCadenceOnlyMode(value: Boolean) {
        prefs.edit().putBoolean("is_cadence_only", value).apply()
        _isCadenceOnlyModeFlow.value = value
    }
}
