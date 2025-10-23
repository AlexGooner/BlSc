package com.citrus.blsc.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.citrus.blsc.data.model.SearchHistoryItem

@Database(entities = [DeviceCoordinate::class, SearchHistoryItem::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceCoordinateDao(): DeviceCoordinateDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}