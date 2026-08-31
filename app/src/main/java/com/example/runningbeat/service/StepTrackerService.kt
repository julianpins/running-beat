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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StepTrackerService : Service() {

    private lateinit var sensorManager: StepSensorManager
    private lateinit var settingsRepository: SettingsRepository
    private val cadenceCalculator = CadenceCalculator()
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

        serviceScope.launch {
            settingsRepository.startingBpmFlow.collect { startingBpm ->
                cadenceCalculator.updateStartingBpm(startingBpm)
                if (_currentBpm.value == 0 || _currentBpm.value == startingBpm) {
                    _currentBpm.value = startingBpm
                }
            }
        }

        // 2. Process incoming step events and calculate live BPM
        serviceScope.launch {
            sensorManager.stepEvents.collect { timestamp ->
                val bpm = cadenceCalculator.processStep(timestamp)
                _currentBpm.value = bpm
            }
        }
    }

    override fun onDestroy() {
        sensorManager.stopListening()
        serviceScope.cancel()
        _currentBpm.value = 0
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

        private val _currentBpm = MutableStateFlow(155)
        val currentBpm: StateFlow<Int> = _currentBpm.asStateFlow()

        fun resetBpmToStarting(startingBpm: Int) {
            _currentBpm.value = startingBpm
        }
    }
}