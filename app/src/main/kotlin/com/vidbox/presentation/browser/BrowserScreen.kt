package com.vidbox.presentation.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vidbox.domain.util.BrowserLinks
import com.vidbox.domain.util.DisplayFormat
import com.vidbox.domain.util.FileNames
import com.vidbox.presentation.components.VidboxIcons
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(browser: BrowserViewModel, state: BrowserState, notificationsAllowed: Boolean,
    onRequestNotifications: () -> Unit, onDownloadPage: (String?) -> Unit, onOpenExternal: (String) -> Unit,
    onDownloadDragStart: (Offset) -> Unit, onDownloadDragMove: (Offset) -> Unit,
    onDownloadDragEnd: (Offset) -> Unit, onDownloadDragCancel: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            // Defense in depth: explicit platform defaults for everything security-relevant.
            settings.safeBrowsingMode = WebSettings.SAFE_BROWSING_ENABLED
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    val scheme = request.url.scheme?.lowercase()
                    // Internal schemes are part of page operation; ignore them without alarming the user.
                    if (scheme in setOf("about", "data", "javascript", "blob")) return true
                    if (scheme != "http" && scheme != "https") {
                        browser.unsupported(url)
                        return true
                    }
                    // A direct link to a media or file (X.mp4, X.pdf, X.zip) becomes a download
                    // instead of an inline page load; page links still navigate normally.
                    val ext = FileNames.extensionOf(url)
                    if (ext != null && FileNames.isDownloadable(ext)) {
                        browser.downloadStart(url, null, null, -1)
                        return true
                    }
                    return false
                }
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { browser.pageStarted(url) }
                override fun onPageFinished(view: WebView, url: String?) {
                    browser.pageFinished(url, view.canGoBack(), view.canGoForward())
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) { browser.progress(newProgress) }
                override fun onReceivedTitle(view: WebView, title: String?) { browser.title(title) }
                override fun onJsAlert(view: WebView, url: String?, message: String?, result: android.webkit.JsResult): Boolean {
                    browser.jsDialog(message.orEmpty(), isPrompt = false) { result.confirm() }
                    return true
                }
                override fun onJsConfirm(view: WebView, url: String?, message: String?, result: android.webkit.JsResult): Boolean {
                    browser.jsDialog(message.orEmpty(), isPrompt = false) { value -> if (value != null) result.confirm() else result.cancel() }
                    return true
                }
                override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?,
                    result: android.webkit.JsPromptResult): Boolean {
                    browser.jsDialog(message.orEmpty(), promptDefault = defaultValue, isPrompt = true) { value ->
                        if (value != null) result.confirm(value) else result.cancel()
                    }
                    return true
                }
            }
            setDownloadListener { url, _, contentDisposition, mimetype, contentLength ->
                browser.downloadStart(url, contentDisposition, mimetype, contentLength)
            }
        }
    }
    DisposableEffect(Unit) { onDispose { webView.destroy() } }

    // Apply user browser preferences; a mid-session change reloads the current page.
    var preferencesApplied by remember { mutableStateOf(false) }
    LaunchedEffect(state.javaScript, state.cookies, state.desktop) {
        webView.settings.javaScriptEnabled = state.javaScript
        webView.settings.userAgentString = if (state.desktop) BrowserLinks.DESKTOP_USER_AGENT else null
        webView.settings.useWideViewPort = state.desktop
        webView.settings.loadWithOverviewMode = state.desktop
        CookieManager.getInstance().setAcceptCookie(state.cookies)
        if (preferencesApplied && webView.url != null) webView.reload()
        preferencesApplied = true
    }
    // Release rendering and timers while the app is backgrounded; the WebView itself survives.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { webView.onResume(); webView.resumeTimers() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { webView.onPause(); webView.pauseTimers() }

    LaunchedEffect(browser) {
        browser.events.collect { event ->
            when (event) {
                is BrowserEvent.Navigate -> webView.loadUrl(event.url)
                is BrowserEvent.Message -> if (event.text.isNotBlank()) snackbar.showSnackbar(event.text)
                is BrowserEvent.ClearBrowsingData -> {
                    com.vidbox.util.BrowsingData.clear(context)
                    webView.apply { clearCache(true); clearHistory(); clearFormData() }
                    browser.browsingDataCleared()
                }
            }
        }
    }
    LaunchedEffect(Unit) { state.currentUrl?.let { webView.loadUrl(it) } }
    BackHandler(enabled = state.canGoBack || state.historyOpen) {
        if (state.historyOpen) browser.toggleHistory(false) else webView.goBack()
    }

    Box(Modifier.fillMaxSize().testTag("browser_screen")) {
        Column(Modifier.fillMaxSize()) {
            AddressBar(state, browser, webView)
            if (state.loading) {
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp))
            }
            AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth())
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = browser::home, modifier = Modifier.testTag("browser_home")) {
                        Icon(VidboxIcons.home, "Home page")
                    }
                    if (state.loading) {
                        IconButton(onClick = { webView.stopLoading(); browser.stop() },
                            modifier = Modifier.testTag("browser_stop")) { Icon(VidboxIcons.cancel, "Stop loading") }
                    } else {
                        IconButton(onClick = { webView.reload() }, enabled = state.currentUrl != null,
                            modifier = Modifier.testTag("browser_reload")) { Icon(VidboxIcons.reload, "Reload page") }
                    }
                    BrowserDownloadAction(
                        enabled = state.currentUrl != null,
                        onClick = { onDownloadPage(state.currentUrl) },
                        onLiftStart = { onDownloadDragStart(it) },
                        onLiftMove = { onDownloadDragMove(it) },
                        onLiftEnd = { onDownloadDragEnd(it) },
                        onLiftCancel = { onDownloadDragCancel() })
                    FilledTonalIconButton(onClick = { state.currentUrl?.let(onOpenExternal) }, enabled = state.currentUrl != null,
                        modifier = Modifier.testTag("browser_open_external")) { Icon(VidboxIcons.openExternally, "Open in your browser") }
                    BrowserMenu(browser, state) { text -> scope.launch { snackbar.showSnackbar(text) } }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    HistorySheet(state, browser)

    state.jsDialog?.let { dialog ->
        var prompt by remember(dialog) { mutableStateOf(dialog.default) }
        AlertDialog(onDismissRequest = { browser.dismissJsDialog(null) },
            title = { Text(dialog.promptText?.let { "Website asks for input" } ?: "Website request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(dialog.message)
                    if (dialog.promptText != null) {
                        OutlinedTextField(value = prompt, onValueChange = { prompt = it }, singleLine = true,
                            label = { Text(dialog.promptText!!) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { browser.dismissJsDialog(prompt) }) { Text(if (dialog.promptText != null) "OK" else "Allow") } },
            dismissButton = { TextButton(onClick = { browser.dismissJsDialog(null) }) { Text("Don't allow") } })
    }

    state.pending?.let { pending ->
        AlertDialog(onDismissRequest = browser::dismissDownload,
            title = { Text(if (pending.isMedia) "Download media?" else "Download file?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pending.fileName ?: "File from ${pending.url.take(120)}", style = MaterialTheme.typography.titleSmall)
                    Text(downloadTypeLabel(pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    pending.totalBytes?.let { Text("Size ${DisplayFormat.bytes(it)}", style = MaterialTheme.typography.bodySmall) }
                    Text(if (pending.isMedia) "Saves to your media folder (Movies/Vidbox or the folder you chose in Settings)."
                        else "Saves to Downloads/Vidbox.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!notificationsAllowed) onRequestNotifications()
                    browser.confirmDownload()
                }, modifier = Modifier.testTag("browser_confirm_download")) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = browser::dismissDownload) { Text("Cancel") } })
    }
}

@Composable
private fun BrowserMenu(browser: BrowserViewModel, state: BrowserState, onMessage: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("browser_menu")) {
            Icon(VidboxIcons.menu, "More browser options")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Share page") }, leadingIcon = { Icon(VidboxIcons.share, null) },
                enabled = state.currentUrl != null, onClick = {
                    menu = false
                    state.currentUrl?.let { url ->
                        runCatching {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }, "Share page"))
                        }.onFailure { onMessage("No app can share links.") }
                    }
                })
            DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(VidboxIcons.link, null) },
                enabled = state.currentUrl != null, onClick = {
                    menu = false
                    state.currentUrl?.let { url ->
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                            ClipData.newPlainText("Page link", url))
                        onMessage("Page link copied")
                    }
                })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Desktop site") }, leadingIcon = { Icon(VidboxIcons.browser, null) },
                trailingIcon = { Checkbox(checked = state.desktop, onCheckedChange = null) },
                onClick = { menu = false; browser.setDesktop(!state.desktop) })
            DropdownMenuItem(text = { Text("Page history") }, leadingIcon = { Icon(VidboxIcons.history, null) },
                onClick = { menu = false; browser.toggleHistory(true) })
            DropdownMenuItem(text = { Text("Clear browsing data") }, leadingIcon = { Icon(VidboxIcons.delete, null) },
                onClick = { menu = false; browser.clearBrowsingData() })
        }
    }
}

/** Session page history: in memory only, cleared with browsing data, never written to disk. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(state: BrowserState, browser: BrowserViewModel) {
    if (!state.historyOpen) return
    ModalBottomSheet(onDismissRequest = { browser.toggleHistory(false) },
        modifier = Modifier.testTag("browser_history_sheet")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Pages visited here", style = MaterialTheme.typography.titleMedium)
                if (state.visited.isNotEmpty()) {
                    TextButton(onClick = browser::clearBrowsingData) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                }
            }
            if (state.visited.isEmpty()) {
                Text("No pages yet. Sites you open in this tab appear here until you clear them.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp))
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(state.visited.asReversed(), key = { it.url + it.at }) { page ->
                        ListItem(headlineContent = { Text(page.title ?: page.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(page.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(if (page.url.startsWith("https://")) VidboxIcons.secure else VidboxIcons.insecure,
                                if (page.url.startsWith("https://")) "Secure page" else "Insecure page", Modifier.size(20.dp)) },
                            modifier = Modifier.testTag("browser_history_item").fillMaxWidth()
                                .clickable(onClick = { browser.toggleHistory(false); browser.open(page.url) }))
                    }
                }
            }
        }
    }
}

/**
 * The "Download from this page" action is also a drag handle: hold it for a moment and the
 * action detaches and follows your finger anywhere on screen (the drop target is rendered by
 * the app shell, which receives window-space positions). A plain tap keeps its normal behavior.
 */
@Composable
private fun RowScope.BrowserDownloadAction(enabled: Boolean, onClick: () -> Unit,
    onLiftStart: (Offset) -> Unit, onLiftMove: (Offset) -> Unit,
    onLiftEnd: (Offset) -> Unit, onLiftCancel: () -> Unit) {
    val view = LocalView.current
    var lifting by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestStart by rememberUpdatedState(onLiftStart)
    val latestMove by rememberUpdatedState(onLiftMove)
    val latestEnd by rememberUpdatedState(onLiftEnd)
    val latestCancel by rememberUpdatedState(onLiftCancel)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestClick by rememberUpdatedState(onClick)

    fun windowOf(local: Offset): Offset = (coordinates?.positionInWindow() ?: Offset.Zero) + local

    Box(Modifier.weight(1f).heightIn(min = 48.dp)
        .onGloballyPositioned { coordinates = it }
        .then(if (enabled) Modifier.pointerInput(Unit) {
            var lastLocal = Offset.Zero
            detectDragGesturesAfterLongPress(
                onDragStart = { start ->
                    if (latestEnabled) {
                        lifting = true
                        lastLocal = start
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        latestStart(windowOf(start))
                    }
                },
                onDrag = { change, _ ->
                    if (latestEnabled) {
                        change.consume()
                        lastLocal = change.position
                        latestMove(windowOf(change.position))
                    }
                },
                onDragEnd = {
                    lifting = false
                    if (latestEnabled) latestEnd(windowOf(lastLocal))
                },
                onDragCancel = {
                    lifting = false
                    latestCancel()
                })
        } else Modifier)) {
        Button(onClick = { latestClick() }, enabled = enabled && !lifting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .alpha(if (lifting) 0f else 1f).testTag("browser_download_page"),
            shape = RoundedCornerShape(14.dp)) {
            Icon(VidboxIcons.download, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
            Text("Download from this page")
        }
    }
}

@Composable
private fun AddressBar(state: BrowserState, browser: BrowserViewModel, webView: WebView) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { webView.goBack() }, enabled = state.canGoBack, modifier = Modifier.testTag("browser_back")) {
                    Icon(VidboxIcons.back, "Back")
                }
                IconButton(onClick = { webView.goForward() }, enabled = state.canGoForward, modifier = Modifier.testTag("browser_forward")) {
                    Icon(VidboxIcons.forward, "Forward")
                }
                OutlinedTextField(value = state.address, onValueChange = browser::address,
                    modifier = Modifier.weight(1f).testTag("browser_address"), singleLine = true,
                    placeholder = { Text("Search or enter a web address") },
                    leadingIcon = {
                        Icon(if (state.secure) VidboxIcons.secure else VidboxIcons.insecure,
                            if (state.secure) "Secure HTTPS page" else "Connection is not secure",
                            Modifier.size(18.dp),
                            tint = if (state.secure) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { browser.open(state.address) }),
                    shape = RoundedCornerShape(14.dp))
                IconButton(onClick = { browser.open(state.address) }, enabled = state.address.isNotBlank(), modifier = Modifier.testTag("browser_go")) {
                    Icon(VidboxIcons.search, "Open address")
                }
            }
            state.title?.let { title ->
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))
            }
        }
    }
}

internal fun downloadTypeLabel(pending: PendingDownload): String {
    val ext = FileNames.extensionOf(pending.url, pending.fileName)
    return when {
        pending.mimeType?.startsWith("video/") == true -> "Video file"
        pending.mimeType?.startsWith("audio/") == true -> "Audio file"
        ext != null && FileNames.hasVideo(ext) -> "${ext.uppercase()} video"
        ext != null && ext in FileNames.extensions -> "${ext.uppercase()} audio"
        ext != null -> "${ext.uppercase()} file"
        else -> "File"
    }
}
