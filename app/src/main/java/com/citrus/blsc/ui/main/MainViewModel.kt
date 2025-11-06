package com.citrus.blsc.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.citrus.blsc.data.model.BluetoothDeviceInfo
import com.citrus.blsc.data.model.SearchHistoryItem
import com.citrus.blsc.data.database.AppDatabase
import com.citrus.blsc.utils.TextFileHelper
import com.citrus.blsc.utils.VibrationHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Calendar


class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val PROXIMITY_THRESHOLD = 10
    }

    private val _devices = MutableLiveData<List<BluetoothDeviceInfo>>()
    val devices: LiveData<List<BluetoothDeviceInfo>> get() = _devices
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val discoveredDevices = mutableListOf<BluetoothDeviceInfo>()
    private val _location = MutableLiveData<String>()
    val location: LiveData<String> get() = _location
    private lateinit var textFileHelper: TextFileHelper
    private val vibratedDevices = mutableSetOf<String>()
    private var timer: CountDownTimer? = null
    private var startTime = 0L
    private val _lastDevice = MutableLiveData<BluetoothDeviceInfo?>()
    val lastDevice: LiveData<BluetoothDeviceInfo?> get() = _lastDevice
    private var scanning = false
    var favouriteMacs: List<String> = emptyList()
    var favouriteVibrations: Map<String, String> = emptyMap()
    private val counterList = mutableMapOf<String, Int>()
    private val database = AppDatabase.getDatabase(application)
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private val currentCycleDevices = mutableSetOf<String>()

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning(
        context: Context,
        text: String,
        textTwo: String,
        textView: TextView,
        textViewTwo: TextView
    ) {
        scanning = true

        currentCycleDevices.clear()

        _devices.value = discoveredDevices.toList()

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
        }
        context.registerReceiver(receiver, filter)
        bluetoothAdapter?.startDiscovery()

        textFileHelper = TextFileHelper(context)
        textFileHelper.writeToFile(text)
        clear(textView)
        clear(textViewTwo)
    }

    @SuppressLint("MissingPermission")
    fun stopScanning(context: Context) {
        if (scanning) {
            scanning = false
            bluetoothAdapter?.cancelDiscovery()

            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
            }

            Toast.makeText(context, "Scanning is over", Toast.LENGTH_SHORT).show()
        }

    }

    fun setCurrentCoordinates(latitude: Double?, longitude: Double?) {
        currentLatitude = latitude
        currentLongitude = longitude
        Log.d("MainViewModel", "Coordinates set: $latitude, $longitude")
    }

    fun clearAllData() {
        discoveredDevices.clear()
        currentCycleDevices.clear()
        _devices.value = emptyList()
        counterList.clear()
        vibratedDevices.clear()
        _lastDevice.value = null
        Log.d("MainViewModel", "All data cleared")
    }

    fun startTimer(textView: TextView) {
        startTime = System.currentTimeMillis()
        timer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            @SuppressLint("DefaultLocale")
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = System.currentTimeMillis() - startTime
                val seconds = (elapsed / 1000) % 60
                val minutes = (elapsed / (1000 * 60)) % 60
                val hours = (elapsed / (1000 * 60 * 60)) % 24
                textView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }

            override fun onFinish() {
            }
        }.start()
    }


    fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    @SuppressLint("DefaultLocale")
    fun getCurrentTime(): String {
        val calendar = Calendar.getInstance()
        return String.format(
            "%02d.%02d.%d %02d:%02d:%02d",
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            calendar.get(Calendar.SECOND)
        )
    }


    fun getLocation(context: Context) {
        val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            _location.value = "Разрешение на доступ к местоположению не предоставлено"
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            _location.value = if (location != null) {
                "${location.latitude} ${location.longitude}"
            } else {
                "Координаты недоступны"
            }
        }.addOnFailureListener { exception ->
            Log.e("LocationError", "Failed to get location: ${exception.message}")

            _location.value = "Координаты недоступны"
        }
    }

    fun saveToSearchHistory(deviceInfo: BluetoothDeviceInfo, context: Context) {
        viewModelScope.launch {
            try {
                val device = deviceInfo.device
                val deviceName = device.name ?: "Unknown Device"
                val macAddress = device.address
                val rssi = deviceInfo.rssi.toString()
                val timestamp = System.currentTimeMillis()

                var latitude = currentLatitude
                var longitude = currentLongitude

                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val fusedLocationClient =
                        LocationServices.getFusedLocationProviderClient(context)
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            latitude = it.latitude
                            longitude = it.longitude
                        }
                    }
                }

                val historyItem = SearchHistoryItem(
                    deviceName = deviceName,
                    macAddress = macAddress,
                    rssi = rssi,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = timestamp,
                    isFavourite = favouriteMacs.contains(macAddress)
                )

                try {
                    database.searchHistoryDao().insertHistoryItem(historyItem)
                    Log.d(
                        "SearchHistory",
                        "Successfully saved device to history: $deviceName ($macAddress)"
                    )
                } catch (e: Exception) {
                    Log.e("SearchHistory", "Failed to save device to history: ${e.message}")
                }


            } catch (e: Exception) {
                Log.e("SearchHistory", "Error creating history item: ${e.message}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun vibratePhone(context: Context, vibrationType: String = VibrationHelper.DEFAULT_VIBRATION) {
        VibrationHelper.vibrate(context, vibrationType)
    }

    private fun clear(textView: TextView) {
        textView.text = ""
    }

    private val receiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.S)
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String? = intent?.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    val macAddress = it.address
                    if (!currentCycleDevices.contains(macAddress)) {
                        val rssi: Short =
                            intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        val deviceInfo = BluetoothDeviceInfo(it, rssi)

                        discoveredDevices.add(deviceInfo)
                        currentCycleDevices.add(macAddress)

                        _devices.value = discoveredDevices.toList()
                        _lastDevice.value = deviceInfo

                        if (context != null) {
                            saveToSearchHistory(deviceInfo, context)
                        }

                        val count = counterList.getOrDefault(macAddress, 0) + 1
                        counterList[macAddress] = count
                        Log.d(
                            "MainViewModel",
                            "New device: $macAddress, RSSI: $rssi, count: $count"
                        )

                        if (count == PROXIMITY_THRESHOLD) {
                            if (context != null) {
                                val rootView =
                                    (context as? Activity)?.window?.decorView?.findViewById<View>(
                                        android.R.id.content
                                    )
                                rootView?.let { view ->
                                    Snackbar.make(
                                        view,
                                        "$macAddress продолжительное время рядом с вами",
                                        Snackbar.LENGTH_LONG
                                    ).show()
                                }
                            }
                            counterList[macAddress] = 0
                        }

                        if (context != null && favouriteMacs.contains(macAddress)) {
                            val vibrationType =
                                favouriteVibrations[macAddress] ?: VibrationHelper.DEFAULT_VIBRATION
                            vibratePhone(context, vibrationType)
                            Toast.makeText(
                                context,
                                "Обнаружено устройство $macAddress",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timer?.cancel()
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        bluetoothAdapter?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        bluetoothAdapter?.cancelDiscovery()
    }
}