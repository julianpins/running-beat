package com.example.runningbeat.data

import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val minBpmFlow: StateFlow<Int>
    val maxBpmFlow: StateFlow<Int>
    val startingBpmFlow: StateFlow<Int>
    val allowSkippingFlow: StateFlow<Boolean>
    val bpmDiffSwitchFlow: StateFlow<Int>
    val switchDelaySecondsFlow: StateFlow<Int>
    val useFallbackTracksFlow: StateFlow<Boolean>
    val isCadenceOnlyModeFlow: StateFlow<Boolean>

    fun saveBpmWindow(min: Int, max: Int)
    fun saveStartingBpm(value: Int)
    fun saveAllowSkipping(value: Boolean)
    fun saveBpmDiffSwitch(value: Int)
    fun saveSwitchDelaySeconds(value: Int)
    fun saveUseFallbackTracks(value: Boolean)
    fun saveIsCadenceOnlyMode(value: Boolean)
}
