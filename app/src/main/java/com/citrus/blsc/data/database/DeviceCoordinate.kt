package com.citrus.blsc.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_coordinates")
data class DeviceCoordinate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val macAddress : String,
    val latitude: Double,
    val longitude: Double,
    val time: String
)