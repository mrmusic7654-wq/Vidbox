package com.vidbox.presentation.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vidbox.domain.util.BrowserLinks
import com.vidbox.domain.util.FileNames
import com.vidbox.presentation.components.BrandMark
import com.vidbox.presentation.components.VidboxIcons

/**
 * A Chrome-like browser: pill address bar with a security shield, a home button, a tab
 * counter that opens a card grid of open tabs, and an overflow menu. Each tab owns its
 * own WebView (created lazily on first display) and its own session page history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(browser: BrowserViewModel, state: BrowserState, notificationsAllowed: Boolean,
    onRequestNotifications: () -> Unit, onDownloadPage: (String?) -> Unit, onOpenExternal: (String) -> Unit,
    onClosed: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val webViews = remember { mutableMapOf<String, WebView>() }

    fun webViewFor(tabId: String): WebView = webViews.getOrPut(tabId) {
        buildWebView(context, tabId, browser)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { it.destroy() }
            webViews.clear()
        }
    }

    // Apply user browser preferences; a mid-session change reloads the current page.
    var preferencesApplied by remember { mutableStateOf(false) }
    LaunchedEffect(state.javaScript, state.cookies, state.desktop) {
        webViews.values.forEach { webView ->
            webView.settings.javaScriptEnabled = state.javaScript
            webView.settings.userAgentString = if (state.desktop) BrowserLinks.DESKTOP_USER_AGENT else null
            webView.settings.useWideViewPort = state.desktop
            webView.settings.loadWithOverviewMode = state.desktop
            if (preferencesApplied && webView.url != null) webView.reload()
        }
        CookieManager.getInstance().setAcceptCookie(state.cookies)
        preferencesApplied = true
    }
    // Release rendering and timers while the app is backgrounded; the WebViews survive.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { webViews.values.forEach { it.onResume(); it.resumeTimers() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { webViews.values.forEach { it.onPause(); it.pauseTimers() } }

    LaunchedEffect(browser) {
        browser.events.collect { event ->
            when (event) {
                is BrowserEvent.Navigate -> {
                    val view = webViewFor(event.tabId)
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.loadUrl(event.url)
                }
                is BrowserEvent.Message -> if (event.text.isNotBlank()) snackbar.showSnackbar(event.text)
                is BrowserEvent.ClearBrowsingData -> {
                    com.vidbox.util.BrowsingData.clear(context)
                    webViews.values.forEach { it.apply { clearCache(true); clearHistory(); clearFormData() } }
                    browser.browsingDataCleared()
                }
            }
        }
    }

    val activeTab = state.activeTab
    // Floating quick-download button: draggable anywhere in the window, remembers its spot per screen visit.
    var browserSize by remember { mutableStateOf(IntSize.Zero) }
    var quickFiles by remember { mutableStateOf<List<PageFile>>(emptyList()) }
    var quickOpen by remember { mutableStateOf(false) }
    var quickScanning by remember { mutableStateOf(false) }
    BackHandler {
        val view = activeTab?.let { webViews[it.id] }
        when {
            state.switcherOpen -> browser.toggleSwitcher(false)
            view?.canGoBack() == true -> view.goBack()
            else -> onClosed()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("browser_screen")
        .onSizeChanged { browserSize = it }) {
        Column(Modifier.fillMaxSize()) {
            BrowserTopBar(state, browser, onDownloadPage)
            if ((activeTab?.loading == true)) {
                LinearProgressIndicator(
                    progress = { (activeTab.progress.takeIf { it > 0 } ?: 8) / 100f },
                    modifier = Modifier.fillMaxWidth().height(3.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (activeTab == null) {
                    Text("No tabs open", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (activeTab.currentUrl == null && activeTab.pendingUrl == null && !activeTab.loading) {
                    StartPage(onOpen = { url -> browser.open(activeTab.id, url) })
                } else {
                    key(activeTab.id) {
                        AndroidView(
                            factory = {
                                val pooled = webViewFor(activeTab.id)
                                (pooled.parent as? ViewGroup)?.removeView(pooled)
                                pooled
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Tab switcher: Chrome-style card grid.
        if (state.switcherOpen) {
            TabSwitcher(state, browser)
        }

        state.pending?.let { pending ->
            AlertDialog(onDismissRequest = browser::dismissDownload,
                title = { Text("Download this file?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(pending.fileName ?: BrowserLinks.displayHost(pending.url) ?: pending.url,
                            style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(buildString {
                            append(downloadTypeLabel(pending))
                            pending.totalBytes?.let { append("  ·  about ${com.vidbox.domain.util.DisplayFormat.bytes(it)}") }
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = { TextButton(onClick = browser::confirmDownload) { Text("Download") } },
                dismissButton = { TextButton(onClick = browser::dismissDownload) { Text("Cancel") } })
        }
        state.jsDialog?.let { dialog ->
            var text by remember(dialog.message) { mutableStateOf(dialog.promptText ?: dialog.default) }
            AlertDialog(onDismissRequest = { browser.dismissJsDialog(null) },
                title = { Text(if (dialog.promptText != null) "This page asks for input" else "Message from this page") },
                text = {
                    Column {
                        Text(dialog.message, style = MaterialTheme.typography.bodyMedium)
                        if (dialog.promptText != null) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { browser.dismissJsDialog(if (dialog.promptText != null) text else "ok") }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { browser.dismissJsDialog(null) }) { Text("Cancel") } })
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        if (activeTab?.currentUrl != null && !state.switcherOpen) {
            QuickDownloadButton(browserSize, enabled = !quickScanning, modifier = Modifier.align(Alignment.BottomEnd),
                onScan = {
                    val view = activeTab.let { webViews[it.id] }
                    if (view == null) {
                        quickFiles = emptyList(); quickOpen = true
                    } else {
                        quickScanning = true
                        view.evaluateJavascript(SCAN_PAGE_JS) { payload ->
                            quickScanning = false
                            quickFiles = runCatching { parsePageFiles(payload ?: "") }.getOrDefault(emptyList())
                            quickOpen = true
                        }
                    }
                })
        }
        if (quickOpen) {
            ModalBottomSheet(onDismissRequest = { quickOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                QuickDownloadSheet(files = quickFiles, hasPage = activeTab?.currentUrl != null,
                    onFile = { file -> quickOpen = false; browser.downloadStart(file.url, null, null, 0) },
                    onAnalyzePage = { quickOpen = false; onDownloadPage(activeTab?.currentUrl) })
            }
        }
    }
    HistorySheet(state, browser)
}

/** Address pill + home + tab counter + menu, matching the Chrome toolbar layout. */
@Composable
private fun BrowserTopBar(state: BrowserState, browser: BrowserViewModel, onDownloadPage: (String?) -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val active = state.activeTab
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shadowElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                AddressPill(state, browser, modifier = Modifier.weight(1f))
                IconButton(onClick = { active?.let { browser.home(it.id) } }, enabled = active != null,
                    modifier = Modifier.testTag("browser_home")) {
                    Icon(VidboxIcons.home, "Home page")
                }
                Surface(onClick = { browser.toggleSwitcher(!state.switcherOpen) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.size(30.dp).testTag("browser_tabs")) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${state.tabs.size}", style = MaterialTheme.typography.labelMedium,
                            fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("browser_menu")) {
                        Icon(VidboxIcons.menu, "More browser options")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("New tab") }, leadingIcon = { Icon(VidboxIcons.newTab, null) },
                            onClick = { menu = false; browser.newTab() })
                        DropdownMenuItem(text = { Text("Page history") }, leadingIcon = { Icon(VidboxIcons.history, null) },
                            onClick = { menu = false; browser.toggleHistory(true) })
                        DropdownMenuItem(text = { Text("Share page") }, leadingIcon = { Icon(VidboxIcons.share, null) },
                            enabled = state.activeTab?.currentUrl != null, onClick = {
                                menu = false
                                state.activeTab?.currentUrl?.let { url ->
                                    runCatching {
                                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, url)
                                        }, "Share page"))
                                    }.onFailure { }
                                }
                            })
                        DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(VidboxIcons.link, null) },
                            enabled = state.activeTab?.currentUrl != null, onClick = {
                                menu = false
                                state.activeTab?.currentUrl?.let { url ->
                                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                                        ClipData.newPlainText("Page link", url))
                                }
                            })
                        DropdownMenuItem(text = { Text("Download from this page") },
                            leadingIcon = { Icon(VidboxIcons.download, null) },
                            enabled = state.activeTab?.currentUrl != null, onClick = {
                                menu = false
                                onDownloadPage(state.activeTab?.currentUrl)
                            })
                        DropdownMenuItem(text = { Text("Desktop site") }, leadingIcon = { Icon(VidboxIcons.browser, null) },
                            trailingIcon = { Checkbox(checked = state.desktop, onCheckedChange = null) },
                            onClick = { menu = false; browser.setDesktop(!state.desktop) })
                        DropdownMenuItem(text = { Text("Clear browsing data") }, leadingIcon = { Icon(VidboxIcons.delete, null) },
                            onClick = { menu = false; browser.clearBrowsingData() })
                    }
                }
            }
            active?.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp, top = 1.dp))
            }
        }
    }
}

/** The rounded Chrome address bar: shield or open lock, editable text, reload/stop. */
@Composable
private fun AddressPill(state: BrowserState, browser: BrowserViewModel, modifier: Modifier = Modifier) {
    val active = state.activeTab
    val secure = state.activeTab?.secure == true
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier.height(46.dp)) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (secure) VidboxIcons.privacy else VidboxIcons.insecure,
                if (secure) "Secure HTTPS page" else "Connection is not secure",
                Modifier.size(19.dp),
                tint = if (secure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                if ((active?.address ?: "").isEmpty()) {
                    Text("Search or type a URL", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = active?.address ?: "",
                    onValueChange = { value -> active?.let { browser.address(it.id, value) } },
                    modifier = Modifier.fillMaxWidth().testTag("browser_address"),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        active?.let { browser.open(it.id, it.address) }
                    }),
                )
            }
            if (active?.loading == true) {
                IconButton(onClick = { active?.let { browser.stop(it.id) } }, modifier = Modifier.testTag("browser_stop")) {
                    Icon(VidboxIcons.cancel, "Stop loading", Modifier.size(21.dp))
                }
            } else {
                IconButton(onClick = { browser.reload(active?.id ?: "") }, enabled = active != null,
                    modifier = Modifier.testTag("browser_reload")) {
                    Icon(Icons.Rounded.Refresh, "Reload page", Modifier.size(21.dp))
                }
            }
        }
    }
}

/** Shown for a brand-new empty tab: quick links instead of a blank WebView. */
@Composable
private fun StartPage(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BrandMark(64.dp)
        Spacer(Modifier.height(18.dp))
        Text("Search or type a URL", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text("Any HTTPS page opens here. Use the ⋮ menu to download from the page you are on.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("google.com" to "Google", "youtube.com" to "YouTube", "wikipedia.org" to "Wikipedia").forEach { (host, label) ->
                OutlinedButton(onClick = { onOpen("https://$host") }) { Text(label) }
            }
        }
    }
}

/** Full-screen grid of open tabs with per-card close and a new-tab button. */
@Composable
private fun TabSwitcher(state: BrowserState, browser: BrowserViewModel) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), modifier = Modifier.fillMaxSize()
        .testTag("browser_switcher")) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${state.tabs.size} tab${if (state.tabs.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = browser::closeAllTabs) { Text("Close all", color = MaterialTheme.colorScheme.error) }
            }
            LazyVerticalGrid(columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)) {
                items(state.tabs, key = { it.id }) { tab ->
                    val selected = tab.id == state.activeTabId
                    Surface(onClick = { browser.selectTab(tab.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        shadowElevation = if (selected) 6.dp else 1.dp,
                        modifier = Modifier.aspectRatio(0.72f).testTag("browser_tab_card")) {
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp, top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (tab.secure) VidboxIcons.privacy else VidboxIcons.browser, null,
                                    Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(BrowserLinks.displayHost(tab.currentUrl ?: tab.pendingUrl) ?: "New tab",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 6.dp))
                                IconButton(onClick = { browser.closeTab(tab.id) },
                                    modifier = Modifier.size(30.dp).testTag("browser_tab_close")) {
                                    Icon(Icons.Rounded.Close, "Close tab", Modifier.size(16.dp))
                                }
                            }
                            Column(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.Center) {
                                Icon(VidboxIcons.browser, null, Modifier.size(30.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Spacer(Modifier.height(8.dp))
                                Text(tab.title ?: BrowserLinks.displayHost(tab.currentUrl ?: tab.pendingUrl) ?: "New tab",
                                    style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                FilledTonalButton(onClick = { browser.newTab() }, modifier = Modifier.testTag("browser_new_tab")) {
                    Icon(VidboxIcons.add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("New tab")
                }
            }
        }
    }
}

/** Session page history of the active tab: in memory only, cleared with browsing data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(state: BrowserState, browser: BrowserViewModel) {
    val visited = state.activeTab?.visited.orEmpty()
    if (!state.historyOpen) return
    ModalBottomSheet(onDismissRequest = { browser.toggleHistory(false) },
        modifier = Modifier.testTag("browser_history_sheet")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Pages visited here", style = MaterialTheme.typography.titleMedium)
                if (visited.isNotEmpty()) {
                    TextButton(onClick = browser::clearBrowsingData) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                }
            }
            if (visited.isEmpty()) {
                Text("No pages yet. Sites you open in this tab appear here until you clear them.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp))
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(visited.asReversed(), key = { it.url + it.at }) { page ->
                        ListItem(headlineContent = { Text(page.title ?: page.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(page.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(if (page.url.startsWith("https://")) VidboxIcons.secure else VidboxIcons.insecure,
                                if (page.url.startsWith("https://")) "Secure page" else "Insecure page", Modifier.size(20.dp)) },
                            modifier = Modifier.testTag("browser_history_item").fillMaxWidth()
                                .clickable(onClick = { browser.toggleHistory(false); browser.openCurrent(page.url) }))
                    }
                }
            }
        }
    }
}

/**
 * Builds one WebView per tab with the same security posture as before: no file/content
 * access, no mixed content, Safe Browsing pinned on by the manifest, and direct media
 * links routed into the download pipeline instead of an inline load.
 */
private fun buildWebView(context: Context, tabId: String, browser: BrowserViewModel): WebView =
    WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val scheme = request.url.scheme?.lowercase()
                if (scheme in setOf("about", "data", "javascript", "blob")) return true
                if (scheme != "http" && scheme != "https") {
                    browser.unsupported(url)
                    return true
                }
                val ext = FileNames.extensionOf(url)
                if (ext != null && FileNames.isDownloadable(ext)) {
                    browser.downloadStart(url, null, null, -1)
                    return true
                }
                return false
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                browser.pageStarted(tabId, url)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                browser.pageFinished(tabId, url, view.canGoBack(), view.canGoForward())
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { browser.progress(tabId, newProgress) }
            override fun onReceivedTitle(view: WebView, title: String?) { browser.title(tabId, title) }
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

/**
 * The floating, draggable download button for the browser. Tap scans the visible page for
 * direct video, audio, and file links and opens a picker; dragging repositions it anywhere
 * along the page edges so it never covers content you are reading.
 */
@Composable
private fun QuickDownloadButton(browserSize: IntSize, enabled: Boolean, modifier: Modifier = Modifier,
    onScan: () -> Unit) {
    val density = LocalDensity.current
    val fabSize = 54.dp
    val margin = with(density) { 16.dp.toPx() }
    val fabPx = with(density) { fabSize.toPx() }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val maxX = (browserSize.width - fabPx - margin).coerceAtLeast(0f)
    val maxY = (browserSize.height - fabPx - with(density) { 110.dp.toPx() }).coerceAtLeast(0f)
    Surface(
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = if (dragging) 10.dp else 6.dp,
        modifier = modifier
            .offset { IntOffset(-offset.x.roundToInt(), -offset.y.roundToInt()) }
            .size(fabSize)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, amount ->
                    change.consume()
                    offset = Offset((offset.x - amount.x).coerceIn(0f, maxX), (offset.y - amount.y).coerceIn(0f, maxY))
                }
            }
            .clickable(enabled = enabled && !dragging) { onScan() },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(fabSize)) {
            if (enabled) {
                Icon(VidboxIcons.download, "Find downloads on this page",
                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
            } else {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

/** One direct media/file link found on the current page. */
internal data class PageFile(val url: String, val name: String)

/**
 * Rows in the style of the video-search results: icon, file name, site · type, and a
 * download action per row. The last entry always offers the page analyzer for streams
 * that do not expose a direct link (e.g. segmented video players).
 */
@Composable
private fun QuickDownloadSheet(files: List<PageFile>, hasPage: Boolean, onFile: (PageFile) -> Unit,
    onAnalyzePage: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text("Download from this page", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        if (files.isEmpty()) {
            Text(
                if (hasPage) "No direct video, audio, or file links were found on this page. " +
                    "Videos that stream in pieces cannot be saved directly — run the page through the analyzer below."
                else "Open a page first, then look for downloads here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp))
        } else {
            LazyColumn(Modifier.heightIn(max = 430.dp), contentPadding = PaddingValues(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(files, key = { it.url }) { file ->
                    Surface(onClick = { onFile(file) }, shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth().testTag("page_file_row")) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val ext = file.url.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
                            Icon(when {
                                ext in FileNames.audioExtensions -> VidboxIcons.audio
                                ext in FileNames.extensions -> VidboxIcons.video
                                else -> VidboxIcons.file
                            }, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(file.name, style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(BrowserLinks.displayHost(file.url), ext.takeIf { it.isNotEmpty() }?.uppercase())
                                    .joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(VidboxIcons.download, "Download ${file.name}",
                                Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        if (hasPage) {
            HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            Surface(onClick = onAnalyzePage, shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).testTag("page_analyze_row")) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(VidboxIcons.analyzing, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Analyze this page for videos", style = MaterialTheme.typography.bodyLarge)
                        Text("Runs the page link through the video engine — works for players without direct links",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(VidboxIcons.download, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/**
 * Collects candidate downloads from the current page: media element sources first, then
 * document anchors. The scanner script answers one `url\Tname` pair per line, tab-separated;
 * Kotlin filters to real download targets (media/file extensions) and de-duplicates.
 */
internal fun parsePageFiles(payload: String): List<PageFile> {
    val seen = mutableSetOf<String>()
    val files = mutableListOf<PageFile>()
    payload.lineSequence().forEach { line ->
        if (line.isBlank()) return@forEach
        val tab = line.indexOf('\t')
        val url = (if (tab >= 0) line.substring(0, tab) else line).trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return@forEach
        val clean = url.substringBefore('#').substringBefore('?')
        val ext = clean.substringAfterLast('.', "").lowercase()
        if (ext !in FileNames.extensions && ext !in FileNames.fileExtensions) return@forEach
        if (!seen.add(clean)) return@forEach
        var name = if (tab >= 0) line.substring(tab + 1).trim() else ""
        if (name.isEmpty()) name = clean.substringAfterLast('/').ifEmpty { url }
        files += PageFile(url, name.take(110))
    }
    return files.take(40)
}

private val SCAN_PAGE_JS = """
(function() {
  var lines = [];
  var seen = {};
  function abs(u) { try { return new URL(u, location.href).href } catch (e) { return null } }
  function clean(s) { return (s || '').replace(/[\t\r\n]+/g, ' ').trim().slice(0, 110); }
  function push(u, label) {
    var a = abs(u);
    if (!a || !/^https?:/i.test(a) || seen[a]) return;
    seen[a] = 1;
    var name = clean(label);
    if (!name) {
      try { name = decodeURIComponent(a.split('#')[0].split('?')[0].split('/').pop() || '') } catch (e) { name = '' }
    }
    lines.push(a + '\t' + name);
  }
  var media = document.querySelectorAll('video[src],audio[src],source[src]');
  for (var i = 0; i < media.length && i < 60; i++) push(media[i].getAttribute('src'), null);
  var anchors = document.querySelectorAll('a[href]');
  for (var j = 0; j < anchors.length && j < 400; j++) push(anchors[j].getAttribute('href'), anchors[j].textContent);
  return lines.slice(0, 120).join('\n');
})()
""".trimIndent()
