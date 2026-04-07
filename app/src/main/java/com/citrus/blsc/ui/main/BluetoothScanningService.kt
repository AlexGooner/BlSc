package com.citrus.blsc.ui.main

import android.annotation.SuppressLint
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.citrus.blsc.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BluetoothScanningService : Service() {

    private lateinit var viewModel: MainViewModel
    private var wakeLock: PowerManager.WakeLock? = null
    private var isScanning = false
    private var scanningJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        viewModel = MainViewModel(application as Application)
        createForegroundChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val favouriteMacs = intent?.getStringArrayListExtra(EXTRA_FAVOURITE_MACS) ?: arrayListOf()
        val favouriteVibrations =
            (intent?.getSerializableExtra(EXTRA_FAVOURITE_VIBRATIONS) as? HashMap<String, String>)
                ?: hashMapOf()
        viewModel.favouriteMacs = favouriteMacs
        viewModel.favouriteVibrations = favouriteVibrations

        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundScanning()
                stopSelf()
            }

            ACTION_START, null -> {
                try {
                    startForeground(
                        FOREGROUND_NOTIFICATION_ID,
                        buildForegroundNotification()
                    )
                    startScanning()
                } catch (e: Exception) {
                    Log.e("BluetoothScanningService", "Failed to start foreground/service: ${e.message}", e)
                    stopSelf()
                }
            }
        }
        return START_STICKY
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
        if (isScanning) return
        isScanning = true
        scanningJob = CoroutineScope(Dispatchers.Main).launch {
            var cycleCount = 0
            while (isActive && isScanning) {
                cycleCount++
                // Reset per-cycle deduplication state so repeated discoveries
                // in new background cycles are processed and persisted again.
                viewModel.clearCycleData()
                try {
                    viewModel.startScanning(this@BluetoothScanningService)
                } catch (e: Exception) {
                    Log.e("BluetoothScanningService", "startScanning failed in cycle $cycleCount: ${e.message}", e)
                    break
                }
                delay(SCAN_DURATION_MS)
                try {
                    viewModel.stopScanning(this@BluetoothScanningService)
                } catch (e: Exception) {
                    Log.e("BluetoothScanningService", "stopScanning failed in cycle $cycleCount: ${e.message}", e)
                }
                delay(CYCLE_PAUSE_MS)
            }
        }
    }

    private fun stopForegroundScanning() {
        isScanning = false
        scanningJob?.cancel()
        scanningJob = null
        viewModel.stopScanning(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createForegroundChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            "Background Bluetooth Scan",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Bluetooth scanning active when app is backgrounded"
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Сканирование Bluetooth активно")
            .setContentText("Поиск устройств продолжается в фоне")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForegroundScanning()
        wakeLock?.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.citrus.blsc.action.START_SCAN_SERVICE"
        const val ACTION_STOP = "com.citrus.blsc.action.STOP_SCAN_SERVICE"
        const val EXTRA_FAVOURITE_MACS = "extra_favourite_macs"
        const val EXTRA_FAVOURITE_VIBRATIONS = "extra_favourite_vibrations"
        private const val FOREGROUND_CHANNEL_ID = "bluetooth_scan_foreground_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 2001
        private const val SCAN_DURATION_MS = 15000L
        private const val CYCLE_PAUSE_MS = 1000L
    }

}