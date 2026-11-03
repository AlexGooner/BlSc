package com.citrus.blsc.ui.map

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.citrus.blsc.R
import com.citrus.blsc.data.database.AppDatabase
import com.citrus.blsc.data.database.DeviceCoordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class OfflineMapViewerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OfflineMapViewerActivity"

        private const val MOSCOW_LAT = 55.7558
        private const val MOSCOW_LON = 37.6173
        private const val DEFAULT_ZOOM = 12.0
    }

    private var mapView: MapView? = null
    private lateinit var db: AppDatabase
    private lateinit var pickDayButton: Button
    private lateinit var allTimeButton: Button
    private lateinit var headerView: TextView
    private var macAddress: String? = null
    private var selectedDay: String? = null
    private var allCoordinates: List<DeviceCoordinate> = emptyList()
    private val markerOverlays = mutableListOf<Marker>()
    private var routeOverlay: Polyline? = null
    private var initialLoadNotified = false

    private val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

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
        headerView = findViewById(R.id.header)
        pickDayButton = findViewById(R.id.pick_day_button)
        allTimeButton = findViewById(R.id.all_time_button)

        mapView?.setTileSource(TileSourceFactory.MAPNIK)
        mapView?.setMultiTouchControls(true)
        setupZoomLimits()
        centerMapOnMoscow()

        pickDayButton.setOnClickListener { showDayPicker() }
        allTimeButton.setOnClickListener {
            selectedDay = null
            renderSelection(showEmptyDayToast = false)
        }
        updateFilterButtons()
    }

    private fun loadAndDisplayCoordinates() {
        if (macAddress.isNullOrEmpty()) {
            showNoCoordinatesMessage("MAC-адрес не указан")
            headerView.text = "MAC-адрес не указан"
            return
        }

        lifecycleScope.launch {
            try {
                val coordinates = withContext(Dispatchers.IO) {
                    db.deviceCoordinateDao().getAllCoordinatesByMac(macAddress!!)
                }
                allCoordinates = sortChronologically(coordinates)
                renderSelection(showEmptyDayToast = false)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки координат", e)
                showNoCoordinatesMessage("Ошибка загрузки координат")
                headerView.text = "Ошибка загрузки"
            }
        }
    }

    private fun renderSelection(showEmptyDayToast: Boolean) {
        val selected = selectedCoordinates()
        clearDrawnOverlays()
        updateFilterButtons()
        updateHeader(selected.size)

        if (selected.isEmpty()) {
            if (allCoordinates.isEmpty()) {
                showNoCoordinatesMessage("Координаты не найдены для MAC-адреса: $macAddress")
            } else if (showEmptyDayToast) {
                Toast.makeText(this, R.string.map_no_points_for_day, Toast.LENGTH_SHORT).show()
            }
            mapView?.invalidate()
            return
        }

        addMarkersAndRoute(selected)
        focusOn(selected.map { GeoPoint(it.latitude, it.longitude) })

        if (!initialLoadNotified) {
            initialLoadNotified = true
            Toast.makeText(this, "Найдено записей: ${allCoordinates.size}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun selectedCoordinates(): List<DeviceCoordinate> {
        val day = selectedDay ?: return allCoordinates
        return allCoordinates.filter { dayOf(it) == day }
    }

    private fun addMarkersAndRoute(coordinates: List<DeviceCoordinate>) {
        val mapView = mapView ?: return
        val points = coordinates.map { GeoPoint(it.latitude, it.longitude) }

        if (points.size > 1) {
            val line = Polyline()
            line.setPoints(points)
            line.outlinePaint.color = ContextCompat.getColor(this, R.color.purple)
            line.outlinePaint.strokeWidth = 4f * resources.displayMetrics.density
            line.outlinePaint.strokeCap = Paint.Cap.ROUND
            line.outlinePaint.strokeJoin = Paint.Join.ROUND
            line.outlinePaint.isAntiAlias = true
            mapView.overlays.add(line)
            routeOverlay = line
        }

        coordinates.forEachIndexed { index, coord ->
            val marker = Marker(mapView)
            marker.position = GeoPoint(coord.latitude, coord.longitude)
            marker.title = markerTitle(index, coordinates.size)
            marker.snippet = "${coord.time}\n${coord.latitude}, ${coord.longitude}"
            marker.setOnMarkerClickListener { _, _ ->
                Toast.makeText(
                    this,
                    "${marker.title}\n${coord.time}",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
            mapView.overlays.add(marker)
            markerOverlays.add(marker)
        }

        mapView.invalidate()
    }

    private fun markerTitle(index: Int, count: Int): String {
        return when {
            count > 1 && index == 0 -> getString(R.string.map_route_start)
            count > 1 && index == count - 1 -> getString(R.string.map_route_finish)
            else -> getString(R.string.map_route_point, index + 1)
        }
    }

    private fun clearDrawnOverlays() {
        val mapView = mapView ?: return
        routeOverlay?.let { mapView.overlays.remove(it) }
        routeOverlay = null
        markerOverlays.forEach { mapView.overlays.remove(it) }
        markerOverlays.clear()
    }

    private fun focusOn(points: List<GeoPoint>) {
        val mapView = mapView ?: return
        if (points.isEmpty()) return
        if (points.size == 1) {
            mapView.controller.setCenter(points.first())
            mapView.controller.setZoom(16.0)
            return
        }
        val box = BoundingBox.fromGeoPointsSafe(points)
        mapView.post {
            mapView.zoomToBoundingBox(box, true, 100)
        }
    }

    private fun showDayPicker() {
        val calendar = Calendar.getInstance()
        selectedDay?.let { day ->
            dayFormat.parse(day)?.let { calendar.time = it }
        }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance()
                picked.set(year, month, dayOfMonth, 0, 0, 0)
                selectedDay = dayFormat.format(picked.time)
                renderSelection(showEmptyDayToast = true)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateFilterButtons() {
        val daySelected = selectedDay != null
        pickDayButton.text = selectedDay ?: getString(R.string.map_pick_day)
        pickDayButton.backgroundTintList = buttonTint(daySelected)
        allTimeButton.backgroundTintList = buttonTint(!daySelected)
    }

    private fun buttonTint(active: Boolean): ColorStateList {
        val color = if (active) R.color.purple else R.color.grey
        return ColorStateList.valueOf(ContextCompat.getColor(this, color))
    }

    private fun updateHeader(count: Int) {
        val scope = if (selectedDay == null) {
            getString(R.string.map_all_detections)
        } else {
            getString(R.string.map_day_detections, selectedDay)
        }
        val points = if (count > 0) {
            resources.getQuantityString(R.plurals.map_points_count, count, count)
        } else {
            getString(R.string.map_no_points)
        }
        headerView.text = "${macAddress.orEmpty()}\n$scope · $points"
    }

    private fun sortChronologically(coordinates: List<DeviceCoordinate>): List<DeviceCoordinate> {
        return coordinates.sortedWith(compareBy({ parsedTime(it) }, { it.id }))
    }

    private fun parsedTime(coordinate: DeviceCoordinate): Long {
        return timeFormat.parse(coordinate.time)?.time ?: Long.MAX_VALUE
    }

    private fun dayOf(coordinate: DeviceCoordinate): String? {
        val parsed = timeFormat.parse(coordinate.time)
        if (parsed != null) return dayFormat.format(parsed)
        return coordinate.time.takeIf { it.length >= 10 }?.take(10)
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
        Toast.makeText(this, "Карта открыта в центре Москвы", Toast.LENGTH_SHORT).show()
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
