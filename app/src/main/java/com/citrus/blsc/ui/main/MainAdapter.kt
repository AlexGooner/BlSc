package com.citrus.blsc.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.bluetooth.BluetoothClass
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.citrus.blsc.ui.fav.FavActivity
import com.citrus.blsc.R
import com.citrus.blsc.data.model.BluetoothDeviceInfo
import java.text.SimpleDateFormat
import java.util.Date

class MainAdapter(private val devices: List<BluetoothDeviceInfo>, private val context: Context) :
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
        val deviceInfo = devices[position]
        val device = deviceInfo.device

        holder.menuButton.setOnClickListener { anchor ->
            val popup = PopupMenu(context, anchor)
            popup.menuInflater.inflate(R.menu.device_item_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_add_favourite -> {
                        val intent = Intent(context, FavActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra("name", device.name ?: "unnamed")
                            putExtra("macAddress", device.address)
                        }
                        context.startActivity(intent)
                        true
                    }

                    R.id.menu_device_info -> {
                        val intent = Intent(context, DeviceInfoActivity::class.java).apply {
                            putExtra(DeviceInfoActivity.EXTRA_DEVICE_NAME, device.name ?: "unnamed")
                            putExtra(DeviceInfoActivity.EXTRA_DEVICE_MAC, device.address)
                            putExtra(
                                DeviceInfoActivity.EXTRA_DEVICE_CATEGORY,
                                getDeviceCategoryLabel(device),
                            )
                        }
                        context.startActivity(intent)
                        true
                    }

                    else -> false
                }
            }
            popup.show()
        }

        if (ContextCompat.checkSelfPermission(
                holder.itemView.context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            holder.nameTextView.text = device.name ?: "unnamed"
            holder.macAddressTextView.text = device.address

            holder.timeTextView.text =
                dateFormat.format(Date(deviceInfo.discoveredAtEpochMillis))

            holder.rssiTextView.text = "RSSI: ${deviceInfo.rssi} dBm"
            holder.typeTextView.text = "Категория: ${getDeviceCategoryLabel(device)}"
            holder.weekCountTextView.text =
                "Обнаружений за 7 дней: ${deviceInfo.detectionsLast7Days}"

        } else {
            val REQUEST_PERMISSION_BLUETOOTH_CONNECT = 0
            ActivityCompat.requestPermissions(
                holder.itemView.context as Activity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_PERMISSION_BLUETOOTH_CONNECT
            )
        }
    }

    override fun getItemCount(): Int {
        return devices.size
    }

    private fun getDeviceCategoryLabel(device: android.bluetooth.BluetoothDevice): String {
        val deviceClass = device.bluetoothClass
        val name = (device.name ?: "").lowercase()

        // 1) Try BluetoothClass first (most reliable for Classic devices)
        if (deviceClass != null) {
            when (deviceClass.majorDeviceClass) {
                BluetoothClass.Device.Major.PHONE -> return "Телефон"
                BluetoothClass.Device.Major.WEARABLE -> return "Часы/носимое"
                BluetoothClass.Device.Major.COMPUTER -> return "Компьютер"
                BluetoothClass.Device.Major.AUDIO_VIDEO -> {
                    return when (deviceClass.deviceClass) {
                        BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
                        BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
                        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
                        BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
                        BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
                        BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "Наушники/аудио"
                        BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> "Авто/мультимедиа"
                        else -> "Аудио/видео устройство"
                    }
                }

                BluetoothClass.Device.Major.PERIPHERAL -> return "Периферия (клавиатура/мышь)"
                BluetoothClass.Device.Major.HEALTH -> return "Медицинское устройство"
                BluetoothClass.Device.Major.IMAGING -> return "Устройство изображения"
                BluetoothClass.Device.Major.NETWORKING -> return "Сетевое устройство"
                BluetoothClass.Device.Major.TOY -> return "Игровое устройство"
            }
        }

        // 2) Fallback by common name keywords (helps with BLE wearables/earbuds)
        if (
            name.contains("watch") || name.contains("band") || name.contains("fit") ||
            name.contains("mi band") || name.contains("amazfit") || name.contains("galaxy watch")
        ) {
            return "Часы/носимое"
        }
        if (
            name.contains("buds") || name.contains("ear") || name.contains("head") ||
            name.contains("airpods") || name.contains("headset")
        ) {
            return "Наушники/аудио"
        }
        if (
            name.contains("iphone") || name.contains("android") || name.contains("phone") ||
            name.contains("pixel") || name.contains("galaxy")
        ) {
            return "Телефон"
        }
        if (
            name.contains("car") || name.contains("auto") || name.contains("bmw") ||
            name.contains("audi") || name.contains("mercedes")
        ) {
            return "Авто/мультимедиа"
        }

        // 3) Last fallback by transport type
        return when (device.type) {
            android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic Bluetooth устройство"
            android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE -> "BLE устройство"
            android.bluetooth.BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual Bluetooth устройство"
            else -> "Неизвестное устройство"
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val menuButton: ImageButton = itemView.findViewById(R.id.device_menu_button)
        val nameTextView: TextView = itemView.findViewById(R.id.name_text_view)
        val macAddressTextView: TextView = itemView.findViewById(R.id.mac_address_text_view)
        val timeTextView: TextView = itemView.findViewById(R.id.time_text_view)
        val rssiTextView: TextView = itemView.findViewById(R.id.rssi_text_view)
        val typeTextView: TextView = itemView.findViewById(R.id.type_text_view)
        val weekCountTextView: TextView = itemView.findViewById(R.id.week_count_text_view)
    }
}
