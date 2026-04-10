package com.lagradost.cloudstream3.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete

@Dao
interface CachedSeriesDao {
    @Query("SELECT * FROM cached_series WHERE id = :id")
    suspend fun getSeries(id: String): CachedSeriesEntity?
    
    @Query("SELECT * FROM cached_series ORDER BY name ASC")
    suspend fun getAllSeries(): List<CachedSeriesEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheSeries(series: CachedSeriesEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheAllSeries(series: List<CachedSeriesEntity>)
    
    @Query("DELETE FROM cached_series WHERE id = :id")
    suspend fun deleteCachedSeries(id: String)
    
    @Query("SELECT COUNT(*) FROM cached_series")
    suspend fun getCachedSeriesCount(): Int
    
    @Query("DELETE FROM cached_series")
    suspend fun clearAllCachedSeries()
}