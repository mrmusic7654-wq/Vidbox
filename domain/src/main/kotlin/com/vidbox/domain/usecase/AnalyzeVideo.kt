package com.vidbox.domain.usecase

import com.vidbox.domain.model.ErrorCode
import com.vidbox.domain.model.Errors
import com.vidbox.domain.model.MediaInfo
import com.vidbox.domain.repository.VideoExtractor
import com.vidbox.domain.util.UrlValidator
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

class AnalyzeVideo @Inject constructor(private val extractor: VideoExtractor) {
    suspend operator fun invoke(input: String): MediaInfo = withTimeout(120_000) {
        val media = extractor.analyze(UrlValidator.validate(input))
        if (media.formats.isEmpty()) throw Errors.exception(ErrorCode.FORMAT_UNAVAILABLE)
        media
    }
}
