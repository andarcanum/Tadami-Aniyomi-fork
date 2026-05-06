package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelRuntimeWebViewCookieBridgeTest {

    @Test
    fun `applyWebViewCookies injects cookie header and xsrf token when absent`() {
        val headers = mutableMapOf<String, String>()

        applyWebViewCookies(
            headers = headers,
            url = "https://novel.tl/api/graphql",
            getCookie = { "cf_clearance=clear; XSRF-TOKEN=token%20123" },
        )

        headers shouldContain ("Cookie" to "cf_clearance=clear; XSRF-TOKEN=token%20123")
        headers shouldContain ("X-XSRF-TOKEN" to "token 123")
    }

    @Test
    fun `applyWebViewCookies merges explicit cookie header with webview cookies`() {
        val headers = mutableMapOf(
            "Cookie" to "session=manual",
            "X-XSRF-TOKEN" to "manual-token",
        )

        applyWebViewCookies(
            headers = headers,
            url = "https://novel.tl/api/graphql",
            getCookie = { "cf_clearance=clear; XSRF-TOKEN=token%20123" },
        )

        headers shouldContain ("Cookie" to "session=manual; cf_clearance=clear; XSRF-TOKEN=token%20123")
        headers shouldContain ("X-XSRF-TOKEN" to "manual-token")
    }

    @Test
    fun `applyWebViewCookies prefers latest webview value for duplicate cookie names`() {
        val headers = mutableMapOf(
            "Cookie" to "session=manual; cf_clearance=stale",
        )

        applyWebViewCookies(
            headers = headers,
            url = "https://novel.tl/api/graphql",
            getCookie = { "cf_clearance=fresh; XSRF-TOKEN=token%20123" },
        )

        headers shouldContain ("Cookie" to "session=manual; cf_clearance=fresh; XSRF-TOKEN=token%20123")
        headers shouldContain ("X-XSRF-TOKEN" to "token 123")
    }

    @Test
    fun `syncWebViewResponseCookies forwards each set cookie`() {
        val synced = mutableListOf<Pair<String, String>>()

        syncWebViewResponseCookies(
            url = "https://novel.tl/api/graphql",
            setCookieHeaders = listOf("cf_clearance=clear; Path=/", "session=abc; Path=/"),
            setCookie = { url, value -> synced += url to value },
        )

        synced.size shouldBe 2
        synced[0] shouldBe ("https://novel.tl/api/graphql" to "cf_clearance=clear; Path=/")
        synced[1] shouldBe ("https://novel.tl/api/graphql" to "session=abc; Path=/")
    }
}
