package com.citrus.blsc.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.citrus.blsc.data.database.AppDatabase
import com.citrus.blsc.data.model.SearchHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceInfoViewModel(
    application: Application,
    private val macAddress: String,
) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).searchHistoryDao()

    private val _state = MutableLiveData<DeviceInfoState>()
    val state: LiveData<DeviceInfoState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DeviceInfoState.Loading
            try {
                val fromTimestamp = System.currentTimeMillis() - SEVEN_DAYS_MS
                val (total, weekCount, allItems, weekItems) = withContext(Dispatchers.IO) {
                    val t = dao.getDetectionCountForMac(macAddress)
                    val w = dao.getDetectionCountForMacSince(macAddress, fromTimestamp)
                    val all = dao.getHistoryItemsForMac(macAddress)
                    val week = dao.getHistoryItemsForMacSince(macAddress, fromTimestamp)
                    Tuple(t, w, all, week)
                }
                _state.value = DeviceInfoState.Ready(
                    totalDetections = total,
                    detectionsLast7Days = weekCount,
                    allDiscoveries = allItems,
                    weekDiscoveries = weekItems,
                )
            } catch (e: Exception) {
                _state.value = DeviceInfoState.Error(e.message ?: e.toString())
            }
        }
    }

    private data class Tuple(
        val total: Int,
        val weekCount: Int,
        val all: List<SearchHistoryItem>,
        val week: List<SearchHistoryItem>,
    )

    sealed class DeviceInfoState {
        data object Loading : DeviceInfoState()
        data class Ready(
            val totalDetections: Int,
            val detectionsLast7Days: Int,
            val allDiscoveries: List<SearchHistoryItem>,
            val weekDiscoveries: List<SearchHistoryItem>,
        ) : DeviceInfoState()

        data class Error(val message: String) : DeviceInfoState()
    }

    class Factory(
        private val application: Application,
        private val macAddress: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DeviceInfoViewModel::class.java))
            return DeviceInfoViewModel(application, macAddress) as T
        }
    }

    companion object {
        private val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
