package com.citrus.blsc.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationHelper.COPY_ACTION -> {
                val macAddress = intent.getStringExtra("mac_address")
                macAddress?.let {
                    NotificationHelper.copyToClipboard(context, it, "MAC Address")
                    Toast.makeText(
                        context,
                        "MAC-адрес скопирован: $it",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}