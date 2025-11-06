package com.citrus.blsc.ui.map

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*


class OfflineMapManager(
    private val context: Context
) {

    companion object {
        private const val TAG = "OfflineMapManager"
        private const val TILE_SERVER = "https://tile.openstreetmap.org"
        private const val USER_AGENT = "OSMOfflineMapApp/1.0"
    }

    data class BoundingBox(
        val north: Double,
        val south: Double,
        val east: Double,
        val west: Double
    ) {
        override fun toString(): String {
            return "BoundingBox(north=$north, south=$south, east=$east, west=$west)"
        }
    }


    interface DownloadCallback {

        fun onProgress(progress: Int, message: String)

        fun onComplete(successCount: Int, totalCount: Int)

        fun onError(error: String)
    }

    fun downloadMapArea(
        boundingBox: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        callback: DownloadCallback
    ) {
        Log.d(TAG, "Начинаем загрузку карты...")
        Log.d(TAG, "Область: $boundingBox")
        Log.d(TAG, "Уровни масштабирования: $minZoom-$maxZoom")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Проверяем валидность bounding box
                if (!isValidBoundingBox(boundingBox)) {
                    callback.onError("Некорректная географическая область")
                    return@launch
                }

                // Проверяем валидность уровней масштабирования
                if (minZoom < 0 || maxZoom > 18 || minZoom > maxZoom) {
                    callback.onError("Некорректные уровни масштабирования: $minZoom-$maxZoom")
                    return@launch
                }

                // Рассчитываем общее количество тайлов
                val totalTiles = calculateTotalTiles(boundingBox, minZoom, maxZoom)
                Log.d(TAG, "Всего тайлов для загрузки: $totalTiles")

                if (totalTiles == 0) {
                    callback.onError("Нет тайлов для загрузки в указанной области")
                    return@launch
                }

                if (totalTiles > 10000) {
                    Log.w(TAG, "Большое количество тайлов: $totalTiles. Загрузка может занять много времени.")
                }

                callback.onProgress(0, "Подготовка к загрузке...")

                var downloadedTiles = 0
                var failedTiles = 0

                // Загружаем тайлы для каждого уровня масштабирования
                for (zoom in minZoom..maxZoom) {
                    val tileRange = calculateTileRange(boundingBox, zoom)
                    Log.d(TAG, "Zoom $zoom: x=${tileRange.xMin}-${tileRange.xMax}, y=${tileRange.yMin}-${tileRange.yMax}")

                    val tilesInZoom = (tileRange.xMax - tileRange.xMin + 1) * (tileRange.yMax - tileRange.yMin + 1)
                    Log.d(TAG, "Тайлов на уровне $zoom: $tilesInZoom")

                    callback.onProgress(
                        (downloadedTiles * 100 / totalTiles).coerceIn(0, 100),
                        "Начинаем загрузку уровня $zoom ($tilesInZoom тайлов)"
                    )

                    // Загружаем все тайлы в диапазоне
                    for (x in tileRange.xMin..tileRange.xMax) {
                        for (y in tileRange.yMin..tileRange.yMax) {
                            val success = downloadSingleTile(x, y, zoom)

                            if (success) {
                                downloadedTiles++
                                // Логируем каждые 10 успешных загрузок
                                if (downloadedTiles % 10 == 0) {
                                    Log.d(TAG, "Успешно загружено тайлов: $downloadedTiles")
                                }
                            } else {
                                failedTiles++
                                // Логируем каждые 10 ошибок
                                if (failedTiles % 10 == 0) {
                                    Log.w(TAG, "Ошибок загрузки: $failedTiles")
                                }
                            }

                            // Обновляем прогресс
                            val currentTotal = downloadedTiles + failedTiles
                            val progress = if (totalTiles > 0) {
                                (currentTotal * 100 / totalTiles).coerceIn(0, 100)
                            } else {
                                0
                            }

                            val message = when {
                                progress < 100 -> "Уровень $zoom: $currentTotal/$totalTiles\nУспешно: $downloadedTiles, Ошибок: $failedTiles"
                                else -> "Завершение..."
                            }

                            callback.onProgress(progress, message)

                            // Задержка чтобы не перегружать сервер
                            delay(30)
                        }
                    }

                    Log.d(TAG, "Уровень $zoom завершен. Успешно: $downloadedTiles, Ошибок: $failedTiles")
                }

                // Загрузка завершена
                val finalMessage = "Загрузка завершена! Успешно: $downloadedTiles, Ошибок: $failedTiles"
                Log.d(TAG, finalMessage)
                callback.onComplete(downloadedTiles, totalTiles)

            } catch (e: Exception) {
                Log.e(TAG, "Критическая ошибка загрузки карты", e)
                callback.onError("Критическая ошибка: ${e.message ?: "Неизвестная ошибка"}")
            }
        }
    }

    private suspend fun downloadSingleTile(x: Int, y: Int, zoom: Int): Boolean {
        return try {
            val tileUrl = "$TILE_SERVER/$zoom/$x/$y.png"
            Log.v(TAG, "Загрузка тайла: $tileUrl")

            val url = URL(tileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "image/png")

            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    connection.inputStream.use { inputStream ->
                        val tileData = inputStream.readBytes()
                        if (tileData.isNotEmpty()) {
                            saveTileToInternalStorage(x, y, zoom, tileData)
                        } else {
                            Log.w(TAG, "Пустой ответ для тайла $zoom/$x/$y")
                            false
                        }
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    Log.w(TAG, "Тайл не найден: $zoom/$x/$y")
                    false
                }
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    Log.w(TAG, "Доступ запрещен к тайлу: $zoom/$x/$y")
                    false
                }
                else -> {
                    Log.w(TAG, "HTTP ошибка ${connection.responseCode} для тайла $zoom/$x/$y")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки тайла $zoom/$x/$y: ${e.message}", e)
            false
        }
    }

    private fun saveTileToInternalStorage(x: Int, y: Int, zoom: Int, data: ByteArray): Boolean {
        return try {
            // Используем внутреннее хранилище приложения (не требует разрешений)
            val tilesDir = File(context.filesDir, "offline_tiles/$zoom/$x")

            if (!tilesDir.exists()) {
                val created = tilesDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Не удалось создать директорию: ${tilesDir.absolutePath}")
                    return false
                }
            }

            val tileFile = File(tilesDir, "$y.png")
            FileOutputStream(tileFile).use { outputStream ->
                outputStream.write(data)
            }

            Log.v(TAG, "Тайл сохранен: ${tileFile.absolutePath}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения тайла $zoom/$x/$y: ${e.message}", e)
            false
        }
    }

    private fun isValidBoundingBox(bbox: BoundingBox): Boolean {
        return when {
            bbox.north <= bbox.south -> {
                Log.e(TAG, "Северная граница должна быть больше южной")
                false
            }
            bbox.east <= bbox.west -> {
                Log.e(TAG, "Восточная граница должна быть больше западной")
                false
            }
            bbox.north > 90.0 || bbox.north < -90.0 -> {
                Log.e(TAG, "Северная граница вне диапазона (-90 до 90)")
                false
            }
            bbox.south > 90.0 || bbox.south < -90.0 -> {
                Log.e(TAG, "Южная граница вне диапазона (-90 до 90)")
                false
            }
            bbox.east > 180.0 || bbox.east < -180.0 -> {
                Log.e(TAG, "Восточная граница вне диапазона (-180 до 180)")
                false
            }
            bbox.west > 180.0 || bbox.west < -180.0 -> {
                Log.e(TAG, "Западная граница вне диапазона (-180 до 180)")
                false
            }
            else -> true
        }
    }


    private fun calculateTotalTiles(bbox: BoundingBox, minZoom: Int, maxZoom: Int): Int {
        var total = 0
        for (zoom in minZoom..maxZoom) {
            val tileRange = calculateTileRange(bbox, zoom)
            val tilesInZoom = (tileRange.xMax - tileRange.xMin + 1) * (tileRange.yMax - tileRange.yMin + 1)
            total += tilesInZoom
        }
        return total
    }


    fun calculateTileRange(bbox: BoundingBox, zoom: Int): TileRange {
        val xMin = lonToTileX(bbox.west, zoom)
        val xMax = lonToTileX(bbox.east, zoom)
        val yMin = latToTileY(bbox.north, zoom)
        val yMax = latToTileY(bbox.south, zoom)

        val maxTile = (1 shl zoom) - 1

        return TileRange(
            xMin = xMin.coerceIn(0, maxTile),
            xMax = xMax.coerceIn(0, maxTile),
            yMin = yMin.coerceIn(0, maxTile),
            yMax = yMax.coerceIn(0, maxTile)
        )
    }


    private fun lonToTileX(lon: Double, zoom: Int): Int {
        return ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
        return ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
    }

    fun deleteOfflineMaps() {
        try {
            val tilesDir = File(context.filesDir, "offline_tiles")
            if (tilesDir.exists() && tilesDir.isDirectory) {
                val deleted = deleteRecursive(tilesDir)
                if (deleted) {
                    Log.d(TAG, "Оффлайн карты успешно удалены")
                } else {
                    Log.w(TAG, "Не все файлы оффлайн карт были удалены")
                }
            } else {
                Log.d(TAG, "Директория оффлайн карт не существует")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка удаления оффлайн карт: ${e.message}", e)
        }
    }

    fun hasOfflineMaps(): Boolean {
        return try {
            val tilesDir = File(context.filesDir, "offline_tiles")
            tilesDir.exists() && tilesDir.isDirectory && tilesDir.listFiles()?.isNotEmpty() == true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка проверки оффлайн карт", e)
            false
        }
    }

    fun getAvailableZoomLevels(): List<Int> {
        return try {
            val tilesDir = File(context.filesDir, "offline_tiles")
            if (tilesDir.exists() && tilesDir.isDirectory) {
                tilesDir.listFiles()
                    ?.filter { it.isDirectory && it.name.matches(Regex("\\d+")) }
                    ?.map { it.name.toInt() }
                    ?.sorted()
                    ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun deleteRecursive(file: File): Boolean {
        return try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    deleteRecursive(child)
                }
            }
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка удаления файла ${file.absolutePath}", e)
            false
        }
    }

    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        try {
            if (directory.isDirectory) {
                directory.listFiles()?.forEach { file ->
                    size += if (file.isDirectory) {
                        calculateDirectorySize(file)
                    } else {
                        file.length()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка расчета размера директории", e)
        }
        return size
    }

    data class TileRange(
        val xMin: Int,
        val xMax: Int,
        val yMin: Int,
        val yMax: Int
    ) {
        override fun toString(): String {
            return "TileRange(x=$xMin..$xMax, y=$yMin..$yMax)"
        }
    }
}