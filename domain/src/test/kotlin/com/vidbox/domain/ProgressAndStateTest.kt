package com.vidbox.domain

import com.vidbox.domain.model.*
import com.vidbox.domain.util.*
import org.junit.Assert.*
import org.junit.Test

class ProgressAndStateTest {
    @Test fun parsesMachineReadableProgress() {
        val p = ProgressParser.parse("VIDBOX_PROGRESS:{\"downloaded\":1024,\"total\":2048,\"speed\":512.5,\"eta\":2}")!!
        assertEquals(1024L, p.downloaded); assertEquals(2048L, p.total); assertEquals(512L, p.speed)
    }
    @Test fun missingTotalsStayIndeterminate() {
        val p = ProgressParser.parse("VIDBOX_PROGRESS:{\"downloaded\":15,\"total\":null,\"estimate\":\"NA\",\"speed\":null,\"eta\":\"NA\"}")!!
        assertNull(p.total); assertNull(p.eta); assertEquals(0L, p.speed)
    }
    @Test fun estimatesAndMalformedOutputAreSafe() {
        assertEquals(300L, ProgressParser.parse("VIDBOX_PROGRESS:{\"downloaded\":10,\"estimate\":300}")!!.total)
        assertNull(ProgressParser.parse("[download] 50%"))
        assertNull(ProgressParser.parse("VIDBOX_PROGRESS:broken"))
    }
    @Test fun terminalStatesCannotBeResurrectedByLateProgress() {
        assertFalse(DownloadStateMachine.permits(DownloadState.COMPLETED, DownloadState.DOWNLOADING))
        assertFalse(DownloadStateMachine.permits(DownloadState.CANCELLED, DownloadState.PROCESSING))
        assertFalse(DownloadStateMachine.permits(DownloadState.PAUSED, DownloadState.DOWNLOADING))
        assertTrue(DownloadStateMachine.permits(DownloadState.PAUSED, DownloadState.QUEUED))
        assertTrue(DownloadStateMachine.permits(DownloadState.FAILED, DownloadState.QUEUED))
    }
    @Test fun onlyProcessingMayComplete() {
        assertFalse(DownloadStateMachine.permits(DownloadState.QUEUED, DownloadState.COMPLETED))
        assertTrue(DownloadStateMachine.permits(DownloadState.PROCESSING, DownloadState.COMPLETED))
    }
    @Test fun mapsEngineErrorsWithoutLeakingRawUrls() {
        val errors = mapOf("ERROR: Private video" to ErrorCode.PRIVATE,
            "Sign in to confirm your age" to ErrorCode.AUTH_REQUIRED,
            "not available in your country" to ErrorCode.GEO_RESTRICTED,
            "Requested format is not available" to ErrorCode.FORMAT_UNAVAILABLE,
            "This video is DRM protected" to ErrorCode.DRM,
            "No space left on device" to ErrorCode.LOW_STORAGE,
            "Unsupported URL https://secret.example/?token=123" to ErrorCode.UNSUPPORTED_URL)
        errors.forEach { (raw, expected) ->
            val mapped = ErrorMapper.engine(raw)
            assertEquals(expected, mapped.code)
            assertFalse(mapped.message.contains("token="))
        }
    }
    @Test fun wifiPolicyRequiresUnmeteredWifiNotJustAnyInternet() {
        val prefs = AppSettings(wifiOnly = true)
        assertFalse(NetworkStatus(true, cellular = true).permits(prefs))
        assertFalse(NetworkStatus(true, wifi = true, metered = true).permits(prefs))
        assertTrue(NetworkStatus(true, wifi = true, metered = false).permits(prefs))
        assertFalse(NetworkStatus().permits(AppSettings()))
    }
}
