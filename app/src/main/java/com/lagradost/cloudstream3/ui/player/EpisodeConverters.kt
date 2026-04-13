package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.player.ExtractorUri
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DataStoreHelper.getVideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.VideoWatchState

/**
 * Extension functions to convert between different episode representations.
 * This provides a clean, testable way to convert ExtractorUri to ResultEpisode.
 */

/**
 * Converts an ExtractorUri (local playback) to ResultEpisode with cached metadata.
 * This is used to provide rich metadata in the player's episode overlay for local files.
 */
fun ExtractorUri.toResultEpisode(
    index: Int,
    cachedEpisode: DownloadObjects.DownloadEpisodeCached? = null,
    cachedHeader: DownloadObjects.DownloadHeaderCached? = null
): ResultEpisode {
    val posDur = getViewPos(id ?: 0)
    
    android.util.Log.d(
        "EpisodeConverters",
        "Converting ExtractorUri to ResultEpisode: episode=${episode}, " +
                "cached=${cachedEpisode?.name}, " +
                "poster=${cachedEpisode?.poster != null}, " +
                "header=${cachedHeader?.name}"
    )
    
    return ResultEpisode(
        headerName = headerName ?: cachedHeader?.name ?: "",
        name = cachedEpisode?.name ?: name ?: "Episode ${episode ?: (index + 1)}",
        poster = cachedEpisode?.poster,
        episode = episode ?: (index + 1),
        seasonIndex = season?.let { it - 1 } ?: cachedEpisode?.season?.let { it - 1 },
        season = season ?: cachedEpisode?.season,
        data = "",
        apiName = cachedHeader?.apiName ?: "Local",
        id = id ?: 0,
        index = index,
        position = posDur?.position ?: 0,
        duration = posDur?.duration ?: 0,
        score = cachedEpisode?.score,
        description = cachedEpisode?.description,
        isFiller = null,
        tvType = tvType ?: cachedHeader?.type ?: TvType.Anime,
        parentId = parentId ?: cachedEpisode?.parentId ?: 0,
        videoWatchState = getVideoWatchState(id ?: 0) ?: VideoWatchState.None,
        totalEpisodeIndex = episode
    )
}

/**
 * Loads cached episode data for a given episode ID.
 */
fun loadCachedEpisode(episodeId: Int?): DownloadObjects.DownloadEpisodeCached? {
    return episodeId?.let { id ->
        CloudStreamApp.getKey<DownloadObjects.DownloadEpisodeCached>(
            DOWNLOAD_EPISODE_CACHE,
            id.toString()
        )
    }
}

/**
 * Loads cached header (show-level) data for a given parent ID.
 */
fun loadCachedHeader(parentId: Int?): DownloadObjects.DownloadHeaderCached? {
    return parentId?.let { id ->
        CloudStreamApp.getKey<DownloadObjects.DownloadHeaderCached>(
            DOWNLOAD_HEADER_CACHE,
            id.toString()
        )
    }
}

/**
 * Loads all cached episodes for a given show (parent ID).
 * This is used to show the full episode list when playing local files.
 */
fun loadAllCachedEpisodes(parentId: Int?): List<DownloadObjects.DownloadEpisodeCached> {
    if (parentId == null) return emptyList()
    
    val allKeys = CloudStreamApp.getKeys(DOWNLOAD_EPISODE_CACHE)
    android.util.Log.d("EpisodeConverters", "loadAllCachedEpisodes: parentId=$parentId, total keys in cache=${allKeys?.size}")
    
    // Check first few keys to see what's stored
    allKeys?.take(5)?.forEach { key ->
        android.util.Log.d("EpisodeConverters", "Sample cache key: $key")
    }
    
    val allEpisodes = allKeys
        ?.mapNotNull { key ->
            // Keys from getKeys already include cache name prefix, so use getKey without cache name parameter
            val data = CloudStreamApp.getKey<DownloadObjects.DownloadEpisodeCached>(key)
            if (data == null) {
                android.util.Log.d("EpisodeConverters", "Failed to load cache entry for key: $key")
            }
            data
        }
        ?: emptyList()
    
    android.util.Log.d("EpisodeConverters", "loadAllCachedEpisodes: loaded ${allEpisodes.size} episodes, parentIds=${allEpisodes.map { it.parentId }.distinct()}")
    
    val filtered = allEpisodes.filter { it.parentId == parentId }
    android.util.Log.d("EpisodeConverters", "loadAllCachedEpisodes: filtered to ${filtered.size} episodes with matching parentId")
    
    // Deduplicate by episode number (keep the first occurrence)
    val deduplicated = filtered.distinctBy { it.episode }
    android.util.Log.d("EpisodeConverters", "loadAllCachedEpisodes: deduplicated to ${deduplicated.size} episodes")
    
    return deduplicated.sortedBy { it.episode }
}

/**
 * Extracts the episode number from a filename.
 * Supports patterns like:
 * - "Episode 1", "Episode 1.mkv"
 * - "E1", "E01"
 * - "S01E01"
 * - "1. Title", "01 Title"
 */
fun extractEpisodeNumber(filename: String): Int? {
    // Pattern 1: Episode X (any format)
    val episodePattern = Regex("(?i)episode[\\s_-]*(\\d+)")
    episodePattern.find(filename)?.let {
        return it.groupValues[1].toInt()
    }
    
    // Pattern 2: S01E01 format
    val seasonEpPattern = Regex("S\\d+E(\\d+)", RegexOption.IGNORE_CASE)
    seasonEpPattern.find(filename)?.let {
        return it.groupValues[1].toInt()
    }
    
    // Pattern 3: Just the number at start (e.g., "1. Title", "01 Title")
    val startsWithNumber = Regex("^(\\d+)[.\\s_-]")
    startsWithNumber.find(filename)?.let {
        return it.groupValues[1].toInt()
    }
    
    // Pattern 4: E01 or E1 format
    val ePattern = Regex("\\bE(\\d+)\\b", RegexOption.IGNORE_CASE)
    ePattern.find(filename)?.let {
        return it.groupValues[1].toInt()
    }
    
    return null
}
