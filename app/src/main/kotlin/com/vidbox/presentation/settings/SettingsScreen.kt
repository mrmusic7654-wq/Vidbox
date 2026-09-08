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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.vidbox.BuildConfig
import com.vidbox.domain.model.*
import com.vidbox.domain.util.BrowserLinks
import com.vidbox.presentation.components.*

@Composable
fun SettingsScreen(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onFolder: () -> Unit, onClearHistory: () -> Unit, notificationsAllowed: Boolean, onNotifications: () -> Unit,
    onClearBrowserData: () -> Unit, onAboutLink: () -> Unit) {
    var clearDialog by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf(false) }
    var homepageDialog by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("settings_screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineLarge)
                Text("A little more your way.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SettingsGroup("STORAGE") {
                SettingAction(VidboxIcons.folder, "Download location", settings.destinationLabel ?: "Videos: Movies/Vidbox\nAudio: Music/Vidbox\nFiles: Downloads/Vidbox", onFolder)
                if (settings.destinationTree != null) TextButton(onClick = { onUpdate { it.copy(destinationTree = null, destinationLabel = null) } },
                    modifier = Modifier.padding(start = 46.dp)) { Text("Use device media folders") }
                Text("Location changes apply to new downloads. No broad storage access is needed.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
        item {
            SettingsGroup("DOWNLOAD DEFAULTS") {
                SettingChoice(VidboxIcons.quality, "Default quality", if (settings.defaultQuality == 0) "Best available" else "Up to ${settings.defaultQuality}p",
                    listOf(0, 360, 480, 720, 1080, 1440, 2160), settings.defaultQuality,
                    label = { if (it == 0) "Best available" else "Up to ${it}p" }, onSelect = { quality -> onUpdate { it.copy(defaultQuality = quality) } })
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingChoice(VidboxIcons.videoFormat, "Preferred container", settings.defaultContainer.uppercase(), listOf("mp4", "webm", "mkv"), settings.defaultContainer,
                    label = { it.uppercase() }, onSelect = { container -> onUpdate { it.copy(defaultContainer = container) } })
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingChoice(VidboxIcons.queue, "Concurrent downloads", "${settings.maxConcurrent} at a time", listOf(1, 2, 3, 4), settings.maxConcurrent,
                    label = { "$it at a time" }, onSelect = { max -> onUpdate { it.copy(maxConcurrent = max) } })
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(VidboxIcons.reload, "Resume after interruptions", "Reconnect or app restart continues paused downloads automatically.",
                    settings.autoResume, "settings_auto_resume") { enabled -> onUpdate { it.copy(autoResume = enabled) } }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingChoice(VidboxIcons.file, "Duplicate files", if (settings.duplicatePolicy == DuplicatePolicy.KEEP_BOTH)
                    "Keep both (adds a number to the new file)" else "Skip when a file with the same name exists",
                    DuplicatePolicy.entries, settings.duplicatePolicy,
                    label = { if (it == DuplicatePolicy.KEEP_BOTH) "Keep both" else "Skip duplicates" },
                    onSelect = { policy -> onUpdate { it.copy(duplicatePolicy = policy) } })
                Text("Defaults prefer compatible source formats. Audio stays in its original format. For low-memory devices, use one transfer at a time.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }
        item {
            SettingsGroup("BROWSER") {
                SettingAction(VidboxIcons.home, "Homepage",
                    settings.browserHomepage?.takeIf { it.isNotBlank() } ?: "Default search page",
                    onClick = { homepageDialog = true }, tag = "settings_homepage")
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(VidboxIcons.browser, "Desktop site", "Request desktop layouts with a desktop browser identity.",
                    settings.browserDesktop, "settings_desktop") { enabled -> onUpdate { it.copy(browserDesktop = enabled) } }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(VidboxIcons.analyzing, "JavaScript", "Many sites need JavaScript to render. Turning it off may break pages.",
                    settings.browserJavaScript, "settings_javascript") { enabled -> onUpdate { it.copy(browserJavaScript = enabled) } }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(VidboxIcons.privacy, "Cookies", "Sessions and preferences for sites you browse. Never exported to downloads.",
                    settings.browserCookies, "settings_cookies") { enabled -> onUpdate { it.copy(browserCookies = enabled) } }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingAction(VidboxIcons.delete, "Clear browsing data", "Cookies, site storage, form data, and the browser page list.", onClearBrowserData)
            }
        }
        item {
            SettingsGroup("CONNECTION & ALERTS") {
                SettingSwitch(VidboxIcons.wifi, "Wi-Fi only", "Wait on mobile data and metered hotspots.", settings.wifiOnly, "settings_wifi") { enabled -> onUpdate { it.copy(wifiOnly = enabled) } }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(VidboxIcons.notifications, "Completion & error alerts", "Foreground progress is always required by Android.", settings.completionNotifications, "settings_notifications") { enabled ->
                    onUpdate { it.copy(completionNotifications = enabled) }
                    if (enabled && !notificationsAllowed) onNotifications()
                }
                if (!notificationsAllowed) {
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingAction(VidboxIcons.notificationsOff, "Enable system notifications", "Android is currently hiding notifications.", onNotifications)
                }
            }
        }
        item {
            SettingsGroup("APPEARANCE") {
                SettingChoice(VidboxIcons.appearance, "Theme", settings.theme.name.lowercase().replaceFirstChar(Char::uppercase), AppTheme.entries, settings.theme,
                    label = { it.name.lowercase().replaceFirstChar(Char::uppercase) }, onSelect = { theme -> onUpdate { it.copy(theme = theme) } })
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingSwitch(VidboxIcons.appearance, "Dynamic colors", "Blend your device's Material You palette with Vidbox on Android 12+.",
                    settings.dynamicColors, "settings_dynamic") { enabled -> onUpdate { it.copy(dynamicColors = enabled) } }
            }
        }
        item {
            SettingsGroup("YOUR DATA") {
                SettingAction(VidboxIcons.history, "Clear download history", "Remove finished entries, not saved media.", { clearDialog = true })
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingAction(VidboxIcons.info, "About Vidbox", "Version ${BuildConfig.VERSION_NAME} · Open source", { aboutDialog = true })
            }
        }
        item {
            Text("Made for media you have the right to save. Vidbox does not bypass DRM, sign-in, geographic restrictions, or other access controls.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
        }
    }
    if (homepageDialog) {
        var value by remember { mutableStateOf(settings.browserHomepage.orEmpty()) }
        val trimmed = value.trim()
        val valid = trimmed.isEmpty() || BrowserLinks.homepage(trimmed) == trimmed
        AlertDialog(onDismissRequest = { homepageDialog = false },
            title = { Text("Browser homepage") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = value, onValueChange = { value = it.take(2048) }, singleLine = true,
                        label = { Text("HTTPS address") }, placeholder = { Text(BrowserLinks.DEFAULT_HOMEPAGE) },
                        isError = !valid, supportingText = {
                            Text(if (valid) "Leave blank to use the default search page."
                            else "Enter a complete HTTPS address, for example https://example.com")
                        })
                }
            },
            confirmButton = {
                TextButton(onClick = { homepageDialog = false
                    onUpdate { it.copy(browserHomepage = trimmed.takeIf(String::isNotBlank)) } },
                    enabled = valid, modifier = Modifier.testTag("settings_homepage_save")) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { homepageDialog = false }) { Text("Cancel") } })
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
            Text("Powered by yt-dlp ${BuildConfig.MEDIA_ENGINE_VERSION}, Android runtime 0.18.1, and FFmpeg. Engine updates ship with app releases. Not every site or protected video is supported.")
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
private fun SettingAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit,
    tag: String? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(VidboxIcons.chevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Switch(checked = value, onCheckedChange = onValue, modifier = Modifier.testTag(tag).semantics { contentDescription = title }, thumbContent = {
            if (value) Icon(VidboxIcons.check, null, Modifier.size(16.dp))
        })
    }
}

@Composable
private fun <T> SettingChoice(icon: ImageVector, title: String, subtitle: String, options: List<T>, selected: T,
    label: (T) -> String, onSelect: (T) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        SettingAction(icon, title, subtitle, onClick = { menu = true })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(label(option)) },
                trailingIcon = { if (selected == option) Icon(VidboxIcons.check, null) }, onClick = { menu = false; onSelect(option) }) }
        }
    }
}
