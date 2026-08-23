package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import eu.kanade.tachiyomi.network.AndroidCookieJar
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression: each host got its own lock only, so challenges on *different* hosts ran their
 * WebView solves in parallel (one heavyweight WebView per host, up to ~30 s each). Under a
 * library refresh that multiplied memory pressure and ended in OutOfMemoryError crashes.
 * The interceptor must serialize challenge solves globally: at most one resolver call active.
 */
class CloudflareGlobalGateTest {

    private class RecordingResolver : CloudflareChallengeResolver {
        val insideCount = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val resolvedRequests = AtomicInteger(0)

        override fun resolve(originalRequest: Request, oldCookie: Cookie?) {
            val nowInside = insideCount.incrementAndGet()
            maxConcurrent.updateAndGet { current -> maxOf(current, nowInside) }
            try {
                // Simulate the WebView solve window.
                Thread.sleep(150)
            } finally {
                insideCount.decrementAndGet()
            }
            resolvedRequests.incrementAndGet()
        }
    }

    private fun successResponse(request: Request): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

    private fun buildInterceptor(
        cookieJar: AndroidCookieJar,
        resolver: CloudflareChallengeResolver,
    ): CloudflareInterceptor =
        CloudflareInterceptor(
            context = mockk<Context>(relaxed = true),
            cookieManager = cookieJar,
            defaultUserAgentProvider = { "test-agent" },
            challengeResolver = resolver,
        )

    @Test
    fun `challenge solves on different hosts do not overlap`() {
        val resolver = RecordingResolver()

        val requestA = Request.Builder().url("https://a.example.com/chapter").build()
        val requestB = Request.Builder().url("https://b.example.com/chapter").build()

        val cookieJar = mockk<AndroidCookieJar>()
        every { cookieJar.get(requestA.url) } returns emptyList()
        every { cookieJar.get(requestB.url) } returns emptyList()

        // One interceptor instance per caller mirrors real usage: each network client builds
        // its own CloudflareInterceptor, but WebView creation must still be serialized globally.
        val startBarrier = CyclicBarrier(2)
        fun spawn(request: Request): Thread {
            return Thread {
                startBarrier.await(5, TimeUnit.SECONDS)
                val chain = mockk<Interceptor.Chain>()
                every { chain.proceed(request) } returns successResponse(request)
                buildInterceptor(cookieJar, resolver).intercept(chain, request, successResponse(request))
            }.apply { start() }
        }

        val tA = spawn(requestA)
        val tB = spawn(requestB)
        tA.join(15_000)
        tB.join(15_000)
        tA.isAlive shouldBe false
        tB.isAlive shouldBe false

        resolver.resolvedRequests.get() shouldBe 2
        resolver.maxConcurrent.get() shouldBe 1
    }
}
