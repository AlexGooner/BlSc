package com.citrus.blsc.data.model

import android.bluetooth.BluetoothDevice

data class BluetoothDeviceInfo(
    val device: BluetoothDevice,
    val rssi: Short,
    /** Wall-clock time when this device was first seen in the current scan cycle (for list UI). */
    val discoveredAtEpochMillis: Long,
    val detectionsLast7Days: Int = 0,
)