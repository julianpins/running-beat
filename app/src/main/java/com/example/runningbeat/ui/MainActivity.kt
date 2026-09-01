package com.example.runningbeat.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.runningbeat.data.SettingsRepository
import com.example.runningbeat.data.SpotifyManager
import com.example.runningbeat.data.TrackDao
import com.example.runningbeat.data.AppDatabase
import com.example.runningbeat.service.StepTrackerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AutoSkipController(
    currentBpm: Int,
    playingBpm: Int?,
    allowSkipping: Boolean,
    useFallback: Boolean,
    bpmDiffSwitch: Int,
    switchDelaySeconds: Int,
    trackDao: TrackDao,
    spotifyManager: SpotifyManager,
) {
    var lastTrackBpm by remember { mutableIntStateOf(0) }
    var shiftStartTime by remember { mutableLongStateOf(0L) }
    var isSkipping by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(playingBpm) {
        if (playingBpm != null && playingBpm > 0) {
            lastTrackBpm = playingBpm
        }
    }

    LaunchedEffect(currentBpm, allowSkipping) {
        if (!allowSkipping || currentBpm <= 0 || isSkipping) {
            shiftStartTime = 0L
            return@LaunchedEffect
        }
        if (lastTrackBpm <= 0) {
            lastTrackBpm = currentBpm
            return@LaunchedEffect
        }

        val bpmDiff = kotlin.math.abs(currentBpm - lastTrackBpm)
        if (bpmDiff >= bpmDiffSwitch) {
            val now = System.currentTimeMillis()
            if (shiftStartTime == 0L) {
                shiftStartTime = now
                Log.d("AUTO_SKIP", "BPM shift detected! Diff: $bpmDiff. Waiting for $switchDelaySeconds seconds...")
            }
            val elapsedSeconds = (now - shiftStartTime) / 1000L
            if (elapsedSeconds >= switchDelaySeconds) {
                Log.d("AUTO_SKIP", "Delay reached! Triggering skip. Original BPM: $lastTrackBpm, New BPM: $currentBpm")
                shiftStartTime = 0L
                isSkipping = true
                coroutineScope.launch {
                    try {
                        val originalVolume = spotifyManager.getCurrentVolume()
                        spotifyManager.fadeVolume(from = originalVolume, to = 0.0f, durationMs = 2000L)
                        spotifyManager.playBestMatchingTrack(
                            currentBpm = currentBpm,
                            trackDao = trackDao,
                            useFallback = useFallback,
                            onError = { err ->
                                Log.e("AUTO_SKIP", "Failed to switch track: ${err.localizedMessage}")
                            }
                        )
                        spotifyManager.fadeVolume(from = 0.0f, to = originalVolume, durationMs = 500L)
                    } catch (e: Exception) {
                    } finally {
                        isSkipping = false
                    }
                }
            }
        } else {
            shiftStartTime = 0L
        }
    }
}

data class AppMessage(val text: String, val isError: Boolean)

class MainActivity : ComponentActivity() {

    private lateinit var spotifyManager: SpotifyManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var trackDao: TrackDao

    private var appMessageState = mutableStateOf<AppMessage?>(null)
    private var isPlayingState = mutableStateOf(false)
    private var isConnectedState = mutableStateOf(false)
    private var isSyncingState = mutableStateOf(false)
    private var isRunningState = mutableStateOf(false)

    @RequiresApi(Build.VERSION_CODES.Q)
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val activityGranted = permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: false
        if (activityGranted) {
            startTrackingService()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsRepository = SettingsRepository(applicationContext)

        // Persistent Room DB pre-populated with fallback_tracks.db from assets
        val db = AppDatabase.getDatabase(applicationContext)
        trackDao = db.trackDao()

        spotifyManager = SpotifyManager(
            context = this,
            clientId = "025f3a3fa8154feabc32f98af22f747c",
            redirectUri = "cadencerunner://callback"
        )

        requestRequiredPermissions()

        setContent {
            val bpm by StepTrackerService.currentBpm.collectAsState()
            val appMessage by appMessageState
            val isConnected by isConnectedState
            val isRunning by isRunningState
            val isPlaying by isPlayingState
            val isSyncing by isSyncingState

            val coroutineScope = rememberCoroutineScope()

            var showSettingsDialog by remember { mutableStateOf(false) }
            var showHelpScreen by remember { mutableStateOf(false) }
            var showStatsScreen by remember { mutableStateOf(false) }
            val runBpmHistory = remember { mutableStateListOf<Pair<Long, Double>>() }
            var runStartTime by remember { mutableLongStateOf(0L) }

            // Record BPM every time it changes during a run
            LaunchedEffect(isRunning) {
                if (isRunning) {
                    if (StepTrackerService.preciseBpm.value > 0.0) {
                        runBpmHistory.add(0L to StepTrackerService.preciseBpm.value)
                    }
                    StepTrackerService.bpmUpdates.collect { preciseBpm ->
                        if (preciseBpm > 0.0) {
                            val elapsedMillis = System.currentTimeMillis() - runStartTime
                            runBpmHistory.add(elapsedMillis to preciseBpm)
                        }
                    }
                }
            }

            val minBpm by settingsRepository.minBpmFlow.collectAsState(initial = 145)
            val maxBpm by settingsRepository.maxBpmFlow.collectAsState(initial = 165)
            val startingBpm by settingsRepository.startingBpmFlow.collectAsState(initial = 155)
            val allowSkipping by settingsRepository.allowSkippingFlow.collectAsState(initial = true)
            val bpmDiffSwitch by settingsRepository.bpmDiffSwitchFlow.collectAsState(initial = 4)
            val switchDelaySeconds by settingsRepository.switchDelaySecondsFlow.collectAsState(initial = 7)
            val useFallbackTracks by settingsRepository.useFallbackTracksFlow.collectAsState(initial = true)
            val isCadenceOnly by settingsRepository.isCadenceOnlyModeFlow.collectAsState(initial = false)

            val displayMessage = remember(appMessage, isSyncing) {
                if (isSyncing) AppMessage("Loading your playlists...", false)
                else appMessage
            }

            val playingBpm by spotifyManager.currentlyPlayingBpm.collectAsState()
            val playingTitle by spotifyManager.currentlyPlayingTitle.collectAsState()

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CadenceScreen(
                        currentBpm = bpm,
                        isConnected = isConnected && !isSyncing,
                        isRunning = isRunning,
                        isPlaying = isPlaying,
                        isCadenceOnly = isCadenceOnly,
                        appMessage = displayMessage,
                        playingBpm = playingBpm,
                        playingTitle = playingTitle,
                        onClearError = { appMessageState.value = null },
                        onConnectSpotify = {
                            if (!isSyncing) {
                                appMessageState.value = null
                                spotifyManager.authorize(this@MainActivity)
                            }
                        },
                        onToggleMode = { onlyCadence ->
                            coroutineScope.launch {
                                settingsRepository.saveIsCadenceOnlyMode(onlyCadence)
                                if (onlyCadence) {
                                    isPlayingState.value = false
                                    StepTrackerService.resetCadence(0)
                                } else {
                                    val startingBpm = settingsRepository.startingBpmFlow.first()
                                    StepTrackerService.resetCadence(startingBpm)
                                }
                            }
                        },
                        onStartRun = {
                            isRunningState.value = true
                            runBpmHistory.clear()
                            runStartTime = System.currentTimeMillis()
                            coroutineScope.launch {
                                if (!isCadenceOnly) {
                                    val startingBpm = settingsRepository.startingBpmFlow.first()
                                    val currentLiveBpm = StepTrackerService.currentBpm.value
                                    val bpmToUse = if (currentLiveBpm > 0) currentLiveBpm else startingBpm
                                    
                                    spotifyManager.playBestMatchingTrack(
                                        currentBpm = bpmToUse,
                                        trackDao = trackDao,
                                        useFallback = useFallbackTracks,
                                        onError = { err ->
                                            appMessageState.value = AppMessage("Playback Error: ${err.localizedMessage}", true)
                                        }
                                    )
                                }
                            }
                        },
                        onEndRun = {
                            // Capture one final data point at the exact moment the run ends
                            if (runStartTime > 0 && StepTrackerService.preciseBpm.value > 0.0) {
                                val finalElapsed = System.currentTimeMillis() - runStartTime
                                runBpmHistory.add(finalElapsed to StepTrackerService.preciseBpm.value)
                            }
                            if (runBpmHistory.isEmpty()) {
                                appMessageState.value = AppMessage("No steps detected during last run.", false)
                            }
                            isRunningState.value = false
                            if (!isCadenceOnly) {
                                coroutineScope.launch {
                                    try {
                                        val currentVol = spotifyManager.getCurrentVolume()
                                        spotifyManager.fadeVolume(from = currentVol, to = 0.0f, durationMs = 1500L)
                                        spotifyManager.pausePlayback()
                                        // Reset volume for next time (app remote might keep it at 0 otherwise)
                                        spotifyManager.fadeVolume(from = 0.0f, to = currentVol, durationMs = 0L)
                                    } catch (e: Exception) {
                                        spotifyManager.pausePlayback()
                                    }
                                }
                            }
                        },
                        onPlayPause = {
                            if (isSyncing || isCadenceOnly) return@CadenceScreen

                            if (isPlaying) {
                                spotifyManager.pausePlayback { err ->
                                    appMessageState.value = AppMessage(err.localizedMessage ?: "Unknown Error", true)
                                }
                            } else {
                                spotifyManager.resumePlayback { err ->
                                    appMessageState.value = AppMessage("Playback Error: ${err.localizedMessage}", true)
                                }
                            }
                        },
                        onRestart = {
                            if (!isSyncing && !isCadenceOnly) {
                                spotifyManager.restartTrack { err -> appMessageState.value = AppMessage(err.localizedMessage ?: "Unknown Error", true) }
                            }
                        },
                        onSkip = {
                            if (!isSyncing && !isCadenceOnly) {
                                coroutineScope.launch {
                                    spotifyManager.playBestMatchingTrack(
                                        currentBpm = StepTrackerService.currentBpm.value,
                                        useFallback = useFallbackTracks,
                                        trackDao = trackDao,
                                    )
                                }
                            }
                        },
                        onOpenSettings = {
                            if (isRunning) {
                                appMessageState.value = AppMessage("Settings cannot be changed during a run.", false)
                            } else {
                                showSettingsDialog = true
                            }
                        },
                        onOpenHelp = { showHelpScreen = true },
                        onViewStats = { showStatsScreen = true },
                        hasStats = runBpmHistory.isNotEmpty()
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
                                coroutineScope.launch { settingsRepository.saveBpmWindow(min, max) }
                                if (!isCadenceOnly) {
                                    if (startingBpm < min) {
                                        coroutineScope.launch { settingsRepository.saveStartingBpm(min) }
                                        StepTrackerService.resetBpmToStarting(min)
                                    } else if (startingBpm > max) {
                                        coroutineScope.launch { settingsRepository.saveStartingBpm(max) }
                                        StepTrackerService.resetBpmToStarting(max)
                                    }
                                }
                            },
                            onStartingBpmChange = { value ->
                                coroutineScope.launch { settingsRepository.saveStartingBpm(value) }
                                if (!isCadenceOnly) {
                                    StepTrackerService.resetBpmToStarting(value)
                                }
                            },
                            onAllowSkippingChange = { value ->
                                coroutineScope.launch { settingsRepository.saveAllowSkipping(value) }
                            },
                            onBpmDiffSwitchChange = { value ->
                                coroutineScope.launch { settingsRepository.saveBpmDiffSwitch(value) }
                            },
                            onSwitchDelaySecondsChange = { value ->
                                coroutineScope.launch { settingsRepository.saveSwitchDelaySeconds(value) }
                            },
                            onUseFallbackTracksChange = { value ->
                                coroutineScope.launch { settingsRepository.saveUseFallbackTracks(value) }
                            },
                            onDismiss = { showSettingsDialog = false }
                        )
                    }

                    if (showHelpScreen) {
                        HelpScreen(onDismiss = { showHelpScreen = false })
                    }

                    if (showStatsScreen) {
                        StatsScreen(
                            bpmHistory = runBpmHistory.toList(),
                            onDismiss = { showStatsScreen = false }
                        )
                    }
                    AutoSkipController(
                        currentBpm = bpm,
                        playingBpm = playingBpm,
                        allowSkipping = allowSkipping && isRunning && !isCadenceOnly,
                        useFallback = useFallbackTracks,
                        bpmDiffSwitch = bpmDiffSwitch,
                        switchDelaySeconds = switchDelaySeconds,
                        trackDao = trackDao,
                        spotifyManager = spotifyManager,
                    )
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SpotifyManager.AUTH_TOKEN_REQUEST_CODE) {
            spotifyManager.handleAuthResponse(
                resultCode = resultCode,
                intent = data,
                onConnected = {
                    spotifyManager.pausePlayback()

                    spotifyManager.subscribeToPlayerState { isPlaying ->
                        isPlayingState.value = isPlaying
                    }

                    lifecycleScope.launch {
                        isSyncingState.value = true
                        try {
                            val minBpm = settingsRepository.minBpmFlow.first()
                            val maxBpm = settingsRepository.maxBpmFlow.first()
                            val useFallback = settingsRepository.useFallbackTracksFlow.first()

                            // Set up observer BEFORE potentially slow sync
                            // After
                            spotifyManager.setupAutoQueue(
                                trackDao = trackDao,
                                getCurrentBpm = { StepTrackerService.currentBpm.value },
                                useFallback = useFallback,
                            )

                            spotifyManager.syncBpmPlaylists(
                                trackDao = trackDao,
                                minBpm = minBpm,
                                maxBpm = maxBpm,
                                useFallback = useFallback,
                                onWarning = { warningText ->
                                    appMessageState.value = AppMessage(warningText, false)
                                })

                            logAllDatabaseTracks(trackDao)
                        } catch (e: Exception) {
                            appMessageState.value = AppMessage("Failed to load playlists: ${e.localizedMessage}", true)
                        } finally {
                            isSyncingState.value = false
                        }
                    }
                    isConnectedState.value = true
                },
                onFailure = { errorMsg ->
                    appMessageState.value = AppMessage(errorMsg, true)
                }
            )
        }
    }

    private suspend fun logAllDatabaseTracks(trackDao: TrackDao) {
        withContext(Dispatchers.IO) {
            val tracks = trackDao.getAllTracks()

            Log.d("DB_CHECK", "==================================================")
            Log.d("DB_CHECK", "TOTAL TRACKS IN DATABASE: ${tracks.size}")
            Log.d("DB_CHECK", "==================================================")

            if (tracks.isEmpty()) {
                Log.d("DB_CHECK", "⚠️ DATABASE IS EMPTY!")
                return@withContext
            }

            tracks.forEachIndexed { index, track ->
                Log.d(
                    "DB_CHECK",
                    "#${index + 1} | BPM: ${track.bpm} | Fallback: ${track.isFallback} | " +
                            "Title: \"${track.title}\" | Artist: \"${track.artist}\" | URI: ${track.uri}"
                )
            }

            Log.d("DB_CHECK", "==================================================")
        }
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun startTrackingService() {
        val serviceIntent = Intent(this, StepTrackerService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    override fun onDestroy() {
        spotifyManager.disconnect()
        super.onDestroy()
    }
}