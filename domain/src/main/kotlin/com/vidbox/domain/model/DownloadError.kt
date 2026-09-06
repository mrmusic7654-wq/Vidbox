package com.vidbox.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ErrorCode {
    INVALID_URL, UNSUPPORTED_URL, NETWORK, TIMEOUT, REJECTED, UNAVAILABLE, PRIVATE,
    AUTH_REQUIRED, GEO_RESTRICTED, DRM, LIVE, FORMAT_UNAVAILABLE, LOW_STORAGE,
    PERMISSION, PROCESSING, ENGINE, INTERRUPTED, FILE_EXISTS, CORRUPT_PARTIAL, MISSING_FILE, UNKNOWN,
}

@Serializable
data class DownloadError(val code: ErrorCode, val message: String, val retryable: Boolean)

class DownloadException(val error: DownloadError, cause: Throwable? = null) : Exception(error.message, cause)

object Errors {
    fun of(code: ErrorCode): DownloadError = when (code) {
        ErrorCode.INVALID_URL -> DownloadError(code, "Enter a complete HTTPS video link, without sign-in credentials.", false)
        ErrorCode.UNSUPPORTED_URL -> DownloadError(code, "This link is not supported. Try a public video page or a direct HTTPS media file.", false)
        ErrorCode.NETWORK -> DownloadError(code, "The connection was interrupted. Your partial download has been kept. Check your network and retry.", true)
        ErrorCode.TIMEOUT -> DownloadError(code, "The source took too long to respond. Please try again.", true)
        ErrorCode.REJECTED -> DownloadError(code, "The server refused the request. The link may have expired or the source may limit downloads.", true)
        ErrorCode.UNAVAILABLE -> DownloadError(code, "This video is no longer available at the source.", false)
        ErrorCode.PRIVATE -> DownloadError(code, "This video is private. Vidbox only downloads content you can access without signing in.", false)
        ErrorCode.AUTH_REQUIRED -> DownloadError(code, "This source requires sign-in or verification. Vidbox does not bypass authentication or accept account cookies.", false)
        ErrorCode.GEO_RESTRICTED -> DownloadError(code, "The source does not make this media available in your region.", false)
        ErrorCode.DRM -> DownloadError(code, "This media is DRM-protected. Vidbox cannot download protected media.", false)
        ErrorCode.LIVE -> DownloadError(code, "Live broadcasts are not supported. Try again after the recording is published.", false)
        ErrorCode.FORMAT_UNAVAILABLE -> DownloadError(code, "That format is no longer available. Analyze the link again and choose another quality.", false)
        ErrorCode.LOW_STORAGE -> DownloadError(code, "There is not enough free storage. Free some space, then retry. Merging and saving need additional temporary space.", true)
        ErrorCode.PERMISSION -> DownloadError(code, "The download folder is not writable. Choose an accessible folder in Settings and analyze the link again.", false)
        ErrorCode.PROCESSING -> DownloadError(code, "The streams could not be merged. Retry, or analyze again and choose another container.", true)
        ErrorCode.ENGINE -> DownloadError(code, "The media engine could not finish. Retry, or install the latest Vidbox release if the source has changed.", true)
        ErrorCode.INTERRUPTED -> DownloadError(code, "Android stopped background work. Open Vidbox and resume to continue safely.", true)
        ErrorCode.FILE_EXISTS -> DownloadError(code, "A file with this name already exists. Choose another destination and try again.", false)
        ErrorCode.CORRUPT_PARTIAL -> DownloadError(code, "The source returned inconsistent file data. Retry to safely restart the download.", true)
        ErrorCode.MISSING_FILE -> DownloadError(code, "The saved file was moved, deleted, or is no longer accessible.", false)
        ErrorCode.UNKNOWN -> DownloadError(code, "Something went wrong. Your other downloads are safe. Please try again.", true)
    }
    fun exception(code: ErrorCode, cause: Throwable? = null) = DownloadException(of(code), cause)
}
