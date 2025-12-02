package com.citrus.blsc.ui.map

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.citrus.blsc.R
import com.google.android.material.textfield.TextInputEditText
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.text.DecimalFormat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class OfflineMapActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OfflineMapActivity"
        private const val PERMISSION_REQUEST_CODE = 1001

        private const val MOSCOW_LAT = 55.7558
        private const val MOSCOW_LON = 37.6173
        private const val DEFAULT_ZOOM = 10.0
        private const val DEFAULT_NORTH = 55.9111
        private const val DEFAULT_SOUTH = 55.5690
        private const val DEFAULT_EAST = 37.8553
        private const val DEFAULT_WEST = 37.3686
        private const val DEFAULT_MIN_ZOOM = 10
        private const val DEFAULT_MAX_ZOOM = 16
        // Bounding box Москвы
        private val MOSCOW_BOUNDING_BOX = OfflineMapManager.BoundingBox(
            north = 55.9111,
            south = 55.5690,
            east = 37.8553,
            west = 37.3686
        )
    }


    private var mapView: MapView? = null
    private var btnDownloadMap: Button? = null
    private var btnDeleteMap: Button? = null
    private var btnViewOfflineMap: Button? = null
    private var btnTestCoordinates: Button? = null
    private var btnZoomInfo: Button? = null
    private var layoutProgress: LinearLayout? = null
    private var progressBar: ProgressBar? = null
    private var tvProgress: TextView? = null

    private var editNorth: TextInputEditText? = null
    private var editSouth: TextInputEditText? = null
    private var editEast: TextInputEditText? = null
    private var editWest: TextInputEditText? = null
    private var editMinZoom: TextInputEditText? = null
    private var editMaxZoom: TextInputEditText? = null
    // Оверлеи карты
    private var locationOverlay: MyLocationNewOverlay? = null
    private var compassOverlay: CompassOverlay? = null
    private var scaleBarOverlay: ScaleBarOverlay? = null
    private var boundingBoxOverlay: Polygon? = null

    // Менеджер оффлайн карт
    private var offlineMapManager: OfflineMapManager? = null

    private val requiredPermissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_cards)

        Log.d(TAG, "=== OfflineMapActivity started ===")

        try {
            // Инициализация OSMDroid конфигурации
            initializeOsmdroid()

            // Инициализация UI элементов
            initializeUI()

            // Настройка карты
            setupMap()

            // Инициализация менеджера оффлайн карт
            offlineMapManager = OfflineMapManager(this)

            // Проверка существующих оффлайн карт
            checkExistingOfflineMaps()

            // Проверка разрешений
            checkPermissions()

            Log.d(TAG, "=== OfflineMapActivity initialized successfully ===")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в onCreate", e)
            Toast.makeText(this, "Ошибка инициализации: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeOsmdroid() {
        try {
            Configuration.getInstance().userAgentValue = packageName

            val osmdroidBasePath = getExternalFilesDir(null)?.let {
                File(it, "osmdroid")
            }
            osmdroidBasePath?.let {
                Configuration.getInstance().osmdroidBasePath = it

                val tileCache = File(it, "tile-cache")
                Configuration.getInstance().osmdroidTileCache = tileCache

                if (!it.exists()) it.mkdirs()
                if (!tileCache.exists()) tileCache.mkdirs()

                Log.d(TAG, "OSMDroid инициализирован. Путь: $it")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации OSMDroid", e)
            throw e
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initializeUI() {
        try {
            // Основные элементы
            mapView = findViewById(R.id.mapView)
            btnDownloadMap = findViewById(R.id.btnDownloadMap)
            btnDeleteMap = findViewById(R.id.btnDeleteMap)
            btnViewOfflineMap = findViewById(R.id.btnViewOfflineMap)
            btnZoomInfo = findViewById(R.id.btnZoomInfo)
            layoutProgress = findViewById(R.id.layoutProgress)
            progressBar = findViewById(R.id.progressBar)
            tvProgress = findViewById(R.id.tvProgress)

            // Поля ввода
            editNorth = findViewById(R.id.editNorth)
            editSouth = findViewById(R.id.editSouth)
            editEast = findViewById(R.id.editEast)
            editWest = findViewById(R.id.editWest)
            editMinZoom = findViewById(R.id.editMinZoom)
            editMaxZoom = findViewById(R.id.editMaxZoom)

            // Настройка обработчиков кликов
            btnDownloadMap?.setOnClickListener {
                Log.d(TAG, "Кнопка download нажата!")
                downloadMapWithSettings()
            }

            btnDeleteMap?.setOnClickListener {
                Log.d(TAG, "Кнопка delete нажата!")
                deleteOfflineMap()
            }

            btnViewOfflineMap?.setOnClickListener {
                Log.d(TAG, "Кнопка view offline map нажата!")
                startOfflineMapViewer()
            }

            btnTestCoordinates?.setOnClickListener {
                Log.d(TAG, "Кнопка test coordinates нажата!")
                showBoundingBoxOnMap()
            }

            btnZoomInfo?.setOnClickListener {
                Log.d(TAG, "Кнопка zoom info нажата!")
                showZoomInfoDialog()
            }

            // Скрываем прогресс по умолчанию
            layoutProgress?.visibility = LinearLayout.GONE

            Log.d(TAG, "UI инициализирован успешно")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации UI", e)
            Toast.makeText(this, "Ошибка инициализации интерфейса", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupMap() {
        try {
            val mapView = mapView ?: run {
                Log.e(TAG, "MapView не инициализирован")
                return
            }

            // Устанавливаем источник тайлов
            mapView.setTileSource(TileSourceFactory.MAPNIK)

            // Настройка контроллера карты
            val mapController = mapView.controller
            mapController.setZoom(DEFAULT_ZOOM)
            mapController.setCenter(GeoPoint(MOSCOW_LAT, MOSCOW_LON))

            // Включаем мультитач зум
            mapView.setMultiTouchControls(true)
            mapView.minZoomLevel = 5.0
            mapView.maxZoomLevel = 18.0

            // Компас
            compassOverlay = CompassOverlay(
                this,
                InternalCompassOrientationProvider(this),
                mapView
            )
            compassOverlay?.enableCompass()
            mapView.overlays.add(compassOverlay)

            // Шкала масштаба
            scaleBarOverlay = ScaleBarOverlay(mapView)
            scaleBarOverlay?.setCentred(true)
            scaleBarOverlay?.setScaleBarOffset(
                resources.displayMetrics.widthPixels / 2,
                20
            )
            mapView.overlays.add(scaleBarOverlay)

            // Слой текущего местоположения
            if (hasLocationPermission()) {
                enableLocationOverlay()
            }

            // Добавляем маркер центра Москвы
            addMoscowMarker()

            Log.d(TAG, "Карта успешно настроена")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка настройки карты", e)
            Toast.makeText(this, "Ошибка настройки карты: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addMoscowMarker() {
        try {
            val mapView = mapView ?: return

            val moscowMarker = Marker(mapView)
            moscowMarker.position = GeoPoint(MOSCOW_LAT, MOSCOW_LON)
            moscowMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            moscowMarker.title = "Центр Москвы"
            moscowMarker.snippet = "Широта: $MOSCOW_LAT, Долгота: $MOSCOW_LON"
            moscowMarker.setOnMarkerClickListener { marker, mapView ->
                Toast.makeText(this, "Центр Москвы", Toast.LENGTH_SHORT).show()
                true
            }
            mapView.overlays.add(moscowMarker)

            Log.d(TAG, "Маркер Москвы добавлен")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка добавления маркера", e)
        }
    }

    private fun enableLocationOverlay() {
        try {
            val mapView = mapView ?: return

            locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
            locationOverlay?.enableMyLocation()
            locationOverlay?.enableFollowLocation()
            mapView.overlays.add(locationOverlay)

            Log.d(TAG, "Слой местоположения включен")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка включения слоя местоположения", e)
        }
    }

    private fun showBoundingBoxOnMap() {
        try {
            val boundingBox = getBoundingBoxFromInput()
            val mapView = mapView ?: return

            // Удаляем предыдущий bounding box
            boundingBoxOverlay?.let {
                mapView.overlays.remove(it)
            }

            // Создаем новый bounding box overlay
            boundingBoxOverlay = Polygon().apply {
                points = listOf(
                    GeoPoint(boundingBox.north, boundingBox.west),
                    GeoPoint(boundingBox.north, boundingBox.east),
                    GeoPoint(boundingBox.south, boundingBox.east),
                    GeoPoint(boundingBox.south, boundingBox.west),
                    GeoPoint(boundingBox.north, boundingBox.west) // Замыкаем полигон
                )
                fillColor = 0x22FF0000 // Полупрозрачный красный
                strokeColor = 0x22FF0000 // Красный
                strokeWidth = 3.0f
                title = "Выбранная область"
            }

            mapView.overlays.add(boundingBoxOverlay)

            // Центрируем карту на bounding box
            val osmdroidBoundingBox = BoundingBox(
                boundingBox.north,
                boundingBox.east,
                boundingBox.south,
                boundingBox.west
            )

            mapView.zoomToBoundingBox(osmdroidBoundingBox, true, 50)
            mapView.invalidate()

            // Показываем информацию о площади
            val areaKm2 = calculateAreaKm2(boundingBox)
            val formatter = DecimalFormat("#.##")
            Toast.makeText(
                this,
                "Область показана на карте. Примерная площадь: ${formatter.format(areaKm2)} км²",
                Toast.LENGTH_LONG
            ).show()

            Log.d(TAG, "Bounding box показан на карте: $boundingBox")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка показа bounding box", e)
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun calculateAreaKm2(bbox: OfflineMapManager.BoundingBox): Double {
        // Простой расчет площади в квадратных километрах
        val latDiff = bbox.north - bbox.south
        val lonDiff = bbox.east - bbox.west

        // 1 градус широты ≈ 111 км, 1 градус долготы ≈ 111 км * cos(широты)
        val latKm = latDiff * 111.0
        val avgLat = (bbox.north + bbox.south) / 2.0
        val lonKm = lonDiff * 111.0 * Math.cos(Math.toRadians(avgLat))

        return Math.abs(latKm * lonKm)
    }

    private fun showZoomInfoDialog() {
        val message = """
            Уровни масштабирования карты:
            
            0-5: Континенты, страны
            6-9: Области, крупные города
            10-12: Города, районы
            13-15: Улицы, здания
            16-18: Детальные планы, внутренние помещения
            
            Рекомендации:
            • Для навигации по городу: 10-12
            • Для детального просмотра улиц: 13-15
            • Для максимальной детализации: 16-18
            
            Внимание: Чем выше уровни zoom, тем больше тайлов нужно загрузить и больше места потребуется!
            
            Примерное количество тайлов для Москвы:
            • Zoom 10-12: 50-200 тайлов
            • Zoom 10-15: 500-2000 тайлов
            • Zoom 10-18: 5000-20000 тайлов
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Уровни масштабирования (zoom)")
            .setMessage(message)
            .setPositiveButton("Понятно") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun downloadMapWithSettings() {
        Log.d(TAG, "=== downloadMapWithSettings called ===")

        val manager = offlineMapManager ?: run {
            Log.e(TAG, "Менеджер карт null!")
            Toast.makeText(this, "Менеджер карт не инициализирован", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Менеджер карт доступен")

        // Проверяем разрешения
        if (!checkPermissions()) {
            Log.d(TAG, "Ждем разрешения для местоположения...")
            return
        }

        // Получаем настройки из полей ввода
        try {
            val boundingBox = getBoundingBoxFromInput()
            val minZoom = getMinZoomFromInput()
            val maxZoom = getMaxZoomFromInput()

            // Проверяем корректность введенных данных
            if (!isValidInput(boundingBox, minZoom, maxZoom)) {
                return
            }

            // Показываем подтверждение
            showDownloadConfirmation(boundingBox, minZoom, maxZoom) {
                startMapDownload(boundingBox, minZoom, maxZoom)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения настроек: ${e.message}")
            Toast.makeText(this, "Ошибка в настройках: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getBoundingBoxFromInput(): OfflineMapManager.BoundingBox {
        val north = editNorth?.text.toString().toDoubleOrNull() ?: DEFAULT_NORTH
        val south = editSouth?.text.toString().toDoubleOrNull() ?: DEFAULT_SOUTH
        val east = editEast?.text.toString().toDoubleOrNull() ?: DEFAULT_EAST
        val west = editWest?.text.toString().toDoubleOrNull() ?: DEFAULT_WEST

        return OfflineMapManager.BoundingBox(north, south, east, west)
    }

    private fun getMinZoomFromInput(): Int {
        return editMinZoom?.text.toString().toIntOrNull() ?: DEFAULT_MIN_ZOOM
    }

    private fun getMaxZoomFromInput(): Int {
        return editMaxZoom?.text.toString().toIntOrNull() ?: DEFAULT_MAX_ZOOM
    }

    private fun isValidInput(
        boundingBox: OfflineMapManager.BoundingBox,
        minZoom: Int,
        maxZoom: Int
    ): Boolean {
        // Проверка bounding box
        if (boundingBox.north <= boundingBox.south) {
            Toast.makeText(this, "Северная граница должна быть больше южной", Toast.LENGTH_LONG).show()
            return false
        }

        if (boundingBox.east <= boundingBox.west) {
            Toast.makeText(this, "Восточная граница должна быть больше западной", Toast.LENGTH_LONG).show()
            return false
        }

        // Проверка уровней zoom
        if (minZoom < 0 || minZoom > 18) {
            Toast.makeText(this, "Минимальный zoom должен быть от 0 до 18", Toast.LENGTH_LONG).show()
            return false
        }

        if (maxZoom < 0 || maxZoom > 18) {
            Toast.makeText(this, "Максимальный zoom должен быть от 0 до 18", Toast.LENGTH_LONG).show()
            return false
        }

        if (minZoom > maxZoom) {
            Toast.makeText(this, "Минимальный zoom не может быть больше максимального", Toast.LENGTH_LONG).show()
            return false
        }

        // Проверка разницы уровней zoom
        val zoomDiff = maxZoom - minZoom
        if (zoomDiff > 6) {
            val confirmationFuture = CompletableFuture<Boolean>()

            AlertDialog.Builder(this)
                .setTitle("Большой диапазон zoom")
                .setMessage("Выбранный диапазон zoom ($minZoom-$maxZoom) может потребовать загрузки очень большого количества тайлов (десятки тысяч). Это займет много времени и места. Вы уверены?")
                .setPositiveButton("Продолжить") { dialog, _ ->
                    dialog.dismiss()
                    confirmationFuture.complete(true)
                }
                .setNegativeButton("Отмена") { dialog, _ ->
                    dialog.dismiss()
                    confirmationFuture.complete(false)
                }
                .setOnCancelListener {
                    confirmationFuture.complete(false)
                }
                .show()

            // Блокируем поток до получения ответа
            return try {
                // Таймаут 30 секунд на случай если пользователь не ответит
                confirmationFuture.get(30, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка ожидания подтверждения: ${e.message}")
                false
            }
        }

        return true
    }

    private fun showDownloadConfirmation(
        boundingBox: OfflineMapManager.BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        onConfirm: () -> Unit
    ) {
        val areaKm2 = calculateAreaKm2(boundingBox)
        val formatter = DecimalFormat("#.##")

        val message = """
            Параметры загрузки:
            
            Область:
            • Север: ${boundingBox.north}
            • Юг: ${boundingBox.south}
            • Восток: ${boundingBox.east}
            • Запад: ${boundingBox.west}
            • Примерная площадь: ${formatter.format(areaKm2)} км²
            
            Уровни масштабирования:
            • Минимальный: $minZoom
            • Максимальный: $maxZoom
            • Всего уровней: ${maxZoom - minZoom + 1}
            
            Внимание: Загрузка может занять несколько минут и потребовать места на устройстве.
            Продолжить?
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Подтверждение загрузки")
            .setMessage(message)
            .setPositiveButton("Загрузить") { dialog, _ ->
                dialog.dismiss()
                onConfirm()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }


    private fun startMapDownload(
        boundingBox: OfflineMapManager.BoundingBox,
        minZoom: Int,
        maxZoom: Int
    ) {
        Log.d(TAG, "=== startMapDownload ===")

        val manager = offlineMapManager ?: return

        // Показываем прогресс
        layoutProgress?.visibility = LinearLayout.VISIBLE
        btnDownloadMap?.isEnabled = false
        btnViewOfflineMap?.isEnabled = false
        progressBar?.progress = 0
        tvProgress?.text = "Подготовка к загрузке..."

        Log.d(TAG, "Запускаем загрузку с параметрами:")
        Log.d(TAG, "Bounding box: $boundingBox")
        Log.d(TAG, "Zoom levels: $minZoom-$maxZoom")

        manager.downloadMapArea(
            boundingBox = boundingBox,
            minZoom = minZoom,
            maxZoom = maxZoom,
            callback = object : OfflineMapManager.DownloadCallback {
                override fun onProgress(progress: Int, message: String) {
                    Log.d(TAG, "Прогресс: $progress% - $message")
                    runOnUiThread {
                        progressBar?.progress = progress
                        tvProgress?.text = message
                    }
                }

                override fun onComplete(successCount: Int, totalCount: Int) {
                    Log.d(TAG, "Загрузка завершена! Успешно: $successCount/$totalCount")
                    runOnUiThread {
                        progressBar?.progress = 100
                        tvProgress?.text = "Загрузка завершена! Успешно: $successCount/$totalCount"
                        btnDownloadMap?.isEnabled = true
                        btnViewOfflineMap?.isEnabled = true
                        btnDownloadMap?.text = "Карта загружена"

                        // Скрываем прогресс через 3 секунды
                        Handler(Looper.getMainLooper()).postDelayed({
                            layoutProgress?.visibility = LinearLayout.GONE
                        }, 3000)

                        Toast.makeText(
                            this@OfflineMapActivity,
                            "Оффлайн карта успешно загружена!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Ошибка загрузки: $error")
                    runOnUiThread {
                        tvProgress?.text = "Ошибка: $error"
                        btnDownloadMap?.isEnabled = true
                        btnViewOfflineMap?.isEnabled = true
                        layoutProgress?.visibility = LinearLayout.GONE
                        Toast.makeText(
                            this@OfflineMapActivity,
                            "Ошибка загрузки: $error", Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )

        Log.d(TAG, "Метод downloadMapArea вызван успешно")
    }


    private fun deleteOfflineMap() {
        try {
            offlineMapManager?.deleteOfflineMaps()
            btnDownloadMap?.text = "Скачать карту"
            btnDownloadMap?.isEnabled = true
            btnViewOfflineMap?.isEnabled = false
            Toast.makeText(this, "Оффлайн карты удалены", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Оффлайн карты удалены")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка удаления карт", e)
            Toast.makeText(this, "Ошибка удаления карт", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startOfflineMapViewer() {
        val manager = offlineMapManager ?: return

        if (manager.hasOfflineMaps()) {
            val intent = Intent(this, OfflineMapViewerActivity::class.java)
            startActivity(intent)
            Log.d(TAG, "Запускаем OfflineMapViewerActivity")
        } else {
            Toast.makeText(this, "Сначала загрузите оффлайн карту", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "Попытка запуска просмотра без загруженных карт")
        }
    }

    private fun checkExistingOfflineMaps() {
        val hasMaps = offlineMapManager?.hasOfflineMaps() == true
        Log.d(TAG, "Проверка оффлайн карт: $hasMaps")

        runOnUiThread {
            if (hasMaps) {
                btnDownloadMap?.text = "Карта уже загружена"
                btnDownloadMap?.isEnabled = false
                btnViewOfflineMap?.isEnabled = true
            } else {
                btnViewOfflineMap?.isEnabled = false
            }
        }
    }


    private fun checkPermissions(): Boolean {
        Log.d(TAG, "Проверяем разрешения...")

        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        Log.d(TAG, "Недостающие разрешения: $missingPermissions")

        return if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "Запрашиваем разрешения: $missingPermissions")
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
            false
        } else {
            Log.d(TAG, "Все разрешения уже предоставлены")
            true
        }
    }


    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }


    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
        Log.d(TAG, "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Освобождаем ресурсы
        locationOverlay?.disableMyLocation()
        locationOverlay?.disableFollowLocation()
        compassOverlay?.disableCompass()
        Log.d(TAG, "onDestroy")
    }
}