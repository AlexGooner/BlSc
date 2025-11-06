package com.citrus.blsc.ui.map

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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.citrus.blsc.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

class OfflineMapActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OfflineMapActivity"
        private const val PERMISSION_REQUEST_CODE = 1001

        private const val MOSCOW_LAT = 55.7558
        private const val MOSCOW_LON = 37.6173
        private const val DEFAULT_ZOOM = 10.0

        // Bounding box Москвы
        private val MOSCOW_BOUNDING_BOX = OfflineMapManager.BoundingBox(
            north = 55.9111,
            south = 55.5690,
            east = 37.8553,
            west = 37.3686
        )
    }

    // UI элементы
    private var mapView: MapView? = null
    private var btnDownloadMap: Button? = null
    private var btnDeleteMap: Button? = null
    private var btnViewOfflineMap: Button? = null
    private var layoutProgress: LinearLayout? = null
    private var progressBar: ProgressBar? = null
    private var tvProgress: TextView? = null

    // Оверлеи карты
    private var locationOverlay: MyLocationNewOverlay? = null
    private var compassOverlay: CompassOverlay? = null
    private var scaleBarOverlay: ScaleBarOverlay? = null

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
            // Инициализация OSMDroid конфигурации ДО всего остального
            initializeOsmdroid()

            // Инициализация UI элементов
            initializeUI()

            // Настройка карты
            setupMap()

            // Инициализация менеджера оффлайн карт
            offlineMapManager = OfflineMapManager(this)

            // Проверка существующих оффлайн карт
            checkExistingOfflineMaps()

            // Проверка разрешений (только для местоположения)
            checkPermissions()

            Log.d(TAG, "=== OfflineMapActivity initialized successfully ===")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в onCreate", e)
            Toast.makeText(this, "Ошибка инициализации: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeOsmdroid() {
        try {
            // Установка пользовательского агента (обязательно)
            Configuration.getInstance().userAgentValue = packageName

            // Настройка кэш тайлов
            val osmdroidBasePath = getExternalFilesDir(null)?.let {
                File(it, "osmdroid")
            }
            osmdroidBasePath?.let {
                Configuration.getInstance().osmdroidBasePath = it

                val tileCache = File(it, "tile-cache")
                Configuration.getInstance().osmdroidTileCache = tileCache

                // Создание директории, если не существуют
                if (!it.exists()) it.mkdirs()
                if (!tileCache.exists()) tileCache.mkdirs()

                Log.d(TAG, "OSMDroid инициализирован. Путь: $it")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации OSMDroid", e)
            throw e
        }
    }

    private fun initializeUI() {
        try {
            mapView = findViewById(R.id.mapView)
            btnDownloadMap = findViewById(R.id.btnDownloadMap)
            btnDeleteMap = findViewById(R.id.btnDeleteMap)
            btnViewOfflineMap = findViewById(R.id.btnViewOfflineMap)
            layoutProgress = findViewById(R.id.layoutProgress)
            progressBar = findViewById(R.id.progressBar)
            tvProgress = findViewById(R.id.tvProgress)

            // Проверяем что все элементы найдены
            if (btnDownloadMap == null) {
                Log.e(TAG, "btnDownloadMap не найден в layout!")
                Toast.makeText(this, "Ошибка: кнопка не найдена", Toast.LENGTH_LONG).show()
                return
            }

            Log.d(TAG, "Все UI элементы найдены, настраиваем клики...")

            // Настройка обработчиков кликов
            btnDownloadMap?.setOnClickListener {
                Log.d(TAG, "Кнопка download нажата!")
                downloadMoscowMap()
            }

            btnDeleteMap?.setOnClickListener {
                Log.d(TAG, "Кнопка delete нажата!")
                deleteOfflineMap()
            }

            btnViewOfflineMap?.setOnClickListener {
                Log.d(TAG, "Кнопка view offline map нажата!")
                startOfflineMapViewer()
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

            compassOverlay = CompassOverlay(
                this,
                InternalCompassOrientationProvider(this),
                mapView
            )
            compassOverlay?.enableCompass()
            mapView.overlays.add(compassOverlay)

            // Добавляем шкалу масштаба
            scaleBarOverlay = ScaleBarOverlay(mapView)
            scaleBarOverlay?.setCentred(true)
            scaleBarOverlay?.setScaleBarOffset(
                resources.displayMetrics.widthPixels / 2,
                20
            )
            mapView.overlays.add(scaleBarOverlay)

            // Добавляем слой текущего местоположения
            if (hasLocationPermission()) {
                enableLocationOverlay()
            }

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
            moscowMarker.title = "Москва"
            moscowMarker.snippet = "Столица России"
            moscowMarker.setOnMarkerClickListener { marker, mapView ->
                Toast.makeText(this, "Москва - столица России", Toast.LENGTH_SHORT).show()
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

    private fun downloadMoscowMap() {
        Log.d(TAG, "=== downloadMoscowMap called ===")

        val manager = offlineMapManager ?: run {
            Log.e(TAG, "Менеджер карт null!")
            Toast.makeText(this, "Менеджер карт не инициализирован", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Менеджер карт доступен")

        // Проверяем только необходимые разрешения (местоположение)
        if (!checkPermissions()) {
            Log.d(TAG, "Ждем разрешения для местоположения...")
            return
        }

        // Немедленно начинаем загрузку - хранилище больше не требует разрешений
        startMapDownload()
    }


    private fun startMapDownload() {
        Log.d(TAG, "=== startMapDownload ===")

        val manager = offlineMapManager ?: return

        // Показываем прогресс
        layoutProgress?.visibility = LinearLayout.VISIBLE
        btnDownloadMap?.isEnabled = false
        btnViewOfflineMap?.isEnabled = false
        progressBar?.progress = 0
        tvProgress?.text = "Подготовка к загрузке..."

        Log.d(TAG, "Запускаем загрузку с bounding box: $MOSCOW_BOUNDING_BOX")

        // ЗАГРУЖАЕМ БОЛЕЕ ВЫСОКИЕ УРОВНИ ZOOM ДЛЯ ЛУЧШЕЙ ДЕТАЛИЗАЦИИ
        manager.downloadMapArea(
            boundingBox = MOSCOW_BOUNDING_BOX,
            minZoom = 10,    // Обзорный вид
            maxZoom = 16,    // Детальный вид улиц (вместо 12)
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
                            "Оффлайн карта Москвы загружена с высокой детализацией!",
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
            btnDownloadMap?.text = "Скачать карту Москвы"
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


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d(TAG, "onRequestPermissionsResult: requestCode=$requestCode")
        Log.d(TAG, "Запрошенные разрешения: ${permissions.joinToString()}")
        Log.d(TAG, "Результаты: ${grantResults.joinToString()}")

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

                if (allGranted) {
                    Log.d(TAG, "Все разрешения предоставлены!")

                    // Включаем слой местоположения
                    enableLocationOverlay()

                    Toast.makeText(this, "Разрешения предоставлены!", Toast.LENGTH_SHORT).show()

                    // Если мы ждали разрешений для загрузки карты, начинаем загрузку
                    if (layoutProgress?.visibility == LinearLayout.VISIBLE) {
                        Log.d(TAG, "Разрешения получены, продолжаем загрузку карты")
                        startMapDownload()
                    }
                } else {
                    Log.w(TAG, "Не все разрешения предоставлены")

                    Toast.makeText(
                        this,
                        "Некоторые функции карты могут не работать без разрешения местоположения",
                        Toast.LENGTH_LONG
                    ).show()

                    // Все равно позволяем загружать карты, просто без функционала местоположения
                    if (layoutProgress?.visibility == LinearLayout.VISIBLE) {
                        Log.d(TAG, "Разрешения не получены, но продолжаем загрузку карты")
                        startMapDownload()
                    }
                }
            }
        }
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