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
import com.vidbox.domain.repository.SettingsRepository
import com.vidbox.domain.util.FileNames
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileNotFoundException
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMediaStorage @Inject constructor(@param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository) : MediaStorage {
    private val resolver = context.contentResolver
    override suspend fun publish(id: String, media: StagedMedia, fileName: String, destinationTree: String?,
        onPending: suspend (String?) -> Unit): StoredMedia = withContext(Dispatchers.IO) {
        val source = File(media.path)
        if (!source.isFile || source.length() == 0L || source.length() != media.sizeBytes) throw Errors.exception(ErrorCode.CORRUPT_PARTIAL)
        // Allow a full copy plus reserve for MediaStore (same primary storage). SAF providers report write failures themselves.
        WorkFiles.requireSpace(source.parentFile!!, if (destinationTree == null) source.length() else 0)
        val safeName = FileNames.sanitize(fileName, 220)
        // "Skip duplicates" refuses the transfer before bytes are copied. The default policy
        // keeps both: MediaStore and document providers uniquify the new name themselves.
        if (settings.settings.first().duplicatePolicy == DuplicatePolicy.SKIP) {
            if (destinationTree == null) requireNoMediaStoreDuplicate(safeName, media.mimeType)
            else requireNoDocumentDuplicate(destinationTree, safeName)
        }
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

    /** MediaStore collection plus the relative path Vidbox writes to for this media kind. */
    private fun publicFolder(mime: String): Pair<Uri, String> {
        val video = mime.startsWith("video/")
        val audio = !video && mime.startsWith("audio/")
        // Generic browser files (PDF, archives, images, …) belong in Downloads, not Music.
        val collection = when {
            video -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            audio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val folder = when {
            video -> Environment.DIRECTORY_MOVIES
            audio -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        return collection to "$folder/Vidbox"
    }

    private fun createMediaStore(name: String, mime: String): Uri {
        val (collection, relativePath) = publicFolder(mime)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return resolver.insert(collection, values) ?: throw Errors.exception(ErrorCode.PERMISSION)
    }

    /** The provider cannot tell us a pending insert's final name, so a visible name clash refuses the copy. */
    private fun requireNoMediaStoreDuplicate(name: String, mime: String) {
        val (collection, relativePath) = publicFolder(mime)
        val pathClause = "${MediaStore.MediaColumns.RELATIVE_PATH}=? OR ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ($pathClause) AND ${MediaStore.MediaColumns.IS_PENDING}=0",
            arrayOf(name, relativePath, "$relativePath/"), null)?.use { cursor ->
            if (cursor.moveToFirst()) throw Errors.exception(ErrorCode.FILE_EXISTS)
        }
    }

    private fun requireNoDocumentDuplicate(tree: String, name: String) {
        val uri = Uri.parse(tree)
        require(uri.scheme == "content")
        val folder = DocumentFile.fromTreeUri(context, uri) ?: return
        if (folder.findFile(name) != null || folder.findFile(".$name.part") != null)
            throw Errors.exception(ErrorCode.FILE_EXISTS)
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
        val writer = AtomicReference<Closeable?>()
        val canceller = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally {
                signal.cancel()
                // The descriptor may be a cloud provider's pipe: cancel the open AND any blocked write.
                runCatching { writer.getAndSet(null)?.close() }
            }
        }
        try {
            val descriptor = resolver.openFileDescriptor(destination, "w", signal) ?: throw Errors.exception(ErrorCode.PERMISSION)
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                writer.set(output)
                currentCoroutineContext().ensureActive()
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
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            throw error
        } finally {
            writer.set(null)
            canceller.cancel()
        }
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
