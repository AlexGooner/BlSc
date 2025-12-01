package com.citrus.blsc.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.citrus.blsc.data.database.AppDatabase
import com.citrus.blsc.data.database.DeviceCoordinate
import com.citrus.blsc.data.model.BluetoothDeviceInfo
import com.citrus.blsc.databinding.ActivityMainBinding
import com.citrus.blsc.ui.fav.FavActivity
import com.citrus.blsc.ui.history.HistoryActivity
import com.citrus.blsc.ui.settings.SettingsActivity
import com.citrus.blsc.utils.AnimationHelper
import com.citrus.blsc.utils.ThemeHelper
import com.citrus.blsc.utils.UIAnimationHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.common.api.ResolvableApiException
import android.content.IntentSender
import android.os.Looper
import com.google.android.gms.location.Priority
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class MainActivity() : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val REQUEST_CHECK_SETTINGS = 1001
    private val launcher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }

            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                val deniedPermissions = permissions.filter { !it.value }.keys
                Toast.makeText(
                    this,
                    "Denied permissions: ${deniedPermissions.joinToString()}",
                    Toast.LENGTH_LONG
                ).show()
                showPermissionExplanationDialog(deniedPermissions.toList())
            }
        }

    private fun showPermissionExplanationDialog(deniedPermissions: List<String>) {
        val message = when {
            deniedPermissions.any { it.contains("BLUETOOTH") } ->
                "Bluetooth permissions are required to discover nearby devices"

            deniedPermissions.any { it.contains("LOCATION") } ->
                "Location permission is required to find Bluetooth devices"

            else -> "Some permissions are required for the app to function properly"
        }

        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: MainAdapter
    private var devices = mutableListOf<BluetoothDeviceInfo>()
    private var job: Job? = null
    private var favouriteMacs: List<String> = emptyList()
    private var favouriteVibrations: Map<String, String> = emptyMap()
    private lateinit var db: AppDatabase
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentLocation: String? = null
    private var isScanning = false
    private var scanningJob: Job? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        checkPermissions()
        binding.lottieAnimation.isVisible = false
        setupRecyclerView()
        observeViewModel()
        check()

        binding.startBtn.setOnClickListener {
            UIAnimationHelper.animateButtonPress(binding.startBtn)
            if (!areAllPermissionsGranted()) {
                Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT)
                    .show()
                checkPermissions()
                return@setOnClickListener
            }
            if (!isLocationEnabled()) {
                showLocationEnableDialog()
                return@setOnClickListener
            }
            clearList()
            startScanningProcess()
        }

        viewModel.location.observe(this) { location ->
            binding.locationTextView.text = location
            currentLocation = location
        }

        binding.stopBtn.setOnClickListener {
            UIAnimationHelper.animateButtonPress(binding.stopBtn)
            releaseWakeLock()
            job?.cancel()
            job = null
            viewModel.stopScanning(this)
            viewModel.stopTimer()
            stopScanningProcess()
            binding.lottieAnimation.isVisible = false
        }

        binding.favImageView.setOnClickListener {
            UIAnimationHelper.animatePulse(binding.favImageView)
            val intent = Intent(this, FavActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            AnimationHelper.startActivityWithAnimation(this, intent)
        }

        binding.settingsImageView.setOnClickListener {
            UIAnimationHelper.animateRotation(binding.settingsImageView)
            val intent = Intent(this, SettingsActivity::class.java)
            AnimationHelper.startActivityWithAnimation(this, intent)
        }

        binding.historyImageView.setOnClickListener {
            UIAnimationHelper.animatePulse(binding.historyImageView)
            val intent = Intent(this, HistoryActivity::class.java)
            AnimationHelper.startActivityWithAnimation(this, intent)
        }


        viewModel.lastDevice.observe(this) { device ->
            device.let {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                }
                if (it != null) {
                    binding.mainTextView.append(
                        "\n" + (it.device.name
                            ?: "unnamed") + "\n" + it.device.address + "\n"
                                + binding.locationTextView.text + "\n"
                                + viewModel.getCurrentTime() + "\n"
                                + it.rssi + " dBm"
                    )
                    binding.mapsTextView.append(
                        it.device.address + "\n" + binding.locationTextView.text + "\n"
                    )

                    db = AppDatabase.getDatabase(this)
                    val textFromMaps = binding.mapsTextView.text.toString()
                    val lines = textFromMaps.split("\n")
                    lifecycleScope.launch {
                        for (i in lines.indices step 2) {
                            if (i + 1 < lines.size) {
                                val mac = lines[i].trim()
                                val coordinates = lines[i + 1].trim().split(" ")
                                if (coordinates.size == 2) {
                                    val latitudeStr = coordinates[0].trim()
                                    val longitudeStr =
                                        coordinates[1].trim()
                                    val latitude = latitudeStr.toDoubleOrNull()
                                    val longitude = longitudeStr.toDoubleOrNull()
                                    if (latitude != null && longitude != null) {
                                        val deviceCoordinate = DeviceCoordinate(
                                            0,
                                            mac,
                                            latitude,
                                            longitude,
                                            viewModel.getCurrentTime()
                                        )
                                        db.deviceCoordinateDao().insert(deviceCoordinate)
                                    } else {
                                        Log.e(
                                            "MainActivity",
                                            "Invalid coordinates: $latitudeStr, $longitudeStr"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MyApp::BluetoothScanningWakeLock"
        )
        wakeLock?.acquire()
    }


    private fun releaseWakeLock() {
        wakeLock?.release()
        wakeLock = null
    }

    private fun startScanningProcess() {
        isScanning = true
        acquireWakeLock()

        // Проверяем настройки локации перед началом
        requestLocationSettings()

        scanningJob = CoroutineScope(Dispatchers.Main).launch {
            var cycleCount = 0

            while (isActive && isScanning) {
                cycleCount++
                Log.d("MainActivity", "Starting scanning cycle $cycleCount")

                try {
                    val currentCoordinates = withContext(Dispatchers.IO) {
                        getCurrentCoordinatesSync()
                    }

                    // Если координаты не получены, ждем и пробуем еще раз
                    if (currentCoordinates.first == null || currentCoordinates.second == null) {
                        Log.w("MainActivity", "Координаты не получены, пробуем еще раз через 2 секунды")
                        delay(2000)

                        val retryCoordinates = withContext(Dispatchers.IO) {
                            getCurrentCoordinatesSync()
                        }

                        withContext(Dispatchers.Main) {
                            viewModel.setCurrentCoordinates(
                                retryCoordinates.first,
                                retryCoordinates.second
                            )
                            updateLocationText(retryCoordinates.first, retryCoordinates.second)
                            binding.mainTextView.text =
                                viewModel.getLocation(this@MainActivity).toString()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            viewModel.setCurrentCoordinates(
                                currentCoordinates.first,
                                currentCoordinates.second
                            )
                            updateLocationText(currentCoordinates.first, currentCoordinates.second)
                            binding.mainTextView.text =
                                viewModel.getLocation(this@MainActivity).toString()
                        }
                    }
                    if (areAllPermissionsGranted()) {
                        try {
                            viewModel.startScanning(
                                this@MainActivity,
                                binding.mainTextView.text.toString(),
                                binding.mapsTextView.text.toString(),
                                binding.mainTextView,
                                binding.mapsTextView
                            )
                        } catch (securityException: SecurityException) {
                            Log.e(
                                "MainActivity",
                                "Security exception in startScanning: ${securityException.message}"
                            )
                            withContext(Dispatchers.Main) {
                                handlePermissionError("Cannot start scanning - permissions required")
                            }
                            break
                        }
                    } else {
                        break
                    }
                    delay(15000)
                    if (areAllPermissionsGranted()) {
                        try {
                            viewModel.stopScanning(this@MainActivity)
                        } catch (securityException: SecurityException) {
                            Log.e(
                                "MainActivity",
                                "Security exception in stopScanning: ${securityException.message}"
                            )
                        }
                    } else {
                        break
                    }
                    withContext(Dispatchers.Main) {
                        clearList()
                        Log.d("MainActivity", "Cycle $cycleCount completed, list cleared")
                    }
                    delay(1000)

                } catch (cancellationException: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error in cycle $cycleCount: ${e.message}")
                    delay(1000)
                }
            }
        }

        viewModel.startTimer(binding.timerTextView)
        binding.lottieAnimation.isVisible = true
        binding.startBtn.isEnabled = false
        binding.stopBtn.isEnabled = true
        Toast.makeText(this, "Scanning started", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearList() {
        viewModel.clearAllData()
        devices.clear()
        adapter.notifyDataSetChanged()
    }

    private fun getCurrentCoordinatesSync(): Pair<Double?, Double?> {
        return try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("MainActivity", "Нет разрешения на доступ к локации")
                return Pair(null, null)
            }

            val locationFuture = CompletableFuture<Pair<Double?, Double?>>()

            // Создаем LocationCallback
            val callback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        // Получаем координаты
                        val coordinates = Pair(location.latitude, location.longitude)
                        Log.d("MainActivity", "Получены координаты: ${location.latitude}, ${location.longitude}")

                        // Удаляем обновления чтобы не тратить батарею
                        removeLocationUpdates()

                        // Завершаем Future с координатами
                        if (!locationFuture.isDone) {
                            locationFuture.complete(coordinates)
                        }
                    }
                }
            }

            this.locationCallback = callback

            // Создаем LocationRequest с использованием Builder (не deprecated)
            val locationRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                    .setMinUpdateIntervalMillis(2000)
                    .setMaxUpdates(1)
                    .setMaxUpdateDelayMillis(10000)
                    .build()
            } else {
                // Для старых версий используем create(), но с обновленными полями
                @Suppress("DEPRECATION")
                LocationRequest.create().apply {
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                    interval = 5000
                    fastestInterval = 2000
                    numUpdates = 1
                    maxWaitTime = 10000
                }
            }

            // Запрашиваем обновления локации
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )

            Log.d("MainActivity", "Запрошены обновления локации")

            // Таймаут 15 секунд
            try {
                val result = locationFuture.get(15, TimeUnit.SECONDS)
                Log.d("MainActivity", "Координаты получены успешно")
                result
            } catch (e: TimeoutException) {
                Log.e("MainActivity", "Таймаут получения локации (15 сек)")

                // Пробуем получить последнюю известную локацию как запасной вариант
                try {
                    val lastLocationTask = fusedLocationClient.lastLocation
                    val lastLocation = Tasks.await(lastLocationTask, 2, TimeUnit.SECONDS)

                    lastLocation?.let { location ->
                        Pair(location.latitude, location.longitude)
                    } ?: run {
                        Log.e("MainActivity", "Нет кэшированной локации")
                        Pair(null, null)
                    }
                } catch (ex: Exception) {
                    Log.e("MainActivity", "Ошибка получения кэшированной локации: ${ex.message}")
                    Pair(null, null)
                }
            } finally {
                // Всегда удаляем обновления
                removeLocationUpdates()
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "Общая ошибка получения координат: ${e.message}")
            removeLocationUpdates()
            Pair(null, null)
        }
    }

    private fun removeLocationUpdates() {
        locationCallback?.let { callback ->
            try {
                fusedLocationClient.removeLocationUpdates(callback)
                Log.d("MainActivity", "Обновления локации остановлены")
            } catch (e: Exception) {
                Log.e("MainActivity", "Ошибка удаления обновлений локации: ${e.message}")
            }
            locationCallback = null
        }
    }

    private fun requestLocationSettings() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Создаем LocationRequest с использованием Builder
        val locationRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build()
        } else {
            @Suppress("DEPRECATION")
            LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = 10000
                fastestInterval = 5000
            }
        }

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true) // Показать диалог даже если настройки в порядке

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            Log.d("MainActivity", "Настройки локации в порядке")
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    Log.d("MainActivity", "Показываем диалог включения GPS")
                    exception.startResolutionForResult(this, REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.e("MainActivity", "Ошибка показа диалога GPS: ${sendEx.message}")
                }
            }
        }
    }

    private fun handlePermissionError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        stopScanningProcess()
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Bluetooth scanning requires location and Bluetooth permissions to work properly.")
            .setPositiveButton("Grant Permissions") { _, _ ->
                checkPermissions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun stopScanningProcess() {
        isScanning = false
        scanningJob?.cancel()
        scanningJob = null

        viewModel.stopScanning(this)
        viewModel.stopTimer()

        releaseWakeLock()

        binding.lottieAnimation.isVisible = false
        binding.startBtn.isEnabled = true
        binding.stopBtn.isEnabled = false

        Toast.makeText(this, "Scanning stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateLocationText(latitude: Double?, longitude: Double?) {
        val locationText = if (latitude != null && longitude != null) {
            "$latitude $longitude"
        } else {
            "Координаты недоступны"
        }
        binding.locationTextView.text = locationText
    }

    private fun areAllPermissionsGranted(): Boolean {
        return REQUEST_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("ObsoleteSdkInt", "ServiceCast")
    private fun isLocationEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.isLocationEnabled
        } else {
            val contentResolver = contentResolver
            val locationMode = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )
            locationMode != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    private fun showLocationEnableDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Location Services Required")
            .setMessage("To scan for Bluetooth devices, location services must be enabled. This is a system requirement for Bluetooth scanning on Android devices.")
            .setPositiveButton("Enable Location") { dialog, _ ->
                dialog.dismiss()
                openLocationSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(
                    this,
                    "Bluetooth scanning requires location services",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setCancelable(false)

        val imageView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_dialog_map)
            setPadding(32, 32, 32, 16)
        }

        dialog.setView(imageView)
        dialog.show()
    }

    private fun openLocationSettings() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(
                    this,
                    "Please enable location manually in settings",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = MainAdapter(devices, this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun observeViewModel() {
        viewModel.devices.observe(this) { newDevices ->
            devices.clear()
            devices.addAll(newDevices)
            adapter.notifyDataSetChanged()
        }
    }

    private fun check() {
        favouriteMacs = intent.getStringArrayListExtra("macs") ?: emptyList()
        viewModel.favouriteMacs = favouriteMacs
        val vibrationMap = intent.getSerializableExtra("vibrations") as? Map<String, String>
        favouriteVibrations = vibrationMap ?: emptyMap()
        viewModel.favouriteVibrations = favouriteVibrations
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        check()
    }

    private fun checkPermissions() {
        val isAllGranted = REQUEST_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (isAllGranted) {
            Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            launcher.launch(REQUEST_PERMISSIONS)
        }
    }


    companion object {
        private fun getRequiredPermissions(): Array<String> {
            val permissions = mutableListOf<String>()

            permissions.apply {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)

                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        add(Manifest.permission.BLUETOOTH_SCAN)
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                    }

                    else -> {
                        add(Manifest.permission.BLUETOOTH)
                        add(Manifest.permission.BLUETOOTH_ADMIN)
                    }
                }
            }

            return permissions.toTypedArray()
        }

        val REQUEST_PERMISSIONS = getRequiredPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (job != null && !isLocationEnabled()) {

            job?.cancel()
            job = null
            viewModel.stopScanning(this)
            viewModel.stopTimer()
            binding.lottieAnimation.isVisible = false
            Toast.makeText(this, "Scanning paused - location disabled", Toast.LENGTH_SHORT).show()
        }
        if (job != null && wakeLock == null) {
            acquireWakeLock()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isScanning) {
            releaseWakeLock()
        }
    }

    override fun onDestroy() {
        stopScanningProcess()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CHECK_SETTINGS -> {
                when (resultCode) {
                    RESULT_OK -> {
                        Log.d("MainActivity", "Пользователь включил GPS")
                        Toast.makeText(this, "GPS включен", Toast.LENGTH_SHORT).show()
                    }
                    RESULT_CANCELED -> {
                        Log.w("MainActivity", "Пользователь отказался включать GPS")
                        Toast.makeText(this,
                            "Для работы сканирования требуется включить GPS",
                            Toast.LENGTH_LONG
                        ).show()
                        // Можно остановить сканирование
                        stopScanningProcess()
                    }
                }
            }
        }
    }


}

