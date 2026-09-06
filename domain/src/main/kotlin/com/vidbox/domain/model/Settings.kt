package com.vidbox.domain.model

enum class AppTheme { SYSTEM, LIGHT, DARK }
data class AppSettings(
    val destinationTree: String? = null,
    val destinationLabel: String? = null,
    val defaultQuality: Int = 1080,
    val defaultContainer: String = "mp4",
    val wifiOnly: Boolean = false,
    val maxConcurrent: Int = 2,
    val completionNotifications: Boolean = true,
    val theme: AppTheme = AppTheme.SYSTEM,
)

data class NetworkStatus(
    val connected: Boolean = false,
    val wifi: Boolean = false,
    val cellular: Boolean = false,
    val metered: Boolean = true,
) {
    fun permits(settings: AppSettings): Boolean = connected &&
        (!settings.wifiOnly || (wifi && !metered))
}
