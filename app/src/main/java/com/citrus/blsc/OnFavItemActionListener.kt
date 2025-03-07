package com.citrus.blsc

import com.citrus.blsc.data.model.FavItem

interface OnFavItemActionListener {
    fun saveFavItemToPrefs()
    fun removeFavItem(favItem: FavItem)
}