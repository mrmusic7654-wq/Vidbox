package com.vidbox.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.MediaStorage
import com.vidbox.domain.util.FileNames
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMediaStorage @Inject constructor(@ApplicationContext private val context: Context) : MediaStorage {
    private val resolver = context.contentResolver
    override suspend fun publish(id: String, media: StagedMedia, fileName: String, destinationTree: String?,
        onPending: suspend (String?) -> Unit): StoredMedia = withContext(Dispatchers.IO) {
        val source = File(media.path)
        if (!source.isFile || source.length() == 0L || source.length() != media.sizeBytes) throw Errors.exception(ErrorCode.CORRUPT_PARTIAL)
        // Allow a full copy plus reserve for MediaStore (same primary storage). SAF providers report write failures themselves.
        WorkFiles.requireSpace(source.parentFile!!, if (destinationTree == null) source.length() else 0)
        val safeName = FileNames.sanitize(fileName, 220)
        var pending: Uri? = null
        try {
            currentCoroutineContext().ensureActive()
            val created = if (destinationTree == null) createMediaStore(safeName, media.mimeType)
                else createDocument(destinationTree, safeName, media.mimeType)
            pending = created
            withContext(NonCancellable) { onPending(created.toString()) }
            copy(source, created, sync = destinationTree == null)
            currentCoroutineContext().ensureActive()
            val published = if (destinationTree == null) {
                val changed = resolver.update(created, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                if (changed != 1) throw Errors.exception(ErrorCode.PERMISSION)
                created
            } else {
                try { DocumentsContract.renameDocument(resolver, created, safeName) ?: throw Errors.exception(ErrorCode.PERMISSION) }
                catch (error: UnsupportedOperationException) { throw Errors.exception(ErrorCode.PERMISSION, error) }
            }
            pending = published
            withContext(NonCancellable) { onPending(published.toString()) }
            val actualName = resolver.query(published, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else safeName
            } ?: safeName
            StoredMedia(published.toString(), actualName, media.mimeType, media.sizeBytes)
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                pending?.let { uri ->
                    // Leave the journal intact when a provider is unavailable; recovery can retry cleanup later.
                    runCatching { delete(uri.toString()); onPending(null) }
                }
            }
            throw error
        }
    }

    private fun createMediaStore(name: String, mime: String): Uri {
        val video = mime.startsWith("video/")
        val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val folder = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Vidbox")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return resolver.insert(collection, values) ?: throw Errors.exception(ErrorCode.PERMISSION)
    }

    private fun createDocument(tree: String, name: String, mime: String): Uri {
        val uri = Uri.parse(tree)
        require(uri.scheme == "content")
        if (resolver.persistedUriPermissions.none { it.uri == uri && it.isWritePermission }) throw Errors.exception(ErrorCode.PERMISSION)
        val folder = DocumentFile.fromTreeUri(context, uri)?.takeIf { it.canWrite() }
            ?: throw Errors.exception(ErrorCode.PERMISSION)
        return folder.createFile(mime, ".$name.part")?.uri ?: throw Errors.exception(ErrorCode.PERMISSION)
    }

    private suspend fun copy(source: File, destination: Uri, sync: Boolean) = coroutineScope {
        val signal = CancellationSignal()
        val canceller = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { signal.cancel() }
        }
        try {
            val descriptor = resolver.openFileDescriptor(destination, "w", signal) ?: throw Errors.exception(ErrorCode.PERMISSION)
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                source.inputStream().buffered(64 * 1024).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        written += count
                    }
                    if (written != source.length()) throw Errors.exception(ErrorCode.CORRUPT_PARTIAL)
                }
                output.flush()
                if (sync) output.fd.sync()
            }
        } finally { canceller.cancel() }
    }

    override suspend fun exists(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = Uri.parse(uri)
            if (parsed.scheme != "content") return@runCatching false
            resolver.openFileDescriptor(parsed, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }
    override suspend fun delete(uri: String) = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        require(parsed.scheme == "content")
        try {
            if (DocumentsContract.isDocumentUri(context, parsed)) {
                if (!DocumentsContract.deleteDocument(resolver, parsed) && exists(uri)) throw Errors.exception(ErrorCode.PERMISSION)
            } else {
                if (resolver.delete(parsed, null, null) == 0 && exists(uri)) throw Errors.exception(ErrorCode.PERMISSION)
            }
        } catch (_: FileNotFoundException) { /* Already deleted outside Vidbox. */ }
        Unit
    }
}
