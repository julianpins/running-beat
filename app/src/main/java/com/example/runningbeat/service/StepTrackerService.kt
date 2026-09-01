package com.example.runningbeat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.runningbeat.data.SettingsRepository
import com.example.runningbeat.data.StepSensorManager
import com.example.runningbeat.domain.CadenceCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StepTrackerService : Service() {

    private lateinit var sensorManager: StepSensorManager
    private lateinit var settingsRepository: SettingsRepository
    private val cadenceCalculator = CadenceCalculator(0) // Start with 0
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        sensorManager = StepSensorManager(this)
        settingsRepository = SettingsRepository(applicationContext)
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RunningBeat Active")
            .setContentText("Tracking running cadence in background...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        sensorManager.startListening()

        // 1. Monitor starting BPM from settings, respecting the current mode
        serviceScope.launch {
            combine(
                settingsRepository.startingBpmFlow,
                settingsRepository.isCadenceOnlyModeFlow
            ) { bpm, isCadenceOnly ->
                if (isCadenceOnly) 0 else bpm
            }.distinctUntilChanged().collect { bpmToUse ->
                // This updates the local storage in calculator
                cadenceCalculator.updateStartingBpm(bpmToUse)
            }
        }

        // 2. Process incoming step events and calculate live BPM
        serviceScope.launch {
            sensorManager.stepEvents.collect { timestamp ->
                val bpm = cadenceCalculator.processStep(timestamp)
                _currentBpm.value = bpm.toInt()
                _preciseBpm.value = bpm
                _bpmUpdates.emit(bpm)
            }
        }

        // 3. Listen for reset commands
        serviceScope.launch {
            _resetCommands.collect { initialBpm ->
                cadenceCalculator.resetToStartingBpm(initialBpm)
                _currentBpm.value = initialBpm
                _preciseBpm.value = initialBpm.toDouble()
            }
        }
    }

    override fun onDestroy() {
        sensorManager.stopListening()
        serviceScope.cancel()
        _currentBpm.value = 0
        _preciseBpm.value = 0.0
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cadence Tracker",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "running_beat_channel"
        private const val NOTIFICATION_ID = 1001

        private val _currentBpm = MutableStateFlow(0)
        val currentBpm: StateFlow<Int> = _currentBpm.asStateFlow()

        private val _preciseBpm = MutableStateFlow(0.0)
        val preciseBpm: StateFlow<Double> = _preciseBpm.asStateFlow()

        private val _bpmUpdates = MutableSharedFlow<Double>()
        val bpmUpdates = _bpmUpdates.asSharedFlow()

        private val _resetCommands = MutableSharedFlow<Int>()

        suspend fun resetCadence(startingBpm: Int) {
            _resetCommands.emit(startingBpm)
        }

        fun resetBpmToStarting(startingBpm: Int) {
            _currentBpm.value = startingBpm
            _preciseBpm.value = startingBpm.toDouble()
        }
    }
}
