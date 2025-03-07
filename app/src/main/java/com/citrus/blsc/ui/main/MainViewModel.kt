package com.citrus.blsc.ui.main

import android.annotation.SuppressLint
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
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.citrus.blsc.data.model.BluetoothDeviceInfo
import com.citrus.blsc.utils.TextFileHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.Calendar



class MainViewModel(application: Application) : AndroidViewModel(application) {

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
    private val counterList = mutableMapOf<String, Int>()


    @SuppressLint("MissingPermission")
    fun startScanning(context: Context, text: String, textTwo: String, textView: TextView, textViewTwo: TextView) {
        if (!scanning) {
            scanning = true
            discoveredDevices.clear()
            _devices.value = emptyList()
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(receiver, filter)
            bluetoothAdapter?.startDiscovery()
            textFileHelper = TextFileHelper(context)
            textFileHelper.writeToFile(text)
            clear(textView)
            clear(textViewTwo)
        } else {
            discoveredDevices.clear()
            _devices.value = emptyList()
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(receiver, filter)
            bluetoothAdapter?.startDiscovery()
            textFileHelper = TextFileHelper(context)
            textFileHelper.writeToFile(text)
            clear(textView)
            clear(textViewTwo)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning(context: Context) {
        if (scanning) {
            scanning = false
            bluetoothAdapter?.cancelDiscovery()
            context.unregisterReceiver(receiver)
            Toast.makeText(context, "Scanning is over", Toast.LENGTH_SHORT).show()
        }

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


    @RequiresApi(Build.VERSION_CODES.S)
    fun vibratePhone(context: Context) {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val vibrator = vibratorManager.defaultVibrator

        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 300, 500, 600), -1)
        vibrator.vibrate(pattern)
    }

    private fun clear(textView: TextView) {
        textView.text = ""
    }


    private val receiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String? = intent?.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                device?.let {
                    if (!discoveredDevices.any { d -> d.device.address == it.address }) {
                        val rssi: Short =
                            intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        val deviceInfo = BluetoothDeviceInfo( it, rssi)
                        discoveredDevices.add(deviceInfo)
                        _devices.value = discoveredDevices.toList()
                        _lastDevice.value = deviceInfo

                        val mac = it.address

                        val count = counterList.getOrDefault(mac, 0) + 1
                        counterList[mac] = count
                        println(counterList)

                        if (count == 5){
                            println("5555555555555555555555555555555555555555 попался $mac")
                            counterList[mac] = 0
                        }
                        if (context != null && favouriteMacs.contains(mac)) {
                            vibratePhone(context)
                            Toast.makeText(
                                context,
                                "Обнаружено устройство $mac",
                                Toast.LENGTH_SHORT
                            ).show()

                        }

                    }
                }
            }
        }
    }
}

// AIzaSyD8b_63WbyJvlw1J8ILa-96LwfEC9A6JJw - api для этого спо