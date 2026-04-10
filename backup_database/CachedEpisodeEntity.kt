package com.lagradost.cloudstream3.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_episodes")
data class CachedEpisodeEntity(
    @PrimaryKey val id: String,
    val seriesId: String,
    val name: String,
    val posterPath: String?, // 20s thumbnail
    val description: String?,
    val episode: Int,
    val season: Int?,
    val duration: Long?,
    val skipIntroStart: Long?,
    val skipIntroEnd: Long?,
    val filePath: String?, // Local file if downloaded
    val cachedAt: Long
)