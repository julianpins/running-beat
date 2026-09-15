package com.example.runningbeat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.example.runningbeat.ui.AppMessage
import com.example.runningbeat.ui.CadenceScreen
import com.example.runningbeat.ui.HelpScreen
import com.example.runningbeat.ui.SettingsDialog
import com.example.runningbeat.ui.StatsScreen
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    RunningBeatApp()
}

@Composable
private fun RunningBeatApp() {
    var currentBpm by remember { mutableStateOf(0) }
    var isConnected by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isCadenceOnly by remember { mutableStateOf(true) }
    var appMessage by remember { mutableStateOf<AppMessage?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHelpScreen by remember { mutableStateOf(false) }
    var showStatsScreen by remember { mutableStateOf(false) }

    var minBpm by remember { mutableStateOf(145) }
    var maxBpm by remember { mutableStateOf(165) }
    var startingBpm by remember { mutableStateOf(155) }
    var allowSkipping by remember { mutableStateOf(true) }
    var bpmDiffSwitch by remember { mutableStateOf(4) }
    var switchDelaySeconds by remember { mutableStateOf(7) }
    var useFallbackTracks by remember { mutableStateOf(true) }

    val bpmHistory = remember { mutableStateListOf<Pair<Long, Double>>() }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                CadenceScreen(
                    currentBpm = currentBpm,
                    isConnected = isConnected,
                    isRunning = isRunning,
                    isPlaying = isPlaying,
                    appMessage = appMessage,
                    playingBpm = if (isPlaying) currentBpm else null,
                    playingTitle = if (isPlaying) "Preview Track" else null,
                    onClearError = { appMessage = null },
                    onConnectSpotify = {
                        isConnected = true
                        appMessage = AppMessage("Spotify integration is not available on iOS yet.", false)
                    },
                    onStartRun = {
                        isRunning = true
                        isPlaying = !isCadenceOnly && isConnected
                        currentBpm = if (currentBpm > 0) currentBpm else startingBpm
                        bpmHistory.clear()
                        bpmHistory.add(0L to currentBpm.toDouble())
                    },
                    onEndRun = {
                        isRunning = false
                        isPlaying = false
                        if (currentBpm > 0) {
                            bpmHistory.add(1L to currentBpm.toDouble())
                        }
                    },
                    onPlayPause = { isPlaying = !isPlaying },
                    onSkip = {
                        appMessage = AppMessage("Track skipping is not available on iOS yet.", false)
                    },
                    onRestart = {
                        currentBpm = startingBpm
                        appMessage = AppMessage("Cadence reset to starting BPM.", false)
                    },
                    onOpenSettings = {
                        if (isRunning) {
                            appMessage = AppMessage("Settings cannot be changed during a run.", false)
                        } else {
                            showSettingsDialog = true
                        }
                    },
                    onOpenHelp = { showHelpScreen = true },
                    onViewStats = { showStatsScreen = true },
                    onToggleMode = { onlyCadence ->
                        isCadenceOnly = onlyCadence
                        if (onlyCadence) {
                            isPlaying = false
                        }
                    },
                    hasStats = bpmHistory.isNotEmpty(),
                    isCadenceOnly = isCadenceOnly
                )

                if (showSettingsDialog) {
                    SettingsDialog(
                        minBpm = minBpm,
                        maxBpm = maxBpm,
                        startingBpm = startingBpm,
                        allowSkipping = allowSkipping,
                        bpmDiffSwitch = bpmDiffSwitch,
                        switchDelaySeconds = switchDelaySeconds,
                        useFallbackTracks = useFallbackTracks,
                        onBpmWindowChange = { min, max ->
                            minBpm = min
                            maxBpm = max
                            startingBpm = startingBpm.coerceIn(min, max)
                        },
                        onStartingBpmChange = { value ->
                            startingBpm = value
                            if (!isCadenceOnly) {
                                currentBpm = value
                            }
                        },
                        onAllowSkippingChange = { allowSkipping = it },
                        onBpmDiffSwitchChange = { bpmDiffSwitch = it },
                        onSwitchDelaySecondsChange = { switchDelaySeconds = it },
                        onUseFallbackTracksChange = { useFallbackTracks = it },
                        onDismiss = { showSettingsDialog = false }
                    )
                }

                if (showHelpScreen) {
                    HelpScreen(onDismiss = { showHelpScreen = false })
                }

                if (showStatsScreen) {
                    StatsScreen(
                        bpmHistory = bpmHistory,
                        onDismiss = { showStatsScreen = false }
                    )
                }
            }
        }
    }
}
