package com.vidbox.presentation.browser

import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vidbox.domain.util.DisplayFormat
import com.vidbox.domain.util.FileNames

@Composable
fun BrowserScreen(browser: BrowserViewModel, state: BrowserState, notificationsAllowed: Boolean,
    onRequestNotifications: () -> Unit, onDownloadPage: (String?) -> Unit, onOpenExternal: (String) -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
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
                override fun onPageStarted(view: WebView, url: String?) { browser.pageStarted(url) }
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
    LaunchedEffect(browser) {
        browser.events.collect { event ->
            when (event) {
                is BrowserEvent.Navigate -> webView.loadUrl(event.url)
                is BrowserEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    LaunchedEffect(Unit) { state.currentUrl?.let { webView.loadUrl(it) } }
    BackHandler(enabled = state.canGoBack) { webView.goBack() }

    Box(Modifier.fillMaxSize().testTag("browser_screen")) {
        Column(Modifier.fillMaxSize()) {
            AddressBar(state, browser, webView)
            if (state.loading) {
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp))
            }
            AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth())
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { webView.reload() }, enabled = state.currentUrl != null,
                        modifier = Modifier.testTag("browser_reload")) { Icon(Icons.Rounded.Refresh, "Reload page") }
                    Button(onClick = { onDownloadPage(state.currentUrl) }, enabled = state.currentUrl != null,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("browser_download_page"),
                        shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                        Text("Download from this page")
                    }
                    FilledTonalIconButton(onClick = { state.currentUrl?.let(onOpenExternal) }, enabled = state.currentUrl != null,
                        modifier = Modifier.testTag("browser_open_external")) { Icon(Icons.Rounded.OpenInNew, "Open in your browser") }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

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
private fun AddressBar(state: BrowserState, browser: BrowserViewModel, webView: WebView) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { webView.goBack() }, enabled = state.canGoBack, modifier = Modifier.testTag("browser_back")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
                IconButton(onClick = { webView.goForward() }, enabled = state.canGoForward, modifier = Modifier.testTag("browser_forward")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Forward")
                }
                OutlinedTextField(value = state.address, onValueChange = browser::address,
                    modifier = Modifier.weight(1f).testTag("browser_address"), singleLine = true,
                    placeholder = { Text("Search or enter a web address") }, leadingIcon = { Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { browser.open(state.address) }),
                    shape = RoundedCornerShape(14.dp))
                IconButton(onClick = { browser.open(state.address) }, enabled = state.address.isNotBlank(), modifier = Modifier.testTag("browser_go")) {
                    Icon(Icons.Rounded.Search, "Open address")
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
