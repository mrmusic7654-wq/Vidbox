package com.vidbox.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "downloads", indices = [Index("state"), Index("createdAt")])
data class DownloadEntity(
    @PrimaryKey val id: String,
    val specCiphertext: String,
    val title: String,
    val fileName: String,
    val state: String,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speedBytesPerSecond: Long,
    val etaSeconds: Long?,
    val createdAt: Long,
    val startedAt: Long?,
    val completedAt: Long?,
    val errorCode: String?,
    val outputUri: String?,
    val pendingUri: String?,
    val mimeType: String?,
    val fileMissing: Boolean,
    val resumeSupported: Boolean,
    val pauseReason: String?,
    val attempt: Int,
    val needsCleanup: Boolean = false,
)
