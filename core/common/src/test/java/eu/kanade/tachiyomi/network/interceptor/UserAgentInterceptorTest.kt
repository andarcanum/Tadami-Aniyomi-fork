package eu.kanade.tachiyomi.network.interceptor

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test

class UserAgentInterceptorTest {

    private fun runInterceptor(
        request: Request,
        defaultUa: String = "Default/1.0",
    ): Request {
        val capturedRequest = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(capturedRequest)) } answers {
            Response.Builder()
                .request(capturedRequest.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val interceptor = UserAgentInterceptor { defaultUa }
        interceptor.intercept(chain)
        return capturedRequest.captured
    }

    @Test
    fun `injects default User-Agent when missing`() {
        val request = Request.Builder().url("https://example.com/").build()
        val result = runInterceptor(request, "CustomUA/2.0")

        result.header("User-Agent") shouldBe "CustomUA/2.0"
    }

    @Test
    fun `injects Android WebView brand when User-Agent contains wv and Version-4-0`() {
        val webViewUa =
            "Mozilla/5.0 (Linux; Android 17; Pixel 10 Pro XL Build/TE1A.260310.003; wv) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/134.0.6998.136 Mobile Safari/537.36"
        val request = Request.Builder()
            .url("https://example.com/")
            .header("User-Agent", webViewUa)
            .build()
        val result = runInterceptor(request)

        result.header("sec-ch-ua") shouldBe
            "\"Android WebView\";v=\"134\", \"Chromium\";v=\"134\", \"Not:A-Brand\";v=\"24\""
        result.header("sec-ch-ua-mobile") shouldBe "?1"
        result.header("sec-ch-ua-platform") shouldBe "\"Android\""
    }

    @Test
    fun `injects Chromium client hints when User-Agent is standard Android Chrome`() {
        val chromeAndroidUa =
            "Mozilla/5.0 (Linux; Android 17; Pixel 10 Pro XL) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/134.0.6998.136 Mobile Safari/537.36"
        val request = Request.Builder()
            .url("https://example.com/")
            .header("User-Agent", chromeAndroidUa)
            .build()
        val result = runInterceptor(request)

        result.header("sec-ch-ua") shouldBe
            "\"Chromium\";v=\"134\", \"Not:A-Brand\";v=\"24\", \"Google Chrome\";v=\"134\""
        result.header("sec-ch-ua-mobile") shouldBe "?1"
        result.header("sec-ch-ua-platform") shouldBe "\"Android\""
    }

    @Test
    fun `does not overwrite existing Client Hints`() {
        val chromeUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/134.0.0.0 Safari/537.36"
        val request = Request.Builder()
            .url("https://example.com/")
            .header("User-Agent", chromeUa)
            .header("sec-ch-ua", "\"Custom\";v=\"1\"")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"CustomPlatform\"")
            .build()
        val result = runInterceptor(request)

        result.header("sec-ch-ua") shouldBe "\"Custom\";v=\"1\""
        result.header("sec-ch-ua-mobile") shouldBe "?0"
        result.header("sec-ch-ua-platform") shouldBe "\"CustomPlatform\""
    }

    @Test
    fun `extractChromeMajorVersion extracts major version correctly`() {
        UserAgentInterceptor.extractChromeMajorVersion(
            "Mozilla/5.0 (Linux; Android 17) AppleWebKit/537.36 Chrome/134.0.6998.136 Mobile Safari/537.36",
        ) shouldBe "134"
        UserAgentInterceptor.extractChromeMajorVersion("NonChrome/1.0") shouldBe null
    }

    @Test
    fun `isAndroidWebView detects webview markers and rejects regular browser`() {
        UserAgentInterceptor.isAndroidWebView("Mozilla/5.0 (Linux; U; Android 17; wv) AppleWebKit/537.36") shouldBe true
        UserAgentInterceptor.isAndroidWebView("Mozilla/5.0 Version/4.0 Chrome/134.0 Mobile Safari/537.36") shouldBe true
        UserAgentInterceptor.isAndroidWebView(
            "Mozilla/5.0 (Linux; Android 17) Chrome/134.0 Mobile Safari/537.36",
        ) shouldBe
            false
    }
}
