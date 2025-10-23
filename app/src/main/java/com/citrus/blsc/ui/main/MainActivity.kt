package com.citrus.blsc.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
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
import com.citrus.blsc.data.model.FavItem
import com.citrus.blsc.data.model.SearchHistoryItem
import com.citrus.blsc.databinding.ActivityMainBinding
import com.citrus.blsc.ui.fav.FavActivity
import com.citrus.blsc.ui.history.HistoryActivity
import com.citrus.blsc.ui.settings.SettingsActivity
import com.citrus.blsc.utils.AnimationHelper
import com.citrus.blsc.utils.ThemeHelper
import com.citrus.blsc.utils.UIAnimationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.ArrayList


class MainActivity() : AppCompatActivity() {

    private val launcher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }

            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                // Показываем, какие именно разрешения не получены
                val deniedPermissions = permissions.filter { !it.value }.keys
                Toast.makeText(
                    this,
                    "Denied permissions: ${deniedPermissions.joinToString()}",
                    Toast.LENGTH_LONG
                ).show()

                // Можно показать диалог с объяснением
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

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Применяем тему перед созданием UI
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        binding.lottieAnimation.isVisible = false

        setupRecyclerView()
        observeViewModel()
        check()

        binding.startBtn.setOnClickListener {
            UIAnimationHelper.animateButtonPress(binding.startBtn)
            // Проверяем разрешения перед проверкой локации
            if (!areAllPermissionsGranted()) {
                Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT)
                    .show()
                checkPermissions()
                return@setOnClickListener
            }

            // Проверяем включена ли локация
            if (!isLocationEnabled()) {
                showLocationEnableDialog()
                return@setOnClickListener
            }

            // Если все условия выполнены - начинаем работу
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
                                        coordinates[1].trim()// Проверяем, что координаты корректны
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

    override fun onPause() {
        super.onPause()
        // Не отпускаем wakeLock если сканирование активно
        if (job == null) {
            releaseWakeLock()
        }
    }


    private fun releaseWakeLock() {
        wakeLock?.release()
        wakeLock = null
    }

    private fun startScanningProcess() {

        acquireWakeLock()
        binding.mainTextView.text = viewModel.getLocation(this).toString()

        job = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                viewModel.startScanning(
                    this@MainActivity,
                    binding.mainTextView.text.toString(),
                    binding.mapsTextView.text.toString(),
                    binding.mainTextView,
                    binding.mapsTextView
                )
                delay(15000)
            }
        }
        viewModel.startTimer(binding.timerTextView)
        binding.lottieAnimation.isVisible = true
    }

    private fun areAllPermissionsGranted(): Boolean {
        return REQUEST_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("ObsoleteSdkInt", "ServiceCast")
    private fun isLocationEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9.0+ (API 28+)
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.isLocationEnabled
        } else {
            // Android 8.1 и ниже
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

        // Показываем иконку локации в диалоге
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
            // Fallback если ACTION_LOCATION_SOURCE_SETTINGS не доступен
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
        
        // Получаем информацию о вибрации из Intent
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
                        // Android 12+
                        add(Manifest.permission.BLUETOOTH_SCAN)
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                    }

                    else -> {
                        // Android 6-11
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
        // Если сканирование было прервано из-за выключенной локации,
        // можно автоматически возобновить при возвращении в приложение
        if (job != null && !isLocationEnabled()) {
            // Останавливаем сканирование если локация выключена
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

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }
}

