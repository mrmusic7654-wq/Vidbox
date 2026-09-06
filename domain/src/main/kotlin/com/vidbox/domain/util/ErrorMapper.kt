package com.vidbox.domain.util

import com.vidbox.domain.model.*
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.FileAlreadyExistsException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.TimeoutCancellationException

object ErrorMapper {
    fun from(error: Throwable): DownloadError = when (error) {
        is DownloadException -> error.error
        is SocketTimeoutException, is TimeoutException, is TimeoutCancellationException -> Errors.of(ErrorCode.TIMEOUT)
        is UnknownHostException -> Errors.of(ErrorCode.NETWORK)
        is SecurityException -> Errors.of(ErrorCode.PERMISSION)
        is FileAlreadyExistsException -> Errors.of(ErrorCode.FILE_EXISTS)
        is IOException -> {
            val message = error.message.orEmpty().lowercase()
            when {
                "enospc" in message || "no space left" in message -> Errors.of(ErrorCode.LOW_STORAGE)
                "eacces" in message || "permission denied" in message -> Errors.of(ErrorCode.PERMISSION)
                else -> Errors.of(ErrorCode.NETWORK)
            }
        }
        else -> Errors.of(ErrorCode.UNKNOWN)
    }

    /** Classify engine diagnostics, but never show or persist raw output (it can contain signed URLs). */
    fun engine(output: String, processing: Boolean = false): DownloadError {
        val s = output.lowercase()
        val code = when {
            "no space left" in s || "enospc" in s -> ErrorCode.LOW_STORAGE
            "permission denied" in s || "read-only file system" in s -> ErrorCode.PERMISSION
            "drm" in s || "protected content" in s -> ErrorCode.DRM
            "private video" in s || "video is private" in s -> ErrorCode.PRIVATE
            "sign in" in s || "login" in s || "log in" in s || "cookies" in s || "authentication" in s || "confirm you're not a bot" in s -> ErrorCode.AUTH_REQUIRED
            "not available in your country" in s || "geo" in s || "not available in your region" in s -> ErrorCode.GEO_RESTRICTED
            "requested format" in s || "no video formats" in s -> ErrorCode.FORMAT_UNAVAILABLE
            "unsupported url" in s || "no suitable extractor" in s -> ErrorCode.UNSUPPORTED_URL
            "404" in s || "video unavailable" in s || "has been removed" in s -> ErrorCode.UNAVAILABLE
            "timed out" in s || "timeout" in s -> ErrorCode.TIMEOUT
            "unable to download" in s && ("resolve" in s || "network" in s || "connection" in s) -> ErrorCode.NETWORK
            "403" in s || "429" in s -> ErrorCode.REJECTED
            processing -> ErrorCode.PROCESSING
            else -> ErrorCode.ENGINE
        }
        return Errors.of(code)
    }
}
