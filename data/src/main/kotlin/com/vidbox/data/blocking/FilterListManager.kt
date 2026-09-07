package com.vidbox.data.blocking

import android.content.Context
import com.vidbox.data.network.withResponse
import com.vidbox.domain.blocking.FilterListParser
import com.vidbox.domain.blocking.NetworkRule
import com.vidbox.domain.repository.EventLogger
import com.vidbox.domain.repository.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

/** A filter list known to the app: bundled for offline use and refreshed from a fixed HTTPS URL. */
data class FilterList(
    val id: String,
    val name: String,
    val category: Category,
    val bundledAsset: String,
    val updateUrl: String,
) {
    enum class Category { ADS, TRACKERS }
}

data class FilterListStatus(
    val list: FilterList,
    val rules: Int,
    val version: String?,
    /** Epoch millis of the last successful download; null when the bundled copy is in use. */
    val updatedAt: Long?,
    val lastError: String? = null,
)

/**
 * Owns the list files on disk. Every list has three possible sources, in order of preference:
 * the last successfully downloaded copy, the copy shipped in the APK, nothing. A failed or
 * malformed download never replaces a working list; the previous file stays in place.
 */
@Singleton
class FilterListManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val logger: EventLogger,
    private val clock: TimeProvider,
) {
    private val directory = File(context.noBackupFilesDir, "filters")
    private val lock = Mutex()

    val lists: List<FilterList> = listOf(
        FilterList("easylist", "EasyList", FilterList.Category.ADS, "filters/easylist.txt",
            "https://raw.githubusercontent.com/uBlockOrigin/uAssetsCDN/main/thirdparties/easylist.txt"),
        FilterList("easyprivacy", "EasyPrivacy", FilterList.Category.TRACKERS, "filters/easyprivacy.txt",
            "https://raw.githubusercontent.com/uBlockOrigin/uAssetsCDN/main/thirdparties/easyprivacy.txt"),
    )

    /** Parses the best available copy of every list. Called off the main thread by the engine. */
    suspend fun load(): List<Pair<FilterListStatus, List<NetworkRule>>> = withContext(Dispatchers.IO) {
        lists.map { list ->
            val downloaded = fileFor(list)
            val meta = metaFor(list)
            val text = if (downloaded.isFile && downloaded.length() > 0) {
                runCatching { downloaded.readText() }.getOrNull()
            } else null
            val (source, fromBundle) = if (text != null) text to false else bundled(list) to true
            val parsed = FilterListParser.parse(source, list.id)
            val status = FilterListStatus(list, parsed.rules.size, parsed.version,
                if (fromBundle) null else meta.getProperty("updatedAt")?.toLongOrNull(), meta.getProperty("lastError"))
            status to parsed.rules
        }
    }

    /**
     * Downloads every list. Returns true when at least one list changed. A response must parse
     * into a plausible rule set (not empty, not a stray HTML page) before it replaces the old copy.
     */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            var changed = false
            for (list in lists) {
                try {
                    val text = download(list.updateUrl)
                    val parsed = FilterListParser.parse(text, list.id)
                    if (parsed.rules.size < MIN_RULES) throw IOException("List has only ${parsed.rules.size} rules")
                    val target = fileFor(list)
                    val temp = File(directory, "${list.id}.tmp")
                    directory.mkdirs()
                    temp.writeText(text)
                    if (!temp.renameTo(target)) {
                        target.delete()
                        if (!temp.renameTo(target)) throw IOException("Could not replace filter list")
                    }
                    writeMeta(list, updatedAt = clock.nowMillis(), error = null)
                    logger.event("filters.updated", list.id, mapOf("rules" to parsed.rules.size.toString()))
                    changed = true
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // The previous copy (downloaded or bundled) stays in force.
                    writeMeta(list, updatedAt = metaFor(list).getProperty("updatedAt")?.toLongOrNull(),
                        error = error.javaClass.simpleName)
                    logger.event("filters.update_failed", list.id, mapOf("reason" to error.javaClass.simpleName))
                }
            }
            changed
        }
    }

    fun lastUpdate(): Long? = lists.mapNotNull { metaFor(it).getProperty("updatedAt")?.toLongOrNull() }.maxOrNull()

    private suspend fun download(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "text/plain").build()
        return http.newCall(request).withResponse { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty body")
            if (body.contentLength() > MAX_BYTES) throw IOException("List too large")
            val bytes = body.byteStream().use { stream -> readBounded(stream) }
            val text = String(bytes, Charsets.UTF_8)
            if (text.trimStart().startsWith("<")) throw IOException("Not a filter list")
            text
        }
    }

    private fun readBounded(stream: java.io.InputStream): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) break
            total += read
            if (total > MAX_BYTES) throw IOException("List too large")
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun bundled(list: FilterList): String =
        runCatching { context.assets.open(list.bundledAsset).bufferedReader().use { it.readText() } }.getOrDefault("")

    private fun fileFor(list: FilterList) = File(directory, "${list.id}.txt")
    private fun metaFile(list: FilterList) = File(directory, "${list.id}.properties")

    private fun metaFor(list: FilterList): Properties = Properties().apply {
        val file = metaFile(list)
        if (file.isFile) runCatching { file.inputStream().use { load(it) } }
    }

    private fun writeMeta(list: FilterList, updatedAt: Long?, error: String?) {
        directory.mkdirs()
        val props = Properties()
        if (updatedAt != null) props.setProperty("updatedAt", updatedAt.toString())
        if (error != null) props.setProperty("lastError", error)
        runCatching { metaFile(list).outputStream().use { props.store(it, null) } }
    }

    private companion object {
        const val MAX_BYTES = 12L * 1024 * 1024
        const val MIN_RULES = 500
    }
}
