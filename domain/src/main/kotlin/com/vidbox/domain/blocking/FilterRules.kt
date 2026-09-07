package com.vidbox.domain.blocking

/** Request categories used by filter-list type options. */
enum class ResourceType(val bit: Int) {
    DOCUMENT(1), SUBDOCUMENT(1 shl 1), SCRIPT(1 shl 2), STYLESHEET(1 shl 3), IMAGE(1 shl 4), FONT(1 shl 5),
    MEDIA(1 shl 6), OBJECT(1 shl 7), XMLHTTPREQUEST(1 shl 8), WEBSOCKET(1 shl 9), PING(1 shl 10), OTHER(1 shl 11);

    companion object {
        const val ALL: Int = (1 shl 12) - 1
        /** Everything a page can load besides itself (every bit except DOCUMENT's bit 0). */
        const val SUBRESOURCES: Int = ALL - 1
    }
}

enum class Anchor { NONE, START, DOMAIN }

/**
 * One parsed network filter. Immutable and shared between the parser, the index, and the
 * matcher; it holds no compiled regex except for the rare `/regex/` filters.
 */
class NetworkRule internal constructor(
    /** Original filter text (used for `$badfilter` cancellation and diagnostics). */
    val text: String,
    /** Lowercased match pattern without anchors and options. */
    val pattern: String,
    val anchor: Anchor,
    val endAnchor: Boolean,
    val isException: Boolean,
    private val regex: Regex?,
    /** Positive type mask; 0 means the rule applies to every type not excluded by [notTypeMask]. */
    val typeMask: Int,
    val notTypeMask: Int,
    /** null = both parties, true = third-party only, false = first-party only. */
    val thirdParty: Boolean?,
    val domains: List<String>,
    val excludedDomains: List<String>,
    val important: Boolean,
    /** `@@…$document`: the whole page (and every request on it) is exempt. */
    val documentException: Boolean,
    val listId: String,
) {
    val isRegex: Boolean get() = regex != null

    /** Whether [type] is covered by this rule's type options. */
    fun coversType(type: ResourceType): Boolean = when {
        typeMask != 0 -> typeMask and type.bit != 0
        notTypeMask != 0 -> notTypeMask and type.bit == 0
        else -> true
    }

    fun coversParty(isThirdParty: Boolean): Boolean = thirdParty == null || thirdParty == isThirdParty

    /** `$domain=` option: the page host must match an included domain and none of the excluded ones. */
    fun coversPage(pageHost: String?): Boolean {
        if (domains.isEmpty() && excludedDomains.isEmpty()) return true
        val host = pageHost ?: return domains.isEmpty()
        if (excludedDomains.any { hostMatches(host, it) }) return false
        return domains.isEmpty() || domains.any { hostMatches(host, it) }
    }

    /** Pattern-only test against a lowercased URL. */
    fun matchesUrl(url: String): Boolean {
        regex?.let { return it.containsMatchIn(url) }
        return when (anchor) {
            Anchor.START -> Glob.matchAt(pattern, 0, url, 0, endAnchor)
            Anchor.DOMAIN -> {
                val schemeEnd = url.indexOf("://")
                val hostStart = if (schemeEnd < 0) 0 else schemeEnd + 3
                var hostEnd = url.length
                for (i in hostStart until url.length) {
                    val c = url[i]
                    if (c == '/' || c == '?' || c == '#' || c == ':') { hostEnd = i; break }
                }
                if (Glob.matchAt(pattern, 0, url, hostStart, endAnchor)) return true
                for (i in hostStart until hostEnd) {
                    if (url[i] == '.' && Glob.matchAt(pattern, 0, url, i + 1, endAnchor)) return true
                }
                false
            }
            Anchor.NONE -> Glob.find(pattern, url, endAnchor)
        }
    }

    override fun toString(): String = text

    internal companion object {
        fun hostMatches(host: String, domain: String): Boolean =
            host == domain || (host.length > domain.length && host.endsWith(domain) && host[host.length - domain.length - 1] == '.')
    }
}

/** ABP-style wildcard matching: `*` any run, `^` a separator or the end of the address. */
internal object Glob {
    fun isSeparator(c: Char): Boolean = !(c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '%')

    fun matchAt(p: String, pStart: Int, u: String, uStart: Int, endAnchor: Boolean): Boolean {
        var pi = pStart
        var ui = uStart
        while (pi < p.length) {
            when (val c = p[pi]) {
                '*' -> {
                    while (pi < p.length && p[pi] == '*') pi++
                    if (pi == p.length) return true
                    for (k in ui..u.length) if (matchAt(p, pi, u, k, endAnchor)) return true
                    return false
                }
                '^' -> {
                    if (ui == u.length) { pi++ }
                    else if (isSeparator(u[ui])) { pi++; ui++ }
                    else return false
                }
                else -> {
                    if (ui < u.length && u[ui] == c) { pi++; ui++ } else return false
                }
            }
        }
        return !endAnchor || ui == u.length
    }

    /** Unanchored search: the first literal run is located with indexOf to skip hopeless positions. */
    fun find(p: String, u: String, endAnchor: Boolean): Boolean {
        var literalEnd = 0
        while (literalEnd < p.length && p[literalEnd] != '*' && p[literalEnd] != '^') literalEnd++
        if (literalEnd == 0) {
            for (i in 0..u.length) if (matchAt(p, 0, u, i, endAnchor)) return true
            return false
        }
        val literal = p.substring(0, literalEnd)
        var from = 0
        while (true) {
            val at = u.indexOf(literal, from)
            if (at < 0) return false
            if (matchAt(p, 0, u, at, endAnchor)) return true
            from = at + 1
        }
    }
}

/** Parsed content of one list: network rules plus statistics for the UI. */
data class ParsedFilterList(
    val listId: String,
    val rules: List<NetworkRule>,
    val cosmeticRules: Int,
    val skippedRules: Int,
    val title: String?,
    val version: String?,
)

/**
 * Parses the standard filter-list formats: Adblock Plus / uBlock Origin network filters,
 * hosts files (`0.0.0.0 host`), and plain domain lists. Cosmetic filters and options whose
 * semantics cannot be honoured by request blocking are counted and skipped, never guessed.
 */
object FilterListParser {
    private val hostsLine = Regex("^(?:0\\.0\\.0\\.0|127\\.0\\.0\\.1|::1?|0)\\s+([^\\s#]+)")
    private val bareDomain = Regex("^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z][a-z0-9-]{1,62}$")
    private val optionChars = Regex("^[~a-z0-9=|,._*:/\\[\\]-]+$", RegexOption.IGNORE_CASE)
    private val localHosts = setOf("localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix", "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0")
    private val cosmeticMarkers = listOf("##", "#@#", "#?#", "#$#", "#%#", "#@?#", "#@$#", "#@%#", "#$?#")

    private val typeNames: Map<String, Int> = mapOf(
        "document" to ResourceType.DOCUMENT.bit, "doc" to ResourceType.DOCUMENT.bit,
        "subdocument" to ResourceType.SUBDOCUMENT.bit, "frame" to ResourceType.SUBDOCUMENT.bit,
        "script" to ResourceType.SCRIPT.bit,
        "stylesheet" to ResourceType.STYLESHEET.bit, "css" to ResourceType.STYLESHEET.bit,
        "image" to ResourceType.IMAGE.bit,
        "font" to ResourceType.FONT.bit,
        "media" to ResourceType.MEDIA.bit,
        "object" to ResourceType.OBJECT.bit, "object-subrequest" to ResourceType.OBJECT.bit,
        "xmlhttprequest" to ResourceType.XMLHTTPREQUEST.bit, "xhr" to ResourceType.XMLHTTPREQUEST.bit,
        "websocket" to ResourceType.WEBSOCKET.bit,
        "ping" to ResourceType.PING.bit, "beacon" to ResourceType.PING.bit,
        "other" to ResourceType.OTHER.bit,
        "all" to ResourceType.ALL,
    )

    /** Options that only make sense for cosmetic filtering, popups, or rewriting; rules carrying them are skipped. */
    private val unsupportedOptions = setOf("popup", "popunder", "elemhide", "ehide", "generichide", "ghide", "specifichide", "shide",
        "genericblock", "inline-script", "inline-font", "webrtc", "cname", "empty", "mp4", "strict1p", "strict3p", "to", "from", "method", "ipaddress")
    private val unsupportedValueOptions = setOf("csp", "redirect", "redirect-rule", "removeparam", "queryprune", "header", "replace",
        "permissions", "urltransform", "denyallow", "rewrite", "uritransform", "urlskip")

    fun parse(text: String, listId: String): ParsedFilterList = parse(text.lineSequence(), listId)

    fun parse(lines: Sequence<String>, listId: String): ParsedFilterList {
        val rules = ArrayList<NetworkRule>()
        val badFilters = HashSet<String>()
        var cosmetic = 0
        var skipped = 0
        var title: String? = null
        var version: String? = null
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("!") || line.startsWith("[")) {
                if (line.startsWith("! Title:", ignoreCase = true)) title = line.substringAfter(':').trim().take(80)
                if (line.startsWith("! Version:", ignoreCase = true)) version = line.substringAfter(':').trim().take(40)
                continue
            }
            if (line.startsWith("#")) continue // hosts-file comment
            if (cosmeticMarkers.any { line.contains(it) }) { cosmetic++; continue }
            when (val parsed = parseLine(line, listId)) {
                null -> skipped++
                is Parsed.Bad -> badFilters += parsed.canonical
                is Parsed.Rule -> rules += parsed.rule
            }
        }
        val effective = if (badFilters.isEmpty()) rules else rules.filterNot { it.text in badFilters }
        return ParsedFilterList(listId, effective, cosmetic, skipped, title, version)
    }

    private sealed interface Parsed {
        class Rule(val rule: NetworkRule) : Parsed
        class Bad(val canonical: String) : Parsed
    }

    private fun parseLine(line: String, listId: String): Parsed? {
        hostsLine.find(line)?.let { match ->
            val host = match.groupValues[1].lowercase()
            if (host in localHosts || host.startsWith("ip6-") || !host.contains('.')) return null
            return Parsed.Rule(domainRule("||$host^", host, listId))
        }
        var body = line
        var exception = false
        if (body.startsWith("@@")) { exception = true; body = body.substring(2) }
        if (body.isEmpty()) return null

        // Options start at the last '$' whose remainder looks like an option list.
        var options = emptyList<String>()
        val dollar = body.lastIndexOf('$')
        if (dollar > 0 && dollar < body.length - 1 && optionChars.matches(body.substring(dollar + 1)) &&
            !(body.startsWith("/") && body.endsWith("/") && dollar == body.length - 1)) {
            options = body.substring(dollar + 1).split(',').map { it.trim() }.filter { it.isNotEmpty() }
            body = body.substring(0, dollar)
        }
        if (body.isEmpty()) return null

        var typeMask = 0
        var notTypeMask = 0
        var thirdParty: Boolean? = null
        val domains = ArrayList<String>()
        val excluded = ArrayList<String>()
        var important = false
        var badfilter = false
        var documentException = false
        for (option in options) {
            val lower = option.lowercase()
            val negated = lower.startsWith("~")
            val name = lower.removePrefix("~").substringBefore('=')
            val value = if (option.contains('=')) option.substringAfter('=') else null
            when {
                name in typeNames && value == null -> {
                    val bit = typeNames.getValue(name)
                    if (negated) notTypeMask = notTypeMask or bit else typeMask = typeMask or bit
                    if (!negated && name == "document" || name == "doc") documentException = exception
                }
                name == "third-party" || name == "3p" -> thirdParty = !negated
                name == "first-party" || name == "1p" -> thirdParty = negated
                name == "domain" && value != null -> {
                    for (entry in value.split('|')) {
                        val host = entry.trim().lowercase()
                        if (host.isEmpty()) continue
                        if (host.endsWith(".*") || host.startsWith("/")) return null // entity/regex domains are not supported
                        if (host.startsWith("~")) excluded += host.substring(1) else domains += host
                    }
                }
                name == "important" -> important = true
                name == "badfilter" -> badfilter = true
                name == "match-case" -> Unit // matching is case-insensitive; a case-sensitive rule still applies
                name in unsupportedOptions || name in unsupportedValueOptions -> return null
                else -> return null // unknown option: never guess semantics
            }
        }
        if (badfilter) return Parsed.Bad(canonical(line))
        // Pure page-blocking rules (document/popup only) are outside request blocking.
        if (!exception && typeMask != 0 && typeMask and ResourceType.SUBRESOURCES == 0) return null
        if (exception && documentException && typeMask == ResourceType.DOCUMENT.bit) {
            // Whole-page exemption: matched against the page URL, not against subresources.
        }

        var pattern = body
        var anchor = Anchor.NONE
        var endAnchor = false
        var regex: Regex? = null
        if (pattern.length > 2 && pattern.startsWith("/") && pattern.endsWith("/")) {
            regex = runCatching { Regex(pattern.substring(1, pattern.length - 1), RegexOption.IGNORE_CASE) }.getOrNull() ?: return null
            pattern = pattern.lowercase()
        } else {
            if (pattern.startsWith("||")) { anchor = Anchor.DOMAIN; pattern = pattern.substring(2) }
            else if (pattern.startsWith("|")) { anchor = Anchor.START; pattern = pattern.substring(1) }
            if (pattern.endsWith("|")) { endAnchor = true; pattern = pattern.dropLast(1) }
            pattern = pattern.lowercase()
            if (anchor == Anchor.NONE && !endAnchor && bareDomain.matches(pattern)) anchor = Anchor.DOMAIN.also { pattern = "$pattern^" }
            // A leading wildcard adds nothing to an unanchored pattern.
            if (anchor == Anchor.NONE) pattern = pattern.trimStart('*')
            if (pattern.isEmpty() || (pattern == "^" && anchor == Anchor.NONE)) return null
            // Patterns shorter than this match far too much to be intentional network rules.
            if (anchor == Anchor.NONE && pattern.replace("*", "").length < 3) return null
        }
        return Parsed.Rule(NetworkRule(canonical(line), pattern, anchor, endAnchor, exception, regex, typeMask, notTypeMask,
            thirdParty, domains, excluded, important, documentException, listId))
    }

    private fun domainRule(text: String, host: String, listId: String) =
        NetworkRule(text, "$host^", Anchor.DOMAIN, false, false, null, 0, 0, null, emptyList(), emptyList(), false, false, listId)

    /** Text without the badfilter marker, so a `$badfilter` line cancels exactly its twin. */
    private fun canonical(line: String): String = line.replace(",badfilter", "").replace("\$badfilter", "").trim()
}
