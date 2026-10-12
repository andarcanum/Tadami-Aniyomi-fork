package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class NovelWebViewFetchBridgeTest {

    @Test
    fun `bridge headers drop transport headers forbidden by browser fetch`() {
        val filtered = NovelWebViewFetchBridge.filterBridgeHeaders(
            mapOf(
                "Accept" to "*/*",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Referer" to "https://www.novelupdates.com/",
                "Accept-Encoding" to "zstd, br, gzip",
                "User-Agent" to "Mozilla/5.0",
                "Proxy-Auth" to "x",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Dest" to "empty",
                "sec-ch-ua" to "Chromium",
                "X-Custom" to "keep-me",
            ),
        )

        filtered shouldContainKey "Accept"
        filtered shouldContainKey "Content-Type"
        filtered shouldContainKey "X-Custom"
        filtered shouldNotContainKey "Referer"
        filtered shouldNotContainKey "Accept-Encoding"
        filtered shouldNotContainKey "User-Agent"
        filtered shouldNotContainKey "Proxy-Auth"
        filtered shouldNotContainKey "Sec-Fetch-Mode"
        filtered shouldNotContainKey "Sec-Fetch-Dest"
        filtered shouldNotContainKey "sec-ch-ua"
    }

    @Test
    fun `bridge config carries url method and form body for same-origin fetch`() {
        val config = NovelWebViewFetchBridge.buildBridgeConfigJson(
            token = "token-1",
            url = "https://www.novelupdates.com/wp-admin/admin-ajax.php",
            options = NovelJsRuntimeFactory.JsFetchRequest(
                method = "POST",
                headers = mapOf("Referer" to "https://www.novelupdates.com/"),
                bodyType = NovelJsRuntimeFactory.BodyType.Form,
                formEntries = listOf(
                    NovelJsRuntimeFactory.FormEntry(key = "action", value = "search"),
                ),
            ),
        )

        config shouldContain """"url":"https://www.novelupdates.com/wp-admin/admin-ajax.php""""
        config shouldContain """"method":"POST""""
        config shouldContain """"bodyKind":"form""""
        config shouldContain """"action","search""""
        config shouldContain """"referrer":"https://www.novelupdates.com/""""
        config shouldNotContain """"Referer""""
    }

    @Test
    fun `isRedirectUrl flags extnu redirect links and rejects regular pages`() {
        NovelWebViewFetchBridge.isRedirectUrl("https://www.novelupdates.com/extnu/2303408/") shouldBe true
        NovelWebViewFetchBridge.isRedirectUrl("https://www.novelupdates.com/series/solo-leveling/") shouldBe false
        NovelWebViewFetchBridge.isRedirectUrl("https://www.novelupdates.com/wp-admin/admin-ajax.php") shouldBe false
        NovelWebViewFetchBridge.isRedirectUrl("https://asuratls.com/chapter-1/") shouldBe false
    }

    @Test
    fun `retry-after header drives rate-limit backoff case-insensitively`() {
        NovelWebViewFetchBridge.retryAfterMs(mapOf("retry-after" to "3")) shouldBe 3_000L
        NovelWebViewFetchBridge.retryAfterMs(mapOf("Retry-After" to "1")) shouldBe 1_000L
        NovelWebViewFetchBridge.retryAfterMs(emptyMap()) shouldBe 2_500L
        NovelWebViewFetchBridge.retryAfterMs(mapOf("retry-after" to "garbage")) shouldBe 2_500L
    }

    @Test
    fun `minHostIntervalMs paces novelupdates more conservatively than default hosts`() {
        NovelWebViewFetchBridge.minHostIntervalMs("www.novelupdates.com") shouldBe 1_500L
        NovelWebViewFetchBridge.minHostIntervalMs("novelupdates.com") shouldBe 1_500L
        NovelWebViewFetchBridge.minHostIntervalMs("asuratls.com") shouldBe 600L
        NovelWebViewFetchBridge.minHostIntervalMs(null) shouldBe 600L
    }

    @Test
    fun `bridge cache keeps chapters and catalogues with updated TTLs`() {
        NovelWebViewFetchBridge.bridgeCacheTtlMs("https://www.novelupdates.com/extnu/2303408/") shouldBe
            30 * 60 * 1_000L
        NovelWebViewFetchBridge.bridgeCacheTtlMs("https://www.novelupdates.com/series/solo-leveling/") shouldBe
            10 * 60 * 1_000L
        NovelWebViewFetchBridge.bridgeCacheTtlMs("https://www.novelupdates.com/series-ranking/?rank=popmonth") shouldBe
            10 * 60 * 1_000L
        NovelWebViewFetchBridge.bridgeCacheTtlMs("https://www.novelupdates.com/series-finder/?sf=1&pg=1") shouldBe
            10 * 60 * 1_000L
        NovelWebViewFetchBridge.bridgeCacheTtlMs("https://www.novelupdates.com/other-page/") shouldBe
            5 * 60 * 1_000L
    }

    @Test
    fun `cloudflare challenge markers detect english and russian turnstile html`() {
        val russianTurnstile =
            "<html><body>Выполнение проверки безопасности ... Ожидание ответа www.novelupdates.com</body></html>"
        val turnstileWidget = """<div class="cf-turnstile" data-sitekey="0x4AAA"></div>"""
        val orchestrateScript = """<script src="/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page"></script>"""
        val cleanChapterHtml = "<html><body><h1>Chapter 1</h1><p>Solo Leveling chapter content.</p></body></html>"

        val detector = eu.kanade.tachiyomi.network.interceptor.CloudflareChallengeDetector
        detector.hasChallengeMarkers(russianTurnstile) shouldBe true
        detector.hasChallengeMarkers(turnstileWidget) shouldBe true
        detector.hasChallengeMarkers(orchestrateScript) shouldBe true
        detector.hasChallengeMarkers(cleanChapterHtml) shouldBe false
    }

    @Test
    fun `isBlockingChallengePage distinguishes blocking challenges from chapter pages with comment widgets`() {
        val detector = eu.kanade.tachiyomi.network.interceptor.CloudflareChallengeDetector
        val russianTurnstile =
            "<html><body>Выполнение проверки безопасности ... Ожидание ответа www.novelupdates.com</body></html>"
        val activeChallenge =
            "<html><head><title>Just a moment...</title></head><body><div id=\"challenge-stage\"></div></body></html>"
        val chapterWithTurnstileWidget = (1..60).joinToString("\n") {
            "<p>Paragraph $it of substantial novel text describing an intense fight scene.</p>"
        } + """<form id="commentform"><div class="cf-turnstile" data-sitekey="0x4AAA"></div></form>"""

        detector.isBlockingChallengePage(russianTurnstile) shouldBe true
        detector.isBlockingChallengePage(activeChallenge) shouldBe true
        detector.isBlockingChallengePage(chapterWithTurnstileWidget) shouldBe false
    }

    @Test
    fun `countParagraphTags counts real p tags and ignores pre, path, param, picture`() {
        val detector = eu.kanade.tachiyomi.network.interceptor.CloudflareChallengeDetector
        val htmlWithOtherTags = """
            <pre>code block</pre>
            <svg><path d="M0 0"/></svg>
            <object><param name="foo" value="bar"/></object>
            <picture><source srcset="x.jpg"/></picture>
        """.trimIndent()
        detector.countParagraphTags(htmlWithOtherTags) shouldBe 0

        val htmlWithPTags = """
            <p>First paragraph</p>
            <p class="chapter-text">Second paragraph</p>
            <p
               id="p3">Third paragraph</p>
        """.trimIndent()
        detector.countParagraphTags(htmlWithPTags) shouldBe 3
    }

    @Test
    fun `isChallengeTitle rejects normal redirecting titles and catches cloudflare titles`() {
        val detector = eu.kanade.tachiyomi.network.interceptor.CloudflareChallengeDetector
        detector.isChallengeTitle("redirecting to chapter 1...") shouldBe false
        detector.isChallengeTitle("you are being redirected") shouldBe false
        detector.isChallengeTitle("just a moment...") shouldBe true
        detector.isChallengeTitle("attention required! | cloudflare") shouldBe true
        detector.isChallengeTitle("проверка безопасности...") shouldBe true
    }

    @Test
    fun `isMeaningfulSearchQuery filters out stopword queries`() {
        val engine = eu.kanade.tachiyomi.data.suggestions.novel.NovelSearchFallbackEngine
        engine.isMeaningfulSearchQuery("i'm the") shouldBe false
        engine.isMeaningfulSearchQuery("the a an") shouldBe false
        engine.isMeaningfulSearchQuery("Solo Leveling") shouldBe true
        engine.isMeaningfulSearchQuery("I'm the Grim Reaper") shouldBe true
    }
}
