package com.lagradost.cloudstream3.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache_status")
data class CacheStatusEntity(
    @PrimaryKey val seriesId: String,
    val postersCached: Boolean,
    val episodesCached: Boolean,
    val metadataCached: Boolean,
    val totalSize: Long, // Cache size in bytes
    val lastAccessed: Long
)