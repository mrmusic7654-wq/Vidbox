package com.vidbox.domain.blocking

import java.net.URI

/** Why a request was allowed or blocked; surfaced in the shield UI and diagnostics. */
enum class Verdict { ALLOW, ALLOW_ALLOWLISTED, ALLOW_EXCEPTION, ALLOW_PAGE_EXEMPT, BLOCK }

data class BlockDecision(val verdict: Verdict, val rule: NetworkRule? = null) {
    val blocked: Boolean get() = verdict == Verdict.BLOCK
    companion object {
        val ALLOW = BlockDecision(Verdict.ALLOW)
    }
}

/** A request as seen by the matcher; every field is derived once by the caller. */
data class RequestContext(
    val url: String,
    val pageUrl: String?,
    val type: ResourceType,
) {
    val lowerUrl: String = url.lowercase()
    val host: String? = Hosts.of(lowerUrl)
    val pageHost: String? = pageUrl?.let { Hosts.of(it.lowercase()) }
    val isThirdParty: Boolean = Hosts.isThirdParty(host, pageHost)
}

internal object Hosts {
    private val commonSecondLevel = setOf("co", "com", "org", "net", "gov", "edu", "ac", "gob", "ne", "or", "go", "mil", "nom", "ltd", "plc", "sch")

    fun of(lowerUrl: String): String? {
        val schemeEnd = lowerUrl.indexOf("://")
        if (schemeEnd < 0) return null
        var start = schemeEnd + 3
        val at = lowerUrl.indexOf('@', start)
        val slash = lowerUrl.indexOf('/', start)
        if (at in 0 until (if (slash < 0) lowerUrl.length else slash)) start = at + 1
        var end = lowerUrl.length
        for (i in start until lowerUrl.length) {
            val c = lowerUrl[i]
            if (c == '/' || c == '?' || c == '#') { end = i; break }
        }
        var host = lowerUrl.substring(start, end)
        if (host.startsWith("[")) {
            val close = host.indexOf(']')
            if (close > 0) host = host.substring(0, close + 1)
        } else {
            val colon = host.lastIndexOf(':')
            if (colon >= 0) host = host.substring(0, colon)
        }
        return host.trimEnd('.').takeIf { it.isNotEmpty() }
    }

    /** Registrable domain without a public-suffix list: two labels, or three for `co.uk`-style suffixes. */
    fun registrable(host: String): String {
        if (host.startsWith("[") || host.all { it.isDigit() || it == '.' }) return host
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val tld = labels.last()
        val second = labels[labels.size - 2]
        val take = if (tld.length == 2 && second in commonSecondLevel && labels.size >= 3) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    fun isThirdParty(requestHost: String?, pageHost: String?): Boolean {
        if (requestHost == null || pageHost == null) return false
        return registrable(requestHost) != registrable(pageHost)
    }

    fun hostOfUrl(url: String?): String? = runCatching { URI(url ?: return null).host?.lowercase() }.getOrNull()
}

/**
 * Indexed matcher. Rules are bucketed by (a) the host of `||host^`-style rules and (b) one
 * token that must occur in any matching URL; only those buckets plus a small generic set are
 * evaluated per request, so cost stays roughly constant as lists grow. The index is immutable
 * and therefore safe to query from WebView's interception threads while a new one is built.
 */
class RuleIndex(rules: List<NetworkRule>) {
    private val hostBlock = HashMap<String, MutableList<NetworkRule>>()
    private val hostExcept = HashMap<String, MutableList<NetworkRule>>()
    private val tokenBlock = HashMap<String, MutableList<NetworkRule>>()
    private val tokenExcept = HashMap<String, MutableList<NetworkRule>>()
    private val genericBlock = ArrayList<NetworkRule>()
    private val genericExcept = ArrayList<NetworkRule>()
    private val documentExceptions = ArrayList<NetworkRule>()
    val size: Int = rules.size

    init {
        for (rule in rules) {
            if (rule.documentException) { documentExceptions += rule; continue }
            val hostKey = hostKey(rule)
            if (hostKey != null) {
                (if (rule.isException) hostExcept else hostBlock).getOrPut(hostKey) { ArrayList(2) }.add(rule)
                continue
            }
            val token = keyToken(rule)
            if (token != null) (if (rule.isException) tokenExcept else tokenBlock).getOrPut(token) { ArrayList(2) }.add(rule)
            else (if (rule.isException) genericExcept else genericBlock).add(rule)
        }
    }

    val genericRuleCount: Int get() = genericBlock.size + genericExcept.size

    /** Matching block and exception rules for the request, cheapest buckets first. */
    fun decide(request: RequestContext): BlockDecision {
        if (request.type == ResourceType.DOCUMENT) return BlockDecision.ALLOW
        val url = request.lowerUrl
        val blocking = firstMatch(request, url, hostBlock, tokenBlock, genericBlock, preferImportant = true) ?: return BlockDecision.ALLOW
        if (blocking.important) return BlockDecision(Verdict.BLOCK, blocking)
        val exception = firstMatch(request, url, hostExcept, tokenExcept, genericExcept, preferImportant = false)
        return if (exception != null) BlockDecision(Verdict.ALLOW_EXCEPTION, exception) else BlockDecision(Verdict.BLOCK, blocking)
    }

    /** `@@…$document` rules exempt whole pages, evaluated once per page URL. */
    fun pageExemption(pageUrl: String): NetworkRule? {
        if (documentExceptions.isEmpty()) return null
        val lower = pageUrl.lowercase()
        val host = Hosts.of(lower)
        return documentExceptions.firstOrNull { it.matchesUrl(lower) && it.coversPage(host) }
    }

    private fun firstMatch(request: RequestContext, url: String, byHost: Map<String, List<NetworkRule>>,
        byToken: Map<String, List<NetworkRule>>, generic: List<NetworkRule>, preferImportant: Boolean): NetworkRule? {
        var found: NetworkRule? = null
        fun consider(rule: NetworkRule): Boolean {
            if (!rule.coversType(request.type) || !rule.coversParty(request.isThirdParty) || !rule.coversPage(request.pageHost)) return false
            if (!rule.matchesUrl(url)) return false
            if (rule.important) { found = rule; return true }
            if (found == null) found = rule
            return !preferImportant
        }
        request.host?.let { host ->
            var index = 0
            while (true) {
                val suffix = if (index == 0) host else host.substring(index)
                byHost[suffix]?.forEach { if (consider(it)) return found }
                val dot = host.indexOf('.', index)
                if (dot < 0) break
                index = dot + 1
            }
        }
        if (byToken.isNotEmpty()) {
            var start = 0
            val length = url.length
            while (start < length) {
                while (start < length && !isTokenChar(url[start])) start++
                var end = start
                while (end < length && isTokenChar(url[end])) end++
                if (end > start) {
                    byToken[url.substring(start, end)]?.forEach { if (consider(it)) return found }
                }
                start = end
            }
        }
        generic.forEach { if (consider(it)) return found }
        return found
    }

    private companion object {
        fun isTokenChar(c: Char): Boolean = c in 'a'..'z' || c in '0'..'9' || c == '%'

        /** `||host^`, `||host/…`, `||host` become host-indexed; wildcards in the host part do not qualify. */
        fun hostKey(rule: NetworkRule): String? {
            if (rule.anchor != Anchor.DOMAIN || rule.isRegex) return null
            val pattern = rule.pattern
            var end = 0
            while (end < pattern.length) {
                val c = pattern[end]
                if (c == '^' || c == '/' || c == '?' || c == ':' || c == '*') break
                if (!(c.isLetterOrDigit() || c == '.' || c == '-' || c == '_')) return null
                end++
            }
            if (end == 0) return null
            if (end < pattern.length && pattern[end] == '*') return null
            val host = pattern.substring(0, end)
            return if (host.contains('.')) host else null
        }

        /**
         * A token that is delimited on both sides inside the pattern, so it must appear as a whole
         * token in every matching URL. Tokens touching a wildcard or an unanchored edge are unsafe.
         */
        fun keyToken(rule: NetworkRule): String? {
            if (rule.isRegex) return null
            val p = rule.pattern
            var best: String? = null
            var i = 0
            while (i < p.length) {
                if (!isTokenChar(p[i])) { i++; continue }
                var j = i
                while (j < p.length && isTokenChar(p[j])) j++
                val startSafe = if (i == 0) rule.anchor != Anchor.NONE else p[i - 1] != '*'
                val endSafe = if (j == p.length) rule.endAnchor else p[j] != '*'
                if (startSafe && endSafe) {
                    val token = p.substring(i, j)
                    if (best == null || token.length > best.length) best = token
                }
                i = j
            }
            return best
        }
    }
}

/**
 * Per-site exceptions chosen by the user. They take precedence over every list rule, and a
 * site's exemption covers its subdomains.
 */
class Allowlist(hosts: Collection<String>) {
    private val hosts: Set<String> = hosts.map { normalize(it) }.filter { it.isNotEmpty() }.toHashSet()
    val size: Int get() = hosts.size

    fun covers(pageHost: String?): Boolean {
        val host = pageHost?.let(::normalize) ?: return false
        if (host.isEmpty()) return false
        var index = 0
        while (true) {
            val suffix = if (index == 0) host else host.substring(index)
            if (suffix in hosts) return true
            val dot = host.indexOf('.', index)
            if (dot < 0) return false
            index = dot + 1
        }
    }

    fun contains(host: String): Boolean = normalize(host) in hosts
    fun all(): List<String> = hosts.sorted()

    companion object {
        val EMPTY = Allowlist(emptyList())
        fun normalize(host: String): String = host.trim().lowercase().removePrefix("www.").trimEnd('.')
    }
}

/**
 * The full decision path: user allowlist → page-level list exemptions → indexed rule match.
 * Blocking can also be disabled per category so the tracker list is honoured even when ads
 * are allowed and vice versa (each list carries a category via its id prefix).
 */
class ContentBlocker(
    private val index: RuleIndex,
    private val allowlist: Allowlist,
    private val enabledLists: Set<String>? = null,
) {
    private val pageCache = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 256
    }

    fun decide(url: String, pageUrl: String?, type: ResourceType): BlockDecision {
        val request = RequestContext(url, pageUrl, type)
        if (allowlist.covers(request.pageHost)) return BlockDecision(Verdict.ALLOW_ALLOWLISTED)
        if (pageUrl != null && isPageExempt(pageUrl)) return BlockDecision(Verdict.ALLOW_PAGE_EXEMPT)
        val decision = index.decide(request)
        if (decision.blocked && enabledLists != null && decision.rule?.listId !in enabledLists) return BlockDecision.ALLOW
        return decision
    }

    private fun isPageExempt(pageUrl: String): Boolean {
        synchronized(pageCache) { pageCache[pageUrl]?.let { return it } }
        val exempt = index.pageExemption(pageUrl) != null
        synchronized(pageCache) { pageCache[pageUrl] = exempt }
        return exempt
    }

    val ruleCount: Int get() = index.size
}
