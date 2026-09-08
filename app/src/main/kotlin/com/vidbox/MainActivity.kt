package com.vidbox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import com.vidbox.domain.util.UrlValidator
import com.vidbox.presentation.VidboxApp
import com.vidbox.presentation.browser.BrowserViewModel
import com.vidbox.presentation.downloads.DownloadsViewModel
import com.vidbox.presentation.history.HistoryViewModel
import com.vidbox.presentation.home.HomeViewModel
import com.vidbox.presentation.search.SearchViewModel
import com.vidbox.presentation.settings.SettingsViewModel
import com.vidbox.presentation.theme.VidboxTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val home: HomeViewModel by viewModels()
    private val downloads: DownloadsViewModel by viewModels()
    private val history: HistoryViewModel by viewModels()
    private val browser: BrowserViewModel by viewModels()
    private val search: SearchViewModel by viewModels()
    private val settings: SettingsViewModel by viewModels()
    @Inject lateinit var playerController: com.vidbox.player.PlayerController
    private var showDownloads by mutableStateOf(false)
    private var startSearch by mutableStateOf(false)
    private var notificationPermission by mutableStateOf(true)
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationPermission = notificationsAllowed()
        if (!it) settings.report("Downloads still run, but Android may hide their notifications. Enable notifications in system settings for progress controls.")
    }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val label = withContext(Dispatchers.IO) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    val folder = DocumentFile.fromTreeUri(this@MainActivity, uri)
                    check(folder?.canWrite() == true)
                    folder?.name ?: "Selected folder"
                }
                settings.location(uri.toString(), label)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { settings.report("This folder is not writable. Please choose another folder.") }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consume(intent)
        setContent {
            val prefs by settings.state.collectAsStateWithLifecycle()
            VidboxTheme(prefs.theme, prefs.dynamicColors) {
                VidboxApp(home, downloads, history, browser, search, settings, playerController,
                    notificationPermission,
                    onRequestNotifications = ::requestNotifications,
                    onChooseFolder = { folderPicker.launch(prefs.destinationTree?.let(Uri::parse)) },
                    openDownloads = showDownloads, onNavigationConsumed = { showDownloads = false },
                    openSearch = startSearch, onSearchConsumed = { startSearch = false })
            }
        }
    }
    override fun onResume() {
        super.onResume()
        notificationPermission = notificationsAllowed()
        downloads.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // No media foreground service: playback pauses when the whole app is backgrounded.
        playerController.onAppBackground()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); consume(intent) }
    private fun consume(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.let { shared ->
            val text = UrlValidator.findInSharedText(shared)
            // A shared link goes straight to analysis; shared text opens the search flow.
            if (com.vidbox.domain.usecase.SearchVideos.looksLikeLink(text)) { home.paste(text); home.analyze() }
            else { search.input(text); startSearch = true }
        }
        if (intent?.getBooleanExtra(OPEN_DOWNLOADS, false) == true) showDownloads = true
    }
    private fun notificationsAllowed() = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }
    companion object { const val OPEN_DOWNLOADS = "open_downloads" }
}
