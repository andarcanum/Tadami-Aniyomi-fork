package eu.kanade.tachiyomi.data.coil

import coil3.fetch.SourceFetchResult
import eu.kanade.tachiyomi.network.interceptor.CoverRecoveryInterceptor
import eu.kanade.tachiyomi.network.interceptor.CoverRequestPolicy
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.FileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class AuroraPosterRequestFetcherTest {

    private val primaryServer = MockWebServer()
    private val fallbackServer = MockWebServer()

    @BeforeEach
    fun setUp() {
        primaryServer.start()
        fallbackServer.start()
        CoverRequestPolicy.resetForTests()
    }

    @AfterEach
    fun tearDown() {
        primaryServer.shutdown()
        fallbackServer.shutdown()
        CoverRequestPolicy.resetForTests()
    }

    @Test
    fun `loadAuroraPosterSource retries fallback after primary failure`() = runTest {
        primaryServer.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))
        fallbackServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody("fallback-bytes"),
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(CoverRecoveryInterceptor())
            .build()

        val result = loadAuroraPosterSource(
            callFactory = client,
            fileSystem = FileSystem.SYSTEM,
            request = AuroraPosterRequest(
                primaryUrl = primaryServer.url("/primary.png").toString(),
                fallbackUrl = fallbackServer.url("/fallback.png").toString(),
            ),
        )

        assertTrue(result is SourceFetchResult)
        assertEquals(1, primaryServer.requestCount)
        assertEquals(1, fallbackServer.requestCount)
    }

    @Test
    fun `loadAuroraPosterSource stops after one attempt per candidate`() {
        primaryServer.enqueue(MockResponse().setResponseCode(403).setBody("primary-forbidden"))
        fallbackServer.enqueue(MockResponse().setResponseCode(403).setBody("fallback-forbidden"))

        val client = OkHttpClient.Builder()
            .addInterceptor(CoverRecoveryInterceptor())
            .build()

        assertThrows(IOException::class.java) {
            runTest {
                loadAuroraPosterSource(
                    callFactory = client,
                    fileSystem = FileSystem.SYSTEM,
                    request = AuroraPosterRequest(
                        primaryUrl = primaryServer.url("/primary.png").toString(),
                        fallbackUrl = fallbackServer.url("/fallback.png").toString(),
                    ),
                )
            }
        }

        assertEquals(1, primaryServer.requestCount)
        assertEquals(1, fallbackServer.requestCount)
    }

    @Test
    fun `loadAuroraPosterSource skips blacklisted primary host and uses fallback`() = runTest {
        val primaryHost = primaryServer.hostName
        CoverRequestPolicy.recordFailure(primaryHost)
        CoverRequestPolicy.recordFailure(primaryHost)

        primaryServer.enqueue(MockResponse().setResponseCode(200).setBody("primary-ok"))
        fallbackServer.enqueue(MockResponse().setResponseCode(200).setBody("fallback-ok"))
        val fallbackUrl = fallbackServer.url("/fallback.png")
            .newBuilder()
            .host("127.0.0.1")
            .build()

        val client = OkHttpClient.Builder()
            .addInterceptor(CoverRecoveryInterceptor())
            .build()

        val result = loadAuroraPosterSource(
            callFactory = client,
            fileSystem = FileSystem.SYSTEM,
            request = AuroraPosterRequest(
                primaryUrl = primaryServer.url("/primary.png").toString(),
                fallbackUrl = fallbackUrl.toString(),
            ),
        )

        assertTrue(result is SourceFetchResult)
        assertEquals(0, primaryServer.requestCount)
        assertEquals(1, fallbackServer.requestCount)
    }

    @Test
    fun `loadAuroraPosterSource does not blacklist primary host after transient disconnect`() = runTest {
        primaryServer.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
        )
        primaryServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody("primary-recovered"),
        )
        fallbackServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody("fallback-first"),
        )
        fallbackServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody("fallback-second"),
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(CoverRecoveryInterceptor())
            .build()

        val firstResult = loadAuroraPosterSource(
            callFactory = client,
            fileSystem = FileSystem.SYSTEM,
            request = AuroraPosterRequest(
                primaryUrl = primaryServer.url("/primary.png").toString(),
                fallbackUrl = fallbackServer.url("/fallback.png").toString(),
            ),
        )

        assertTrue(firstResult is SourceFetchResult)
        assertFalse(CoverRequestPolicy.isBlacklisted(primaryServer.hostName))

        val secondResult = loadAuroraPosterSource(
            callFactory = client,
            fileSystem = FileSystem.SYSTEM,
            request = AuroraPosterRequest(
                primaryUrl = primaryServer.url("/primary.png").toString(),
                fallbackUrl = fallbackServer.url("/fallback.png").toString(),
            ),
        )

        assertTrue(secondResult is SourceFetchResult)
        assertEquals(2, primaryServer.requestCount)
        assertEquals(1, fallbackServer.requestCount)
    }
}
