package com.citrus.blsc.data.model

import android.bluetooth.BluetoothDevice

data class BluetoothDeviceInfo(
    val device: BluetoothDevice,
    val rssi: Short
)