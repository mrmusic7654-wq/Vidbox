package com.vidbox.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidbox.domain.model.*
import com.vidbox.presentation.components.*

/** The built-in shortcut tiles shown before the user's own tiles (screenshot home grid). */
private data class SiteTile(val label: String, val url: String, val background: Color, val letter: String,
    val gradient: List<Color>? = null)

private val builtInTiles = listOf(
    SiteTile("Tiktok", "https://www.tiktok.com", Color(0xFF111111), "♪"),
    SiteTile("Facebook", "https://www.facebook.com", Color(0xFF1877F2), "f"),
    SiteTile("Twitter", "https://x.com", Color(0xFF111111), "𝕏"),
    SiteTile("Vimeo", "https://vimeo.com", Color(0xFF17B3E8), "V"),
    SiteTile("Dailymotion", "https://www.dailymotion.com", Color(0xFF111111), "d"),
    SiteTile("Whatsapp", "https://web.whatsapp.com", Color(0xFF25D366), "☎"),
    SiteTile("Instagram", "https://www.instagram.com", Color(0xFFD6249F), "◎",
        gradient = listOf(Color(0xFF7B2FF7), Color(0xFFD6249F), Color(0xFFFDA085))),
)

/**
 * The downloader-style home screen: an orange "Search video online" pill (opens the
 * YouTube search flow), a "Search or type a URL" bar on the same screen (opens the
 * browser), a grid of site shortcut tiles, and quick help — with active transfers and
 * recently saved media underneath.
 */
@Composable
fun HomeScreen(state: HomeState, active: List<DownloadRecord>, recent: List<DownloadRecord>, network: NetworkStatus,
    preferences: AppSettings, callbacks: DownloadCallbacks,
    onOpenSearch: () -> Unit, onOpenBrowser: (String?) -> Unit, onOpenSettings: () -> Unit,
    onDownloads: () -> Unit, onVideos: () -> Unit,
    onAddShortcut: (String, String) -> Unit, onRemoveShortcut: (SiteShortcut) -> Unit) {
    var helpOpen by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().testTag("home_screen"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(38.dp)
                Text("Vidbox", Modifier.padding(start = 9.dp), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("nav_settings")) {
                    Icon(VidboxIcons.settings, "Open settings")
                }
            }
        }
        item {
            Surface(onClick = onOpenSearch, shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("home_search")) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onPrimary)
                    Text("Search video online", Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
                    Icon(VidboxIcons.playCircle, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        item {
            Surface(onClick = { onOpenBrowser(null) }, shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("home_url_bar")) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Public, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Search or type a URL", Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            val tiles = builtInTiles + preferences.siteShortcuts.map { SiteTile(it.label, it.url, MaterialColor(it.label), it.label.take(1).uppercase()) }
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                tiles.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { tile ->
                            SiteTileView(tile, Modifier.weight(1f), onOpen = { onOpenBrowser(tile.url) },
                                onLongPress = if (preferences.siteShortcuts.any { it.url == tile.url })
                                    ({ onRemoveShortcut(SiteShortcut(tile.label, tile.url)) }) else null)
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Spacer(Modifier.weight(1f)); Spacer(Modifier.weight(1f)); Spacer(Modifier.weight(1f))
                    Column(Modifier.weight(1f).clickable { addDialog = true }.testTag("home_add_shortcut"),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(60.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Add, "Add a site shortcut", Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("Add", Modifier.padding(top = 7.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clickable { helpOpen = !helpOpen }.padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("💡 ", fontSize = 17.sp)
                Text("How to download video?", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (helpOpen) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HelpLine("1", "Search a video by name, or paste its link.")
                        HelpLine("2", "Pick the download you want — quality is shown up front.")
                        HelpLine("3", "Watch it any time from Video, Audio, or Download.")
                    }
                }
            }
        }
        if (network.blocked) item { InfoBanner("Android is restricting network access. Check Data Saver or this app’s network settings.") }
        if (!network.connected) item { InfoBanner("You're offline. Queued downloads will wait for a connection.") }
        if (active.isNotEmpty()) {
            item { SectionHeading("In progress · ${active.size}", "View all", onDownloads) }
            items(active.take(2), key = { "active_${it.id}" }) { DownloadCard(it, callbacks, compact = true) }
        }
        val saved = recent.filter { it.state.isTerminal }
        item { SectionHeading("Recently saved", if (saved.isNotEmpty()) "Library" else null, onVideos) }
        if (saved.isEmpty()) item {
            EmptyState(VidboxIcons.library, "Make room for your favorites",
                "Search or paste a public video link, choose its quality, and save it to your device.",
                modifier = Modifier.padding(vertical = 2.dp))
        } else items(saved, key = { "recent_${it.id}" }) { MediaRow(it, callbacks, showDuration = true) }
    }

    if (addDialog) {
        var label by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var urlError by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { addDialog = false },
            title = { Text("Add a site shortcut") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = label, onValueChange = { label = it }, singleLine = true,
                        label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = url, onValueChange = { url = it; urlError = false }, singleLine = true,
                        label = { Text("Address") }, placeholder = { Text("example.com") },
                        isError = urlError, supportingText = { if (urlError) Text("Use a full https:// address") },
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val candidate = url.trim()
                    val normalized = when {
                        candidate.startsWith("https://", true) || candidate.startsWith("http://", true) -> candidate
                        candidate.contains('.') -> "https://$candidate"
                        else -> null
                    }
                    if (normalized == null) { urlError = true } else {
                        val fallback = runCatching { java.net.URI(normalized).host?.removePrefix("www.") }.getOrNull() ?: "Site"
                        onAddShortcut(label.trim().ifBlank { fallback }, normalized)
                        addDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Cancel") } })
    }
}

@Composable
private fun HelpLine(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
            Text(number, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold)
        }
        Text(text, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Stable, pleasant tile color derived from the label so custom tiles look designed. */
private fun MaterialColor(label: String): Color {
    val palette = listOf(0xFFE53935, 0xFF8E24AA, 0xFF3949AB, 0xFF00897B, 0xFFF4511E, 0xFF6D4C41, 0xFF039BE5, 0xFF7CB342)
    val index = label.lowercase().sumOf { it.code } % palette.size
    return Color(palette[index])
}

/** One circular site tile like the home grid in the reference design. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SiteTileView(tile: SiteTile, modifier: Modifier = Modifier, onOpen: () -> Unit, onLongPress: (() -> Unit)?) {
    Column(modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress).testTag("home_tile_${tile.label}"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        val background = if (tile.gradient != null) Brush.linearGradient(tile.gradient)
        else Brush.linearGradient(listOf(tile.background, tile.background))
        Surface(shape = CircleShape, modifier = Modifier.size(60.dp),
            border = if (tile.gradient == null && tile.background == Color(0xFF111111))
                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null) {
            Box(Modifier.background(background), contentAlignment = Alignment.Center) {
                Text(tile.letter, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(tile.label, Modifier.padding(top = 7.dp), style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center, maxLines = 1)
    }
}
