package com.vidbox.data.storage

import android.content.Context
import com.vidbox.domain.model.DownloadException
import com.vidbox.domain.model.ErrorCode
import com.vidbox.domain.model.Errors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkFiles @Inject constructor(@ApplicationContext context: Context) {
    private val root = File(context.noBackupFilesDir, "transfers")
    fun directory(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(root, id).also {
            check(it.canonicalFile.parentFile == root.canonicalFile)
            if (!it.exists() && !it.mkdirs()) throw Errors.exception(ErrorCode.LOW_STORAGE)
        }
    }
    suspend fun discard(id: String) = withContext(Dispatchers.IO) {
        val dir = directory(id)
        if (!dir.deleteRecursively() && dir.exists()) throw Errors.exception(ErrorCode.PERMISSION)
        Unit
    }
    companion object {
        const val RESERVE_BYTES = 24L * 1024 * 1024
        /** Fails with the exact shortfall ("Required … Available …") so the user knows what to free. */
        fun requireSpace(directory: File, additionalBytes: Long = 0) {
            val required = additionalBytes.coerceIn(0, Long.MAX_VALUE - RESERVE_BYTES) + RESERVE_BYTES
            val available = directory.usableSpace
            if (available < required) throw DownloadException(Errors.lowStorage(required, available))
        }
    }
}
