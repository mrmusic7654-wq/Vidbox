package com.vidbox

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import javax.inject.Inject

@HiltAndroidTest
class AppScreensTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var repository: DownloadRepository
    @Inject lateinit var settings: SettingsRepository
    @Before fun setup() {
        hilt.inject()
        runBlocking { settings.update { AppSettings() }; repository.clearTerminalHistory() }
    }
    @Test fun mainScreenValidatesInput() {
        compose.onNodeWithTag("home_url").performTextInput("javascript:alert(1)")
        compose.onNodeWithTag("analyze_button").performClick()
        compose.onNodeWithTag("analysis_error").assertIsDisplayed()
        compose.onNodeWithTag("formats_screen").assertDoesNotExist()
    }
    @Test fun analysisDisplaysRealContractFieldsAndSelectsFormats() {
        compose.onNodeWithTag("home_url").performTextInput("https://example.com/film")
        compose.onNodeWithTag("analyze_button").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("formats_screen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Open-license test film").assertIsDisplayed()
        compose.onNodeWithText("Quality & format").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("formats_screen").performScrollToNode(hasTestTag("format_18::mp4"))
        compose.onNodeWithTag("format_18::mp4").performClick()
        compose.onNodeWithText("Video + audio · no merging needed").assertIsDisplayed()
        compose.onNodeWithTag("download_button").assertIsEnabled()
    }
    @Test fun downloadScreenHasAnHonestEmptyState() {
        compose.onNodeWithTag("nav_downloads").performClick()
        compose.onNodeWithText("Nothing in the queue").assertIsDisplayed()
        compose.onNodeWithText("Add a link").performClick()
        compose.onNodeWithTag("home_url").assertIsDisplayed()
    }
    @Test fun historySurvivesActivityRecreationAndReportsMissingFiles() {
        val id = runBlocking {
            val spec = DownloadSpec("https://example.com/film", "Saved test film", null, "example.com", 12.0,
                FormatSelection(MediaFormat("18", "mp4", height = 720, hasVideo = true, hasAudio = true)), false, null)
            val id = repository.enqueue(spec)
            repository.transition(id, setOf(DownloadState.QUEUED), DownloadState.EXTRACTING)
            repository.progress(id, DownloadProgress(DownloadState.PROCESSING, 128, 128))
            repository.complete(id, StoredMedia("content://media/external/video/media/2147483647", "Saved test film.mp4", "video/mp4", 128))
            repository.cleaned(id)
            id
        }
        compose.onNodeWithTag("nav_history").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("download_$id").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Saved test film.mp4").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav_history").performClick()
        compose.onNodeWithText("Saved test film.mp4").assertIsDisplayed()
        compose.waitUntil(5000) { compose.onAllNodesWithText("File unavailable").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("File unavailable").assertIsDisplayed()
    }
    @Test fun settingsPersistAcrossActivityRecreation() {
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("settings_wifi"))
        compose.onNodeWithTag("settings_wifi").performClick()
        compose.waitUntil(5000) { runBlocking { settings.settings.first().wifiOnly } }
        compose.onNodeWithTag("settings_wifi").assertIsOn()
        compose.waitForIdle()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("settings_wifi"))
        compose.onNodeWithTag("settings_wifi").assertIsOn()
    }
}
