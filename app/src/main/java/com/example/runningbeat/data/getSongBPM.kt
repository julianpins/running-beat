package com.example.runningbeat.data

class getSongBPM {
}

/*
package com.example.runningbeat.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.JsonElement
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import kotlin.time.Duration.Companion.milliseconds

// --- GetSongBPM Retrofit Interface ---
interface GetSongBpmApiService {
    @GET("search/")
    suspend fun searchSong(
        @Query("api_key") apiKey: String,
        @Query("type") type: String = "song",
        @Query("lookup") lookup: String
    ): JsonElement
}

// --- Spotify Retrofit Data Models ---
data class SpotifyUserPlaylistsResponse(
    val items: List<SpotifyPlaylist>?
)

data class SpotifyPlaylist(
    val id: String,
    val name: String
)

data class SpotifyPlaylistTracksResponse(
    val items: List<SpotifyPlaylistItem>?
)

data class SpotifyPlaylistItem(
    val track: SpotifyTrackObject?,
    val item: SpotifyTrackObject? // Endpoint fallback parameter
)

data class SpotifyTrackObject(
    val uri: String?,
    val name: String?,
    val artists: List<SpotifyArtist>?
)

data class SpotifyArtist(
    val name: String?
)

// --- Spotify Web API Interface ---
interface SpotifyApiService {
    @GET("v1/me/playlists")
    suspend fun getCurrentUserPlaylists(
        @Header("Authorization") authorization: String
    ): SpotifyUserPlaylistsResponse

    @GET("v1/playlists/{playlist_id}/items")
    suspend fun getPlaylistTracks(
        @Header("Authorization") authorization: String,
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 100
    ): SpotifyPlaylistTracksResponse
}

// --- Main Spotify Manager Class ---
class SpotifyManager(
    private val context: Context,
    private val clientId: String,
    private val redirectUri: String
) {
    private val getSongBpmApiKey = "e5289b0a9c3ffd689e51d46fb1fdd422"

    companion object {
        const val AUTH_TOKEN_REQUEST_CODE = 1337
        private const val TAG = "DB_DEBUG"
    }

    var spotifyAppRemote: SpotifyAppRemote? = null
        private set

    var accessToken: String? = null
        private set

    // Lazy initialization for GetSongBPM with Cloudflare bypass headers
    private val bpmApiService: GetSongBpmApiService by lazy {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.getsong.co/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GetSongBpmApiService::class.java)
    }

    // Lazy initialization for Spotify Web API
    private val apiService: SpotifyApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.spotify.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyApiService::class.java)
    }

    /**
     * Cleans up Spotify track names by removing tags like "(feat. ...)", "[Radio Edit]", or "- Remastered".
     */
    private fun sanitizeTrackTitle(title: String): String {
        return title
            .replace(Regex("\\s*\\([^)]*\\)"), "")   // Removes (feat. X), (Remastered 2020), etc.
            .replace(Regex("\\s*\\[[^\\]]*\\]"), "") // Removes [Radio Edit], [Deluxe], etc.
            .replace(Regex("\\s*-.*$"), "")          // Removes - Remastered, - Live, etc.
            .trim()
    }

    /**
     * Fetches BPM using combined Title + Artist query with half-tempo normalization and fallbacks.
     */
    private suspend fun fetchBpmForTrack(title: String, artist: String, fallbackBpm: Int): Int {
        val cleanTitle = sanitizeTrackTitle(title)
        val searchQuery = "$cleanTitle $artist"

        return try {
            val responseJson = bpmApiService.searchSong(
                apiKey = getSongBpmApiKey,
                type = "song",
                lookup = searchQuery
            )

            var foundBpm: Int? = null

            if (responseJson.isJsonObject) {
                val rootObj = responseJson.asJsonObject
                if (rootObj.has("search") && rootObj.get("search").isJsonArray) {
                    val searchArray = rootObj.getAsJsonArray("search")
                    for (element in searchArray) {
                        if (element.isJsonObject) {
                            val songObj = element.asJsonObject
                            if (songObj.has("tempo") && !songObj.get("tempo").isJsonNull) {
                                val tempoStr = songObj.get("tempo").asString
                                val parsed = tempoStr.toDoubleOrNull()?.toInt()
                                if (parsed != null && parsed > 0) {
                                    foundBpm = parsed
                                    break
                                }
                            }
                        }
                    }
                }
            }

            if (foundBpm != null) {
                // Normalize half-tempo values to fit standard running cadence range (110–210 BPM)
                var normalizedBpm = foundBpm
                while (normalizedBpm < 110) normalizedBpm *= 2
                while (normalizedBpm > 210) normalizedBpm /= 2

                Log.d(TAG, "🎯 Found BPM for '$searchQuery': $normalizedBpm (Raw: $foundBpm)")
                normalizedBpm
            } else {
                Log.w(TAG, "⚠️ No BPM found for '$searchQuery', using fallback: $fallbackBpm")
                fallbackBpm
            }

        } catch (e: HttpException) {
            Log.e(TAG, "⚠️ HTTP ${e.code()} Error fetching BPM for '$searchQuery': ${e.message()}. Using fallback.")
            fallbackBpm
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception fetching BPM for '$searchQuery': ${e.message}")
            fallbackBpm
        }
    }

    /**
     * Starts OAuth Login flow requesting necessary scopes.
     */
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

    /**
     * Handles activity result from Spotify auth client.
     */
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
                Log.d(TAG, "🔑 Received OAuth access token successfully.")
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

    /**
     * Fetches user playlist tracks from Spotify, enriches them with Title+Artist BPM data, and persists to Room.
     */
    suspend fun syncPlaylistTracks(
        playlistName: String,
        trackDao: TrackDao,
        fallbackBpm: Int
    ) = withContext(Dispatchers.IO) {
        val token = accessToken
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "❌ Sync failed: Access token is null or empty!")
            return@withContext
        }

        val bearerToken = "Bearer $token"

        try {
            Log.d(TAG, "🔍 Fetching user playlists from Spotify API...")
            val playlistResponse = apiService.getCurrentUserPlaylists(bearerToken)
            val playlists = playlistResponse.items ?: emptyList()

            Log.d(TAG, "📋 Fetched ${playlists.size} playlists from user account.")

            val targetPlaylist = playlists.find {
                it.name.trim().equals(playlistName.trim(), ignoreCase = true)
            }

            if (targetPlaylist == null) {
                val names = playlists.map { "\"${it.name}\"" }
                Log.e(TAG, "❌ Target playlist '$playlistName' not found. Available playlists: $names")
                return@withContext
            }

            Log.d(TAG, "✅ Found playlist '${targetPlaylist.name}' (ID: ${targetPlaylist.id}). Fetching tracks...")

            val tracksResponse = apiService.getPlaylistTracks(
                authorization = bearerToken,
                playlistId = targetPlaylist.id,
                limit = 100
            )
            val items = tracksResponse.items ?: emptyList()
            Log.d(TAG, "📥 Raw tracks returned from Spotify API: ${items.size}")

            val trackEntities = mutableListOf<TrackEntity>()

            for (playlistItem in items) {
                val trackObj = playlistItem.track ?: playlistItem.item ?: continue
                val uri = trackObj.uri ?: continue
                val title = trackObj.name ?: continue
                val artist = trackObj.artists?.firstOrNull()?.name ?: "Unknown Artist"

                // 1. Fetch real BPM using Title + Artist query
                val fetchedBpm = fetchBpmForTrack(title, artist, fallbackBpm)

                trackEntities.add(
                    TrackEntity(
                        uri = uri,
                        title = title,
                        artist = artist,
                        bpm = fetchedBpm,
                        playCount = 0
                    )
                )

                // 2. Delay 250ms between requests to avoid API rate limits
                delay(250.milliseconds)
            }

            Log.d(TAG, "💾 Saving ${trackEntities.size} mapped tracks to Room database...")
            trackDao.clearAll()
            trackDao.insertTracks(trackEntities)
            Log.d(TAG, "🎉 Successfully synced ${trackEntities.size} tracks into SQLite Room database!")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during playlist sync: ${e.localizedMessage}", e)
        }
    }

    /**
     * Finds a user's playlist URI by name query.
     */
    suspend fun findUserPlaylistByName(playlistName: String): String? = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext null
        return@withContext try {
            val response = apiService.getCurrentUserPlaylists("Bearer $token")
            val target = response.items?.find { it.name.trim().equals(playlistName.trim(), ignoreCase = true) }
            target?.let { "spotify:playlist:${it.id}" }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search user playlists: ${e.message}")
            null
        }
    }

    /**
     * Plays best matching track based on current step cadence (BPM).
     */
    suspend fun playBestMatchingTrack(
        currentBpm: Int,
        trackDao: TrackDao,
        onError: (Throwable) -> Unit
    ) = withContext(Dispatchers.IO) {
        val tracks = trackDao.getAllTracks()
        if (tracks.isEmpty()) {
            Log.w(TAG, "⚠️ Cannot play best track: Room database is empty!")
            return@withContext
        }

        // Find track with closest BPM tolerance and lowest play count
        val bestMatch = tracks.minByOrNull { track ->
            val bpmDifference = kotlin.math.abs(track.bpm - currentBpm)
            bpmDifference + (track.playCount * 10)
        }

        bestMatch?.let { track ->
            Log.d(TAG, "▶️ Playing Best Match: '${track.title}' (BPM: ${track.bpm}, Cadence: $currentBpm)")
            withContext(Dispatchers.Main) {
                spotifyAppRemote?.playerApi?.play(track.uri)
            }
            trackDao.incrementPlayCount(track.uri)
        }
    }

    /**
     * Observers player state and triggers next track selection on song completion.
     */
    fun observeTrackEnd(
        getCurrentBpm: () -> Int,
        trackDao: TrackDao,
        coroutineScope: CoroutineScope
    ) {
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
            if (playerState.isPaused && playerState.playbackPosition == 0L) {
                coroutineScope.launch {
                    playBestMatchingTrack(getCurrentBpm(), trackDao) {}
                }
            }
        }
    }

    fun playPlaylist(uri: String, onError: (Throwable) -> Unit) {
        spotifyAppRemote?.playerApi?.play(uri)?.setErrorCallback(onError)
    }

    fun toggleOrStart(onNeedsStart: () -> Unit, onError: (Throwable) -> Unit) {
        spotifyAppRemote?.playerApi?.playerState?.setResultCallback { state ->
            if (state == null || state.track == null) {
                onNeedsStart()
            } else if (state.isPaused) {
                spotifyAppRemote?.playerApi?.resume()?.setErrorCallback(onError)
            } else {
                spotifyAppRemote?.playerApi?.pause()?.setErrorCallback(onError)
            }
        }?.setErrorCallback(onError)
    }

    fun skipToPrevious(onError: (Throwable) -> Unit) {
        spotifyAppRemote?.playerApi?.skipPrevious()?.setErrorCallback(onError)
    }

    fun disconnect() {
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
        spotifyAppRemote = null
    }
}
 */