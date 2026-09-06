package com.vidbox.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vidbox.data.database.*
import com.vidbox.data.repository.RoomDownloadRepository
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import java.util.Base64

internal class TestCipher : SecretCipher {
    override fun encrypt(plain: String) = Base64.getEncoder().encodeToString(plain.toByteArray())
    override fun decrypt(encrypted: String) = String(Base64.getDecoder().decode(encrypted))
}
internal fun testDatabase() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VidboxDatabase::class.java)
    .allowMainThreadQueries().build()
internal fun testRepository(db: VidboxDatabase) = RoomDownloadRepository(db, TestCipher(), TimeProvider(System::currentTimeMillis), Json { encodeDefaults = true })
internal fun testSpec(url: String = "https://example.com/video.mp4", title: String = "Test video") = DownloadSpec(url, title, null, "example.com", 1.0,
    FormatSelection(MediaFormat("direct", "mp4", height = 720, hasVideo = true, hasAudio = true)), true, null)
internal class TestSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)
    override suspend fun update(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
}
internal class TestNetwork : NetworkMonitor {
    override val status = MutableStateFlow(NetworkStatus(connected = true, wifi = true, metered = false))
}
internal class TestLogger : EventLogger {
    override fun event(name: String, id: String?, fields: Map<String, String>) = Unit
}
