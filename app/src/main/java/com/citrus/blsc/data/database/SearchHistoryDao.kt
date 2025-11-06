package com.citrus.blsc.data.database

import androidx.room.*
import com.citrus.blsc.data.model.SearchHistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    fun getAllHistoryItems(): Flow<List<SearchHistoryItem>>
    
    @Query("SELECT * FROM search_history WHERE isFavourite = 1 ORDER BY timestamp DESC")
    fun getFavouriteHistoryItems(): Flow<List<SearchHistoryItem>>
    
    @Query("SELECT * FROM search_history WHERE macAddress LIKE '%' || :query || '%' OR deviceName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchHistoryItems(query: String): Flow<List<SearchHistoryItem>>
    
    @Query("SELECT * FROM search_history WHERE isFavourite = 1 AND (macAddress LIKE '%' || :query || '%' OR deviceName LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchFavouriteHistoryItems(query: String): Flow<List<SearchHistoryItem>>
    
    @Query("SELECT * FROM search_history WHERE date(timestamp/1000, 'unixepoch') = :date ORDER BY timestamp DESC")
    fun getHistoryItemsByDate(date: String): Flow<List<SearchHistoryItem>>
    
    @Query("SELECT * FROM search_history WHERE isFavourite = 1 AND date(timestamp/1000, 'unixepoch') = :date ORDER BY timestamp DESC")
    fun getFavouriteHistoryItemsByDate(date: String): Flow<List<SearchHistoryItem>>
    
    @Query("SELECT DISTINCT date(timestamp/1000, 'unixepoch') as date FROM search_history ORDER BY timestamp DESC")
    fun getAvailableDates(): Flow<List<String>>
    
    @Query("SELECT * FROM search_history WHERE macAddress = :macAddress ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestItemByMac(macAddress: String): SearchHistoryItem?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(item: SearchHistoryItem): Long
    
    @Update
    suspend fun updateHistoryItem(item: SearchHistoryItem)
    
    @Delete
    suspend fun deleteHistoryItem(item: SearchHistoryItem)
    
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteHistoryItemById(id: Long)
    
    @Query("DELETE FROM search_history")
    suspend fun clearAllHistory()
    
    @Query("DELETE FROM search_history WHERE isFavourite = 0")
    suspend fun clearNonFavouriteHistory()
    
    @Query("UPDATE search_history SET isFavourite = :isFavourite WHERE id = :id")
    suspend fun updateFavouriteStatus(id: Long, isFavourite: Boolean)
    
    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun getHistoryCount(): Int
    
    @Query("SELECT COUNT(*) FROM search_history WHERE isFavourite = 1")
    suspend fun getFavouriteHistoryCount(): Int
}
