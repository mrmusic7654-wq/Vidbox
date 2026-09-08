package com.vidbox.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidbox.domain.model.*
import com.vidbox.presentation.browser.*
import com.vidbox.presentation.components.*
import com.vidbox.presentation.downloads.*
import com.vidbox.presentation.formats.FormatsScreen
import com.vidbox.presentation.history.*
import com.vidbox.presentation.home.*
import com.vidbox.presentation.library.AudioScreen
import com.vidbox.presentation.library.VideosScreen
import com.vidbox.presentation.search.*
import com.vidbox.presentation.settings.*
import com.vidbox.player.PlayerActivity
import com.vidbox.player.PlayerController
import com.vidbox.player.PlayerEntry
import com.vidbox.util.FileIntents
import kotlinx.coroutines.launch

private enum class Destination(val label: String, val tag: String) {
    HOME("Home", "nav_home"),
    DOWNLOADS("Download", "nav_downloads"),
    VIDEOS("Video", "nav_videos"),
    AUDIO("Audio", "nav_audio"),
    SETTINGS("Settings", "settings_destination"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VidboxApp(home: HomeViewModel, downloads: DownloadsViewModel, history: HistoryViewModel, browser: BrowserViewModel,
    search: SearchViewModel, settings: SettingsViewModel, player: PlayerController,
    notificationsAllowed: Boolean, onRequestNotifications: () -> Unit,
    onChooseFolder: () -> Unit, openDownloads: Boolean, onNavigationConsumed: () -> Unit,
    openSearch: Boolean, onSearchConsumed: () -> Unit) {
    var destination by remember { mutableStateOf(Destination.HOME) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var browsing by rememberSaveable { mutableStateOf(false) }
    val homeState by home.state.collectAsStateWithLifecycle()
    val active by downloads.active.collectAsStateWithLifecycle()
    val recent by downloads.recent.collectAsStateWithLifecycle()
    val network by downloads.connectivity.collectAsStateWithLifecycle()
    val browserState by browser.state.collectAsStateWithLifecycle()
    val searchState by search.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val preferences by settings.state.collectAsStateWithLifecycle()
    // One history query powers the Download tab and the Video/Audio libraries.
    val libraryTabs = destination == Destination.DOWNLOADS || destination == Destination.VIDEOS || destination == Destination.AUDIO
    val historyState = if (libraryTabs) history.state.collectAsStateWithLifecycle().value else HistoryState(loading = false)
    val historyQuery by history.query.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf<DownloadRecord?>(null) }
    var deletion by remember { mutableStateOf<Pair<DownloadRecord, Boolean>?>(null) }
    var cancellation by remember { mutableStateOf<DownloadRecord?>(null) }

    LaunchedEffect(openDownloads) {
        if (openDownloads) { searching = false; browsing = false; destination = Destination.DOWNLOADS; home.dismissFormats(); onNavigationConsumed() }
    }
    LaunchedEffect(home) {
        home.queued.collect {
            if (!browsing && !searching) destination = Destination.DOWNLOADS
            snackbars.showSnackbar("Added to your download queue")
        }
    }
    LaunchedEffect(search) {
        search.events.collect { event ->
            when (event) {
                is SearchEvent.OpenLink -> { home.paste(event.url); home.analyze() }
            }
        }
    }
    LaunchedEffect(downloads) {
        downloads.events.collect { event ->
            when (event) {
                is DownloadUiEvent.Message -> snackbars.showSnackbar(event.text)
                is DownloadUiEvent.Open -> try { FileIntents.open(context, event.record, event.share) }
                    catch (_: ActivityNotFoundException) { snackbars.showSnackbar("No installed app can open this file type.") }
                    catch (_: SecurityException) { snackbars.showSnackbar("The saved file is no longer accessible. Check your download folder.") }
                is DownloadUiEvent.Play -> play(context, player, listOf(event.record.toEntry()), 0)
            }
        }
    }
    // Analysis failures surface where the analysis started: inline on the search screen,
    // as a snackbar everywhere else (the formats screen shows its own state errors).
    LaunchedEffect(homeState.error, searching, browsing) {
        val error = homeState.error ?: return@LaunchedEffect
        if (!searching && !browsing && homeState.media == null) {
            snackbars.showSnackbar(error); home.dismissError()
        }
    }
    val visibleFiles = if (libraryTabs) historyState.records else recent
    LaunchedEffect(destination, visibleFiles.map { it.id }) { downloads.verify(visibleFiles) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (libraryTabs) downloads.verify(historyState.records) else downloads.verify(recent)
    }
    BackHandler(enabled = searching && homeState.media == null) { searching = false }
    BackHandler(enabled = browsing && homeState.media == null) { browsing = false }
    BackHandler(enabled = destination != Destination.HOME && !searching && !browsing && homeState.media == null) {
        destination = Destination.HOME
    }

    val startDownloadFromPage: (String?) -> Unit = { url ->
        if (url != null) {
            home.paste(url); home.dismissFormats(); home.analyze()
        } else scope.launch { snackbars.showSnackbar("Open a page first, then download from it") }
    }

    val callbacks = DownloadCallbacks(
        pause = { downloads.pause(it.id) }, resume = { downloads.resume(it.id) }, cancel = { cancellation = it },
        open = { downloads.open(it) }, share = { downloads.open(it, true) }, details = { details = it },
        delete = { record, deleteFile -> deletion = record to deleteFile },
        analyze = { home.paste(it.spec.url); home.dismissFormats(); searching = false; browsing = false; destination = Destination.HOME; home.analyze() },
        play = { downloads.play(it) },
    )

    fun playLibrary(records: List<DownloadRecord>, index: Int) =
        scope.launch { play(context, player, records.map { it.toEntry() }, index) }

    val videoRecords = historyState.records.filter { it.state == DownloadState.COMPLETED && it.outputUri != null &&
        it.spec.selection.primary.hasVideo }
    val audioRecords = historyState.records.filter { it.state == DownloadState.COMPLETED && it.outputUri != null &&
        !it.spec.selection.primary.hasVideo }
    val completedRecords = historyState.records.filter { it.state == DownloadState.COMPLETED }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
            if (homeState.media != null) {
                // The analyzer result replaces everything: pick a quality, then the queue takes over.
                FormatsScreen(homeState, home::dismissFormats, home::mode, home::container, home::select,
                    onDownload = { if (!notificationsAllowed) onRequestNotifications(); home.download() },
                    snackbarHost = snackbars)
                return@Box
            }
            Scaffold(containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    Column {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                            listOf(Destination.HOME, Destination.DOWNLOADS, Destination.VIDEOS, Destination.AUDIO)
                                .forEach { item ->
                                    NavigationBarItem(selected = destination == item,
                                        onClick = { destination = item; searching = false; browsing = false },
                                        modifier = Modifier.testTag(item.tag), label = { Text(item.label) },
                                        icon = {
                                            BadgedBox(badge = {
                                                if (item == Destination.DOWNLOADS && active.isNotEmpty()) Badge { Text(active.size.toString()) }
                                            }) {
                                                Icon(when (item) {
                                                    Destination.HOME -> VidboxIcons.home
                                                    Destination.DOWNLOADS -> VidboxIcons.downloading
                                                    Destination.VIDEOS -> VidboxIcons.video
                                                    else -> VidboxIcons.audio
                                                }, null)
                                            }
                                        })
                                }
                        }
                    }
                }, snackbarHost = { SnackbarHost(snackbars) }) { insets ->
                Box(Modifier.fillMaxSize().padding(insets)) {
                    when (destination) {
                        Destination.HOME -> HomeScreen(homeState, active, recent, network, preferences, callbacks,
                            onOpenSearch = { searching = true },
                            onOpenBrowser = { url ->
                                searching = false; browsing = true
                                if (url != null) browser.openInNewTab(url) else browser.newTab()
                            },
                            onOpenSettings = { destination = Destination.SETTINGS },
                            onDownloads = { destination = Destination.DOWNLOADS },
                            onVideos = { destination = Destination.VIDEOS },
                            onAddShortcut = { label, url ->
                                scope.launch { runCatching { settings.update {
                                    it.copy(siteShortcuts = (it.siteShortcuts + SiteShortcut(label, url)).takeLast(8))
                                } } }
                            },
                            onRemoveShortcut = { shortcut ->
                                scope.launch { runCatching { settings.update {
                                    it.copy(siteShortcuts = it.siteShortcuts.filterNot { item -> item.url == shortcut.url })
                                } } }
                            })
                        Destination.DOWNLOADS -> DownloadsScreen(active, completedRecords, notificationsAllowed,
                            preferences.maxConcurrent, historyQuery, history::search, history::sort,
                            onNotifications = onRequestNotifications, onAddLink = { searching = true }, callbacks = callbacks)
                        Destination.VIDEOS -> VideosScreen(videoRecords, historyQuery, history::search, history::sort,
                            preferences.destinationLabel, onChooseFolder,
                            onPlay = { index -> playLibrary(videoRecords, index) }, callbacks = callbacks)
                        Destination.AUDIO -> AudioScreen(audioRecords, historyQuery, history::search, history::sort,
                            preferences.destinationLabel, onChooseFolder,
                            onPlay = { index -> playLibrary(audioRecords, index) }, callbacks = callbacks)
                        Destination.SETTINGS -> SettingsScreen(preferences, settings::update, onChooseFolder, settings::clearHistory, notificationsAllowed,
                            onRequestNotifications,
                            onClearBrowserData = {
                                com.vidbox.util.BrowsingData.clear(context)
                                scope.launch { snackbars.showSnackbar("Browser data cleared.") }
                            },
                            onAboutLink = {
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mrmusic7654-wq/Vidbox"))) }
                                catch (_: ActivityNotFoundException) { scope.launch { snackbars.showSnackbar("No browser is installed") } }
                            })
                    }
                }
            }
            // Full-screen overlays: the video search flow and the tabbed browser.
            if (searching && homeState.media == null) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SearchScreen(searchState, homeState.error, homeState.analyzing, onBack = { searching = false },
                        onInput = search::input, onSubmit = search::submit, onUseRecent = search::useRecent,
                        onRemoveRecent = search::removeRecent, onClearRecents = search::clearRecents,
                        onDownload = { result -> home.paste(result.url); home.analyze() },
                        onOpenInBrowser = { result -> searching = false; browsing = true; browser.openInNewTab(result.url) })
                }
            }
            if (browsing && homeState.media == null) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        BrowserScreen(browser, browserState, notificationsAllowed, onRequestNotifications,
                            onDownloadPage = startDownloadFromPage,
                            onOpenExternal = { url ->
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                catch (_: ActivityNotFoundException) { scope.launch { snackbars.showSnackbar("No browser is installed") } }
                            },
                            onClosed = { browsing = false })
                    }
                }
            }
        }
        details?.let { original ->
            val record = (active + historyState.records + recent).find { it.id == original.id } ?: original
            DownloadDetails(record, onDismiss = { details = null },
                onPlay = if (record.state == DownloadState.COMPLETED && !record.fileMissing &&
                    record.spec.kind == DownloadKind.MEDIA && record.spec.selection.primary.hasVideo)
                    ({ scope.launch { play(context, player, listOf(record.toEntry()), 0) } }) else null)
        }
        deletion?.let { (record, deleteFile) ->
            AlertDialog(onDismissRequest = { deletion = null }, title = { Text(if (deleteFile) "Delete this file?" else "Remove history entry?") },
                text = { Text(if (deleteFile) "${record.fileName}\n\nThis removes the saved file and its history entry. This cannot be undone."
                    else "The saved media stays in its folder. Only its Vidbox history entry and temporary working files will be removed.") },
                confirmButton = { TextButton(onClick = { deletion = null; downloads.delete(record, deleteFile) }) { Text(if (deleteFile) "Delete file" else "Remove entry", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { deletion = null }) { Text("Keep it") } })
        }
        cancellation?.let { record ->
            AlertDialog(onDismissRequest = { cancellation = null }, title = { Text("Cancel this download?") },
                text = { Text("Partial files will be deleted. You can retry later from the library, but the download will start again.") },
                confirmButton = { TextButton(onClick = { cancellation = null; downloads.cancel(record.id) }) { Text("Cancel download", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { cancellation = null }) { Text("Keep downloading") } })
        }
    }
}

private fun DownloadRecord.toEntry() = PlayerEntry(
    uri = requireNotNull(outputUri) { "Only completed media with a saved file can play" },
    title = fileName, mimeType = mimeType, thumbnail = spec.thumbnailUrl,
)

private suspend fun play(context: android.content.Context, player: PlayerController,
    entries: List<PlayerEntry>, index: Int) {
    if (entries.isEmpty()) return
    player.play(entries, index)
    PlayerActivity.start(context)
}
