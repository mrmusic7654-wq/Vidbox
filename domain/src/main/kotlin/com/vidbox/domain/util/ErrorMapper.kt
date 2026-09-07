package com.vidbox.domain.util

import com.vidbox.domain.model.*
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.FileAlreadyExistsException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.TimeoutCancellationException

object ErrorMapper {
    /**
     * @param viaProxy whether the failing connection was routed through the user's proxy; connection
     *   failures are then reported against the proxy instead of the network in general.
     */
    fun from(error: Throwable, viaProxy: Boolean = false): DownloadError = when (error) {
        is DownloadException -> error.error
        is SocketTimeoutException, is TimeoutException, is TimeoutCancellationException ->
            if (viaProxy) Errors.of(ErrorCode.PROXY_UNAVAILABLE) else Errors.of(ErrorCode.TIMEOUT)
        is UnknownHostException, is ConnectException -> if (viaProxy) Errors.of(ErrorCode.PROXY_UNAVAILABLE) else Errors.of(ErrorCode.NETWORK)
        is SecurityException -> Errors.of(ErrorCode.PERMISSION)
        is FileAlreadyExistsException -> Errors.of(ErrorCode.FILE_EXISTS)
        is IOException -> {
            val message = error.message.orEmpty().lowercase()
            when {
                "enospc" in message || "no space left" in message -> Errors.of(ErrorCode.LOW_STORAGE)
                "eacces" in message || "permission denied" in message -> Errors.of(ErrorCode.PERMISSION)
                "proxy authentication" in message || "authenticate with proxy" in message || "407" in message -> Errors.of(ErrorCode.PROXY_AUTH)
                viaProxy && ("proxy" in message || "connection refused" in message || "failed to connect" in message ||
                    "socks" in message || "unexpected end of stream" in message) -> Errors.of(ErrorCode.PROXY_UNAVAILABLE)
                else -> Errors.of(ErrorCode.NETWORK)
            }
        }
        else -> Errors.of(ErrorCode.UNKNOWN)
    }

    /** Maps an HTTP status from a proxy connection test or a proxied request. */
    fun httpStatus(status: Int, viaProxy: Boolean = false): DownloadError? = when {
        status == 407 -> Errors.of(ErrorCode.PROXY_AUTH)
        status in 200..399 -> null
        status == 404 || status == 410 -> Errors.of(ErrorCode.UNAVAILABLE)
        status == 401 -> Errors.of(ErrorCode.AUTH_REQUIRED)
        status == 403 || status == 429 -> Errors.of(ErrorCode.REJECTED)
        status in 500..599 && viaProxy -> Errors.of(ErrorCode.PROXY_UNAVAILABLE)
        else -> Errors.of(ErrorCode.REJECTED)
    }

    /** Classify engine diagnostics, but never show or persist raw output (it can contain signed URLs). */
    fun engine(output: String, processing: Boolean = false, viaProxy: Boolean = false): DownloadError {
        val s = output.lowercase()
        val code = when {
            "no space left" in s || "enospc" in s -> ErrorCode.LOW_STORAGE
            "permission denied" in s || "read-only file system" in s -> ErrorCode.PERMISSION
            "407" in s || "proxy authentication required" in s -> ErrorCode.PROXY_AUTH
            viaProxy && ("proxy" in s || "socks" in s || "connection refused" in s || "tunnel connection failed" in s) -> ErrorCode.PROXY_UNAVAILABLE
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
