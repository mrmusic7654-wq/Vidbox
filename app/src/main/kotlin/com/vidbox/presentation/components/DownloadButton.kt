package com.vidbox.presentation.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vidbox.domain.util.DisplayFormat

/** Every user-visible state of a download action, in lifecycle order. */
enum class DownloadButtonState { IDLE, ANALYZING, READY, DOWNLOADING, PAUSED, PROCESSING, COMPLETED, FAILED }

/**
 * One component for every download call-to-action in the app. State — never screen-local
 * conditionals — decides label, icon, progress and affordance, so a new surface cannot
 * invent an inconsistent variant. Animations are limited to the small indeterminate
 * spinner; progress text is deterministic and screen-reader friendly (polite live region).
 */
@Composable
fun DownloadButton(
    state: DownloadButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** READY detail, for example "Download 1080p"; other states use their canonical label. */
    label: String? = null,
    downloadedBytes: Long? = null,
    totalBytes: Long? = null,
    enabled: Boolean = true,
    filled: Boolean = true,
) {
    val (text, icon, interactive) = presentation(state, label)
    val description = buildString {
        append(text)
        if (state == DownloadButtonState.DOWNLOADING && totalBytes != null && totalBytes > 0)
            append(", ${DisplayFormat.bytes(downloadedBytes)} of ${DisplayFormat.bytes(totalBytes)}")
    }
    val content: @Composable RowScope.() -> Unit = {
        if (!interactive) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary)
        else Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(9.dp))
        Text(text)
        if (state == DownloadButtonState.DOWNLOADING && totalBytes != null && totalBytes > 0) {
            Spacer(Modifier.width(9.dp))
            Text("${DisplayFormat.bytes(downloadedBytes)} / ${DisplayFormat.bytes(totalBytes)}",
                style = MaterialTheme.typography.labelMedium)
        }
    }
    val sizing = Modifier.defaultMinSize(minHeight = 52.dp).semantics {
        contentDescription = description
        liveRegion = LiveRegionMode.Polite
    }
    val active = enabled && state in INTERACTIVE_STATES
    if (filled) Button(onClick = onClick, enabled = active, shape = RoundedCornerShape(14.dp),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = modifier.then(sizing), content = content)
    else OutlinedButton(onClick = onClick, enabled = active, shape = RoundedCornerShape(14.dp),
        contentPadding = ButtonDefaults.OutlinedButtonWithIconContentPadding,
        modifier = modifier.then(sizing), content = content)
}

/** Label, icon, and whether taps are meaningful in this state. */
private fun presentation(state: DownloadButtonState, label: String?): Triple<String, ImageVector, Boolean> =
    when (state) {
        DownloadButtonState.IDLE -> Triple(label ?: "Download", VidboxIcons.download, true)
        DownloadButtonState.ANALYZING -> Triple("Analyzing link…", VidboxIcons.analyzing, false)
        DownloadButtonState.READY -> Triple(label ?: "Download", VidboxIcons.download, true)
        DownloadButtonState.DOWNLOADING -> Triple(label ?: "Downloading…", VidboxIcons.downloading, false)
        DownloadButtonState.PAUSED -> Triple(label ?: "Resume download", VidboxIcons.resume, true)
        DownloadButtonState.PROCESSING -> Triple("Processing…", VidboxIcons.processing, false)
        DownloadButtonState.COMPLETED -> Triple("Completed", VidboxIcons.completed, true)
        DownloadButtonState.FAILED -> Triple("Retry download", VidboxIcons.retry, true)
    }

/** States in which the button's click does something. Transient work stays untappable. */
private val INTERACTIVE_STATES = setOf(
    DownloadButtonState.IDLE, DownloadButtonState.READY,
    DownloadButtonState.PAUSED, DownloadButtonState.COMPLETED, DownloadButtonState.FAILED,
)
