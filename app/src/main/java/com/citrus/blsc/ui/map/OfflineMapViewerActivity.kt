package com.citrus.blsc.ui.map

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.citrus.blsc.R
import com.citrus.blsc.data.database.AppDatabase
import com.citrus.blsc.data.database.DeviceCoordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

class OfflineMapViewerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OfflineMapViewerActivity"

        // Координаты центра Москвы
        private const val MOSCOW_LAT = 55.7558
        private const val MOSCOW_LON = 37.6173
        private const val DEFAULT_ZOOM = 12.0
    }

    private var mapView: MapView? = null
    private lateinit var db: AppDatabase
    private var macAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_map_viewer)

        Log.d(TAG, "=== OfflineMapViewerActivity started ===")


        macAddress = intent.getStringExtra("mac")
        Log.d(TAG, "Received MAC: $macAddress")

        try {

            db = AppDatabase.getDatabase(this)
            initializeOsmdroid()
            initializeUI()
            loadAndDisplayCoordinates()

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации", e)
            Toast.makeText(this, "Ошибка загрузки карты: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeOsmdroid() {
        Configuration.getInstance().userAgentValue = packageName
        val osmdroidBasePath = File(getExternalFilesDir(null), "osmdroid")
        Configuration.getInstance().osmdroidBasePath = osmdroidBasePath
    }

    private fun initializeUI() {
        mapView = findViewById(R.id.mapView)
        mapView?.setTileSource(TileSourceFactory.MAPNIK)
        mapView?.setMultiTouchControls(true)

        setupZoomLimits()

        // Сначала центрируем на Москве (на случай если координат нет)
        centerMapOnMoscow()
    }

    private fun loadAndDisplayCoordinates() {
        if (macAddress.isNullOrEmpty()) {
            showNoCoordinatesMessage("MAC-адрес не указан")
            updateHeader("MAC-адрес не указан", 0)
            return
        }

        GlobalScope.launch(Dispatchers.Main) {
            try {
                val coordinates = withContext(Dispatchers.IO) {
                    db.deviceCoordinateDao().getCoordinateByMac(macAddress!!)
                }

                val count = withContext(Dispatchers.IO) {
                    db.deviceCoordinateDao().countCoordinatesByMac(macAddress!!)
                }

                Log.d(TAG, "Найдено координат: $count для MAC: $macAddress")

                if (coordinates.isNotEmpty()) {
                    addMarkersToMap(coordinates)
                    centerMapOnFirstCoordinate(coordinates)

                    updateHeader("Устройство: $macAddress", count)
                    Toast.makeText(
                        this@OfflineMapViewerActivity,
                        "Найдено записей: $count",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    showNoCoordinatesMessage("Координаты не найдены для MAC-адреса: $macAddress")
                    updateHeader("Устройство: $macAddress", 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки координат", e)
                showNoCoordinatesMessage("Ошибка загрузки координат")
                updateHeader("Ошибка загрузки", 0)
            }
        }
    }

    private fun addMarkersToMap(coordinates: List<DeviceCoordinate?>) {
        val mapView = mapView ?: return

        coordinates.forEachIndexed { index, coordinate ->
            coordinate?.let { coord ->
                val geoPoint = GeoPoint(coord.latitude, coord.longitude)
                val marker = Marker(mapView)

                marker.position = geoPoint
                marker.title = "Устройство: ${coord.macAddress}"
                marker.snippet =
                    "Время: ${coord.time}\nШирота: ${coord.latitude}, Долгота: ${coord.longitude}"


                // marker.icon = resources.getDrawable(R.drawable.marker_icon)

                marker.setOnMarkerClickListener { marker, mapView ->
                    Toast.makeText(
                        this@OfflineMapViewerActivity,
                        "MAC: ${coord.macAddress}\nВремя: ${coord.time}",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }

                mapView.overlays.add(marker)
                Log.d(TAG, "Добавлен маркер $index: ${coord.latitude}, ${coord.longitude}")
            }
        }

        mapView.invalidate()
    }

    private fun centerMapOnFirstCoordinate(coordinates: List<DeviceCoordinate?>) {
        val firstCoordinate = coordinates.firstOrNull() ?: return

        val geoPoint = GeoPoint(firstCoordinate.latitude, firstCoordinate.longitude)
        mapView?.controller?.setCenter(geoPoint)
        mapView?.controller?.setZoom(15.0) // Уровень приближения как в оригинальном коде

        Log.d(
            TAG,
            "Карта центрирована на координатах устройства: ${firstCoordinate.latitude}, ${firstCoordinate.longitude}"
        )
    }

    private fun centerMapOnMoscow() {
        val moscowPoint = GeoPoint(MOSCOW_LAT, MOSCOW_LON)
        mapView?.controller?.setCenter(moscowPoint)
        mapView?.controller?.setZoom(DEFAULT_ZOOM)

        Log.d(TAG, "Карта центрирована на Москве: $MOSCOW_LAT, $MOSCOW_LON")
    }

    private fun showNoCoordinatesMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.w(TAG, message)

        Toast.makeText(
            this,
            "Карта открыта в центре Москвы",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateHeader(title: String, count: Int) {
        val headerText = if (count > 0) {
            "$title\nНайдено точек: $count"
        } else {
            "$title\nТочки не найдены"
        }

        findViewById<TextView>(R.id.header)?.text = headerText
    }

    private fun setupZoomLimits() {
        val offlineMapManager = OfflineMapManager(this)

        if (offlineMapManager.hasOfflineMaps()) {
            val zoomLevels = offlineMapManager.getAvailableZoomLevels()
            if (zoomLevels.isNotEmpty()) {
                val minZoom = zoomLevels.min()
                val maxZoom = zoomLevels.max()

                mapView?.minZoomLevel = minZoom.toDouble()
                mapView?.maxZoomLevel = maxZoom.toDouble()

                Log.d(TAG, "Установлены ограничения zoom: $minZoom - $maxZoom")
            }
        } else {
            // Стандартные ограничения, если оффлайн карт нет
            mapView?.minZoomLevel = 10.0
            mapView?.maxZoomLevel = 16.0
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDetach()
    }
}