package com.vidbox.domain.util

import com.vidbox.domain.model.ErrorCode
import com.vidbox.domain.model.Errors
import java.net.URI

object UrlValidator {
    private val controls = Regex("[\\p{Cc}\\p{Cf}\\s]")

    fun validate(input: String): String {
        val value = input.trim()
        val uri = runCatching { URI(value) }.getOrNull()
        if (value.length !in 10..8192 || controls.containsMatchIn(value) || uri == null ||
            !uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null || uri.port !in -1..65535 || uri.port == 0 ||
            uri.rawAuthority?.contains('\\') == true || uri.host.startsWith("-") ||
            uri.host.endsWith("-")) {
            throw Errors.exception(ErrorCode.INVALID_URL)
        }
        return uri.toASCIIString()
    }

    fun findInSharedText(text: String): String =
        Regex("https://[^\\s<>]+", RegexOption.IGNORE_CASE).find(text.take(16384))?.value
            ?.trimEnd('.', ',', ')', ']', '}', ';') ?: text.take(8192)
}
