package com.citrus.blsc.data.database

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.citrus.blsc.data.model.SearchHistoryItem

@Database(
    entities = [DeviceCoordinate::class, SearchHistoryItem::class],
    version = 4,
    exportSchema = false,
)
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
                ).addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE search_history ADD COLUMN deviceType TEXT NOT NULL DEFAULT 'Неизвестное устройство'"
                )
            }
        }
    }
}