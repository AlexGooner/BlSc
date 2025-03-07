package com.citrus.blsc.data.model

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity

@Entity(tableName = "fav_items")
data class FavItem(var name: String, val macAddress: String, val rssi: String) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(macAddress)
        parcel.writeString(rssi)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<FavItem> {
        override fun createFromParcel(parcel: Parcel): FavItem = FavItem(parcel)
        override fun newArray(size: Int): Array<FavItem?> = arrayOfNulls(size)
    }
}