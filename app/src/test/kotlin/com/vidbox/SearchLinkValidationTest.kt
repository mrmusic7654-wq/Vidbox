package com.vidbox

import com.vidbox.domain.model.AppSettings
import com.vidbox.domain.model.VideoSearchResult
import com.vidbox.domain.repository.SettingsRepository
import com.vidbox.domain.repository.VideoSearcher
import com.vidbox.domain.usecase.SearchVideos
import com.vidbox.presentation.search.SearchEvent
import com.vidbox.presentation.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Typed links are validated before the analyzer round-trip, so a bad scheme reports
 * inline on the search screen instead of silently doing nothing (or hitting the engine).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchLinkValidationTest {
    private val main = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(main) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeSettings : SettingsRepository {
        private val state = MutableStateFlow(AppSettings())
        override val settings get() = state.asStateFlow()
        override suspend fun update(transform: (AppSettings) -> AppSettings) { state.update(transform) }
    }

    private class FakeSearcher : VideoSearcher {
        override suspend fun search(query: String, limit: Int): List<VideoSearchResult> = emptyList()
    }

    private fun viewModel() = SearchViewModel(SearchVideos(FakeSearcher()), FakeSettings())

    @Test
    fun invalidSchemesReportInlineWithoutAnalyzerRoundTrip() = runTest {
        val viewModel = viewModel()
        val events = mutableListOf<SearchEvent>()
        val collector = launch { viewModel.events.collect(events::add) }
        viewModel.input("javascript:alert(1)")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue("no analyzer event may fire for an invalid link", events.isEmpty())
        assertEquals("javascript:alert(1)", viewModel.state.value.query)
        assertTrue(viewModel.state.value.analysisError?.contains("HTTPS") == true)
        collector.cancel()
    }

    @Test
    fun validLinksAreRoutedToTheAnalyzer() = runTest {
        val viewModel = viewModel()
        val events = mutableListOf<SearchEvent>()
        val collector = launch { viewModel.events.collect(events::add) }
        viewModel.input("https://example.com/film")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(listOf("https://example.com/film"),
            events.filterIsInstance<SearchEvent.OpenLink>().map { it.url })
        assertNull(viewModel.state.value.analysisError)
        collector.cancel()
    }

    @Test
    fun plainPhrasesStillGoToSearch() = runTest {
        val viewModel = viewModel()
        val events = mutableListOf<SearchEvent>()
        val collector = launch { viewModel.events.collect(events::add) }
        viewModel.input(" Kurzgesagt docs ")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(events.isEmpty())
        assertNull(viewModel.state.value.analysisError)
        assertEquals("the phrase went through the search branch", "Kurzgesagt docs", viewModel.state.value.searchedFor)
        collector.cancel()
    }

    @Test
    fun proseWithAColonStaysASearch() = runTest {
        val viewModel = viewModel()
        val events = mutableListOf<SearchEvent>()
        val collector = launch { viewModel.events.collect(events::add) }
        viewModel.input("NASA: moon landing")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(events.isEmpty())
        assertNull(viewModel.state.value.analysisError)
        assertEquals("NASA: moon landing", viewModel.state.value.searchedFor)
        collector.cancel()
    }
}
