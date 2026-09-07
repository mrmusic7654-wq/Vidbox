package com.vidbox.data.di

import android.content.Context
import androidx.room.Room
import com.vidbox.data.database.*
import com.vidbox.data.network.AndroidNetworkMonitor
import com.vidbox.data.network.ProxyManager
import com.vidbox.data.repository.*
import com.vidbox.data.util.AndroidEventLogger
import com.vidbox.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InfrastructureBindings {
    @Binds @Singleton abstract fun downloads(impl: RoomDownloadRepository): DownloadRepository
    @Binds @Singleton abstract fun tabs(impl: RoomTabRepository): TabRepository
    @Binds @Singleton abstract fun browsingHistory(impl: RoomBrowsingHistoryRepository): BrowsingHistoryRepository
    @Binds @Singleton abstract fun bookmarks(impl: RoomBookmarkRepository): BookmarkRepository
    @Binds @Singleton abstract fun allowlist(impl: RoomAllowlistRepository): AllowlistRepository
    @Binds @Singleton abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds @Singleton abstract fun cipher(impl: KeystoreSecretCipher): SecretCipher
    @Binds @Singleton abstract fun logger(impl: AndroidEventLogger): EventLogger
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindings {
    @Binds @Singleton abstract fun network(impl: AndroidNetworkMonitor): NetworkMonitor
}

@Module
@InstallIn(SingletonComponent::class)
object InfrastructureModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): VidboxDatabase =
        Room.databaseBuilder(context, VidboxDatabase::class.java, "downloads.db")
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(*VidboxDatabase.MIGRATIONS)
            .build() // No destructive migration: every schema version ships an explicit migration.
    @Provides @Singleton fun json() = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Provides fun clock() = TimeProvider(System::currentTimeMillis)
    /** One shared client; the user's proxy (if any) is consulted per connection through [ProxyManager]. */
    @Provides @Singleton fun http(proxy: ProxyManager): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS).writeTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(false).retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(4, 3, TimeUnit.MINUTES))
        .proxySelector(proxy).proxyAuthenticator(proxy.authenticator)
        .addInterceptor(proxy.interceptor)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("User-Agent", "Vidbox/1.0 (Android; personal media archiver)")
                .header("Accept-Encoding", "identity").build())
        }.build()
}
