package com.lagradost.cloudstream3

/**
 * Application-wide constants to avoid hardcoded strings scattered across codebase.
 * This makes it easier to update values and ensures consistency.
 */
object AppConstants {
    /**
     * Provider name for local cache entries
     */
    const val LOCAL_CACHE_PROVIDER = "Local Cache"

    /**
     * Set of all local-only provider names
     */
    val LOCAL_PROVIDERS = setOf(
        LOCAL_CACHE_PROVIDER,
        "Local Download"
    )

    /**
     * URL prefixes that indicate local content
     */
    val LOCAL_URL_PREFIXES = setOf(
        "local://",
        "file://"
    )

    /**
     * Checks if a provider name is a local provider
     */
    fun isLocalProvider(apiName: String?): Boolean {
        return apiName in LOCAL_PROVIDERS
    }

    /**
     * Checks if a URL is a local URL
     */
    fun isLocalUrl(url: String?): Boolean {
        return url != null && LOCAL_URL_PREFIXES.any { prefix -> url.startsWith(prefix) }
    }

    /**
     * Checks if either the provider or URL indicates local content
     */
    fun isLocalContent(apiName: String?, url: String?): Boolean {
        return isLocalProvider(apiName) || isLocalUrl(url)
    }
}
