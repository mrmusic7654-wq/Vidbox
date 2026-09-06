package com.vidbox.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidbox.domain.model.DownloadRecord
import com.vidbox.presentation.components.*
import com.vidbox.presentation.downloads.*
import com.vidbox.presentation.formats.FormatsScreen
import com.vidbox.presentation.history.*
import com.vidbox.presentation.home.*
import com.vidbox.presentation.settings.*
import com.vidbox.util.FileIntents
import kotlinx.coroutines.launch

private enum class Destination(val label: String, val icon: ImageVector, val tag: String) {
    HOME("Home", Icons.Rounded.Home, "nav_home"),
    DOWNLOADS("Downloads", Icons.Rounded.Downloading, "nav_downloads"),
    HISTORY("Library", Icons.Rounded.VideoLibrary, "nav_history"),
    SETTINGS("Settings", Icons.Rounded.Tune, "nav_settings"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VidboxApp(home: HomeViewModel, downloads: DownloadsViewModel, history: HistoryViewModel, settings: SettingsViewModel,
    notificationsAllowed: Boolean, onRequestNotifications: () -> Unit, onChooseFolder: () -> Unit,
    openDownloads: Boolean, onNavigationConsumed: () -> Unit) {
    val homeState by home.state.collectAsStateWithLifecycle()
    val active by downloads.active.collectAsStateWithLifecycle()
    val recent by downloads.recent.collectAsStateWithLifecycle()
    val network by downloads.connectivity.collectAsStateWithLifecycle()
    val historyState by history.state.collectAsStateWithLifecycle()
    val query by history.query.collectAsStateWithLifecycle()
    val preferences by settings.state.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf<DownloadRecord?>(null) }
    var deletion by remember { mutableStateOf<Pair<DownloadRecord, Boolean>?>(null) }
    var cancellation by remember { mutableStateOf<DownloadRecord?>(null) }

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
            }
        }
    }
    LaunchedEffect(settings) { settings.messages.collect { snackbars.showSnackbar(it) } }
    LaunchedEffect(destination, historyState.records.map { it.id }) {
        if (destination == Destination.HISTORY) downloads.verify(historyState.records)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (destination == Destination.HISTORY) downloads.verify(historyState.records) else downloads.verify(recent)
    }
    BackHandler(enabled = destination != Destination.HOME && homeState.media == null) { destination = Destination.HOME }

    val callbacks = DownloadCallbacks(
        pause = { downloads.pause(it.id) }, resume = { downloads.resume(it.id) }, cancel = { cancellation = it },
        open = { downloads.open(it) }, share = { downloads.open(it, true) }, details = { details = it },
        delete = { record, deleteFile -> deletion = record to deleteFile },
        analyze = { home.paste(it.spec.url); home.dismissFormats(); destination = Destination.HOME; home.analyze() },
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
                        if (destination == Destination.HOME) IconButton(onClick = { destination = Destination.SETTINGS }) { Icon(Icons.Rounded.Tune, "Open settings") }
                    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        Destination.entries.forEach { item ->
                            NavigationBarItem(selected = destination == item, onClick = { destination = item; focus.clearFocus() },
                                modifier = Modifier.testTag(item.tag), label = { Text(item.label) }, icon = {
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
                            onPaste = { clipboard.getText()?.text?.let(home::paste) ?: scope.launch { snackbars.showSnackbar("Your clipboard is empty") }.let {} },
                            onAnalyze = { focus.clearFocus(); home.analyze() }, onCancel = home::cancelAnalysis,
                            onDownloads = { destination = Destination.DOWNLOADS }, onHistory = { destination = Destination.HISTORY }, callbacks = callbacks)
                        Destination.DOWNLOADS -> DownloadsScreen(active, notificationsAllowed, preferences.maxConcurrent, onRequestNotifications,
                            onAddLink = { destination = Destination.HOME }, callbacks = callbacks)
                        Destination.HISTORY -> HistoryScreen(historyState, query, history::search, history::filter, history::sort, history::more,
                            onAddLink = { destination = Destination.HOME }, callbacks = callbacks)
                        Destination.SETTINGS -> SettingsScreen(preferences, settings::update, onChooseFolder, settings::clearHistory, notificationsAllowed,
                            onRequestNotifications, onAboutLink = {
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mrmusic7654-wq/Vidbox"))) }
                                catch (_: ActivityNotFoundException) { scope.launch { snackbars.showSnackbar("No browser is installed") } }
                            })
                    }
                }
            }
        }
    }
    details?.let { original ->
        val record = (active + historyState.records + recent).find { it.id == original.id } ?: original
        DownloadDetails(record) { details = null }
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
