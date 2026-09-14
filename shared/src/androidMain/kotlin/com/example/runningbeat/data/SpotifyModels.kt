package com.example.runningbeat.data

import com.google.gson.annotations.SerializedName
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
