package com.citrus.blsc.data.repository

import android.content.SharedPreferences
import com.citrus.blsc.data.model.FavItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class FavItemRepository(private val sharedPreferences: SharedPreferences) {

    private val gson = Gson()

    fun getFavItems(): List<FavItem> {
        val favItemsJson = sharedPreferences.getString(PREF_FAV_ITEMS, "") ?: return emptyList()
        return gson.fromJson(favItemsJson, object : TypeToken<List<FavItem>>() {}.type)
            ?: emptyList()
    }

    fun saveFavItems(favItems: List<FavItem>) {
        val favItemsJson = gson.toJson(favItems)
        sharedPreferences.edit().putString(PREF_FAV_ITEMS, favItemsJson).apply()
    }

    companion object {
        const val PREF_FAV_ITEMS = "fav_items"
    }
}
