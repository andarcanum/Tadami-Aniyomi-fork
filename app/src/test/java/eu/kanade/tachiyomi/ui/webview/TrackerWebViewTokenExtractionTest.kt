package eu.kanade.tachiyomi.ui.webview

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TrackerWebViewTokenExtractionTest {

    @Test
    fun `extractNovelUpdatesCookie returns the full cookie header when wordpress login cookie is present`() {
        val cookie = "foo=bar; wordpress_logged_in_123=abc; path=/"

        extractNovelUpdatesCookie(cookie) shouldBe cookie
    }

    @Test
    fun `extractNovelListToken decodes base64 cookie and extracts access token`() {
        val cookie = "novellist=base64-eyJhY2Nlc3NfdG9rZW4iOiJ0b2tlbi0xMjMifQ==; path=/"

        extractNovelListToken(cookie) shouldBe "token-123"
    }
}
