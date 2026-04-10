package com.lagradost.cloudstream3.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheStatusDao {
    @Query("SELECT * FROM cache_status WHERE seriesId = :seriesId")
    suspend fun getCacheStatus(seriesId: String): CacheStatusEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateCacheStatus(status: CacheStatusEntity)
    
    @Query("UPDATE cache_status SET lastAccessed = :timestamp WHERE seriesId = :seriesId")
    suspend fun updateLastAccessed(seriesId: String, timestamp: Long)
    
    @Query("DELETE FROM cache_status WHERE seriesId = :seriesId")
    suspend fun deleteCacheStatus(seriesId: String)
    
    @Query("SELECT SUM(totalSize) FROM cache_status")
    suspend fun getTotalCacheSize(): Long?
    
    @Query("SELECT * FROM cache_status WHERE lastAccessed < :threshold")
    suspend fun getOldCacheEntries(threshold: Long): List<CacheStatusEntity>
}