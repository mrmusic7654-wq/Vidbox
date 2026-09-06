package com.vidbox.domain.util

import kotlinx.serialization.json.*

/** Machine-readable yt-dlp progress; localized human output is never used to calculate progress. */
object ProgressParser {
    const val PREFIX = "VIDBOX_PROGRESS:"
    const val TEMPLATE = "download:${PREFIX}{\"downloaded\":%(progress.downloaded_bytes)j,\"total\":%(progress.total_bytes)j,\"estimate\":%(progress.total_bytes_estimate)j,\"speed\":%(progress.speed)j,\"eta\":%(progress.eta)j}"
    data class Parsed(val downloaded: Long, val total: Long?, val speed: Long, val eta: Long?)

    fun parse(line: String): Parsed? {
        if (!line.startsWith(PREFIX)) return null
        return runCatching {
            val obj = Json.parseToJsonElement(line.removePrefix(PREFIX)).jsonObject
            fun number(key: String): Long? = obj[key]?.jsonPrimitive?.doubleOrNull
                ?.takeIf { it.isFinite() && it >= 0 && it <= Long.MAX_VALUE }?.toLong()
            Parsed(number("downloaded") ?: 0,
                (number("total") ?: number("estimate"))?.takeIf { it > 0 },
                number("speed") ?: 0, number("eta"))
        }.getOrNull()
    }
}
