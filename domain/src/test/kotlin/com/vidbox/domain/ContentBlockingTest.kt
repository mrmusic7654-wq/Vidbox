package com.vidbox.domain

import com.vidbox.domain.blocking.*
import org.junit.Assert.*
import org.junit.Test

class ContentBlockingTest {
    private val list = """
        [Adblock Plus 2.0]
        ! Title: Test list
        ! Version: 202609070001
        ||ads.example.com^
        ||tracker.net^${'$'}third-party
        ||cdn.example.com/banner*.gif
        /adserver/*
        |https://static.example.org/pixel.gif|
        ||firstparty.test^${'$'}~third-party,image
        ||scripts.test^${'$'}script,domain=news.example|~sports.news.example
        @@||ads.example.com/allowed.js${'$'}script
        @@||cdn.example.com/banner-keep.gif
        ||must.block^${'$'}important
        @@||must.block^
        @@||exempt.example^${'$'}document
        example.com##.ad-banner
        example.com#@#.ad-banner
        ||badfilter.example^
        ||badfilter.example^${'$'}badfilter
        /^https?:\/\/regex\.example\/[a-z]+\/ad\?/${'$'}image
        0.0.0.0 hosts.example
        127.0.0.1 localhost
        plaindomain.example
        ||popup.example^${'$'}popup
        ||unknown.example^${'$'}strangeoption
    """.trimIndent()

    private val parsed = FilterListParser.parse(list, "test")
    private val blocker = ContentBlocker(RuleIndex(parsed.rules), Allowlist.EMPTY)

    private fun decide(url: String, page: String? = "https://news.example/story", type: ResourceType = ResourceType.SCRIPT) =
        blocker.decide(url, page, type)

    @Test fun parserSeparatesNetworkFromCosmeticAndUnknownRules() {
        assertEquals("Test list", parsed.title)
        assertEquals("202609070001", parsed.version)
        assertEquals(2, parsed.cosmeticRules)
        // popup-only and unknown-option rules are skipped, never guessed; localhost hosts lines are dropped.
        assertTrue(parsed.skippedRules >= 3)
        assertFalse(parsed.rules.any { it.text.contains("badfilter.example") })
        assertFalse(parsed.rules.any { it.text.contains("popup.example") })
        assertFalse(parsed.rules.any { it.text.contains("unknown.example") })
    }

    @Test fun domainAnchoredRulesMatchHostAndSubdomains() {
        assertTrue(decide("https://ads.example.com/a.js").blocked)
        assertTrue(decide("https://sub.ads.example.com/a.js").blocked)
        assertFalse(decide("https://notads.example.com/a.js").blocked)
        assertFalse(decide("https://example.com/ads.example.com/a.js").blocked)
    }

    @Test fun thirdPartyOptionUsesRegistrableDomain() {
        assertTrue(decide("https://tracker.net/t.js", page = "https://news.example/").blocked)
        assertFalse(decide("https://tracker.net/t.js", page = "https://www.tracker.net/home").blocked)
        assertFalse(decide("https://tracker.net/t.js", page = "https://app.tracker.net/").blocked)
    }

    @Test fun wildcardsAndSeparatorsFollowAbpSemantics() {
        assertTrue(decide("https://cdn.example.com/banner-120x600.gif", type = ResourceType.IMAGE).blocked)
        assertFalse(decide("https://cdn.example.com/other/banner.gif", type = ResourceType.IMAGE).blocked)
        assertTrue(decide("https://site.test/adserver/x", type = ResourceType.OTHER).blocked)
        assertTrue(decide("https://static.example.org/pixel.gif", type = ResourceType.IMAGE).blocked)
        assertFalse(decide("https://static.example.org/pixel.gif?x=1", type = ResourceType.IMAGE).blocked)
        assertTrue(decide("https://hosts.example/x.js").blocked)
        assertTrue(decide("https://plaindomain.example/x.js").blocked)
        assertTrue(decide("https://regex.example/abc/ad?id=1", type = ResourceType.IMAGE).blocked)
        assertFalse(decide("https://regex.example/abc/ad?id=1", type = ResourceType.SCRIPT).blocked)
    }

    @Test fun typeAndPartyOptionsRestrictMatches() {
        assertTrue(decide("https://firstparty.test/logo.png", page = "https://firstparty.test/", type = ResourceType.IMAGE).blocked)
        assertFalse(decide("https://firstparty.test/logo.png", page = "https://other.test/", type = ResourceType.IMAGE).blocked)
        assertFalse(decide("https://firstparty.test/app.js", page = "https://firstparty.test/", type = ResourceType.SCRIPT).blocked)
    }

    @Test fun domainOptionScopesRuleToPages() {
        assertTrue(decide("https://scripts.test/a.js", page = "https://news.example/").blocked)
        assertTrue(decide("https://scripts.test/a.js", page = "https://world.news.example/").blocked)
        assertFalse(decide("https://scripts.test/a.js", page = "https://sports.news.example/").blocked)
        assertFalse(decide("https://scripts.test/a.js", page = "https://other.example/").blocked)
    }

    @Test fun exceptionsImportantAndDocumentRulesHaveExpectedPrecedence() {
        val allowed = decide("https://ads.example.com/allowed.js")
        assertEquals(Verdict.ALLOW_EXCEPTION, allowed.verdict)
        assertTrue(decide("https://ads.example.com/allowed.js", type = ResourceType.IMAGE).blocked)
        assertEquals(Verdict.ALLOW_EXCEPTION, decide("https://cdn.example.com/banner-keep.gif", type = ResourceType.IMAGE).verdict)
        assertTrue(decide("https://must.block/x.js").blocked)
        assertEquals(Verdict.ALLOW_PAGE_EXEMPT, decide("https://ads.example.com/a.js", page = "https://exempt.example/page").verdict)
        assertEquals(Verdict.ALLOW, decide("https://exempt.example/", type = ResourceType.DOCUMENT).verdict)
    }

    @Test fun userAllowlistOverridesEveryRule() {
        val allowlisted = ContentBlocker(RuleIndex(parsed.rules), Allowlist(listOf("News.Example")))
        assertEquals(Verdict.ALLOW_ALLOWLISTED, allowlisted.decide("https://must.block/x.js", "https://www.news.example/story", ResourceType.SCRIPT).verdict)
        assertEquals(Verdict.ALLOW_ALLOWLISTED, allowlisted.decide("https://must.block/x.js", "https://sports.news.example/", ResourceType.SCRIPT).verdict)
        assertTrue(allowlisted.decide("https://must.block/x.js", "https://other.example/", ResourceType.SCRIPT).blocked)
        assertTrue(Allowlist(listOf("www.example.com")).contains("example.com"))
        assertFalse(Allowlist(listOf("example.com")).covers("notexample.com"))
    }

    @Test fun disabledListsAreIgnoredButExceptionsStillApply() {
        val onlyOther = ContentBlocker(RuleIndex(parsed.rules), Allowlist.EMPTY, enabledLists = setOf("other"))
        assertFalse(onlyOther.decide("https://ads.example.com/a.js", "https://news.example/", ResourceType.SCRIPT).blocked)
        val enabled = ContentBlocker(RuleIndex(parsed.rules), Allowlist.EMPTY, enabledLists = setOf("test"))
        assertTrue(enabled.decide("https://ads.example.com/a.js", "https://news.example/", ResourceType.SCRIPT).blocked)
    }

    @Test fun indexKeepsGenericBucketSmallForLargeLists() {
        val big = buildString {
            for (i in 0 until 20_000) appendLine("||host$i.example^")
            for (i in 0 until 5_000) appendLine("/path$i/tracker.js")
        }
        val index = RuleIndex(FilterListParser.parse(big, "big").rules)
        assertEquals(25_000, index.size)
        assertTrue("generic rules: ${index.genericRuleCount}", index.genericRuleCount < 50)
        val bigBlocker = ContentBlocker(index, Allowlist.EMPTY)
        assertTrue(bigBlocker.decide("https://host19999.example/x", "https://a.test/", ResourceType.SCRIPT).blocked)
        assertTrue(bigBlocker.decide("https://cdn.test/path4999/tracker.js", "https://a.test/", ResourceType.SCRIPT).blocked)
        assertFalse(bigBlocker.decide("https://cdn.test/path/tracker.js", "https://a.test/", ResourceType.SCRIPT).blocked)
        val started = System.nanoTime()
        repeat(2_000) { bigBlocker.decide("https://cdn.test/assets/$it/app.js?v=$it", "https://a.test/", ResourceType.SCRIPT) }
        val perRequestMicros = (System.nanoTime() - started) / 2_000 / 1_000
        assertTrue("per-request cost ${perRequestMicros}µs", perRequestMicros < 2_000)
    }

    @Test fun hostsAreExtractedWithoutCredentialsPortsOrPaths() {
        val ctx = RequestContext("https://user:pw@Sub.Example.co.uk:8443/p?q=1", "https://example.co.uk/", ResourceType.IMAGE)
        assertEquals("sub.example.co.uk", ctx.host)
        assertFalse(ctx.isThirdParty)
        assertTrue(RequestContext("https://cdn.other.com/x", "https://example.co.uk/", ResourceType.IMAGE).isThirdParty)
    }
}
