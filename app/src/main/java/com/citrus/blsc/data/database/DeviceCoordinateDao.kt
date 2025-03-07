package com.citrus.blsc.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeviceCoordinateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(coordinate: DeviceCoordinate)

    @Query("SELECT * FROM device_coordinates WHERE macAddress = :macAddress LIMIT 100")
    suspend fun getCoordinateByMac(macAddress: String): List<DeviceCoordinate?>

    @Query("SELECT COUNT(*) FROM device_coordinates WHERE macAddress = :macAddress")
    suspend fun countCoordinatesByMac(macAddress: String): Int

    @Query("SELECT time FROM device_coordinates WHERE macAddress = :macAddress LIMIT 1")
    suspend fun getTimeByMac(macAddress: String): String?
}
