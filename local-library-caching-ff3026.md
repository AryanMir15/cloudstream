# Cloudstream Local Library Caching Implementation Plan

This plan implements a true local library with cached metadata and offline behavior, eliminating constant refetching and providing instant access to downloaded content.

## **Phase 1: Database Schema Setup**

### **1.1 Create Cache Database Entities**
- **CachedSeriesEntity**: Store series metadata locally
- **CachedEpisodeEntity**: Store episode data with local file paths  
- **CacheStatusEntity**: Track cache status and sizes

### **1.2 Database Integration**
- Add new entities to existing Room database
- Increment database version for migration
- Create DAOs for cache operations

## **Phase 2: Cache Service Implementation**

### **2.1 Core Caching Logic**
- **LocalLibraryCache** service for metadata management
- One-time download and storage of all series data
- Poster and banner caching to local files
- Episode list caching with file path associations

### **2.2 Image Caching System**
- Download posters/banners to app's private storage
- Generate episode thumbnails from video files (20-second mark)
- Organized file structure under `/files/local_cache/`

## **Phase 3: Library UI Integration**

### **3.1 Modify LibraryFragment**
- Add cache-first loading logic
- "Refetch Metadata" button for manual updates
- Offline indicator for cached content
- Seamless switching between cached/online data

### **3.2 Update LibraryViewModel**
- Cache-aware data loading
- Background cache population
- Cache invalidation logic

## **Phase 4: Settings Integration**

### **4.1 Local Media Settings**
- Toggle for cache preferences
- Downloads folder configuration
- Cache size management

## **Technical Implementation Details**

### **Database Tables**
```kotlin
// Series metadata cache
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

// Episode data with local file associations
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
```

### **Cache File Structure**
```
/data/data/com.lagradost.cloudstream3/files/local_cache/
├── posters/
├── episodes/
└── metadata/
```

### **Key Benefits**
- **Instant Loading**: No network calls for cached content
- **Offline First**: True local library experience  
- **User Control**: Manual refetch only when needed
- **Storage Efficient**: Smart caching with size tracking

## **Implementation Priority**
1. **Database schema** (Foundation)
2. **Cache service** (Core logic)  
3. **UI integration** (User experience)
4. **Settings** (User control)

This approach eliminates the annoying constant refetching while maintaining full feature parity with online library.