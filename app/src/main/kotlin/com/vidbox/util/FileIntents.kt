package com.vidbox.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vidbox.domain.model.DownloadRecord
import com.vidbox.player.PlayerActivity

object FileIntents {
    fun open(context: Context, record: DownloadRecord, share: Boolean) {
        val uri = Uri.parse(requireNotNull(record.outputUri))
        require(uri.scheme == "content")
        val intent = if (share) Intent(Intent.ACTION_SEND).apply {
            type = record.mimeType ?: "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
        } else Intent(Intent.ACTION_VIEW).setDataAndType(uri, record.mimeType ?: "application/octet-stream")
        intent.clipData = ClipData.newUri(context.contentResolver, record.fileName, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, if (share) "Share media" else "Open media"))
    }

    /** Plays a completed video inside Vidbox's built-in player. */
    fun internalPlayer(context: Context, record: DownloadRecord) {
        val uri = Uri.parse(requireNotNull(record.outputUri))
        require(uri.scheme == "content")
        context.startActivity(PlayerActivity.intent(context, uri, record.fileName, record.mimeType))
    }
}
