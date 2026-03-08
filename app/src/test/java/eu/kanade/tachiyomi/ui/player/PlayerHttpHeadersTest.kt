package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import okhttp3.Headers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class PlayerHttpHeadersTest {

    @Test
    fun `sanitizePlaybackHeaders removes AnimeGO resolver headers and keeps playback headers`() {
        val headers = Headers.headersOf(
            "Referer",
            "https://animego.online/title",
            "Origin",
            "https://animego.online",
            "X-AnimeGO-Resolver",
            "cdn",
            "X-AnimeGO-Preferred-Quality",
            "1080p",
        )

        val sanitized = sanitizePlaybackHeaders(headers)

        sanitized["referer"] shouldBe "https://animego.online/title"
        sanitized["origin"] shouldBe "https://animego.online"
        sanitized.shouldNotContainKey("X-AnimeGO-Resolver")
        sanitized.shouldNotContainKey("X-AnimeGO-Preferred-Quality")
    }

    @Test
    fun `mergeAndSanitizePlaybackHeaders keeps source user agent and applies video overrides`() {
        val sourceHeaders = Headers.headersOf(
            "User-Agent",
            "Mozilla/5.0 test",
            "Accept",
            "*/*",
        )
        val videoHeaders = Headers.headersOf(
            "Referer",
            "https://animego.online/title",
            "Origin",
            "https://animego.online",
            "X-AnimeGO-Resolver",
            "cdn",
        )

        val sanitized = mergeAndSanitizePlaybackHeaders(
            sourceHeaders = sourceHeaders,
            videoHeaders = videoHeaders,
        )

        sanitized["user-agent"] shouldBe "Mozilla/5.0 test"
        sanitized["accept"] shouldBe "*/*"
        sanitized["referer"] shouldBe "https://animego.online/title"
        sanitized["origin"] shouldBe "https://animego.online"
        sanitized.shouldNotContainKey("X-AnimeGO-Resolver")
    }

    @Test
    fun `toPlaybackHttpOptions extracts user agent and referrer into dedicated fields`() {
        val sourceHeaders = Headers.headersOf(
            "User-Agent",
            "Mozilla/5.0 test",
            "Accept",
            "*/*",
        )
        val videoHeaders = Headers.headersOf(
            "Referer",
            "https://player.cdnvideohub.com/",
            "Origin",
            "https://player.cdnvideohub.com",
        )

        val options = toPlaybackHttpOptions(
            sourceHeaders = sourceHeaders,
            videoHeaders = videoHeaders,
        )

        options.userAgent shouldBe "Mozilla/5.0 test"
        options.referrer shouldBe "https://player.cdnvideohub.com/"
        options.headers["origin"] shouldBe "https://player.cdnvideohub.com"
        options.headers["user-agent"] shouldBe null
        options.headers["referer"] shouldBe null
    }
}
