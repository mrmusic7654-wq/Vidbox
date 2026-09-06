package com.vidbox.data.extractor

import android.content.Context
import com.vidbox.data.BuildConfig
import com.vidbox.domain.model.ErrorCode
import com.vidbox.domain.model.Errors
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The Maven AAR supplies ABI-specific, executable native libraries and the actual yt-dlp zipapp. */
@Singleton
class AndroidMediaRuntime @Inject constructor(@ApplicationContext private val context: Context) {
    enum class Tool { YT_DLP, FFMPEG }
    data class Environment(val python: File, val executable: File, val quickJs: File,
        val workingDirectory: File, val variables: Map<String, String>)
    private val initialization = Mutex()
    private var pythonReady = false
    private var ffmpegReady = false

    suspend fun environment(tool: Tool): Environment = withContext(Dispatchers.IO) {
        initialization.withLock {
            try {
                if (!pythonReady) {
                    val base = File(context.noBackupFilesDir, "youtubedl-android")
                    val marker = File(base, "bundled-runtime.version")
                    val packagedVersion = "${BuildConfig.MEDIA_RUNTIME_VERSION}:${BuildConfig.MEDIA_ENGINE_VERSION}"
                    if (runCatching { marker.readText() }.getOrNull() != packagedVersion) {
                        // The upstream initializer only copies a missing zipapp; explicitly adopt engine updates with each bundled release.
                        val script = File(base, "yt-dlp/yt-dlp")
                        if (script.exists() && !script.delete()) throw Errors.exception(ErrorCode.ENGINE)
                    }
                    YoutubeDL.init(context)
                    marker.writeText(packagedVersion)
                    pythonReady = true
                }
                if (tool == Tool.FFMPEG && !ffmpegReady) { FFmpeg.init(context); ffmpegReady = true }
            } catch (error: Exception) {
                throw Errors.exception(ErrorCode.ENGINE, error)
            }
        }
        val native = File(context.applicationInfo.nativeLibraryDir)
        val base = File(context.noBackupFilesDir, "youtubedl-android")
        val pythonHome = File(base, "packages/python/usr")
        val python = File(native, "libpython.so")
        val executable = if (tool == Tool.YT_DLP) File(base, "yt-dlp/yt-dlp") else File(native, "libffmpeg.so")
        if (!python.canExecute() || !executable.isFile || (tool == Tool.FFMPEG && !executable.canExecute()))
            throw Errors.exception(ErrorCode.ENGINE)
        Environment(python, executable, File(native, "libqjs.so"), base, mapOf(
            "LD_LIBRARY_PATH" to "${pythonHome.absolutePath}/lib:${base.absolutePath}/packages/ffmpeg/usr/lib",
            "SSL_CERT_FILE" to "${pythonHome.absolutePath}/etc/tls/cert.pem",
            "REQUESTS_CA_BUNDLE" to "${pythonHome.absolutePath}/etc/tls/cert.pem",
            "PYTHONHOME" to pythonHome.absolutePath,
            "HOME" to base.absolutePath,
            "TMPDIR" to context.cacheDir.absolutePath,
            "PYTHONNOUSERSITE" to "1", "PYTHONUTF8" to "1", "PYTHONDONTWRITEBYTECODE" to "1",
            "PATH" to "${System.getenv("PATH")}:$native",
        ))
    }
}
