package com.vidbox.data.repository

import androidx.datastore.preferences.core.emptyPreferences
import com.vidbox.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/** Pure DataStore mapping rules: defaults, clamps, and https-only homepage storage. */
class SettingsEncodingTest {
    @Test fun defaultsDecodeFromEmptyPreferences() {
        val settings = SettingsEncoding.decode(emptyPreferences())
        assertEquals(1080, settings.defaultQuality)
        assertEquals("mp4", settings.defaultContainer)
        assertEquals(2, settings.maxConcurrent)
        assertTrue(settings.wifiOnly)
        assertTrue(settings.completionNotifications)
        assertEquals(AppTheme.SYSTEM, settings.theme)
        assertTrue(settings.autoResume)
        assertEquals(DuplicatePolicy.KEEP_BOTH, settings.duplicatePolicy)
        assertNull(settings.browserHomepage)
        assertFalse(settings.browserDesktop)
        assertTrue(settings.browserJavaScript)
        assertTrue(settings.browserCookies)
        assertTrue(settings.dynamicColors)
    }

    @Test fun applyValidatesEveryFieldBeforePersisting() {
        val prefs = emptyPreferences().toMutablePreferences()
        SettingsEncoding.apply(prefs, AppSettings(
            destinationTree = "content://tree/primary%3Amedia", destinationLabel = "Movies",
            defaultQuality = 9999, defaultContainer = "exe", wifiOnly = true,
            maxConcurrent = 99, completionNotifications = false, theme = AppTheme.DARK,
            autoResume = false, duplicatePolicy = DuplicatePolicy.SKIP,
            browserHomepage = "http://insecure.example", browserDesktop = true,
            browserJavaScript = false, browserCookies = false, dynamicColors = false))
        val decoded = SettingsEncoding.decode(prefs)
        assertEquals(1080, decoded.defaultQuality) // Not an offered choice: stored default.
        assertEquals("mp4", decoded.defaultContainer)
        assertEquals(4, decoded.maxConcurrent)
        assertEquals("content://tree/primary%3Amedia", decoded.destinationTree)
        assertTrue(decoded.wifiOnly)
        assertFalse(decoded.completionNotifications)
        assertEquals(AppTheme.DARK, decoded.theme)
        assertFalse(decoded.autoResume)
        assertEquals(DuplicatePolicy.SKIP, decoded.duplicatePolicy)
        assertNull(decoded.browserHomepage) // Insecure input must never become the homepage.
        assertTrue(decoded.browserDesktop)
        assertFalse(decoded.browserJavaScript)
        assertFalse(decoded.browserCookies)
        assertFalse(decoded.dynamicColors)
    }

    @Test fun httpsHomepageIsStoredVerbatim() {
        val prefs = emptyPreferences().toMutablePreferences()
        SettingsEncoding.apply(prefs, AppSettings(browserHomepage = "https://start.example.com/home"))
        assertEquals("https://start.example.com/home", SettingsEncoding.decode(prefs).browserHomepage)
    }

    @Test fun javascriptAttackStringsNeverBecomeTheHomepage() {
        val prefs = emptyPreferences().toMutablePreferences()
        SettingsEncoding.apply(prefs, AppSettings(browserHomepage = "javascript:alert(document.cookie)"))
        assertNull(SettingsEncoding.decode(prefs).browserHomepage)
    }
}
