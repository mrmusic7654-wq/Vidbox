package com.vidbox.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferences: DataStore<Preferences> by preferencesDataStore(name = "vidbox_settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(@ApplicationContext context: Context) : SettingsRepository {
    private val store = context.preferences
    private object Keys {
        val tree = stringPreferencesKey("destination_tree")
        val label = stringPreferencesKey("destination_label")
        val quality = intPreferencesKey("quality")
        val container = stringPreferencesKey("container")
        val wifi = booleanPreferencesKey("wifi_only")
        val concurrent = intPreferencesKey("max_concurrent")
        val notifications = booleanPreferencesKey("completion_notifications")
        val theme = stringPreferencesKey("theme")
    }
    override val settings: Flow<AppSettings> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map(::decode).distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(decode(prefs))
            if (next.destinationTree == null) prefs.remove(Keys.tree) else prefs[Keys.tree] = next.destinationTree
            if (next.destinationLabel == null) prefs.remove(Keys.label) else prefs[Keys.label] = next.destinationLabel
            prefs[Keys.quality] = next.defaultQuality.takeIf { it in setOf(0, 360, 480, 720, 1080, 1440, 2160) } ?: 1080
            prefs[Keys.container] = next.defaultContainer.takeIf { it in setOf("mp4", "webm", "mkv") } ?: "mp4"
            prefs[Keys.wifi] = next.wifiOnly
            prefs[Keys.concurrent] = next.maxConcurrent.coerceIn(1, 4)
            prefs[Keys.notifications] = next.completionNotifications
            prefs[Keys.theme] = next.theme.name
        }
    }
    private fun decode(prefs: Preferences) = AppSettings(
        destinationTree = prefs[Keys.tree], destinationLabel = prefs[Keys.label],
        defaultQuality = prefs[Keys.quality] ?: 1080, defaultContainer = prefs[Keys.container] ?: "mp4",
        wifiOnly = prefs[Keys.wifi] ?: false, maxConcurrent = (prefs[Keys.concurrent] ?: 2).coerceIn(1, 4),
        completionNotifications = prefs[Keys.notifications] ?: true,
        theme = runCatching { AppTheme.valueOf(prefs[Keys.theme].orEmpty()) }.getOrDefault(AppTheme.SYSTEM),
    )
}
