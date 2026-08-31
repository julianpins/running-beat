package com.example.runningbeat

import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.sql.DriverManager

class GeneratorTest {

    // ⚠️ REPLACE THIS WITH YOUR TEMPORARY SPOTIFY ACCESS TOKEN
    private val spotifyAccessToken = "BQCWr4Zbb-N-d-tK7t5lBViAZMlNfkuN6bh6bb2gFyAZMAWiGwI6zbxOenp1oU8Z5ZFFlnHgSRnxxn0oxb09IEmQCzZzMMNLilGae2-Vw-g479n1K7BcvIMnq7F5er2PePVUrxVjPZgN4Tf3FkUOoWyFpC_yccoHe3g3J_qp9n2QyiVryGXBmCE64RfSS7i7qBvYY6m9mu4aYNjcjVVRuUj51E3a4A1_w2QA3unCAf6eXUyaAOpZ0sKvpHlfQrDSR2wHEvzNLlH3OFHuqygbGIiO"

    @Test
    fun generateFallbackDatabase() {
        require(spotifyAccessToken != "YOUR_DEVELOPER_SPOTIFY_ACCESS_TOKEN") {
            "Please paste a valid Spotify Access Token into spotifyAccessToken before running!"
        }

        val dbFile = File("fallback_tracks.db")
        if (dbFile.exists()) {
            dbFile.delete()
            println("Deleted old fallback_tracks.db")
        }

        println("🛠️ Connecting to SQLite database...")
        val connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        val statement = connection.createStatement()

        // 1. Create table matching Room's TrackEntity schema
        statement.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS tracks (
                uri TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                bpm INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                isFallback INTEGER NOT NULL,
                playCount INTEGER NOT NULL DEFAULT 0
            );
            """.trimIndent()
        )

        // 2. Locate the "Fallback" Playlist on your Spotify Account
        println("🔍 Fetching user playlists from Spotify API...")
        val playlistId = getPlaylistIdByName(spotifyAccessToken, "Fallback")
            ?: error("❌ Playlist named 'Fallback' was not found on your Spotify account!")

        println("📁 Found 'Fallback' playlist (ID: $playlistId). Fetching track list...")
        val trackItems = fetchAllPlaylistTracks(spotifyAccessToken, playlistId)
        println("🎶 Total tracks retrieved from Spotify: ${trackItems.size}")

        // 3. Insert 2 tracks per BPM across the range 100 to 200
        val insertStmt = connection.prepareStatement(
            """
            INSERT OR REPLACE INTO tracks 
            (uri, title, artist, bpm, durationMs, isFallback, playCount) 
            VALUES (?, ?, ?, ?, ?, 1, 0)
            """.trimIndent()
        )

        var trackIndex = 0
        var insertedCount = 0

        for (bpm in 100..200) {
            repeat(2) {
                if (trackIndex < trackItems.size) {
                    val track = trackItems[trackIndex]
                    insertStmt.setString(1, track.uri)
                    insertStmt.setString(2, track.title)
                    insertStmt.setString(3, track.artist)
                    insertStmt.setInt(4, bpm)
                    insertStmt.setLong(5, track.durationMs)
                    insertStmt.executeUpdate()

                    trackIndex++
                    insertedCount++
                }
            }
        }

        connection.close()

        println("✅ SUCCESS!")
        println("🎉 Database created with $insertedCount fallback tracks across 100..200 BPM.")
        println("📍 File Location: ${dbFile.absolutePath}")
    }

    private data class LocalTrack(
        val uri: String,
        val title: String,
        val artist: String,
        val durationMs: Long
    )

    private fun getPlaylistIdByName(token: String, targetName: String): String? {
        var urlString: String? = "https://api.spotify.com/v1/me/playlists?limit=50"

        while (urlString != null) {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")

            if (conn.responseCode != 200) {
                val errorStream = conn.errorStream?.bufferedReader()?.readText()
                error("Spotify API Request Failed [${conn.responseCode}]: $errorStream")
            }

            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val items = json.optJSONArray("items") ?: break

            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                if (obj.optString("name").equals(targetName, ignoreCase = true)) {
                    return obj.optString("id")
                }
            }

            urlString = if (json.has("next") && !json.isNull("next")) json.getString("next") else null
        }
        return null
    }

    private fun fetchAllPlaylistTracks(token: String, playlistId: String): List<LocalTrack> {
        val tracks = mutableListOf<LocalTrack>()
        var urlString: String? = "https://api.spotify.com/v1/playlists/$playlistId/items?limit=100"

        while (urlString != null) {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")

            if (conn.responseCode != 200) {
                val errorStream = conn.errorStream?.bufferedReader()?.readText()
                error("Spotify API Tracks Request Failed [${conn.responseCode}]: $errorStream")
            }

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val items = json.optJSONArray("items") ?: break

            for (i in 0 until items.length()) {
                val itemObj = items.getJSONObject(i)
                val trackObj = if (itemObj.has("track") && !itemObj.isNull("track")) {
                    itemObj.optJSONObject("track")
                } else if (itemObj.has("item") && !itemObj.isNull("item")) {
                    itemObj.optJSONObject("item")
                } else null

                if (trackObj != null && trackObj.has("uri") && !trackObj.isNull("uri")) {
                    val uri = trackObj.getString("uri")
                    val name = trackObj.optString("name", "Unknown Title")
                    val duration = trackObj.optLong("duration_ms", 0L)
                    val artistsArr = trackObj.optJSONArray("artists")
                    val artist = if (artistsArr != null && artistsArr.length() > 0) {
                        artistsArr.getJSONObject(0).optString("name", "Unknown Artist")
                    } else {
                        "Unknown Artist"
                    }

                    tracks.add(LocalTrack(uri, name, artist, duration))
                }
            }

            urlString = if (json.has("next") && !json.isNull("next")) json.getString("next") else null
        }
        return tracks
    }
}