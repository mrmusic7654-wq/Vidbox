package com.vidbox.data.extractor

import android.system.Os
import android.system.OsConstants
import com.vidbox.domain.model.ErrorCode
import com.vidbox.domain.model.Errors
import com.vidbox.domain.model.DownloadException
import com.vidbox.domain.repository.EventLogger
import com.vidbox.domain.util.ErrorMapper
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** No shell, no interpolation, bounded output, independent process groups, and structured cancellation. */
@Singleton
class NativeProcessRunner @Inject constructor(
    private val runtime: AndroidMediaRuntime,
    private val logger: EventLogger,
) {
    suspend fun run(tool: AndroidMediaRuntime.Tool, arguments: List<String>, id: String,
        captureJson: Boolean = false, onLine: suspend (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        coroutineScope {
            val environment = runtime.environment(tool)
            val args = if (tool == AndroidMediaRuntime.Tool.YT_DLP) {
                listOf("--ignore-config", "--no-cache-dir", "--no-colors", "--no-playlist",
                    "--socket-timeout", "25", "--js-runtimes", "quickjs:${environment.quickJs.absolutePath}") + arguments
            } else arguments
            val handshake = UUID.randomUUID().toString()
            val pidPrefix = "VIDBOX_PID:$handshake:"
            val command = listOf(environment.python.absolutePath, "-u", "-c", LAUNCHER, handshake,
                if (tool == AndroidMediaRuntime.Tool.YT_DLP) "python" else "exec",
                environment.executable.absolutePath) + args
            val builder = ProcessBuilder(command).directory(environment.workingDirectory)
            builder.environment().putAll(environment.variables)
            val process = try { builder.start() } catch (error: Exception) { throw Errors.exception(ErrorCode.ENGINE, error) }
            val group = AtomicInteger(0)
            val cancellation = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { terminate(process, group.get()) }
            }
            try {
                val stdout = async(Dispatchers.IO) {
                    val capture = StringBuilder()
                    readLines(process.inputStream, if (captureJson) MAX_JSON else MAX_LINE) { line ->
                        if (captureJson) {
                            if (capture.length + line.length > MAX_JSON) throw Errors.exception(ErrorCode.ENGINE)
                            capture.append(line).append('\n')
                        }
                        onLine(line)
                    }
                    capture.toString()
                }
                val stderr = async(Dispatchers.IO) {
                    val tail = StringBuilder()
                    readLines(process.errorStream, MAX_LINE) { line ->
                        if (group.get() == 0 && line.startsWith(pidPrefix)) {
                            val pid = line.removePrefix(pidPrefix).toIntOrNull()?.takeIf { it > 1 }
                                ?: throw Errors.exception(ErrorCode.ENGINE)
                            group.set(pid)
                            // The child cannot execute extraction or spawn descendants until its group is registered.
                            process.outputStream.apply { write(1); flush(); close() }
                        } else {
                            tail.append(line).append('\n')
                            if (tail.length > MAX_ERROR) tail.delete(0, tail.length - MAX_ERROR)
                        }
                    }
                    tail.toString()
                }
                val exit = runInterruptible { process.waitFor() }
                val out = stdout.await()
                val err = stderr.await()
                currentCoroutineContext().ensureActive()
                if (exit != 0) {
                    val mapped = ErrorMapper.engine(err, tool == AndroidMediaRuntime.Tool.FFMPEG)
                    logger.event("engine.failed", id, mapOf("code" to mapped.code.name, "exit" to exit.toString()))
                    throw DownloadException(mapped)
                }
                out
            } finally {
                withContext(NonCancellable) { cancellation.cancelAndJoin() }
            }
        }
    }

    private suspend fun readLines(stream: InputStream, maxLine: Int, consume: suspend (String) -> Unit) {
        try {
            stream.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                val line = StringBuilder()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = reader.read(buffer)
                    if (count == -1) break
                    for (i in 0 until count) {
                        val char = buffer[i]
                        if (char == '\n' || char == '\r') {
                            if (line.isNotEmpty()) { consume(line.toString()); line.setLength(0) }
                        } else {
                            if (line.length >= maxLine) throw Errors.exception(ErrorCode.ENGINE)
                            line.append(char)
                        }
                    }
                }
                if (line.isNotEmpty()) consume(line.toString())
            }
        } catch (error: IOException) {
            // Android interrupts a blocking pipe read when cancellation closes its descriptor.
            // Preserve structured cancellation instead of promoting that close into a fatal I/O error.
            currentCoroutineContext().ensureActive()
            throw error
        }
    }

    private fun terminate(process: Process, group: Int) {
        if (process.isAlive) {
            // The trusted launcher is the session leader. Killing its group also stops QuickJS/children.
            if (group > 1) runCatching { Os.kill(-group, OsConstants.SIGTERM) }
            process.destroy()
            if (!runCatching { process.waitFor(700, TimeUnit.MILLISECONDS) }.getOrDefault(false)) {
                if (group > 1) runCatching { Os.kill(-group, OsConstants.SIGKILL) }
                process.destroyForcibly()
            }
        }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
    }

    companion object {
        private const val MAX_JSON = 8 * 1024 * 1024
        private const val MAX_LINE = 64 * 1024
        private const val MAX_ERROR = 16 * 1024
        // Only this bundled program is executed with -c. URLs and all other inputs are separate argv entries.
        private val LAUNCHER = """
            import os, sys, runpy
            os.setsid()
            print("VIDBOX_PID:" + sys.argv[1] + ":" + str(os.getpid()), file=sys.stderr, flush=True)
            if sys.stdin.buffer.read(1) != b"\x01":
                sys.exit(1)
            mode = sys.argv[2]
            sys.argv = sys.argv[3:]
            if mode == "python":
                runpy.run_path(sys.argv[0], run_name="__main__")
            else:
                os.execv(sys.argv[0], sys.argv)
        """.trimIndent()
    }
}
