package com.vidbox.data

import com.vidbox.data.storage.WorkFiles
import com.vidbox.domain.model.DownloadException
import com.vidbox.domain.model.ErrorCode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageSafetyTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun refusesInsufficientStagingSpaceBeforeWriting() {
        val error = assertThrows(DownloadException::class.java) { WorkFiles.requireSpace(folder.root, folder.root.usableSpace) }
        assertEquals(ErrorCode.LOW_STORAGE, error.error.code)
    }
    @Test fun implausiblyLargeSourceSizesCannotOverflowSpaceChecks() {
        val error = assertThrows(DownloadException::class.java) { WorkFiles.requireSpace(folder.root, Long.MAX_VALUE) }
        assertEquals(ErrorCode.LOW_STORAGE, error.error.code)
    }
}
