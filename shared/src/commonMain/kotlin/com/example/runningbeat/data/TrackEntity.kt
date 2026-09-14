package com.example.runningbeat.data

import androidx.room.*

@Entity(
    tableName = "tracks",
    indices = [Index(value = ["bpm"])]
)
data class TrackEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String,
    val bpm: Int,
    val playCount: Int = 0,
    val durationMs: Long,
    val isFallback: Boolean
)
@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks")
    suspend fun getAllTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE uri = :uri LIMIT 1")
    suspend fun getTrackByUri(uri: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE bpm BETWEEN :minBpm AND :maxBpm")
    suspend fun getTracksInRange(minBpm: Int, maxBpm: Int): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET playCount = playCount + 1 WHERE uri = :trackUri")
    suspend fun incrementPlayCount(trackUri: String)
}

@Database(entities = [TrackEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
}
