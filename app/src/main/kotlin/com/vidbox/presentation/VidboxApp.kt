package com.vidbox.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidbox.domain.model.DownloadKind
import com.vidbox.domain.model.DownloadRecord
import com.vidbox.domain.model.DownloadState
import com.vidbox.presentation.browser.*
import com.vidbox.presentation.components.*
import com.vidbox.presentation.downloads.*
import com.vidbox.presentation.formats.FormatsScreen
import com.vidbox.presentation.history.*
import com.vidbox.presentation.home.*
import com.vidbox.presentation.settings.*
import com.vidbox.util.FileIntents
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Destination(val label: String, val icon: ImageVector, val tag: String) {
    HOME("Home", VidboxIcons.home, "nav_home"),
    BROWSER("Browser", VidboxIcons.browser, "nav_browser"),
    DOWNLOADS("Downloads", VidboxIcons.downloading, "nav_downloads"),
    HISTORY("Library", VidboxIcons.library, "nav_history"),
    SETTINGS("Settings", VidboxIcons.settings, "nav_settings"),
}

private val DragBadge = 58.dp
private val DragBadgeIcon = 30.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VidboxApp(home: HomeViewModel, downloads: DownloadsViewModel, history: HistoryViewModel, browser: BrowserViewModel,
    settings: SettingsViewModel, notificationsAllowed: Boolean, onRequestNotifications: () -> Unit,
    onChooseFolder: () -> Unit, openDownloads: Boolean, onNavigationConsumed: () -> Unit) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    val homeState by home.state.collectAsStateWithLifecycle()
    val active by downloads.active.collectAsStateWithLifecycle()
    val recent by downloads.recent.collectAsStateWithLifecycle()
    val network by downloads.connectivity.collectAsStateWithLifecycle()
    val browserState by browser.state.collectAsStateWithLifecycle()
    // Do not re-query/decrypt a hidden history page on every active transfer update.
    val historyState = if (destination == Destination.HISTORY) history.state.collectAsStateWithLifecycle().value else HistoryState(loading = false)
    val query by history.query.collectAsStateWithLifecycle()
    val preferences by settings.state.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf<DownloadRecord?>(null) }
    var deletion by remember { mutableStateOf<Pair<DownloadRecord, Boolean>?>(null) }
    var cancellation by remember { mutableStateOf<DownloadRecord?>(null) }
    // Geometry for the drag-and-drop download shortcut (see the Browser screen).
    var downloadsSlot by remember { mutableStateOf<Rect?>(null) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(openDownloads) {
        if (openDownloads) { destination = Destination.DOWNLOADS; home.dismissFormats(); onNavigationConsumed() }
    }
    LaunchedEffect(home) {
        home.queued.collect { destination = Destination.DOWNLOADS; snackbars.showSnackbar("Added to your download queue") }
    }
    LaunchedEffect(downloads) {
        downloads.events.collect { event ->
            when (event) {
                is DownloadUiEvent.Message -> snackbars.showSnackbar(event.text)
                is DownloadUiEvent.Open -> try { FileIntents.open(context, event.record, event.share) }
                    catch (_: ActivityNotFoundException) { snackbars.showSnackbar("No installed app can open this file type.") }
                    catch (_: SecurityException) { snackbars.showSnackbar("The saved file is no longer accessible. Check your download folder.") }
                is DownloadUiEvent.Play -> try { FileIntents.internalPlayer(context, event.record) }
                    catch (_: Exception) { snackbars.showSnackbar("The saved file is no longer accessible. Check your download folder.") }
            }
        }
    }
    LaunchedEffect(settings) { settings.messages.collect { snackbars.showSnackbar(it) } }
    val visibleFiles = if (destination == Destination.HISTORY) historyState.records else recent
    LaunchedEffect(destination, visibleFiles.map { it.id }) {
        downloads.verify(visibleFiles)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (destination == Destination.HISTORY) downloads.verify(historyState.records) else downloads.verify(recent)
    }
    BackHandler(enabled = destination != Destination.HOME && homeState.media == null) { destination = Destination.HOME }

    val startDownloadFromPage: (String?) -> Unit = { url ->
        if (url != null) {
            home.paste(url); home.dismissFormats(); home.analyze()
            destination = Destination.HOME
        } else scope.launch { snackbars.showSnackbar("Open a page first, then download from it") }
    }

    val callbacks = DownloadCallbacks(
        pause = { downloads.pause(it.id) }, resume = { downloads.resume(it.id) }, cancel = { cancellation = it },
        open = { downloads.open(it) }, share = { downloads.open(it, true) }, details = { details = it },
        delete = { record, deleteFile -> deletion = record to deleteFile },
        analyze = { home.paste(it.spec.url); home.dismissFormats(); destination = Destination.HOME; home.analyze() },
        play = { downloads.play(it) },
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
            if (homeState.media != null) {
                FormatsScreen(homeState, home::dismissFormats, home::mode, home::container, home::select,
                    onDownload = { if (!notificationsAllowed) onRequestNotifications(); home.download() }, snackbarHost = snackbars)
            } else Scaffold(containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BrandMark(34.dp); Text("Vidbox", style = MaterialTheme.typography.titleLarge)
                        }
                    }, actions = {
                        if (destination == Destination.HOME) IconButton(onClick = { destination = Destination.SETTINGS }) { Icon(VidboxIcons.settings, "Open settings") }
                    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        Destination.entries.forEach { item ->
                            NavigationBarItem(selected = destination == item, onClick = { destination = item; focus.clearFocus() },
                                modifier = Modifier
                                    .onGloballyPositioned { coordinates ->
                                        if (item == Destination.DOWNLOADS) {
                                            downloadsSlot = Rect(coordinates.positionInWindow(), coordinates.size.toSize())
                                        }
                                    }
                                    .testTag(item.tag), label = { Text(item.label) }, icon = {
                                        BadgedBox(badge = { if (item == Destination.DOWNLOADS && active.isNotEmpty()) Badge { Text(active.size.toString()) } }) {
                                            Icon(item.icon, null)
                                        }
                                    })
                        }
                    }
                }, snackbarHost = { SnackbarHost(snackbars) }) { insets ->
                Box(Modifier.fillMaxSize().padding(insets)) {
                    when (destination) {
                        Destination.HOME -> HomeScreen(homeState, active, recent, network, home::input,
                            onPaste = {
                                scope.launch {
                                    val item = clipboard.getClipEntry()?.clipData?.let { if (it.itemCount > 0) it.getItemAt(0) else null }
                                    val text = item?.text?.take(16384)?.toString() ?: item?.uri?.toString()
                                    if (text != null) home.paste(text) else snackbars.showSnackbar("Your clipboard has no text link")
                                }
                            },
                            onAnalyze = { focus.clearFocus(); home.analyze() }, onCancel = home::cancelAnalysis,
                            onBrowse = { destination = Destination.BROWSER },
                            onDownloads = { destination = Destination.DOWNLOADS }, onHistory = { destination = Destination.HISTORY }, callbacks = callbacks)
                        Destination.BROWSER -> BrowserScreen(browser, browserState, notificationsAllowed,
                            onRequestNotifications, onDownloadPage = startDownloadFromPage,
                            onOpenExternal = { url ->
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                catch (_: ActivityNotFoundException) { scope.launch { snackbars.showSnackbar("No browser is installed") } }
                            },
                            onDownloadDragStart = { dragPosition = it },
                            onDownloadDragMove = { dragPosition = it },
                            onDownloadDragEnd = { position ->
                                dragPosition = null
                                // Dropping the lifted download button on the Downloads tab starts the
                                // page download flow, exactly like pressing the button itself.
                                if (downloadsSlot?.contains(position) == true) startDownloadFromPage(browserState.currentUrl)
                            },
                            onDownloadDragCancel = { dragPosition = null })
                        Destination.DOWNLOADS -> DownloadsScreen(active, notificationsAllowed, preferences.maxConcurrent, onRequestNotifications,
                            onAddLink = { destination = Destination.HOME }, callbacks = callbacks)
                        Destination.HISTORY -> HistoryScreen(historyState, query, history::search, history::filter, history::sort, history::more,
                            onAddLink = { destination = Destination.HOME }, callbacks = callbacks)
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
        }
        // Floating copy of the download action while it is being dragged onto a destination.
        dragPosition?.let { position ->
            val downloadsHit = downloadsSlot?.contains(position) == true
            if (downloadsHit) {
                downloadsSlot?.let { slot ->
                    Box(Modifier.offset { IntOffset(slot.left.roundToInt(), slot.top.roundToInt()) }
                        .size(with(LocalDensity.current) { slot.width.toDp() }, with(LocalDensity.current) { slot.height.toDp() }),
                        contentAlignment = Alignment.Center) {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                            modifier = Modifier.fillMaxSize().testTag("drag_download_target")) {}
                    }
                }
            }
            val badgeHalf = with(LocalDensity.current) { DragBadge.toPx() / 2f }
            Box(Modifier.offset { IntOffset((position.x - badgeHalf).roundToInt(), (position.y - badgeHalf).roundToInt()) },
                contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 10.dp,
                    modifier = Modifier.size(DragBadge).testTag("drag_download_icon")) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(VidboxIcons.download, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(DragBadgeIcon))
                    }
                }
            }
        }
    }
    details?.let { original ->
        val record = (active + historyState.records + recent).find { it.id == original.id } ?: original
        DownloadDetails(record, onDismiss = { details = null },
            onPlay = if (record.state == DownloadState.COMPLETED && !record.fileMissing &&
                record.spec.kind == DownloadKind.MEDIA && record.spec.selection.primary.hasVideo)
                ({ downloads.play(record) }) else null)
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
