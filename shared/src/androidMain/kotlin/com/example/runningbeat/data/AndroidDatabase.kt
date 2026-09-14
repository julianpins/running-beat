package com.example.runningbeat.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

object DatabaseFactory {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            context.deleteDatabase("temp_runningbeat.db")
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "temp_runningbeat.db"
            )
                .createFromAsset("database/fallback_tracks.db")
                .build()
            INSTANCE = instance
            instance
        }
    }
}
