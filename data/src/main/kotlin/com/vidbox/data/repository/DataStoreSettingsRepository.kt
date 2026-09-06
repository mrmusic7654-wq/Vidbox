package com.vidbox.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.SettingsRepository
import com.vidbox.domain.util.BrowserLinks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferences: DataStore<Preferences> by preferencesDataStore(name = "vidbox_settings")

/** Pure mapping between DataStore rows and AppSettings, unit-tested without DataStore lifecycle. */
internal object SettingsEncoding {
    fun decode(prefs: Preferences): AppSettings = AppSettings(
        destinationTree = prefs[Keys.tree], destinationLabel = prefs[Keys.label],
        defaultQuality = prefs[Keys.quality] ?: 1080, defaultContainer = prefs[Keys.container] ?: "mp4",
        wifiOnly = prefs[Keys.wifi] ?: false, maxConcurrent = (prefs[Keys.concurrent] ?: 2).coerceIn(1, 4),
        completionNotifications = prefs[Keys.notifications] ?: true,
        theme = runCatching { AppTheme.valueOf(prefs[Keys.theme].orEmpty()) }.getOrDefault(AppTheme.SYSTEM),
        autoResume = prefs[Keys.autoResume] ?: true,
        duplicatePolicy = runCatching { DuplicatePolicy.valueOf(prefs[Keys.duplicates].orEmpty()) }
            .getOrDefault(DuplicatePolicy.KEEP_BOTH),
        browserHomepage = prefs[Keys.homepage]?.takeIf(String::isNotBlank),
        browserDesktop = prefs[Keys.desktop] ?: false,
        browserJavaScript = prefs[Keys.javascript] ?: true,
        browserCookies = prefs[Keys.cookies] ?: true,
        dynamicColors = prefs[Keys.dynamic] ?: true,
    )

    fun apply(prefs: MutablePreferences, next: AppSettings) {
        val tree = next.destinationTree
        val label = next.destinationLabel
        if (tree == null) prefs.remove(Keys.tree) else prefs[Keys.tree] = tree
        if (label == null) prefs.remove(Keys.label) else prefs[Keys.label] = label
        prefs[Keys.quality] = next.defaultQuality.takeIf { it in setOf(0, 360, 480, 720, 1080, 1440, 2160) } ?: 1080
        prefs[Keys.container] = next.defaultContainer.takeIf { it in setOf("mp4", "webm", "mkv") } ?: "mp4"
        prefs[Keys.wifi] = next.wifiOnly
        prefs[Keys.concurrent] = next.maxConcurrent.coerceIn(1, 4)
        prefs[Keys.notifications] = next.completionNotifications
        prefs[Keys.theme] = next.theme.name
        prefs[Keys.autoResume] = next.autoResume
        prefs[Keys.duplicates] = next.duplicatePolicy.name
        val homepage = next.browserHomepage?.trim().orEmpty()
        if (homepage.isEmpty() || BrowserLinks.homepage(homepage) != homepage) prefs.remove(Keys.homepage)
        else prefs[Keys.homepage] = homepage
        prefs[Keys.desktop] = next.browserDesktop
        prefs[Keys.javascript] = next.browserJavaScript
        prefs[Keys.cookies] = next.browserCookies
        prefs[Keys.dynamic] = next.dynamicColors
    }

    object Keys {
        val tree = stringPreferencesKey("destination_tree")
        val label = stringPreferencesKey("destination_label")
        val quality = intPreferencesKey("quality")
        val container = stringPreferencesKey("container")
        val wifi = booleanPreferencesKey("wifi_only")
        val concurrent = intPreferencesKey("max_concurrent")
        val notifications = booleanPreferencesKey("completion_notifications")
        val theme = stringPreferencesKey("theme")
        val autoResume = booleanPreferencesKey("auto_resume")
        val duplicates = stringPreferencesKey("duplicate_policy")
        val homepage = stringPreferencesKey("browser_homepage")
        val desktop = booleanPreferencesKey("browser_desktop")
        val javascript = booleanPreferencesKey("browser_javascript")
        val cookies = booleanPreferencesKey("browser_cookies")
        val dynamic = booleanPreferencesKey("dynamic_colors")
    }
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(@ApplicationContext context: Context) : SettingsRepository {
    private val store = context.preferences
    override val settings: Flow<AppSettings> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map(SettingsEncoding::decode).distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> SettingsEncoding.apply(prefs, transform(SettingsEncoding.decode(prefs))) }
    }
}
