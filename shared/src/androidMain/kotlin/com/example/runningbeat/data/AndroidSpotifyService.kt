package com.example.runningbeat.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

private enum class PlaybackAction { PLAY, QUEUE }

class AndroidSpotifyService(
    private val context: Context,
    private val clientId: String,
    private val redirectUri: String
) : SpotifyService {
    
    companion object {
        const val AUTH_TOKEN_REQUEST_CODE = 1337
        private const val TAG = "AndroidSpotifyService"
        private const val TICK_MS = 1000L
        private const val QUEUE_LEAD_MS = 10_000L
        private const val QUEUE_RESET_MS = 15_000L
        private const val CALLBACK_TIMEOUT_MS = 5000L
    }

    private var spotifyAppRemote: SpotifyAppRemote? = null
    var accessToken: String? = null
        private set

    private var playerStateSubscription: Subscription<PlayerState>? = null

    private val _currentlyPlayingBpm = MutableStateFlow<Int?>(null)
    override val currentlyPlayingBpm = _currentlyPlayingBpm.asStateFlow()

    private val _currentlyPlayingTitle = MutableStateFlow<String?>(null)
    override val currentlyPlayingTitle = _currentlyPlayingTitle.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying = _isPlaying.asStateFlow()

    private var trackDao: TrackDao? = null
    private var useFallback: Boolean = true
    private var getCurrentBpm: (() -> Int)? = null

    private var lastQueuedTrackUri: String? = null
    private var lastStateSnapshot: PlayerState? = null
    private var lastStateTime: Long = 0L
    private var isQueueing = false
    
    private var isRunning = false
    private var isCadenceOnly = false
    private val expectedUris = mutableListOf<String>()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val apiService: SpotifyApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.spotify.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyApiService::class.java)
    }

    init {
        scope.launch {
            while (true) {
                if (spotifyAppRemote != null) {
                    checkTrackProgress()
                }
                delay(TICK_MS.milliseconds)
            }
        }
    }

    private fun checkTrackProgress() {
        val state = lastStateSnapshot ?: return
        val track = state.track ?: return
        if (state.isPaused || isQueueing) return

        val timeLeft = track.duration - (state.playbackPosition + (System.currentTimeMillis() - lastStateTime))

        when {
            timeLeft in 1..QUEUE_LEAD_MS && lastQueuedTrackUri != track.uri -> {
                val dao = trackDao ?: return
                val getBpm = getCurrentBpm ?: return

                Log.d(TAG, "[AUTO_QUEUE] Song ending in ${timeLeft}ms. Queuing next...")
                lastQueuedTrackUri = track.uri
                isQueueing = true
                scope.launch {
                    try {
                        queueBestMatchingTrack(getBpm(), dao, useFallback)
                    } finally {
                        isQueueing = false
                    }
                }
            }
            timeLeft > QUEUE_RESET_MS && lastQueuedTrackUri == track.uri -> lastQueuedTrackUri = null
        }
    }

    private fun onPlayerStateUpdated(state: PlayerState) {
        val newUri = state.track?.uri?.toString()
        val oldUri = lastStateSnapshot?.track?.uri?.toString()

        lastStateSnapshot = state
        lastStateTime = System.currentTimeMillis()
        _isPlaying.value = !state.isPaused

        if (newUri != null && newUri != oldUri) {
            _currentlyPlayingTitle.value = state.track?.name
            scope.launch(Dispatchers.IO) {
                val track = trackDao?.getTrackByUri(newUri)
                _currentlyPlayingBpm.value = track?.bpm
                
                withContext(Dispatchers.Main) {
                    if (isRunning && !isCadenceOnly) {
                        if (!expectedUris.contains(newUri)) {
                            Log.d(TAG, "[TRACK_CHANGE] Unexpected URI: $newUri. Correcting...")
                            val getBpm = getCurrentBpm
                            val dao = trackDao
                            if (getBpm != null && dao != null) {
                                playBestMatchingTrack(getBpm(), dao, useFallback)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun authorize() {
        val activity = context as? Activity ?: return
        val builder = AuthorizationRequest.Builder(clientId, AuthorizationResponse.Type.TOKEN, redirectUri)
            .setScopes(arrayOf("streaming", "app-remote-control", "playlist-read-private", "user-library-read"))
            .setShowDialog(true)
        AuthorizationClient.openLoginActivity(activity, AUTH_TOKEN_REQUEST_CODE, builder.build())
    }

    fun handleAuthResponse(resultCode: Int, intent: Intent?, onConnected: () -> Unit, onFailure: (String) -> Unit) {
        val response = AuthorizationClient.getResponse(resultCode, intent)
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                accessToken = response.accessToken
                connectAppRemote(onConnected, onFailure)
            }
            else -> onFailure("Authentication failed.")
        }
    }

    private fun connectAppRemote(onConnected: () -> Unit, onFailure: (String) -> Unit) {
        val params = ConnectionParams.Builder(clientId).setRedirectUri(redirectUri).showAuthView(true).build()
        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                startPersistentPlayerStateSubscription()
                onConnected()
            }
            override fun onFailure(t: Throwable) = onFailure("Connection Failed: ${t.message}")
        })
    }

    private fun startPersistentPlayerStateSubscription() {
        playerStateSubscription?.cancel()
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()
            ?.setEventCallback { onPlayerStateUpdated(it) }
            ?.setErrorCallback { Log.e(TAG, "Subscription error: ${it.message}") }

        spotifyAppRemote?.playerApi?.playerState?.setResultCallback { onPlayerStateUpdated(it) }
    }

    fun setupAutoQueue(trackDao: TrackDao, getCurrentBpm: () -> Int, useFallback: Boolean) {
        this.trackDao = trackDao
        this.getCurrentBpm = getCurrentBpm
        this.useFallback = useFallback
    }

    fun setSyncState(isRunning: Boolean, isCadenceOnly: Boolean) {
        this.isRunning = isRunning
        this.isCadenceOnly = isCadenceOnly
        if (!isRunning) {
            expectedUris.clear()
        }
    }

    override fun disconnect() {
        playerStateSubscription?.cancel()
        spotifyAppRemote?.let { SpotifyAppRemote.disconnect(it) }
        spotifyAppRemote = null
        lastStateSnapshot = null
        lastQueuedTrackUri = null
        _currentlyPlayingBpm.value = null
        _currentlyPlayingTitle.value = null
        expectedUris.clear()
    }

    override fun pausePlayback(onError: ((Throwable) -> Unit)?) {
        spotifyAppRemote?.playerApi?.pause()?.setErrorCallback { onError?.invoke(it) }
    }

    override fun resumePlayback(onError: ((Throwable) -> Unit)?) {
        spotifyAppRemote?.playerApi?.resume()?.setErrorCallback { onError?.invoke(it) }
    }

    override fun restartTrack(onError: ((Throwable) -> Unit)?) {
        spotifyAppRemote?.playerApi?.seekTo(0)?.setErrorCallback { onError?.invoke(it) }
    }

    override fun getCurrentVolume(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 1.0f
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    override suspend fun fadeVolume(from: Float?, to: Float, durationMs: Long) {
        val startVolume = from ?: getCurrentVolume()
        val steps = 25
        val stepDuration = (durationMs / steps).coerceAtLeast(1)
        val volumeDelta = (to - startVolume) / steps
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        repeat(steps + 1) { i ->
            val vol = (startVolume + (volumeDelta * i)).coerceIn(0f, 1f)
            withContext(Dispatchers.Main) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (vol * maxVolume).toInt(), 0)
            }
            delay(stepDuration)
        }
    }

    override suspend fun isBetterMatchAvailable(
        targetBpm: Int,
        currentTrackBpm: Int,
        trackDao: TrackDao,
        useFallback: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val bestTrack = findBestTrack(targetBpm, trackDao, useFallback) ?: return@withContext false
        val currentDelta = abs(currentTrackBpm - targetBpm)
        val bestDelta = abs(bestTrack.bpm - targetBpm)
        return@withContext bestDelta < currentDelta
    }

    override suspend fun playBestMatchingTrack(
        currentBpm: Int,
        trackDao: TrackDao,
        useFallback: Boolean,
        onError: (Throwable) -> Unit
    ) {
        executeBestMatch(currentBpm, trackDao, useFallback, PlaybackAction.PLAY, onError)
    }

    suspend fun queueBestMatchingTrack(
        currentBpm: Int,
        trackDao: TrackDao,
        useFallback: Boolean,
        onError: (Throwable) -> Unit = {}
    ) {
        executeBestMatch(currentBpm, trackDao, useFallback, PlaybackAction.QUEUE, onError)
    }

    private suspend fun executeBestMatch(
        currentBpm: Int,
        trackDao: TrackDao,
        useFallback: Boolean,
        action: PlaybackAction,
        onError: (Throwable) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val selected = findBestTrack(currentBpm, trackDao, useFallback) ?: return@withContext
            
            withContext(Dispatchers.Main) {
                if (action == PlaybackAction.PLAY) {
                    expectedUris.clear()
                }
                if (!expectedUris.contains(selected.uri)) {
                    expectedUris.add(selected.uri)
                    if (expectedUris.size > 5) expectedUris.removeAt(0)
                }

                val result = CompletableDeferred<Unit>()
                val call = when (action) {
                    PlaybackAction.PLAY -> spotifyAppRemote?.playerApi?.play(selected.uri)
                    PlaybackAction.QUEUE -> spotifyAppRemote?.playerApi?.queue(selected.uri)
                    else -> null
                }
                call
                    ?.setResultCallback {
                        managerScope.launch(Dispatchers.IO) { trackDao.incrementPlayCount(selected.uri) }
                        result.complete(Unit)
                    }
                    ?.setErrorCallback {
                        onError(it)
                        result.complete(Unit)
                    }
                withTimeoutOrNull(CALLBACK_TIMEOUT_MS) { result.await() }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError(e) }
        }
    }

    private suspend fun findBestTrack(currentBpm: Int, trackDao: TrackDao, useFallback: Boolean): TrackEntity? {
        val tracksInRange = trackDao.getTracksInRange(currentBpm - 5, currentBpm + 5)
        val userTracks = tracksInRange.filter { !it.isFallback }
        fun fallbackPool() = tracksInRange.filter { it.isFallback && abs(it.bpm - currentBpm) <= 1 }

        val pool = if (userTracks.isEmpty()) {
            if (!useFallback) return null
            fallbackPool()
        } else {
            val bestDelta = userTracks.minOf { abs(it.bpm - currentBpm) }
            if (bestDelta < 3 || !useFallback) userTracks.filter { abs(it.bpm - currentBpm) == bestDelta }
            else fallbackPool()
        }
        if (pool.isEmpty()) return null

        val minPlays = pool.minOf { it.playCount }
        return pool.filter { it.playCount == minPlays }.randomOrNull()
    }

    suspend fun syncBpmPlaylists(
        trackDao: TrackDao,
        minBpm: Int,
        maxBpm: Int,
        useFallback: Boolean,
        onWarning: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext
        val bearerToken = "Bearer $token"
        try {
            val playlists = apiService.getCurrentUserPlaylists(bearerToken).items ?: emptyList()
            val bpmPattern = Regex("""\b(1[0-9][0-9]|200)\b""")

            val collectedTracks = playlists.mapNotNull { playlist ->
                val name = playlist.name ?: return@mapNotNull null
                val id = playlist.id ?: return@mapNotNull null
                val bpm = bpmPattern.find(name)?.value?.toIntOrNull() ?: return@mapNotNull null
                id to bpm
            }.flatMap { (id, bpm) ->
                val tracks = apiService.getPlaylistTracks(bearerToken, id).items ?: emptyList()
                tracks.mapNotNull { item ->
                    val track = item.effectiveTrack ?: return@mapNotNull null
                    val uri = track.uri ?: return@mapNotNull null
                    TrackEntity(uri, track.name ?: "", track.artists?.firstOrNull()?.name ?: "", bpm, 0, track.durationMs, false)
                }
            }

            if (collectedTracks.isNotEmpty()) trackDao.insertTracks(collectedTracks.distinctBy { it.uri })

            if (!useFallback) {
                val userTracks = trackDao.getAllTracks().filter { !it.isFallback }
                val startBpm = if (minBpm % 5 == 0) minBpm else minBpm + (5 - minBpm % 5)
                val missingBpms = (startBpm..maxBpm step 5).filter { target ->
                    userTracks.none { abs(it.bpm - target) <= 4 }
                }
                if (missingBpms.isNotEmpty()) {
                    val msg = "Missing track coverage for ${missingBpms.joinToString(", ")} BPM. Enable 'Use Default Tracks'."
                    withContext(Dispatchers.Main) { onWarning(msg) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync Failed: ${e.message}")
        }
    }
}

private val AndroidSpotifyService.managerScope: CoroutineScope get() = CoroutineScope(Dispatchers.Main + SupervisorJob())
