package com.example.runningbeat.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidSettingsRepository(context: Context) : SettingsRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("runningbeat_settings", Context.MODE_PRIVATE)

    private val _minBpmFlow = MutableStateFlow(prefs.getInt("min_bpm", 145))
    override val minBpmFlow: StateFlow<Int> = _minBpmFlow.asStateFlow()

    private val _maxBpmFlow = MutableStateFlow(prefs.getInt("max_bpm", 165))
    override val maxBpmFlow: StateFlow<Int> = _maxBpmFlow.asStateFlow()

    private val _startingBpmFlow = MutableStateFlow(prefs.getInt("starting_bpm", 155))
    override val startingBpmFlow: StateFlow<Int> = _startingBpmFlow.asStateFlow()

    private val _allowSkippingFlow = MutableStateFlow(prefs.getBoolean("allow_skipping", true))
    override val allowSkippingFlow: StateFlow<Boolean> = _allowSkippingFlow.asStateFlow()

    private val _bpmDiffSwitchFlow = MutableStateFlow(prefs.getInt("bpm_diff_switch", 4))
    override val bpmDiffSwitchFlow: StateFlow<Int> = _bpmDiffSwitchFlow.asStateFlow()

    private val _switchDelaySecondsFlow = MutableStateFlow(prefs.getInt("switch_delay_seconds", 7))
    override val switchDelaySecondsFlow: StateFlow<Int> = _switchDelaySecondsFlow.asStateFlow()

    private val _useFallbackTracksFlow = MutableStateFlow(prefs.getBoolean("use_fallback_tracks", true))
    override val useFallbackTracksFlow: StateFlow<Boolean> = _useFallbackTracksFlow.asStateFlow()

    private val _isCadenceOnlyModeFlow = MutableStateFlow(prefs.getBoolean("is_cadence_only", false))
    override val isCadenceOnlyModeFlow: StateFlow<Boolean> = _isCadenceOnlyModeFlow.asStateFlow()

    override fun saveBpmWindow(min: Int, max: Int) {
        prefs.edit().putInt("min_bpm", min).putInt("max_bpm", max).apply()
        _minBpmFlow.value = min
        _maxBpmFlow.value = max
    }

    override fun saveStartingBpm(value: Int) {
        prefs.edit().putInt("starting_bpm", value).apply()
        _startingBpmFlow.value = value
    }

    override fun saveAllowSkipping(value: Boolean) {
        prefs.edit().putBoolean("allow_skipping", value).apply()
        _allowSkippingFlow.value = value
    }

    override fun saveBpmDiffSwitch(value: Int) {
        prefs.edit().putInt("bpm_diff_switch", value).apply()
        _bpmDiffSwitchFlow.value = value
    }

    override fun saveSwitchDelaySeconds(value: Int) {
        prefs.edit().putInt("switch_delay_seconds", value).apply()
        _switchDelaySecondsFlow.value = value
    }

    override fun saveUseFallbackTracks(value: Boolean) {
        prefs.edit().putBoolean("use_fallback_tracks", value).apply()
        _useFallbackTracksFlow.value = value
    }

    override fun saveIsCadenceOnlyMode(value: Boolean) {
        prefs.edit().putBoolean("is_cadence_only", value).apply()
        _isCadenceOnlyModeFlow.value = value
    }
}
