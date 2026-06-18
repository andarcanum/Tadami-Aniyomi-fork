package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import eu.kanade.tachiyomi.network.AndroidCookieJar
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

class CloudflareInterceptorTest {

    @Test
    fun `intercept preserves pre solved cookies and delegates resolution`() {
        val request = Request.Builder()
            .url("https://novel.tl/images/4/4c/cover.jpg")
            .build()
        val solvedCookie = Cookie.Builder()
            .name("cf_clearance")
            .value("solved")
            .domain(request.url.host)
            .path("/")
            .build()

        val initialResponse = response(
            request = request,
            code = 403,
            message = "Forbidden",
            server = "cloudflare",
        )
        val challengeStillUp = response(
            request = request,
            code = 403,
            message = "Forbidden",
            server = "cloudflare",
            cfMitigated = true,
        )
        val finalResponse = response(
            request = request,
            code = 200,
            message = "OK",
            server = null,
        )

        val cookieJar = mockk<AndroidCookieJar>()
        every { cookieJar.get(request.url) } returns listOf(solvedCookie)
        every { cookieJar.remove(request.url, listOf("cf_clearance"), 0) } returns 1

        val challengeResolver = mockk<CloudflareChallengeResolver>()
        justRun { challengeResolver.resolve(request, solvedCookie) }

        val chain = mockk<Interceptor.Chain>()
        every { chain.proceed(request) } returnsMany listOf(challengeStillUp, finalResponse)

        val interceptor = CloudflareInterceptor(
            context = mockk<Context>(relaxed = true),
            cookieManager = cookieJar,
            defaultUserAgentProvider = { "test-agent" },
            challengeResolver = challengeResolver,
        )

        val result = interceptor.intercept(chain, request, initialResponse)

        assertEquals(200, result.code)
        verify(exactly = 0) { cookieJar.remove(request.url, listOf("cf_clearance"), 0) }
        verify(exactly = 1) { cookieJar.get(request.url) }
        verify(exactly = 1) { challengeResolver.resolve(request, solvedCookie) }
        verify(exactly = 2) { chain.proceed(request) }
    }

    @Test
    fun `intercept delegates cloudflare challenge resolution before final retry`() {
        val request = Request.Builder()
            .url("https://novel.tl/images/4/4c/cover.jpg")
            .build()

        val initialResponse = response(
            request = request,
            code = 403,
            message = "Forbidden",
            server = "cloudflare",
        )
        val challengeStillUp = response(
            request = request,
            code = 403,
            message = "Forbidden",
            server = "cloudflare",
            cfMitigated = true,
        )
        val finalResponse = response(
            request = request,
            code = 200,
            message = "OK",
            server = null,
        )

        val cookieJar = mockk<AndroidCookieJar>()
        every { cookieJar.get(request.url) } returns emptyList()
        every { cookieJar.remove(request.url, listOf("cf_clearance"), 0) } returns 0

        val challengeResolver = mockk<CloudflareChallengeResolver>()
        justRun { challengeResolver.resolve(request, null) }

        val chain = mockk<Interceptor.Chain>()
        every { chain.proceed(request) } returnsMany listOf(challengeStillUp, finalResponse)

        val interceptor = CloudflareInterceptor(
            context = mockk<Context>(relaxed = true),
            cookieManager = cookieJar,
            defaultUserAgentProvider = { "test-agent" },
            challengeResolver = challengeResolver,
        )

        val result = interceptor.intercept(chain, request, initialResponse)

        assertEquals(200, result.code)
        verify(exactly = 1) { cookieJar.remove(request.url, listOf("cf_clearance"), 0) }
        verify(exactly = 1) { cookieJar.get(request.url) }
        verify(exactly = 1) { challengeResolver.resolve(request, null) }
        verify(exactly = 2) { chain.proceed(request) }
    }

    @Test
    fun `intercept retries once when first proceed still hits cloudflare challenge`() {
        val request = Request.Builder()
            .url("https://novel.tl/page/1")
            .build()

        val initialResponse = response(
            request = request,
            code = 403,
            message = "Forbidden",
            server = "cloudflare",
        )
        val challengeStillUp = response(
            request = request,
            code = 403,
            message = "Forbidden",
            server = "cloudflare",
            cfMitigated = true,
        )
        val finalResponse = response(
            request = request,
            code = 200,
            message = "OK",
            server = null,
        )

        val cookieJar = mockk<AndroidCookieJar>()
        every { cookieJar.get(request.url) } returns emptyList()
        every { cookieJar.remove(request.url, listOf("cf_clearance"), 0) } returns 0

        val challengeResolver = mockk<CloudflareChallengeResolver>()
        justRun { challengeResolver.resolve(request, null) }

        val chain = mockk<Interceptor.Chain>()
        every { chain.proceed(request) } returnsMany listOf(challengeStillUp, finalResponse)

        val interceptor = CloudflareInterceptor(
            context = mockk<Context>(relaxed = true),
            cookieManager = cookieJar,
            defaultUserAgentProvider = { "test-agent" },
            challengeResolver = challengeResolver,
        )

        val result = interceptor.intercept(chain, request, initialResponse)

        assertEquals(200, result.code)
        verify(exactly = 1) { challengeResolver.resolve(request, null) }
        verify(exactly = 2) { chain.proceed(request) }
    }

    @Test
    fun `intercept surfaces interactive challenge as IOException`() {
        val request = Request.Builder()
            .url("https://novel.tl/page/1")
            .build()

        val initialResponse = response(
            request = request,
            code = 403,
            message = "Forbidden",
            server = "cloudflare",
        )
        val challengeStillUp = response(
            request = request,
            code = 403,
            message = "Forbidden",
            server = "cloudflare",
            cfMitigated = true,
        )

        val cookieJar = mockk<AndroidCookieJar>()
        every { cookieJar.get(request.url) } returns emptyList()
        every { cookieJar.remove(request.url, listOf("cf_clearance"), 0) } returns 0

        val challengeResolver = mockk<CloudflareChallengeResolver>()
        every { challengeResolver.resolve(request, null) } throws CloudflareInteractiveChallengeException()

        val chain = mockk<Interceptor.Chain>()
        every { chain.proceed(request) } returns challengeStillUp

        val interceptor = CloudflareInterceptor(
            context = mockk<Context>(relaxed = true),
            cookieManager = cookieJar,
            defaultUserAgentProvider = { "test-agent" },
            challengeResolver = challengeResolver,
        )

        try {
            interceptor.intercept(chain, request, initialResponse)
            assert(false) { "expected IOException" }
        } catch (e: IOException) {
            assert(e.cause is CloudflareInteractiveChallengeException)
        }
        verify(exactly = 1) { cookieJar.remove(request.url, listOf("cf_clearance"), 0) }
        verify(exactly = 1) { cookieJar.get(request.url) }
        verify(exactly = 1) { chain.proceed(request) }
    }

    private fun response(
        request: Request,
        code: Int,
        message: String,
        server: String?,
        cfMitigated: Boolean = false,
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
                if (cfMitigated) {
                    addHeader("cf-mitigated", "challenge")
                }
            }
            .body("".toResponseBody())
            .build()
    }
}
