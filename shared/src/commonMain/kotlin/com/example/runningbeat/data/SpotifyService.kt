package com.example.runningbeat.data

import kotlinx.coroutines.flow.StateFlow

interface SpotifyService {
    val currentlyPlayingBpm: StateFlow<Int?>
    val currentlyPlayingTitle: StateFlow<String?>
    val isPlaying: StateFlow<Boolean>
    
    fun authorize()
    fun disconnect()
    fun pausePlayback(onError: ((Throwable) -> Unit)? = null)
    fun resumePlayback(onError: ((Throwable) -> Unit)? = null)
    fun restartTrack(onError: ((Throwable) -> Unit)? = null)
    fun getCurrentVolume(): Float
    suspend fun fadeVolume(from: Float? = null, to: Float, durationMs: Long)
    
    suspend fun isBetterMatchAvailable(
        targetBpm: Int,
        currentTrackBpm: Int,
        trackDao: TrackDao,
        useFallback: Boolean
    ): Boolean

    suspend fun playBestMatchingTrack(
        currentBpm: Int,
        trackDao: TrackDao,
        useFallback: Boolean,
        onError: (Throwable) -> Unit = {}
    )
}
