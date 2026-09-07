package com.vidbox.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.vidbox.data.database.SecretCipher
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.SettingsRepository
import com.vidbox.domain.util.BrowserLinks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferences: DataStore<Preferences> by preferencesDataStore(name = "vidbox_settings")

/**
 * Pure mapping between DataStore rows and AppSettings, unit-tested without DataStore lifecycle.
 * Proxy credentials are the only secret here; they are stored encrypted with the keystore-backed
 * [SecretCipher] and never in plaintext preference rows.
 */
internal class SettingsEncoding(private val cipher: SecretCipher? = null) {
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
        searchEngineId = prefs[Keys.searchEngine]?.takeIf { id ->
            id == SearchEngines.CUSTOM_ID || SearchEngines.builtIn.any { it.id == id } } ?: SearchEngines.DEFAULT.id,
        customSearchTemplate = prefs[Keys.customSearch]?.takeIf { SearchEngines.custom(it) != null },
        restoreTabs = prefs[Keys.restoreTabs] ?: true,
        saveBrowsingHistory = prefs[Keys.saveHistory] ?: true,
        addressSuggestions = prefs[Keys.suggestions] ?: true,
        blockAds = prefs[Keys.blockAds] ?: true,
        blockTrackers = prefs[Keys.blockTrackers] ?: true,
        filterListUpdates = prefs[Keys.filterUpdates] ?: true,
        proxy = decodeProxy(prefs),
        downloadProgressNotifications = prefs[Keys.progressNotifications] ?: true,
    )

    private fun decodeProxy(prefs: Preferences): ProxyConfig {
        val type = runCatching { ProxyType.valueOf(prefs[Keys.proxyType].orEmpty()) }.getOrDefault(ProxyType.HTTP)
        val host = prefs[Keys.proxyHost].orEmpty()
        val port = (prefs[Keys.proxyPort] ?: 8080).coerceIn(1, 65535)
        val secret = prefs[Keys.proxySecret]?.let { stored -> cipher?.let { runCatching { it.decrypt(stored) }.getOrNull() } }
        val username = secret?.substringBefore('\n')?.takeIf(String::isNotEmpty)
        val password = secret?.substringAfter('\n', "")?.takeIf { username != null }
        // A proxy can only be "enabled" when its structure is valid; a corrupt row falls back to direct.
        val enabled = (prefs[Keys.proxyEnabled] ?: false) && ProxyConfig.validate(type, host, port) == null
        return ProxyConfig(enabled, type, host, port, username, password)
    }

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
        val custom = SearchEngines.custom(next.customSearchTemplate)
        if (custom == null) prefs.remove(Keys.customSearch) else prefs[Keys.customSearch] = custom.template
        prefs[Keys.searchEngine] = when {
            next.searchEngineId == SearchEngines.CUSTOM_ID && custom != null -> SearchEngines.CUSTOM_ID
            SearchEngines.builtIn.any { it.id == next.searchEngineId } -> next.searchEngineId
            else -> SearchEngines.DEFAULT.id
        }
        prefs[Keys.restoreTabs] = next.restoreTabs
        prefs[Keys.saveHistory] = next.saveBrowsingHistory
        prefs[Keys.suggestions] = next.addressSuggestions
        prefs[Keys.blockAds] = next.blockAds
        prefs[Keys.blockTrackers] = next.blockTrackers
        prefs[Keys.filterUpdates] = next.filterListUpdates
        applyProxy(prefs, next.proxy)
        prefs[Keys.progressNotifications] = next.downloadProgressNotifications
    }

    private fun applyProxy(prefs: MutablePreferences, proxy: ProxyConfig) {
        val host = proxy.host.trim()
        val port = proxy.port.coerceIn(1, 65535)
        val valid = ProxyConfig.validate(proxy.type, host, port) == null
        prefs[Keys.proxyType] = proxy.type.name
        prefs[Keys.proxyHost] = host.take(253)
        prefs[Keys.proxyPort] = port
        prefs[Keys.proxyEnabled] = proxy.enabled && valid
        val username = proxy.username?.take(256).orEmpty()
        val password = proxy.password?.take(256).orEmpty()
        // `cipher` is a nullable *property*, so it cannot be smart-cast inside the lambda; the safe call
        // here matches [decodeProxy] and keeps the exact rule: no credentials or no cipher -> nothing stored.
        val encrypted = if (username.isEmpty()) null else runCatching { cipher?.encrypt("$username\n$password") }.getOrNull()
        if (encrypted == null) prefs.remove(Keys.proxySecret) else prefs[Keys.proxySecret] = encrypted
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
        val searchEngine = stringPreferencesKey("search_engine")
        val customSearch = stringPreferencesKey("search_engine_custom")
        val restoreTabs = booleanPreferencesKey("browser_restore_tabs")
        val saveHistory = booleanPreferencesKey("browser_save_history")
        val suggestions = booleanPreferencesKey("browser_suggestions")
        val blockAds = booleanPreferencesKey("block_ads")
        val blockTrackers = booleanPreferencesKey("block_trackers")
        val filterUpdates = booleanPreferencesKey("filter_list_updates")
        val proxyEnabled = booleanPreferencesKey("proxy_enabled")
        val proxyType = stringPreferencesKey("proxy_type")
        val proxyHost = stringPreferencesKey("proxy_host")
        val proxyPort = intPreferencesKey("proxy_port")
        /** Keystore-encrypted "username\npassword"; absent when no credentials are set. */
        val proxySecret = stringPreferencesKey("proxy_secret_v1")
        val progressNotifications = booleanPreferencesKey("progress_notifications")
    }

    companion object {
        private val plain = SettingsEncoding(null)
        /** Encoding without a cipher: proxy credentials are dropped rather than written in clear. */
        fun decode(prefs: Preferences): AppSettings = plain.decode(prefs)
        fun apply(prefs: MutablePreferences, next: AppSettings) = plain.apply(prefs, next)
    }
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(@ApplicationContext context: Context, cipher: SecretCipher) : SettingsRepository {
    private val store = context.preferences
    private val encoding = SettingsEncoding(cipher)
    override val settings: Flow<AppSettings> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map(encoding::decode).distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> encoding.apply(prefs, transform(encoding.decode(prefs))) }
    }
}
