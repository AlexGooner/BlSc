package com.citrus.blsc.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.citrus.blsc.R
import com.citrus.blsc.ui.main.MainActivity
import com.citrus.blsc.utils.PermissionHelper.isNotificationPermissionGranted

object NotificationHelper {
    private const val CHANNEL_ID = "bluetooth_scanner_channel"
    private const val CHANNEL_NAME = "Bluetooth Scanner Notifications"
    private const val NOTIFICATION_ID = 1001
    const val COPY_ACTION = "COPY_MAC_ACTION"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о Bluetooth устройствах"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showDeviceProximityNotification(context: Context, macAddress: String) {
        // Проверяем разрешение на уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isNotificationPermissionGranted(context)) {
                Log.w("NotificationHelper", "Нет разрешения на уведомления")
                // Можно показать Toast или другой способ уведомления
                showFallbackNotification(context, macAddress)
                return
            }
        }

        // Создаем канал если нужно
        createNotificationChannel(context)

        // Интент для открытия приложения при клике на уведомление
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Интент для кнопки копирования MAC
        val copyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = COPY_ACTION
            putExtra("mac_address", macAddress)
        }
        val copyPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Строим уведомление
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Устройство рядом")
            .setContentText("$macAddress продолжительное время рядом с вами")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Устройство $macAddress находится рядом продолжительное время.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainPendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .addAction(
                R.drawable.ic_copy,
                "Копировать MAC",
                copyPendingIntent
            )

        // Безопасная отправка уведомления с проверкой разрешений
        try {
            with(NotificationManagerCompat.from(context)) {
                // Дополнительная проверка для Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (areNotificationsEnabled()) {
                        notify(NOTIFICATION_ID, notificationBuilder.build())
                        Log.d("NotificationHelper", "Уведомление показано успешно")
                    } else {
                        Log.w("NotificationHelper", "Уведомления отключены в настройках")
                        showFallbackNotification(context, macAddress)
                    }
                } else {
                    notify(NOTIFICATION_ID, notificationBuilder.build())
                    Log.d("NotificationHelper", "Уведомление показано успешно")
                }
            }
        } catch (e: SecurityException) {
            Log.e("NotificationHelper", "SecurityException при показе уведомления: ${e.message}")
            showFallbackNotification(context, macAddress)
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Ошибка при показе уведомления: ${e.message}")
            showFallbackNotification(context, macAddress)
        }
    }

    private fun showFallbackNotification(context: Context, macAddress: String) {
        // Альтернативный способ уведомления если нет разрешений
        runOnUiThreadIfPossible(context) {
            Toast.makeText(
                context,
                "Устройство $macAddress продолжительное время рядом. MAC: $macAddress",
                Toast.LENGTH_LONG
            ).show()

            // Также можно скопировать MAC автоматически
            copyToClipboard(context, macAddress)
            Toast.makeText(
                context,
                "MAC-адрес скопирован в буфер",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun runOnUiThreadIfPossible(context: Context, action: () -> Unit) {
        if (context is android.app.Activity) {
            context.runOnUiThread(action)
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }

    // Добавьте проверку разрешения в NotificationHelper
    private fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun copyToClipboard(context: Context, text: String, label: String = "MAC Address") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }
}