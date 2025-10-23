package com.citrus.blsc.ui.main

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.citrus.blsc.R
import com.citrus.blsc.data.model.BluetoothDeviceInfo

class BluetoothScanningService : Service() {

    private lateinit var viewModel: MainViewModel
    private var wakeLock: PowerManager.WakeLock? = null
    private var isScanning = false

    override fun onCreate() {
        super.onCreate()
        viewModel = MainViewModel(Application())
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        startScanning()
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = createNotificationChannel()

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Bluetooth Scanning")
            .setContentText("Scanning for nearby devices...")
            .setSmallIcon(R.drawable.star) // Добавьте свою иконку
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "bluetooth_scanning_channel"
        val channelName = "Bluetooth Scanning Service"
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        )
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BluetoothScanningService::WakeLock"
        )
        wakeLock?.acquire()
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (!isScanning) {
            isScanning = true
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(bluetoothReceiver, filter)

            // Запускаем периодическое сканирование
            val handler = Handler(Looper.getMainLooper())
            val scanRunnable = object : Runnable {
                override fun run() {
                    if (isScanning) {
                        viewModel.startDiscovery()
                        handler.postDelayed(this, 15000) // Каждые 15 секунд
                    }
                }
            }
            handler.post(scanRunnable)
        }
    }

    private fun stopScanning() {
        if (isScanning) {
            isScanning = false
            try {
                unregisterReceiver(bluetoothReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.stopDiscovery()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.S)
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String? = intent?.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    val rssi: Short = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                    val deviceInfo = BluetoothDeviceInfo(it, rssi)
                    
                    // Сохраняем устройство в историю поиска через ViewModel
                    if (context != null) {
                        Log.d("BluetoothScanningService", "Saving device to history: ${device.name ?: "Unknown"} (${device.address})")
                        viewModel.saveToSearchHistory(deviceInfo, context)
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopScanning()
        wakeLock?.release()
        super.onDestroy()
    }



}