package com.vidbox.domain.model

import kotlinx.serialization.Serializable

enum class AppTheme { SYSTEM, LIGHT, DARK }

/** What happens when the destination already holds a file with the same name. */
enum class DuplicatePolicy { KEEP_BOTH, SKIP }

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
    /** HTTPS homepage for the in-app browser; null uses the built-in default. */
    val browserHomepage: String? = null,
    val browserDesktop: Boolean = false,
    val browserJavaScript: Boolean = true,
    val browserCookies: Boolean = true,
    /** Material You dynamic palettes on Android 12+. */
    val dynamicColors: Boolean = true,
)

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
