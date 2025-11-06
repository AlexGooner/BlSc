package com.citrus.blsc.data.model

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "search_history")
data class SearchHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceName: String,
    val macAddress: String,
    val rssi: String,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long,
    val isFavourite: Boolean = false
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readValue(Double::class.java.classLoader) as? Double,
        parcel.readValue(Double::class.java.classLoader) as? Double,
        parcel.readLong(),
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(deviceName)
        parcel.writeString(macAddress)
        parcel.writeString(rssi)
        parcel.writeValue(latitude)
        parcel.writeValue(longitude)
        parcel.writeLong(timestamp)
        parcel.writeByte(if (isFavourite) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<SearchHistoryItem> {
        override fun createFromParcel(parcel: Parcel): SearchHistoryItem = SearchHistoryItem(parcel)
        override fun newArray(size: Int): Array<SearchHistoryItem?> = arrayOfNulls(size)
    }

    fun getFormattedDate(): String {
        val date = Date(timestamp)
        val formatter =
            java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }

}
