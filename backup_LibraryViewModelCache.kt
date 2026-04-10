package com.lagradost.cloudstream3.ui.library

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.throwAbleToResource
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.cloudstream3.utils.LocalLibraryCache
import kotlinx.coroutines.launch

enum class ListSorting(@StringRes val stringRes: Int) {
    Query(R.string.none),
    RatingHigh(R.string.sort_rating_desc),
    RatingLow(R.string.sort_rating_asc),
    UpdatedNew(R.string.sort_updated_new),
    UpdatedOld(R.string.sort_updated_old),
    AlphabeticalA(R.string.sort_alphabetical_a),
    AlphabeticalZ(R.string.sort_alphabetical_z),
    ReleaseDateNew(R.string.sort_release_date_new),
    ReleaseDateOld(R.string.sort_release_date_old),
    CacheFirst(R.string.sort_cache_first), // New sorting option
}

const val LAST_SYNC_API_KEY = "last_sync_api"
const val PREFER_LOCAL_CACHE_KEY = "prefer_local_cache"

class LibraryViewModel : ViewModel() {
    private val localLibraryCache = LocalLibraryCache(MainActivity.getInstance())
    
    fun switchPage(page: Int) {
        _currentPage.postValue(page)
    }

    private val _currentPage: MutableLiveData<Int> = MutableLiveData(0)
    val currentPage: LiveData<Int> = _currentPage

    private val _pages: MutableLiveData<Resource<List<SyncAPI.Page>>> = MutableLiveData(null)
    val pages: LiveData<Resource<List<SyncAPI.Page>>> = _pages

    private val _currentApiName: MutableLiveData<String> = MutableLiveData("")
    val currentApiName: LiveData<String> = _currentApiName

    private val availableSyncApis
        get() = AccountManager.syncApis.filter { it.isAvailable }

    var currentSyncApi = availableSyncApis.let { allApis ->
        val lastSelection = getKey<String>("$currentAccount/$LAST_SYNC_API_KEY")
        availableSyncApis.firstOrNull { it.name == lastSelection } ?: allApis.firstOrNull()
    }
        private set(value) {
            field = value
            setKey("$currentAccount/$LAST_SYNC_API_KEY", field?.name)
        }

    val availableApiNames: List<String>
        get() = availableSyncApis.map { it.name }

    var sortingMethods = emptyList<ListSorting>()
        private set

    var currentSortingMethod: ListSorting? = sortingMethods.firstOrNull()
        private set

    fun switchList(name: String) {
        currentSyncApi = availableSyncApis[availableApiNames.indexOf(name)]
        _currentApiName.postValue(currentSyncApi?.name)
        reloadPages(true)
    }

    fun sort(method: ListSorting, query: String? = null) = ioSafe {
        val value = _pages.value ?: return@ioSafe
        if (value is Resource.Success) {
            sort(method, query, value.value)
        }
    }

    private fun sort(method: ListSorting, query: String? = null, items: List<SyncAPI.Page>) {
        currentSortingMethod = method
        DataStoreHelper.librarySortingMode = method.ordinal

        when (method) {
            ListSorting.CacheFirst -> {
                // Load cached data first, then online data
                val cachedPages = loadCachedPages()
                val onlinePages = loadOnlinePages(query, items)
                val allPages = mergeCachedAndOnlinePages(cachedPages, onlinePages)
                _pages.postValue(Resource.Success(allPages))
            }
            else -> {
                // Original behavior - load from online API
                items.forEach { page ->
                    page.sort(method, query)
                }
                _pages.postValue(Resource.Success(items))
            }
        }
    }
    
    private fun loadCachedPages(): List<SyncAPI.Page> {
        val cachedSeries = localLibraryCache.getAllCachedSeries()
        return cachedSeries.map { series ->
            val episodes = localLibraryCache.getCachedEpisodes(series.id)
            SyncAPI.Page(
                name = series.name,
                items = episodes.map { episode ->
                    SearchResponse(
                        id = episode.id.toInt(),
                        name = episode.name,
                        poster = episode.posterPath,
                        url = "local://${episode.id}", // Special URL for local content
                        type = SearchResponse.Type.TVSHOW,
                        apiName = "Local Cache",
                        posterPath = episode.posterPath,
                        rating = null,
                        year = null,
                        episode = episode.episode,
                        season = episode.season,
                        date = null,
                        description = episode.description,
                        duration = episode.duration,
                        comesFrom = "Local Cache",
                        skipintro = if (episode.skipIntroStart != null && episode.skipIntroEnd != null) {
                            com.lagradost.cloudstream3.SkipIntroData(episode.skipIntroStart, episode.skipIntroEnd)
                        } else null,
                        parentId = series.id.toInt()
                    )
                }
            )
        }
    }
    
    private fun loadOnlinePages(query: String?, cachedPages: List<SyncAPI.Page>): List<SyncAPI.Page> {
        // Only load online data if not cache-first mode or if cache is empty
        if (cachedPages.isNotEmpty() && currentSortingMethod == ListSorting.CacheFirst) {
            return emptyList()
        }
        
        return ioSafe {
            currentSyncApi?.let { repo ->
                val libraryResource = repo.library()
                val err = libraryResource.exceptionOrNull()
                if (err != null) {
                    return@let emptyList()
                }
                val library = libraryResource.getOrNull()
                if (library == null) {
                    return@let emptyList()
                }

                sortingMethods = library.supportedListSorting.toList()
                repo.requireLibraryRefresh = false

                library.allLibraryLists.map {
                    SyncAPI.Page(
                        it.name,
                        it.items
                    )
                }
            } ?: emptyList()
        }
    }
    
    private fun mergeCachedAndOnlinePages(
        cachedPages: List<SyncAPI.Page>,
        onlinePages: List<SyncAPI.Page>
    ): List<SyncAPI.Page> {
        val allSeries = mutableMapOf<String, SyncAPI.Page>()
        
        // Add cached series first
        cachedPages.forEach { page ->
            allSeries[page.name] = page
        }
        
        // Add online series that aren't cached
        onlinePages.forEach { page ->
            if (!allSeries.containsKey(page.name)) {
                allSeries[page.name] = page
            }
        }
        
        return allSeries.values.toList()
    }
    
    // New method to cache series data
    fun cacheSeriesData(seriesId: String, searchResponse: SearchResponse) = viewModelScope.launch {
        try {
            // Get episodes from the API
            currentSyncApi?.let { repo ->
                val episodesResource = repo.episodes(seriesId.toInt())
                val episodes = episodesResource.getOrNull() ?: emptyList()
                
                localLibraryCache.cacheSeriesData(
                    seriesId = seriesId,
                    name = searchResponse.name,
                    posterUrl = searchResponse.poster,
                    bannerUrl = null, // Could be added if available
                    description = searchResponse.description,
                    rating = searchResponse.rating,
                    year = searchResponse.year,
                    status = null, // Could be derived from API
                    episodes = episodes
                )
            }
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    // New method to check if series is cached
    fun isSeriesCached(seriesId: String): Boolean {
        return localLibraryCache.getCachedSeries(seriesId) != null
    }
    
    // New method to get cached episode with file path
    fun getCachedEpisodeWithFile(seriesId: String, episodeId: String): com.lagradost.cloudstream3.database.CachedEpisodeEntity? {
        val episodes = localLibraryCache.getCachedEpisodes(seriesId)
        return episodes.find { it.id == episodeId && it.filePath != null }
    }
    
    // New method to update episode file path
    fun updateEpisodeFilePath(seriesId: String, episodeId: String, filePath: String) = viewModelScope.launch {
        localLibraryCache.updateEpisodeFilePath(seriesId, episodeId, filePath)
    }
    
    // New method to clear cache for a series
    fun clearSeriesCache(seriesId: String) = viewModelScope.launch {
        localLibraryCache.clearCache(seriesId)
        reloadPages(true) // Reload to reflect cache clear
    }
    
    // New method to get total cache size
    fun getTotalCacheSize(): Long {
        return localLibraryCache.getTotalCacheSize()
    }

    init {
        MainActivity.reloadLibraryEvent += ::reloadPages
    }

    override fun onCleared() {
        MainActivity.reloadLibraryEvent -= ::reloadPages
        super.onCleared()
    }
}