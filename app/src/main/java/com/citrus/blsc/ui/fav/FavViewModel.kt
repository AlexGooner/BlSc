package com.citrus.blsc.ui.fav

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.citrus.blsc.data.model.FavItem
import com.citrus.blsc.data.repository.FavItemRepository

class FavViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FavItemRepository
    val favItems: MutableLiveData<List<FavItem>> = MutableLiveData()


    init {
        val prefs = application.getSharedPreferences(FavItemRepository.PREF_FAV_ITEMS, Context.MODE_PRIVATE)
        repository = FavItemRepository(prefs)
        loadFavItems()
    }

    private fun loadFavItems() {
        favItems.value = repository.getFavItems()
    }

    fun addFavItem(favItem: FavItem) {
        val currentItems = favItems.value?.toMutableList() ?: mutableListOf()
        currentItems.add(favItem)
        favItems.value = currentItems
        repository.saveFavItems(currentItems)
    }

    fun clearFavItems() {
        favItems.value = emptyList()
        repository.saveFavItems(emptyList())
    }

    fun removeFavItem(favItem: FavItem) {
        val currentItems = favItems.value?.toMutableList() ?: mutableListOf()
        currentItems.remove(favItem)
        favItems.value = currentItems
        repository.saveFavItems(currentItems)
    }

    fun getMacs(adapter: FavAdapter, textView: TextView){
        val macs = adapter.extractMacAddresses()
        textView.text = macs.joinToString("\n")
    }



}