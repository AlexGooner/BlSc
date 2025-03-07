package com.citrus.blsc.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.citrus.blsc.ui.fav.FavActivity
import com.citrus.blsc.R
import com.citrus.blsc.data.model.BluetoothDeviceInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class MainAdapter(private val devicess: List<BluetoothDeviceInfo>, private val context: Context) :
    RecyclerView.Adapter<MainAdapter.ViewHolder>() {

    @SuppressLint("SimpleDateFormat")
    val dateFormat = SimpleDateFormat("dd.MM.yy HH:mm:ss")


    @SuppressLint("ResourceType")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.device_item, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val deviceInfo = devicess[position]
        val device = deviceInfo.device
        if (ContextCompat.checkSelfPermission(
                holder.itemView.context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            holder.nameTextView.text = device.name ?: "unnamed"
            holder.macAddressTextView.text = device.address

            // Получаем текущее время при каждом связывании
            val currentTime = dateFormat.format(Date())
            holder.timeTextView.text = currentTime

            holder.rssiTextView.text = "RSSI: ${deviceInfo.rssi} dBm" // Отображение RSSI

        } else {
            // Запросить разрешение
            val REQUEST_PERMISSION_BLUETOOTH_CONNECT = 0
            ActivityCompat.requestPermissions(
                holder.itemView.context as Activity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_PERMISSION_BLUETOOTH_CONNECT
            )
        }

        holder.itemView.setOnLongClickListener {
            val intent = Intent(context, FavActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val name = holder.nameTextView.text.toString()
            val macAddress = holder.macAddressTextView.text.toString()
            println(name)
            println(macAddress)
            intent.putExtra("name", name)
            intent.putExtra("macAddress", macAddress)
            context.startActivity(intent)
            true
        }
    }

    override fun getItemCount(): Int {
        return devicess.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.name_text_view)
        val macAddressTextView: TextView = itemView.findViewById(R.id.mac_address_text_view)
        val timeTextView: TextView = itemView.findViewById(R.id.time_text_view)
        val rssiTextView: TextView = itemView.findViewById(R.id.rssi_text_view)
    }
}