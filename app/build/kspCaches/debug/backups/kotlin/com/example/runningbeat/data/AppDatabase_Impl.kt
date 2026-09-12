package com.example.runningbeat.`data`

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _trackDao: Lazy<TrackDao> = lazy {
    TrackDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "301209fc4a249e8c0748713621e3a17e", "c93c7316819e14cbb9a7a89aa505bfd0") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `tracks` (`uri` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `bpm` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `isFallback` INTEGER NOT NULL, PRIMARY KEY(`uri`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_bpm` ON `tracks` (`bpm`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '301209fc4a249e8c0748713621e3a17e')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `tracks`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsTracks: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTracks.put("uri", TableInfo.Column("uri", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTracks.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTracks.put("artist", TableInfo.Column("artist", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTracks.put("bpm", TableInfo.Column("bpm", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTracks.put("playCount", TableInfo.Column("playCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTracks.put("durationMs", TableInfo.Column("durationMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTracks.put("isFallback", TableInfo.Column("isFallback", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTracks: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTracks: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesTracks.add(TableInfo.Index("index_tracks_bpm", false, listOf("bpm"), listOf("ASC")))
        val _infoTracks: TableInfo = TableInfo("tracks", _columnsTracks, _foreignKeysTracks, _indicesTracks)
        val _existingTracks: TableInfo = read(connection, "tracks")
        if (!_infoTracks.equals(_existingTracks)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |tracks(com.example.runningbeat.data.TrackEntity).
              | Expected:
              |""".trimMargin() + _infoTracks + """
              |
              | Found:
              |""".trimMargin() + _existingTracks)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "tracks")
  }

  public override fun clearAllTables() {
    super.performClear(false, "tracks")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(TrackDao::class, TrackDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun trackDao(): TrackDao = _trackDao.value
}
