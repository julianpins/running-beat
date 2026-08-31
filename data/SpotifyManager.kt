package com.example.runningbeat.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.annotations.SerializedName
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

data class SpotifyPlaylistsResponse(
    @SerializedName("items") val items: List<SpotifyPlaylistObject>?
)

data class SpotifyPlaylistObject(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?
)

data class SpotifyPlaylistTracksResponse(
    @SerializedName("items") val items: List<SpotifyPlaylistTrackItem>?
)

data class SpotifyPlaylistTrackItem(
    @SerializedName("track") val track: SpotifyTrackObject?,
    @SerializedName("item") val itemTrack: SpotifyTrackObject?
) {
    val effectiveTrack: SpotifyTrackObject?
        get() = track ?: itemTrack
}

data class SpotifyTrackObject(
    @SerializedName("id") val id: String?,
    @SerializedName("uri") val uri: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("artists") val artists: List<SpotifyArtist>?,
    @SerializedName("duration_ms") val durationMs: Long,
)

data class SpotifyArtist(
    @SerializedName("name") val name: String?
)

interface SpotifyApiService {
    @GET("v1/me/playlists")
    suspend fun getCurrentUserPlaylists(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 50
    ): SpotifyPlaylistsResponse

    @GET("v1/playlists/{playlist_id}/items")
    suspend fun getPlaylistTracks(
        @Header("Authorization") authorization: String,
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 100
    ): SpotifyPlaylistTracksResponse
}

class SpotifyManager(
    private val context: Context,
    private val clientId: String,
    private val redirectUri: String
) {
    companion object {
        const val AUTH_TOKEN_REQUEST_CODE = 1337
        private const val TAG = "DB_DEBUG"
    }

    var spotifyAppRemote: SpotifyAppRemote? = null
        private set

    var accessToken: String? = null
        private set

    private var lastQueueTime = 0L

    val currentlyPlayingBpm = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)

    private val apiService: SpotifyApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.spotify.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyApiService::class.java)
    }

    fun subscribeToPlayerState(
        onStateChanged: (isPlaying: Boolean) -> Unit
    ) {
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
            val isPlaying = !playerState.isPaused
            onStateChanged(isPlaying)
        }
    }

    suspend fun fadeVolume(from: Float, to: Float, durationMs: Long) {
        val steps = 10
        val stepDuration = durationMs / steps
        val volumeDelta = (to - from) / steps

        for (i in 0..steps) {
            val volume = from + (volumeDelta * i)
            spotifyAppRemote?.connectApi?.connectSetVolume(volume.coerceIn(0f, 1f))
            kotlinx.coroutines.delay(stepDuration)
        }
    }

    fun authorize(activity: Activity) {
        val builder = AuthorizationRequest.Builder(
            clientId,
            AuthorizationResponse.Type.TOKEN,
            redirectUri
        )

        builder.setScopes(
            arrayOf(
                "streaming",
                "app-remote-control",
                "playlist-read-private",
                "playlist-read-collaborative",
                "user-library-read"
            )
        )

        builder.setShowDialog(true)
        val request = builder.build()
        AuthorizationClient.openLoginActivity(activity, AUTH_TOKEN_REQUEST_CODE, request)
    }

    fun handleAuthResponse(
        resultCode: Int,
        intent: Intent?,
        onConnected: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val response = AuthorizationClient.getResponse(resultCode, intent)
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                this.accessToken = response.accessToken
                Log.d(TAG, "🔑 Received OAuth Access Token successfully. Test")
                Log.d("SPOTIFY_TOKEN", "COPY_THIS_TOKEN: ${response.accessToken}")
                connectAppRemote(onConnected, onFailure)
            }
            AuthorizationResponse.Type.ERROR -> {
                val errorMsg = "Auth Error: ${response.error}"
                Log.e(TAG, "❌ $errorMsg")
                onFailure(errorMsg)
            }
            else -> {
                onFailure("Authentication canceled or failed.")
            }
        }
    }

    private fun connectAppRemote(onConnected: () -> Unit, onFailure: (String) -> Unit) {
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(context, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d(TAG, "✅ Spotify App Remote Connected.")
                onConnected()
            }

            override fun onFailure(throwable: Throwable) {
                val errorMsg = "App Remote Connection Failed: ${throwable.message}"
                Log.e(TAG, "❌ $errorMsg", throwable)
                onFailure(errorMsg)
            }
        })
    }

    suspend fun syncBpmPlaylists(
        trackDao: TrackDao,
        minBpm: Int,
        maxBpm: Int,
        useFallback: Boolean,
        onWarning: (String) -> Unit = { Log.w("SpotifyManager", it) }
    ) = withContext(Dispatchers.IO) {
        val token = accessToken
        if (token.isNullOrEmpty()) return@withContext

        val bearerToken = "Bearer $token"

        try {
            val playlistsResponse = apiService.getCurrentUserPlaylists(authorization = bearerToken)
            val playlists = playlistsResponse.items ?: emptyList()

            val collectedTracks = mutableListOf<TrackEntity>()
            val bpmPattern = Regex("""\b(1[0-9][0-9]|200)\b""")

            for (playlist in playlists) {
                val name = playlist.name ?: continue
                val id = playlist.id ?: continue

                val matchResult = bpmPattern.find(name)
                if (matchResult != null) {
                    val extractedBpm = matchResult.value.toInt()
                    if (extractedBpm in 100..200) {
                        val tracksResponse = apiService.getPlaylistTracks(
                            authorization = bearerToken,
                            playlistId = id
                        )
                        val playlistItems = tracksResponse.items ?: emptyList()

                        for (item in playlistItems) {
                            val track = item.effectiveTrack
                            if (track != null && !track.uri.isNullOrEmpty()) {
                                collectedTracks.add(
                                    TrackEntity(
                                        uri = track.uri,
                                        title = track.name ?: "Unknown Title",
                                        artist = track.artists?.firstOrNull()?.name ?: "Unknown Artist",
                                        bpm = extractedBpm,
                                        playCount = 0,
                                        durationMs = track.durationMs,
                                        isFallback = false
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (collectedTracks.isNotEmpty()) {
                val uniqueTracks = collectedTracks.distinctBy { it.uri }
                trackDao.insertTracks(uniqueTracks)
            }

            if (!useFallback) {
                val userTracks = trackDao.getAllTracks().filter { !it.isFallback }
                val missingBpms = mutableListOf<Int>()
                val startBpm = if (minBpm % 5 == 0) minBpm else minBpm + (5 - minBpm % 5)
                for (targetBpm in startBpm..maxBpm step 5) {
                    if (!userTracks.any { kotlin.math.abs(it.bpm - targetBpm) <= 4 }) {
                        missingBpms.add(targetBpm)
                    }
                }
                if (missingBpms.isNotEmpty()) {
                    val warningMessage = "You have missing track coverage for ${missingBpms.joinToString(", ")} BPM. " +
                            "Enable 'Use Backup Tracks'."
                    withContext(Dispatchers.Main) {
                        onWarning(warningMessage)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("DB_DEBUG", "Playlist Sync Failed: ${e.localizedMessage}")
        }
    }
    fun pausePlayback(onError: ((Throwable) -> Unit)? = null) {
        spotifyAppRemote?.playerApi?.pause()?.setErrorCallback { err ->
            onError?.invoke(err)
        }
    }

    fun resumePlayback(onError: ((Throwable) -> Unit)? = null) {
        spotifyAppRemote?.playerApi?.resume()?.setErrorCallback { err ->
            onError?.invoke(err)
        }
    }

    private suspend fun findBestTrack(
        currentBpm: Int,
        trackDao: TrackDao,
        useFallback: Boolean
    ): TrackEntity? {
        val allTracks = trackDao.getAllTracks()
        val userTracks = allTracks.filter { !it.isFallback }
        var pool = allTracks.filter { it.isFallback && kotlin.math.abs(it.bpm - currentBpm) <= 1 }
        if (userTracks.isEmpty()) {
            if (!useFallback) return null
        } else {
            val bestDelta = userTracks.minOfOrNull { kotlin.math.abs(it.bpm - currentBpm) } ?: return null
            if (bestDelta < 3 || !useFallback) {
                pool = userTracks.filter { kotlin.math.abs(it.bpm - currentBpm) == bestDelta }
            }
        }
        val minPlays = pool.minOfOrNull { it.playCount } ?: return null
        return pool.filter { it.playCount == minPlays }.shuffled().randomOrNull()
    }

    suspend fun playBestMatchingTrack(
        currentBpm: Int,
        trackDao: TrackDao,
        useFallback: Boolean,
        onError: (Throwable) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            val selectedTrack = findBestTrack(currentBpm, trackDao, useFallback)
            if (selectedTrack != null) {
                withContext(Dispatchers.Main) {
                    val result = kotlinx.coroutines.CompletableDeferred<Unit>()
                    spotifyAppRemote?.playerApi?.play(selectedTrack.uri)
                        ?.setResultCallback {
                            CoroutineScope(Dispatchers.IO).launch { trackDao.incrementPlayCount(selectedTrack.uri) }
                            result.complete(Unit)
                        }
                        ?.setErrorCallback {
                            onError(it)
                            result.complete(Unit)
                        }
                    result.await()
                }
            } else {
                withContext(Dispatchers.Main) {
                    onError(IllegalStateException("No matching track found. Add playlists or select 'Use default Tracks'!"))
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError(e) }
        }
    }

    fun queueBestMatchingTrack(
        currentBpm: Int,
        trackDao: TrackDao,
        useFallback: Boolean,
        onError: (Throwable) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val selectedTrack = findBestTrack(currentBpm, trackDao, useFallback)
                if (selectedTrack != null) {
                    withContext(Dispatchers.Main) {
                        spotifyAppRemote?.playerApi?.queue(selectedTrack.uri)?.setResultCallback {
                            CoroutineScope(Dispatchers.IO).launch { trackDao.incrementPlayCount(selectedTrack.uri) }
                        }?.setErrorCallback(onError)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    fun observeTrackEnd(
        getCurrentBpm: () -> Int,
        trackDao: TrackDao,
        useFallback: Boolean
    ) {
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { state ->
            val track = state.track ?: return@setEventCallback
            
            // Update currently playing BPM from DB
            CoroutineScope(Dispatchers.IO).launch {
                val dbTrack = trackDao.getTrackByUri(track.uri)
                currentlyPlayingBpm.value = dbTrack?.bpm
            }
            
            if (System.currentTimeMillis() - lastQueueTime < 10000) return@setEventCallback

            if (track.duration - state.playbackPosition in 1..5000) {
                lastQueueTime = System.currentTimeMillis()
                queueBestMatchingTrack(getCurrentBpm(), trackDao, useFallback)
            }
        }
    }

    fun skipToNext(getCurrentBpm: () -> Int, trackDao: TrackDao, useFallback: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            playBestMatchingTrack(getCurrentBpm(), trackDao, useFallback) {}
        }
    }

    fun restartTrack(onError: (Throwable) -> Unit) {
        spotifyAppRemote?.playerApi?.seekTo(0)
            ?.setErrorCallback(onError)
    }

    fun disconnect() {
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
        spotifyAppRemote = null
    }
}
