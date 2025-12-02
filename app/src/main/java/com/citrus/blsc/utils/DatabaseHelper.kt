package com.citrus.blsc.utils

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.util.Log
import androidx.room.Room
import com.citrus.blsc.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DatabaseHelper {

    private const val TAG = "DatabaseHelper"

    fun exportDatabase(context: Context): File? {
        return try {
            val databasePath = context.getDatabasePath("app_database")
            if (!databasePath.exists()) {
                Log.w(TAG, "Database file not found: ${databasePath.absolutePath}")
                return null
            }

            val exportDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "BluetoothScannerDB"
            )

            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportFile = File(exportDir, "app_database_$timestamp.db")

            databasePath.copyTo(exportFile, overwrite = true)

            Log.d(TAG, "Database exported to: ${exportFile.absolutePath}")
            exportFile
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting database: ${e.message}", e)
            null
        }
    }

    // Асинхронное получение информации о базе данных
    fun getDatabaseInfo(context: Context, callback: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val databasePath = context.getDatabasePath("app_database")
                val db = AppDatabase.getDatabase(context)

                // Вызываем suspend функции в корутине
                val searchHistoryCount = db.searchHistoryDao().getHistoryCount()
                val favouriteCount = db.searchHistoryDao().getFavouriteHistoryCount()

                // Для countCoordinatesByMac нужен мак-адрес, используем общее количество
                val allCoordinates = db.deviceCoordinateDao().getCoordinateByMac("")
                val coordinatesCount = allCoordinates.size

                val info = """
                    Информация о базе данных:
                    
                    Путь: ${databasePath.absolutePath}
                    Размер: ${String.format("%.2f", databasePath.length() / 1024.0)} KB
                    Существует: ${databasePath.exists()}
                    
                    Статистика:
                    • Всего записей в истории: $searchHistoryCount
                    • Избранных записей: $favouriteCount
                    • Координат устройств: $coordinatesCount
                    
                    Таблицы:
                    • search_history
                    • device_coordinates
                    • fav_items
                """.trimIndent()

                CoroutineScope(Dispatchers.Main).launch {
                    callback(info)
                }
            } catch (e: Exception) {
                val errorMessage = "Ошибка получения информации: ${e.message}"
                Log.e(TAG, errorMessage, e)

                CoroutineScope(Dispatchers.Main).launch {
                    callback(errorMessage)
                }
            }
        }
    }

    // Блокирующая версия (для использования в обычных функциях)
    fun getDatabaseInfoSync(context: Context): String = runBlocking {
        return@runBlocking try {
            val databasePath = context.getDatabasePath("app_database")
            val db = AppDatabase.getDatabase(context)

            // Вызываем suspend функции с runBlocking
            val searchHistoryCount = db.searchHistoryDao().getHistoryCount()
            val favouriteCount = db.searchHistoryDao().getFavouriteHistoryCount()
            val allCoordinates = db.deviceCoordinateDao().getCoordinateByMac("")
            val coordinatesCount = allCoordinates.size

            """
                Информация о базе данных:
                
                Путь: ${databasePath.absolutePath}
                Размер: ${String.format("%.2f", databasePath.length() / 1024.0)} KB
                Существует: ${databasePath.exists()}
                
                Статистика:
                • Всего записей в истории: $searchHistoryCount
                • Избранных записей: $favouriteCount
                • Координат устройств: $coordinatesCount
                
                Таблицы:
                • search_history
                • device_coordinates
                • fav_items
            """.trimIndent()
        } catch (e: Exception) {
            "Ошибка получения информации: ${e.message}"
        }
    }

    // Упрощенная версия без вызовов DAO (только файловая информация)
    fun getDatabaseFileInfo(context: Context): String {
        return try {
            val databasePath = context.getDatabasePath("app_database")
            val walPath = context.getDatabasePath("app_database-wal")
            val shmPath = context.getDatabasePath("app_database-shm")

            val databaseSize = if (databasePath.exists()) databasePath.length() else 0
            val walSize = if (walPath.exists()) walPath.length() else 0
            val shmSize = if (shmPath.exists()) shmPath.length() else 0
            val totalSize = databaseSize + walSize + shmSize

            """
                Файловая информация:
                
                Основной файл: ${databasePath.absolutePath}
                • Размер: ${String.format("%.2f", databaseSize / 1024.0)} KB
                • Существует: ${databasePath.exists()}
                
                WAL файл: ${walPath.absolutePath}
                • Размер: ${String.format("%.2f", walSize / 1024.0)} KB
                • Существует: ${walPath.exists()}
                
                SHM файл: ${shmPath.absolutePath}
                • Размер: ${String.format("%.2f", shmSize / 1024.0)} KB
                • Существует: ${shmPath.exists()}
                
                Общий размер: ${String.format("%.2f", totalSize / 1024.0)} KB
            """.trimIndent()
        } catch (e: Exception) {
            "Ошибка получения файловой информации: ${e.message}"
        }
    }

    // Обновленные методы для DatabaseViewerActivity
    fun getDatabaseSchema(context: Context): String {
        return try {
            val databasePath = context.getDatabasePath("app_database")
            if (!databasePath.exists()) {
                return "Файл базы данных не найден"
            }

            val db = SQLiteDatabase.openDatabase(
                databasePath.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            val schema = StringBuilder()
            val cursor = db.rawQuery(
                "SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                null
            )

            if (cursor.moveToFirst()) {
                do {
                    val tableName = cursor.getString(0)
                    val tableSql = cursor.getString(1)
                    schema.append("$tableName:\n$tableSql\n\n")

                    // Получаем информацию о количестве строк
                    val countCursor = db.rawQuery("SELECT COUNT(*) FROM $tableName", null)
                    if (countCursor.moveToFirst()) {
                        val rowCount = countCursor.getInt(0)
                        schema.append("Количество записей: $rowCount\n\n")
                    }
                    countCursor.close()
                } while (cursor.moveToNext())
            } else {
                schema.append("Таблицы не найдены")
            }

            cursor.close()
            db.close()
            schema.toString()
        } catch (e: Exception) {
            "Ошибка получения схемы: ${e.message}"
        }
    }

    fun getTableData(context: Context, tableName: String): List<Map<String, Any>> {
        val data = mutableListOf<Map<String, Any>>()

        try {
            val databasePath = context.getDatabasePath("app_database")
            if (!databasePath.exists()) {
                return data
            }

            val db = SQLiteDatabase.openDatabase(
                databasePath.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            val cursor: Cursor = db.rawQuery("SELECT * FROM $tableName LIMIT 100", null)

            if (cursor.moveToFirst()) {
                val columnNames = cursor.columnNames

                do {
                    val row = mutableMapOf<String, Any>()
                    for (column in columnNames) {
                        val columnIndex = cursor.getColumnIndex(column)
                        if (columnIndex >= 0) {
                            when (cursor.getType(columnIndex)) {
                                Cursor.FIELD_TYPE_INTEGER -> row[column] =
                                    cursor.getLong(columnIndex)

                                Cursor.FIELD_TYPE_FLOAT -> row[column] =
                                    cursor.getDouble(columnIndex)

                                Cursor.FIELD_TYPE_STRING -> row[column] =
                                    cursor.getString(columnIndex)

                                Cursor.FIELD_TYPE_BLOB -> row[column] = "BLOB"
                                else -> row[column] = "NULL"
                            }
                        }
                    }
                    data.add(row)
                } while (cursor.moveToNext())
            }

            cursor.close()
            db.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading table data: ${e.message}", e)
        }

        return data
    }
}