package com.example.runningbeat.`data`

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class TrackDao_Impl(
  __db: RoomDatabase,
) : TrackDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfTrackEntity: EntityInsertAdapter<TrackEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfTrackEntity = object : EntityInsertAdapter<TrackEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `tracks` (`uri`,`title`,`artist`,`bpm`,`playCount`,`durationMs`,`isFallback`) VALUES (?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: TrackEntity) {
        statement.bindText(1, entity.uri)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.artist)
        statement.bindLong(4, entity.bpm.toLong())
        statement.bindLong(5, entity.playCount.toLong())
        statement.bindLong(6, entity.durationMs)
        val _tmp: Int = if (entity.isFallback) 1 else 0
        statement.bindLong(7, _tmp.toLong())
      }
    }
  }

  public override suspend fun insertTracks(tracks: List<TrackEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfTrackEntity.insert(_connection, tracks)
  }

  public override suspend fun getAllTracks(): List<TrackEntity> {
    val _sql: String = "SELECT * FROM tracks"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfUri: Int = getColumnIndexOrThrow(_stmt, "uri")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfBpm: Int = getColumnIndexOrThrow(_stmt, "bpm")
        val _columnIndexOfPlayCount: Int = getColumnIndexOrThrow(_stmt, "playCount")
        val _columnIndexOfDurationMs: Int = getColumnIndexOrThrow(_stmt, "durationMs")
        val _columnIndexOfIsFallback: Int = getColumnIndexOrThrow(_stmt, "isFallback")
        val _result: MutableList<TrackEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: TrackEntity
          val _tmpUri: String
          _tmpUri = _stmt.getText(_columnIndexOfUri)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpBpm: Int
          _tmpBpm = _stmt.getLong(_columnIndexOfBpm).toInt()
          val _tmpPlayCount: Int
          _tmpPlayCount = _stmt.getLong(_columnIndexOfPlayCount).toInt()
          val _tmpDurationMs: Long
          _tmpDurationMs = _stmt.getLong(_columnIndexOfDurationMs)
          val _tmpIsFallback: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFallback).toInt()
          _tmpIsFallback = _tmp != 0
          _item = TrackEntity(_tmpUri,_tmpTitle,_tmpArtist,_tmpBpm,_tmpPlayCount,_tmpDurationMs,_tmpIsFallback)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTrackByUri(uri: String): TrackEntity? {
    val _sql: String = "SELECT * FROM tracks WHERE uri = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, uri)
        val _columnIndexOfUri: Int = getColumnIndexOrThrow(_stmt, "uri")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfBpm: Int = getColumnIndexOrThrow(_stmt, "bpm")
        val _columnIndexOfPlayCount: Int = getColumnIndexOrThrow(_stmt, "playCount")
        val _columnIndexOfDurationMs: Int = getColumnIndexOrThrow(_stmt, "durationMs")
        val _columnIndexOfIsFallback: Int = getColumnIndexOrThrow(_stmt, "isFallback")
        val _result: TrackEntity?
        if (_stmt.step()) {
          val _tmpUri: String
          _tmpUri = _stmt.getText(_columnIndexOfUri)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpBpm: Int
          _tmpBpm = _stmt.getLong(_columnIndexOfBpm).toInt()
          val _tmpPlayCount: Int
          _tmpPlayCount = _stmt.getLong(_columnIndexOfPlayCount).toInt()
          val _tmpDurationMs: Long
          _tmpDurationMs = _stmt.getLong(_columnIndexOfDurationMs)
          val _tmpIsFallback: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFallback).toInt()
          _tmpIsFallback = _tmp != 0
          _result = TrackEntity(_tmpUri,_tmpTitle,_tmpArtist,_tmpBpm,_tmpPlayCount,_tmpDurationMs,_tmpIsFallback)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTracksInRange(minBpm: Int, maxBpm: Int): List<TrackEntity> {
    val _sql: String = "SELECT * FROM tracks WHERE bpm BETWEEN ? AND ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, minBpm.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, maxBpm.toLong())
        val _columnIndexOfUri: Int = getColumnIndexOrThrow(_stmt, "uri")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfBpm: Int = getColumnIndexOrThrow(_stmt, "bpm")
        val _columnIndexOfPlayCount: Int = getColumnIndexOrThrow(_stmt, "playCount")
        val _columnIndexOfDurationMs: Int = getColumnIndexOrThrow(_stmt, "durationMs")
        val _columnIndexOfIsFallback: Int = getColumnIndexOrThrow(_stmt, "isFallback")
        val _result: MutableList<TrackEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: TrackEntity
          val _tmpUri: String
          _tmpUri = _stmt.getText(_columnIndexOfUri)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpBpm: Int
          _tmpBpm = _stmt.getLong(_columnIndexOfBpm).toInt()
          val _tmpPlayCount: Int
          _tmpPlayCount = _stmt.getLong(_columnIndexOfPlayCount).toInt()
          val _tmpDurationMs: Long
          _tmpDurationMs = _stmt.getLong(_columnIndexOfDurationMs)
          val _tmpIsFallback: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsFallback).toInt()
          _tmpIsFallback = _tmp != 0
          _item = TrackEntity(_tmpUri,_tmpTitle,_tmpArtist,_tmpBpm,_tmpPlayCount,_tmpDurationMs,_tmpIsFallback)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun incrementPlayCount(trackUri: String) {
    val _sql: String = "UPDATE tracks SET playCount = playCount + 1 WHERE uri = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, trackUri)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
