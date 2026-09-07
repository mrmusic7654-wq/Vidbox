package com.vidbox.data.blocking

import com.vidbox.domain.blocking.*
import com.vidbox.domain.repository.AllowlistRepository
import com.vidbox.domain.repository.EventLogger
import com.vidbox.domain.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Per-page counters the shield icon shows. Keyed by tab so parallel tabs do not mix. */
data class PageBlockingStats(val blockedAds: Int = 0, val blockedTrackers: Int = 0) {
    val total: Int get() = blockedAds + blockedTrackers
}

data class BlockingEngineState(
    val ready: Boolean = false,
    val enabled: Boolean = false,
    val ruleCount: Int = 0,
    val lists: List<FilterListStatus> = emptyList(),
    val allowlist: List<String> = emptyList(),
    val sessionBlocked: Int = 0,
)

/**
 * The single blocking decision point for the browser. WebView calls [decide] on interception
 * threads, so the active [ContentBlocker] is an immutable snapshot swapped atomically whenever
 * lists, settings, or the allowlist change; there is never a half-built index in use.
 */
@Singleton
class ContentBlockingEngine @Inject constructor(
    private val lists: FilterListManager,
    private val allowlist: AllowlistRepository,
    private val settings: SettingsRepository,
    private val logger: EventLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var blocker: ContentBlocker? = null
    @Volatile private var index: RuleIndex? = null
    @Volatile private var listStatus: List<FilterListStatus> = emptyList()
    @Volatile private var categories: Map<String, FilterList.Category> = emptyMap()
    private val sessionBlocked = AtomicInteger()
    private val pageStats = ConcurrentHashMap<String, PageBlockingStats>()
    private val mutableState = MutableStateFlow(BlockingEngineState())
    val state: StateFlow<BlockingEngineState> = mutableState.asStateFlow()
    private val mutablePageStats = MutableStateFlow<Map<String, PageBlockingStats>>(emptyMap())
    val stats: StateFlow<Map<String, PageBlockingStats>> = mutablePageStats.asStateFlow()
    private val reloads = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        scope.launch { rebuildIndex() }
        scope.launch {
            combine(settings.settings.map { Triple(it.blockAds, it.blockTrackers, it.contentBlockingEnabled) }.distinctUntilChanged(),
                allowlist.observe(), reloads.onStart { emit(Unit) }) { prefs, hosts, _ -> prefs to hosts }
                .collect { (prefs, hosts) -> rebuildBlocker(prefs.first, prefs.second, hosts) }
        }
    }

    /** Re-reads list files (after an update) and rebuilds the index off the main thread. */
    suspend fun reload() {
        rebuildIndex()
        reloads.tryEmit(Unit)
    }

    /** Decision for one request. Cheap enough for every subresource of a page. */
    fun decide(url: String, pageUrl: String?, type: ResourceType, tabId: String?): BlockDecision {
        val active = blocker ?: return BlockDecision.ALLOW
        val decision = active.decide(url, pageUrl, type)
        if (decision.blocked) {
            sessionBlocked.incrementAndGet()
            val category = decision.rule?.listId?.let { categories[it] } ?: FilterList.Category.ADS
            if (tabId != null) {
                pageStats.compute(tabId) { _, current ->
                    val base = current ?: PageBlockingStats()
                    if (category == FilterList.Category.TRACKERS) base.copy(blockedTrackers = base.blockedTrackers + 1)
                    else base.copy(blockedAds = base.blockedAds + 1)
                }
                publishStats()
            }
        }
        return decision
    }

    /** A new top-level navigation starts the page counter over. */
    fun pageStarted(tabId: String) {
        pageStats[tabId] = PageBlockingStats()
        publishStats()
    }

    fun tabClosed(tabId: String) {
        pageStats.remove(tabId)
        publishStats()
    }

    fun isAllowlisted(host: String?): Boolean {
        val hosts = mutableState.value.allowlist
        if (host == null || hosts.isEmpty()) return false
        return Allowlist(hosts).covers(host)
    }

    private fun publishStats() {
        mutablePageStats.value = HashMap(pageStats)
        mutableState.update { it.copy(sessionBlocked = sessionBlocked.get()) }
    }

    private suspend fun rebuildIndex() {
        try {
            val loaded = lists.load()
            val rules = loaded.flatMap { it.second }
            val built = withContext(Dispatchers.Default) { RuleIndex(rules) }
            index = built
            listStatus = loaded.map { it.first }
            categories = lists.lists.associate { it.id to it.category }
            logger.event("filters.loaded", "engine", mapOf("rules" to rules.size.toString()))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.event("filters.load_failed", "engine", mapOf("reason" to error.javaClass.simpleName))
            index = index ?: RuleIndex(emptyList())
        }
    }

    private fun rebuildBlocker(blockAds: Boolean, blockTrackers: Boolean, hosts: List<String>) {
        val current = index
        val enabledIds = lists.lists.filter { list ->
            (list.category == FilterList.Category.ADS && blockAds) || (list.category == FilterList.Category.TRACKERS && blockTrackers)
        }.map { it.id }.toSet()
        blocker = if (current == null || enabledIds.isEmpty()) null
        else ContentBlocker(current, Allowlist(hosts), enabledIds)
        mutableState.update {
            it.copy(ready = current != null, enabled = enabledIds.isNotEmpty(), ruleCount = current?.size ?: 0,
                lists = listStatus, allowlist = hosts, sessionBlocked = sessionBlocked.get())
        }
    }
}
