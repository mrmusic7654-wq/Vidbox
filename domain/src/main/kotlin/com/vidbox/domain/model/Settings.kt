package com.vidbox.domain.model

enum class AppTheme { SYSTEM, LIGHT, DARK }

/** What happens when the destination already holds a file with the same name. */
enum class DuplicatePolicy { KEEP_BOTH, SKIP }

/** A home-screen shortcut tile the user added themselves. */
data class SiteShortcut(val label: String, val url: String)

data class AppSettings(
    val destinationTree: String? = null,
    val destinationLabel: String? = null,
    val defaultQuality: Int = 1080,
    val defaultContainer: String = "mp4",
    val wifiOnly: Boolean = false,
    val maxConcurrent: Int = 2,
    val completionNotifications: Boolean = true,
    val theme: AppTheme = AppTheme.SYSTEM,
    /** Reconnect resumes network-paused downloads without asking again. */
    val autoResume: Boolean = true,
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.KEEP_BOTH,
    /** HTTPS homepage for the in-app browser; null uses the search engine's start page. */
    val browserHomepage: String? = null,
    val browserDesktop: Boolean = false,
    val browserJavaScript: Boolean = true,
    val browserCookies: Boolean = true,
    /** Material You dynamic palettes on Android 12+. Off by default so Vidbox keeps its identity. */
    val dynamicColors: Boolean = false,
    // Browser
    val searchEngineId: String = SearchEngines.DEFAULT.id,
    /** Template with `%s` when [searchEngineId] is [SearchEngines.CUSTOM_ID]. */
    val customSearchTemplate: String? = null,
    val restoreTabs: Boolean = true,
    val saveBrowsingHistory: Boolean = true,
    val addressSuggestions: Boolean = true,
    // Video search on the home screen (most recent first, bounded, never synced).
    val searchHistory: List<String> = emptyList(),
    /** Custom shortcut tiles added on the home screen, shown after the built-in sites. */
    val siteShortcuts: List<SiteShortcut> = emptyList(),
    // Privacy protection
    val blockAds: Boolean = true,
    val blockTrackers: Boolean = true,
    /** Refresh filter lists on Wi-Fi via WorkManager; lists shipped with the app are always available. */
    val filterListUpdates: Boolean = true,
    // Network
    val proxy: ProxyConfig = ProxyConfig(),
    // Notifications
    val downloadProgressNotifications: Boolean = true,
) {
    val searchEngine: SearchEngine get() = SearchEngines.resolve(searchEngineId, customSearchTemplate)
    val contentBlockingEnabled: Boolean get() = blockAds || blockTrackers
}

data class NetworkStatus(
    val connected: Boolean = false,
    val wifi: Boolean = false,
    val cellular: Boolean = false,
    val metered: Boolean = true,
    val blocked: Boolean = false,
) {
    fun permits(settings: AppSettings): Boolean = connected && !blocked &&
        (!settings.wifiOnly || (wifi && !metered))
}
