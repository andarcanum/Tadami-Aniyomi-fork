package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import eu.kanade.tachiyomi.network.AndroidCookieJar
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class CloudflareInterceptorTest {

    @Test
    fun `cloudflareChallengeUrlFor keeps original url for api calls`() {
        val request = Request.Builder()
            .url("https://novel.tl/api/site/v2/graphql")
            .addHeader("Accept", "application/json")
            .build()

        val challengeUrl = cloudflareChallengeUrlFor(request)

        assertEquals("https://novel.tl/api/site/v2/graphql", challengeUrl)
    }

    @Test
    fun `cloudflareChallengeUrlFor switches image urls to domain root`() {
        val request = Request.Builder()
            .url("https://novel.tl/images/5/5e/cover.jpg")
            .addHeader("Accept", "image/webp,image/apng,*/*;q=0.8")
            .build()

        val challengeUrl = cloudflareChallengeUrlFor(request)

        assertEquals("https://novel.tl/", challengeUrl)
    }

    @Test
    fun `cloudflareChallengeUrlFor switches static files to domain root`() {
        val request = Request.Builder()
            .url("https://example.com/assets/app.css?v=42")
            .addHeader("Accept", "text/css,*/*;q=0.1")
            .build()

        val challengeUrl = cloudflareChallengeUrlFor(request)

        assertEquals("https://example.com/", challengeUrl)
    }

    @Test
    fun `intercept retries once with existing cf cookie before launching WebView`() {
        val request = Request.Builder()
            .url("https://novel.tl/images/4/4c/cover.jpg")
            .build()

        val initialResponse = response(
            request = request,
            code = 403,
            message = "Forbidden",
            server = "cloudflare",
        )
        val retriedResponse = response(
            request = request,
            code = 200,
            message = "OK",
            server = null,
        )

        val cookieJar = mockk<AndroidCookieJar>()
        every { cookieJar.get(request.url) } returns listOf(
            Cookie.parse(request.url, "cf_clearance=ok; Path=/; Secure; HttpOnly")!!,
        )

        val chain = mockk<Interceptor.Chain>()
        every { chain.proceed(request) } returns retriedResponse

        val interceptor = CloudflareInterceptor(
            context = mockk<Context>(relaxed = true),
            cookieManager = cookieJar,
            defaultUserAgentProvider = { "test-agent" },
        )

        val result = interceptor.intercept(chain, request, initialResponse)

        assertEquals(200, result.code)
        verify(exactly = 1) { cookieJar.get(request.url) }
        verify(exactly = 1) { chain.proceed(request) }
    }

    private fun response(
        request: Request,
        code: Int,
        message: String,
        server: String?,
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(code)
            .message(message)
            .apply {
                if (server != null) {
                    addHeader("Server", server)
                }
            }
            .body("".toResponseBody())
            .build()
    }
}
