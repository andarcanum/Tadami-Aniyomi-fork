package eu.kanade.tachiyomi.ui.webview

import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import okhttp3.Headers
import org.junit.jupiter.api.Test

class WebViewSourceHeadersTest {

    @Test
    fun `resolveWebViewHeaders reads okhttp headers from source reflection`() {
        val source = object {
            @Suppress("unused")
            fun getHeaders(): Headers {
                return Headers.Builder()
                    .add("Referer", "https://novel.tl/")
                    .add("User-Agent", "Plugin-Agent/1.0")
                    .build()
            }
        }

        val headers = resolveWebViewHeaders(
            source = source,
        )

        headers shouldContain ("Referer" to "https://novel.tl/")
        headers shouldContain ("User-Agent" to "Plugin-Agent/1.0")
    }

    @Test
    fun `resolveWebViewHeaders keeps source headers unchanged when user agent missing`() {
        val source = object {
            @Suppress("unused")
            fun getHeaders(): Map<String, String> {
                return mapOf("Referer" to "https://novel.tl/")
            }
        }

        val headers = resolveWebViewHeaders(
            source = source,
        )

        headers["Referer"] shouldBe "https://novel.tl/"
        headers.containsKey("User-Agent") shouldBe false
    }
}
