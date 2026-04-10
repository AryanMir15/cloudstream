package com.lagradost.cloudstream3.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.lagradost.cloudstream3.database.*
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class LocalLibraryCache(private val context: Context) {
    
    companion object {
        const val CACHE_FOLDER = "local_cache"
        const val POSTERS_FOLDER = "posters"
        const val EPISODES_FOLDER = "episodes"
        const val METADATA_FOLDER = "metadata"
    }
    
    private val cacheDir = File(context.filesDir, CACHE_FOLDER)
    
    init {
        // Ensure cache directories exist
        File(cacheDir, POSTERS_FOLDER).mkdirs()
        File(cacheDir, EPISODES_FOLDER).mkdirs()
        File(cacheDir, METADATA_FOLDER).mkdirs()
    }
    
    suspend fun cacheSeriesData(
        seriesId: String,
        name: String,
        posterUrl: String?,
        bannerUrl: String?,
        description: String?,
        rating: Int?,
        year: Int?,
        status: String?,
        episodes: List<com.lagradost.cloudstream3.EpisodeResponse>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Cache poster
            val posterPath = posterUrl?.let { downloadImage(it, "posters/${seriesId}_poster.jpg") }
            val bannerPath = bannerUrl?.let { downloadImage(it, "posters/${seriesId}_banner.jpg") }
            
            // 2. Cache series metadata
            val cachedSeries = CachedSeriesEntity(
                id = seriesId,
                name = name,
                posterPath = posterPath,
                bannerPath = bannerPath,
                description = description,
                rating = rating,
                year = year,
                status = status,
                cachedAt = System.currentTimeMillis(),
                lastRefetchedAt = System.currentTimeMillis()
            )
            
            context.setKey("local_cache/series/$seriesId", cachedSeries)
            
            // 3. Cache episodes
            val cachedEpisodes = episodes.map { episode ->
                val episodePosterPath = downloadEpisodeThumbnail(episode, seriesId)
                CachedEpisodeEntity(
                    id = episode.id.toString(),
                    seriesId = seriesId,
                    name = episode.name,
                    posterPath = episodePosterPath,
                    description = episode.description,
                    episode = episode.episode,
                    season = episode.season,
                    duration = episode.duration,
                    skipIntroStart = episode.skipintro?.start,
                    skipIntroEnd = episode.skipintro?.end,
                    filePath = null, // Will be set when downloaded
                    cachedAt = System.currentTimeMillis()
                )
            }
            
            context.setKey("local_cache/episodes/$seriesId", cachedEpisodes)
            
            // 4. Update cache status
            val cacheStatus = CacheStatusEntity(
                seriesId = seriesId,
                postersCached = posterPath != null,
                episodesCached = true,
                metadataCached = true,
                totalSize = calculateCacheSize(seriesId),
                lastAccessed = System.currentTimeMillis()
            )
            
            context.setKey("local_cache/status/$seriesId", cacheStatus)
            
            Result.success(Unit)
        } catch (e: Exception) {
            logError(e)
            Result.failure(e)
        }
    }
    
    suspend fun getCachedSeries(seriesId: String): CachedSeriesEntity? {
        return context.getKey<CachedSeriesEntity>("local_cache/series/$seriesId")
    }
    
    suspend fun getCachedEpisodes(seriesId: String): List<CachedEpisodeEntity> {
        return context.getKey<List<CachedEpisodeEntity>>("local_cache/episodes/$seriesId") ?: emptyList()
    }
    
    suspend fun updateEpisodeFilePath(seriesId: String, episodeId: String, filePath: String) {
        val episodes = getCachedEpisodes(seriesId).toMutableList()
        val episodeIndex = episodes.indexOfFirst { it.id == episodeId }
        if (episodeIndex != -1) {
            episodes[episodeIndex] = episodes[episodeIndex].copy(filePath = filePath)
            context.setKey("local_cache/episodes/$seriesId", episodes)
        }
    }
    
    private suspend fun downloadImage(url: String, filename: String): String? = withContext(Dispatchers.IO) {
        try {
            val posterFile = File(cacheDir, filename)
            if (posterFile.exists()) return@withContext posterFile.absolutePath
            
            val connection = URL(url).openConnection()
            connection.getInputStream().use { input ->
                FileOutputStream(posterFile).use { output ->
                    input.copyTo(output)
                }
            }
            posterFile.absolutePath
        } catch (e: Exception) {
            logError(e)
            null
        }
    }
    
    private suspend fun downloadEpisodeThumbnail(
        episode: com.lagradost.cloudstream3.EpisodeResponse,
        seriesId: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            // Try to get thumbnail from episode poster first
            episode.poster?.let { posterUrl ->
                downloadImage(posterUrl, "episodes/${seriesId}_${episode.id}_thumb.jpg")?.let { return@withContext it }
            }
            
            // If no poster, we'll need to generate from video file when available
            null
        } catch (e: Exception) {
            logError(e)
            null
        }
    }
    
    suspend fun generateEpisodeThumbnail(videoFile: File, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)
            
            // Get frame at 20 seconds (20,000,000 microseconds)
            val bitmap = retriever.getFrameAtTime(20_000_000)
            retriever.release()
            
            if (bitmap != null) {
                val thumbnailFile = File(cacheDir, outputPath)
                FileOutputStream(thumbnailFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logError(e)
            false
        }
    }
    
    private fun calculateCacheSize(seriesId: String): Long {
        val seriesDir = File(cacheDir, POSTERS_FOLDER)
        val episodesDir = File(cacheDir, EPISODES_FOLDER)
        
        var totalSize = 0L
        
        // Calculate poster sizes
        seriesDir.listFiles { file -> file.name.startsWith(seriesId) }?.forEach { file ->
            totalSize += file.length()
        }
        
        // Calculate episode thumbnail sizes
        episodesDir.listFiles { file -> file.name.startsWith(seriesId) }?.forEach { file ->
            totalSize += file.length()
        }
        
        return totalSize
    }
    
    suspend fun getCacheStatus(seriesId: String): CacheStatusEntity? {
        return context.getKey<CacheStatusEntity>("local_cache/status/$seriesId")
    }
    
    suspend fun clearCache(seriesId: String) {
        context.removeKey("local_cache/series/$seriesId")
        context.removeKey("local_cache/episodes/$seriesId")
        context.removeKey("local_cache/status/$seriesId")
        
        // Delete cached files
        File(cacheDir, POSTERS_FOLDER).listFiles { file -> file.name.startsWith(seriesId) }?.forEach { it.delete() }
        File(cacheDir, EPISODES_FOLDER).listFiles { file -> file.name.startsWith(seriesId) }?.forEach { it.delete() }
    }
    
    suspend fun getTotalCacheSize(): Long {
        val posterDir = File(cacheDir, POSTERS_FOLDER)
        val episodesDir = File(cacheDir, EPISODES_FOLDER)
        
        return (posterDir.walkTopDown().sumOf { it.length() } + 
               episodesDir.walkTopDown().sumOf { it.length() })
    }
}