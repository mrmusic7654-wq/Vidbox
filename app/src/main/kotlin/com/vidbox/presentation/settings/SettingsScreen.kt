package com.vidbox.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vidbox.BuildConfig
import com.vidbox.domain.model.*
import com.vidbox.presentation.components.*

@Composable
fun SettingsScreen(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onFolder: () -> Unit, onClearHistory: () -> Unit, notificationsAllowed: Boolean, onNotifications: () -> Unit,
    onAboutLink: () -> Unit) {
    var clearDialog by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("settings_screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineLarge)
                Text("A little more your way.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SettingsGroup("STORAGE") {
                SettingAction(Icons.Rounded.FolderOpen, "Download location", settings.destinationLabel ?: "Videos: Movies/Vidbox\nAudio: Music/Vidbox", onFolder)
                if (settings.destinationTree != null) TextButton(onClick = { onUpdate { it.copy(destinationTree = null, destinationLabel = null) } },
                    modifier = Modifier.padding(start = 46.dp)) { Text("Use device media folders") }
                Text("Location changes apply to new downloads. No broad storage access is needed.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
        item {
            SettingsGroup("DOWNLOAD DEFAULTS") {
                SettingChoice(Icons.Rounded.HighQuality, "Default quality", if (settings.defaultQuality == 0) "Best available" else "Up to ${settings.defaultQuality}p",
                    listOf(0, 360, 480, 720, 1080, 1440, 2160), settings.defaultQuality,
                    label = { if (it == 0) "Best available" else "Up to ${it}p" }, onSelect = { quality -> onUpdate { it.copy(defaultQuality = quality) } })
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingChoice(Icons.Rounded.VideoFile, "Preferred container", settings.defaultContainer.uppercase(), listOf("mp4", "webm", "mkv"), settings.defaultContainer,
                    label = { it.uppercase() }, onSelect = { container -> onUpdate { it.copy(defaultContainer = container) } })
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingChoice(Icons.Rounded.Queue, "Concurrent downloads", "${settings.maxConcurrent} at a time", listOf(1, 2, 3, 4), settings.maxConcurrent,
                    label = { "$it at a time" }, onSelect = { max -> onUpdate { it.copy(maxConcurrent = max) } })
                Text("Defaults prefer compatible source formats. Audio stays in its original format. For low-memory devices, use one transfer at a time.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }
        item {
            SettingsGroup("CONNECTION & ALERTS") {
                SettingSwitch(Icons.Rounded.Wifi, "Wi-Fi only", "Wait on mobile data and metered hotspots.", settings.wifiOnly, "settings_wifi") { enabled -> onUpdate { it.copy(wifiOnly = enabled) } }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(Icons.Rounded.NotificationsNone, "Completion & error alerts", "Foreground progress is always required by Android.", settings.completionNotifications, "settings_notifications") { enabled ->
                    onUpdate { it.copy(completionNotifications = enabled) }
                    if (enabled && !notificationsAllowed) onNotifications()
                }
                if (!notificationsAllowed) {
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingAction(Icons.Rounded.NotificationsOff, "Enable system notifications", "Android is currently hiding notifications.", onNotifications)
                }
            }
        }
        item {
            SettingsGroup("APPEARANCE") {
                SettingChoice(Icons.Rounded.Palette, "Theme", settings.theme.name.lowercase().replaceFirstChar(Char::uppercase), AppTheme.entries, settings.theme,
                    label = { it.name.lowercase().replaceFirstChar(Char::uppercase) }, onSelect = { theme -> onUpdate { it.copy(theme = theme) } })
            }
        }
        item {
            SettingsGroup("YOUR DATA") {
                SettingAction(Icons.Rounded.History, "Clear download history", "Remove finished entries, not saved media.", { clearDialog = true })
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingAction(Icons.Rounded.Info, "About Vidbox", "Version ${BuildConfig.VERSION_NAME} · Open source", { aboutDialog = true })
            }
        }
        item {
            Text("Made for media you have the right to save. Vidbox does not bypass DRM, sign-in, geographic restrictions, or other access controls.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
        }
    }
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text("Clear download history?") },
        text = { Text("Finished entries and leftover temporary files will be removed. Saved media and active downloads will be kept.") },
        confirmButton = { TextButton(onClick = { clearDialog = false; onClearHistory() }) { Text("Clear history", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Keep history") } })
    if (aboutDialog) AlertDialog(onDismissRequest = { aboutDialog = false },
        title = { Row(verticalAlignment = Alignment.CenterVertically) { BrandMark(); Spacer(Modifier.width(12.dp)); Text("Vidbox") } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Version ${BuildConfig.VERSION_NAME}\nAndroid 10 and newer")
            Text("Media is extracted and saved on your device. No Vidbox account, analytics, or download relay server.")
            Text("Powered by yt-dlp Android 0.18.1 and FFmpeg. Engine updates ship with app releases. Not every site or protected video is supported.")
            Text("GPL-3.0-or-later. See the source repository for licenses, privacy details, supported paths, and build instructions.")
        } }, confirmButton = { TextButton(onClick = onAboutLink) { Text("Source & licenses") } },
        dismissButton = { TextButton(onClick = { aboutDialog = false }) { Text("Close") } })
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitch(icon: ImageVector, title: String, subtitle: String, value: Boolean, tag: String, onValue: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = onValue, modifier = Modifier.testTag(tag), thumbContent = {
            if (value) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
        })
    }
}

@Composable
private fun <T> SettingChoice(icon: ImageVector, title: String, subtitle: String, options: List<T>, selected: T,
    label: (T) -> String, onSelect: (T) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        SettingAction(icon, title, subtitle) { menu = true }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(label(option)) },
                trailingIcon = { if (selected == option) Icon(Icons.Rounded.Check, null) }, onClick = { menu = false; onSelect(option) }) }
        }
    }
}
