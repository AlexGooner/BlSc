package com.citrus.blsc.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.citrus.blsc.data.database.AppDatabase
import com.citrus.blsc.data.model.SearchHistoryItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)

    val historyItems = MutableLiveData<List<SearchHistoryItem>>()
    val isLoading = MutableLiveData<Boolean>()
    val error = MutableLiveData<String>()

    fun loadAllHistoryItems() {
        viewModelScope.launch {
            try {
                isLoading.value = true
                val items = database.searchHistoryDao().getAllHistoryItems().first()
                historyItems.value = items
            } catch (e: Exception) {
                error.value = e.message
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadFavouriteHistoryItems() {
        viewModelScope.launch {
            try {
                isLoading.value = true
                val items = database.searchHistoryDao().getFavouriteHistoryItems().first()
                historyItems.value = items
            } catch (e: Exception) {
                error.value = e.message
            } finally {
                isLoading.value = false
            }
        }
    }

    fun searchHistoryItems(query: String) {
        viewModelScope.launch {
            try {
                isLoading.value = true
                val items = database.searchHistoryDao().searchHistoryItems(query).first()
                historyItems.value = items
            } catch (e: Exception) {
                error.value = e.message
            } finally {
                isLoading.value = false
            }
        }
    }

    fun searchFavouriteHistoryItems(query: String) {
        viewModelScope.launch {
            try {
                isLoading.value = true
                val items = database.searchHistoryDao().searchFavouriteHistoryItems(query).first()
                historyItems.value = items
            } catch (e: Exception) {
                error.value = e.message
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadHistoryItemsByDate(date: String) {
        viewModelScope.launch {
            try {
                isLoading.value = true
                val items = database.searchHistoryDao().getHistoryItemsByDate(date).first()
                historyItems.value = items
            } catch (e: Exception) {
                error.value = e.message
            } finally {
                isLoading.value = false
            }
        }
    }

    fun loadFavouriteHistoryItemsByDate(date: String) {
        viewModelScope.launch {
            try {
                isLoading.value = true
                val items = database.searchHistoryDao().getFavouriteHistoryItemsByDate(date).first()
                historyItems.value = items
            } catch (e: Exception) {
                error.value = e.message
            } finally {
                isLoading.value = false
            }
        }
    }

    fun addToFavourites(item: SearchHistoryItem) {
        viewModelScope.launch {
            try {
                val updatedItem = item.copy(isFavourite = true)
                database.searchHistoryDao().updateHistoryItem(updatedItem)
                val currentItems = historyItems.value?.toMutableList() ?: mutableListOf()
                val index = currentItems.indexOfFirst { it.id == item.id }
                if (index != -1) {
                    currentItems[index] = updatedItem
                    historyItems.value = currentItems
                }
            } catch (e: Exception) {
                error.value = e.message
            }
        }
    }

    fun removeFromFavourites(item: SearchHistoryItem) {
        viewModelScope.launch {
            try {
                val updatedItem = item.copy(isFavourite = false)
                database.searchHistoryDao().updateHistoryItem(updatedItem)
                val currentItems = historyItems.value?.toMutableList() ?: mutableListOf()
                val index = currentItems.indexOfFirst { it.id == item.id }
                if (index != -1) {
                    currentItems[index] = updatedItem
                    historyItems.value = currentItems
                }
            } catch (e: Exception) {
                error.value = e.message
            }
        }
    }

    fun deleteItem(item: SearchHistoryItem) {
        viewModelScope.launch {
            try {
                database.searchHistoryDao().deleteHistoryItem(item)
                val currentItems = historyItems.value?.toMutableList() ?: mutableListOf()
                currentItems.removeAll { it.id == item.id }
                historyItems.value = currentItems
            } catch (e: Exception) {
                error.value = e.message
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                database.searchHistoryDao().clearAllHistory()
                historyItems.value = emptyList()
            } catch (e: Exception) {
                error.value = e.message
            }
        }
    }
}
