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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class CloudflareInterceptorTest {

    @BeforeEach
    fun clearTracker() {
        // The tracker is process-wide; isolate tests from each other's interactive records.
        CloudflareInteractiveChallengeTracker.clearAll()
        CloudflareBridgeRegistry.clearAll()
    }

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
            clock = { 0L },
        )

        val result = interceptor.intercept(chain, request, initialResponse)

        assertEquals(200, result.code)
        verify(exactly = 1) { cookieJar.remove(request.url, listOf("cf_clearance"), 0) }
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
        every { chain.proceed(request) } returns finalResponse

        val interceptor = CloudflareInterceptor(
            context = mockk<Context>(relaxed = true),
            cookieManager = cookieJar,
            defaultUserAgentProvider = { "test-agent" },
            challengeResolver = challengeResolver,
            clock = { 0L },
        )

        val result = interceptor.intercept(chain, request, initialResponse)

        assertEquals(200, result.code)
        verify(exactly = 0) { cookieJar.remove(request.url, listOf("cf_clearance"), 0) }
        verify(exactly = 1) { cookieJar.get(request.url) }
        verify(exactly = 1) { challengeResolver.resolve(request, null) }
        verify(exactly = 1) { chain.proceed(request) }
    }

    @Test
    fun `intercept retries once when first proceed after resolver still hits cloudflare challenge`() {
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
            clock = { 0L },
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
        // Isolation: the tracker is process-wide; a record left by another test would route
        // this call into the negative-cache branch (and the unmocked SystemClock on JVM).
        CloudflareInteractiveChallengeTracker.clear(request.url.host)

        val initialResponse = response(
            request = request,
            code = 403,
            message = "Forbidden",
            server = "cloudflare",
        )

        val cookieJar = mockk<AndroidCookieJar>()
        every { cookieJar.get(request.url) } returns emptyList()
        every { cookieJar.remove(request.url, listOf("cf_clearance"), 0) } returns 0

        val challengeResolver = mockk<CloudflareChallengeResolver>()
        every { challengeResolver.resolve(request, null) } throws CloudflareInteractiveChallengeException()

        val chain = mockk<Interceptor.Chain>()

        val interceptor = CloudflareInterceptor(
            context = mockk<Context>(relaxed = true),
            cookieManager = cookieJar,
            defaultUserAgentProvider = { "test-agent" },
            challengeResolver = challengeResolver,
            clock = { 0L },
        )

        try {
            interceptor.intercept(chain, request, initialResponse)
            assert(false) { "expected IOException" }
        } catch (e: IOException) {
            assert(e.cause is CloudflareInteractiveChallengeException)
        }
        verify(exactly = 0) { cookieJar.remove(request.url, listOf("cf_clearance"), 0) }
        verify(exactly = 1) { cookieJar.get(request.url) }
        verify(exactly = 0) { chain.proceed(request) }
    }

    @Test
    fun `interactive failures are negative-cached per host (P3)`() {
        val request = Request.Builder()
            .url("https://novel.tl/page/1")
            .build()
        CloudflareInteractiveChallengeTracker.clear(request.url.host)
        try {
            negativeCacheScenario(request)
        } finally {
            CloudflareInteractiveChallengeTracker.clear(request.url.host)
        }
    }

    private fun negativeCacheScenario(request: Request) {
        val initialResponse = response(
            request = request,
            code = 403,
            message = "Forbidden",
            server = "cloudflare",
        )
        val cookieJar = mockk<AndroidCookieJar>()
        every { cookieJar.get(request.url) } returns emptyList()
        val challengeResolver = mockk<CloudflareChallengeResolver>()
        every { challengeResolver.resolve(request, null) } throws CloudflareInteractiveChallengeException()
        val chain = mockk<Interceptor.Chain>()
        var now = 0L
        val interceptor = CloudflareInterceptor(
            context = mockk<Context>(relaxed = true),
            cookieManager = cookieJar,
            defaultUserAgentProvider = { "test-agent" },
            challengeResolver = challengeResolver,
            clock = { now },
        )

        // First failure solves (and caches the host).
        try {
            interceptor.intercept(chain, request, initialResponse)
            assert(false) { "expected IOException" }
        } catch (e: IOException) {
            // expected
        }
        // Within the TTL the interceptor must fail fast without touching resolver or jar.
        now = 5 * 60_000
        try {
            interceptor.intercept(chain, request, initialResponse)
            assert(false) { "expected IOException" }
        } catch (e: IOException) {
            // expected
        }
        verify(exactly = 1) { challengeResolver.resolve(request, null) }
        verify(exactly = 1) { cookieJar.get(request.url) }

        // After the TTL the solve is attempted again.
        now = 11 * 60_000
        try {
            interceptor.intercept(chain, request, initialResponse)
            assert(false) { "expected IOException" }
        } catch (e: IOException) {
            // expected
        }
        verify(exactly = 2) { challengeResolver.resolve(request, null) }
    }

    @Test
    fun `fingerprint mismatch marks bridge registry and never the manual solve registry`() {
        val request = Request.Builder()
            .url("https://mismatch.example/page/1")
            .build()
        CloudflareInteractiveChallengeTracker.clear(request.url.host)

        val initialResponse = response(
            request = request,
            code = 403,
            message = "Forbidden",
            server = "cloudflare",
        )

        val cookieJar = mockk<AndroidCookieJar>()
        every { cookieJar.get(request.url) } returns emptyList()

        val challengeResolver = mockk<CloudflareChallengeResolver>()
        every { challengeResolver.resolve(request, null) } throws CloudflareFingerprintMismatchException()

        val chain = mockk<Interceptor.Chain>()

        val interceptor = CloudflareInterceptor(
            context = mockk<Context>(relaxed = true),
            cookieManager = cookieJar,
            defaultUserAgentProvider = { "test-agent" },
            challengeResolver = challengeResolver,
            clock = { 0L },
        )

        try {
            interceptor.intercept(chain, request, initialResponse)
            assert(false) { "expected IOException" }
        } catch (e: IOException) {
            assert(e.cause is CloudflareFingerprintMismatchException)
        }
        // Bridge routing is marked so following plugin fetches go silent via WebView...
        assert(CloudflareBridgeRegistry.isBridged(request.url.host))
        // ...while the dead-end manual captcha button stays hidden.
        assert(!CloudflareManualSolveRegistry.isPending(request.url.host))
        verify(exactly = 0) { chain.proceed(request) }
    }

    @Test
    fun `bridged hosts fast-fail without paying for a hidden webview solve`() {
        val request = Request.Builder()
            .url("https://bridged.example/page/1")
            .build()
        CloudflareBridgeRegistry.mark(request.url.host)

        val initialResponse = response(
            request = request,
            code = 403,
            message = "Forbidden",
            server = "cloudflare",
        )

        val cookieJar = mockk<AndroidCookieJar>()
        val challengeResolver = mockk<CloudflareChallengeResolver>()
        val chain = mockk<Interceptor.Chain>()

        val interceptor = CloudflareInterceptor(
            context = mockk<Context>(relaxed = true),
            cookieManager = cookieJar,
            defaultUserAgentProvider = { "test-agent" },
            challengeResolver = challengeResolver,
            clock = { 0L },
        )

        try {
            interceptor.intercept(chain, request, initialResponse)
            assert(false) { "expected IOException" }
        } catch (e: IOException) {
            assert(e.cause is CloudflareFingerprintMismatchException)
        }
        verify(exactly = 0) { challengeResolver.resolve(any(), any()) }
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `cover requests never trigger a cloudflare solve`() {
        val request = Request.Builder()
            .url("https://covers.example/img/1.jpg")
            .header(CoverRequestPolicy.HEADER_COVER_REQUEST, "1")
            .build()

        val challengeResponse = response(
            request = request,
            code = 403,
            message = "Forbidden",
            server = "cloudflare",
            cfMitigated = true,
        )

        val interceptor = CloudflareInterceptor(
            context = mockk<Context>(relaxed = true),
            cookieManager = mockk<AndroidCookieJar>(relaxed = true),
            defaultUserAgentProvider = { "test-agent" },
            challengeResolver = mockk<CloudflareChallengeResolver>(relaxed = true),
            clock = { 0L },
        )

        assert(!interceptor.shouldIntercept(challengeResponse))
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
