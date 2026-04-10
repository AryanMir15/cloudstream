package com.lagradost.cloudstream3.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete

@Dao
interface CachedEpisodesDao {
    @Query("SELECT * FROM cached_episodes WHERE seriesId = :seriesId ORDER BY episode ASC")
    suspend fun getEpisodes(seriesId: String): List<CachedEpisodeEntity>
    
    @Query("SELECT * FROM cached_episodes WHERE id = :id")
    suspend fun getEpisode(id: String): CachedEpisodeEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheEpisode(episode: CachedEpisodeEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheAllEpisodes(episodes: List<CachedEpisodeEntity>)
    
    @Query("UPDATE cached_episodes SET filePath = :filePath WHERE id = :id")
    suspend fun updateEpisodeFilePath(id: String, filePath: String)
    
    @Query("DELETE FROM cached_episodes WHERE seriesId = :seriesId")
    suspend fun deleteCachedEpisodes(seriesId: String)
    
    @Query("SELECT COUNT(*) FROM cached_episodes WHERE seriesId = :seriesId")
    suspend fun getCachedEpisodeCount(seriesId: String): Int
}