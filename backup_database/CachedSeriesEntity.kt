package com.lagradost.cloudstream3.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_series")
data class CachedSeriesEntity(
    @PrimaryKey val id: String,
    val name: String,
    val posterPath: String?,
    val bannerPath: String?,
    val description: String?,
    val rating: Int?,
    val year: Int?,
    val status: String?,
    val cachedAt: Long,
    val lastRefetchedAt: Long
)