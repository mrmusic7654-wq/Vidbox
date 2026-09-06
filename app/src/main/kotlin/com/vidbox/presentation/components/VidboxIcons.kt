package com.vidbox.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The single place Vidbox names its icons. Screens reference semantics
 * (`VidboxIcons.pause`), never a concrete Material glyph, so a rebrand only
 * replaces this mapping — every theme, size and content description keeps working.
 * All glyphs are Apache-2.0 licensed Material symbols shipped by Compose.
 *
 * For custom branding, any entry can become a vector drawable-backed ImageVector
 * (for example via `ImageVector.vectorResource(R.drawable.brand_pause)`) without
 * touching a screen.
 */
object VidboxIcons {
    // Transfer lifecycle
    val download: ImageVector = Icons.Rounded.Download
    val downloading: ImageVector = Icons.Rounded.Downloading
    val pause: ImageVector = Icons.Rounded.Pause
    val resume: ImageVector = Icons.Rounded.PlayArrow
    val stop: ImageVector = Icons.Rounded.Stop
    val cancel: ImageVector = Icons.Rounded.Close
    val retry: ImageVector = Icons.Rounded.Refresh
    val completed: ImageVector = Icons.Rounded.Check
    val analyzing: ImageVector = Icons.Rounded.ManageSearch
    val processing: ImageVector = Icons.Rounded.MergeType
    val delete: ImageVector = Icons.Rounded.DeleteOutline

    // Navigation and sections
    val home: ImageVector = Icons.Rounded.Home
    val browser: ImageVector = Icons.Rounded.Public
    val downloads: ImageVector = Icons.Rounded.Downloading
    val library: ImageVector = Icons.Rounded.VideoLibrary
    val settings: ImageVector = Icons.Rounded.Tune
    val history: ImageVector = Icons.Rounded.History

    // Browser chrome
    val back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack
    val forward: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward
    val reload: ImageVector = Icons.Rounded.Refresh
    val secure: ImageVector = Icons.Rounded.Lock
    val insecure: ImageVector = Icons.Rounded.LockOpen
    val openExternally: ImageVector = Icons.Rounded.OpenInNew
    val share: ImageVector = Icons.Rounded.Share
    val menu: ImageVector = Icons.Rounded.MoreVert
    val search: ImageVector = Icons.Rounded.Search

    // Media and files
    val play: ImageVector = Icons.Rounded.PlayArrow
    val video: ImageVector = Icons.Rounded.Movie
    val audio: ImageVector = Icons.Rounded.MusicNote
    val file: ImageVector = Icons.Rounded.InsertDriveFile
    val link: ImageVector = Icons.Rounded.Link
    val paste: ImageVector = Icons.Rounded.ContentPaste
    val cloudDownload: ImageVector = Icons.Rounded.CloudDownload
    val videoFormat: ImageVector = Icons.Rounded.VideoFile
    val audioFormat: ImageVector = Icons.Rounded.HighQuality

    // Preferences and states
    val quality: ImageVector = Icons.Rounded.HighQuality
    val folder: ImageVector = Icons.Rounded.FolderOpen
    val wifi: ImageVector = Icons.Rounded.Wifi
    val notifications: ImageVector = Icons.Rounded.NotificationsNone
    val notificationsOff: ImageVector = Icons.Rounded.NotificationsOff
    val appearance: ImageVector = Icons.Rounded.Palette
    val sort: ImageVector = Icons.Rounded.Sort
    val chevronRight: ImageVector = Icons.Rounded.ChevronRight
    val info: ImageVector = Icons.Rounded.Info
    val error: ImageVector = Icons.Rounded.ErrorOutline
    val privacy: ImageVector = Icons.Rounded.VerifiedUser
    val check: ImageVector = Icons.Rounded.Check
    val queue: ImageVector = Icons.Rounded.Queue
}
