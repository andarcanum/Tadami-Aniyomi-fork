package eu.kanade.tachiyomi.data.coil

import android.content.Context
import coil3.Extras
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.CachePolicy
import coil3.request.Options
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import eu.kanade.tachiyomi.network.interceptor.CoverRecoveryInterceptor
import eu.kanade.tachiyomi.network.interceptor.CoverRequestPolicy
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

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

    @Test
    fun `loadAuroraPosterSource converts IDN referer to punycode`() = runTest {
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
                primaryUrl = null,
                fallbackUrl = fallbackServer.url("/fallback.png").toString(),
                refererUrl = "https://ранобэ.рф",
            ),
        )

        assertTrue(result is SourceFetchResult)
        val referer = fallbackServer.takeRequest().getHeader("Referer")
        assertFalse(referer!!.contains("ранобэ"))
        assertTrue(referer.startsWith("https://xn--"))
    }

    @Test
    fun `fetch with network disabled returns disk cache hit and fails fast on miss without network`(
        @TempDir tempDir: Path,
    ) {
        val url = "https://fixture.invalid/poster.jpg"
        val diskCache = DiskCache.Builder()
            .directory(tempDir.resolve("coil_disk").toOkioPath())
            .maxSizeBytes(1024L * 1024L)
            .build()
        try {
            val cacheKey = "aurora-poster;$url;"
            val editor = diskCache.openEditor(cacheKey)!!
            diskCache.fileSystem.write(editor.data) {
                write("cached-poster-bytes".toByteArray())
            }
            editor.commit()

            val networkCalls = AtomicInteger(0)
            val failingFactory = Call.Factory { _: Request ->
                networkCalls.incrementAndGet()
                error("Network must not be called when networkCachePolicy is DISABLED")
            }
            val imageLoader = mockk<ImageLoader>(relaxed = true)
            every { imageLoader.diskCache } returns diskCache
            val options = Options(
                context = mockk<Context>(relaxed = true),
                size = Size.ORIGINAL,
                scale = Scale.FIT,
                precision = Precision.EXACT,
                diskCacheKey = null,
                fileSystem = FileSystem.SYSTEM,
                memoryCachePolicy = CachePolicy.ENABLED,
                diskCachePolicy = CachePolicy.READ_ONLY,
                networkCachePolicy = CachePolicy.DISABLED,
                extras = Extras.EMPTY,
            )

            runTest {
                val hit = AuroraPosterRequestFetcher(
                    data = AuroraPosterRequest(primaryUrl = url),
                    options = options,
                    callFactoryLazy = lazy { failingFactory },
                    imageLoader = imageLoader,
                ).fetch() as SourceFetchResult
                assertEquals(DataSource.DISK, hit.dataSource)
                assertEquals("cached-poster-bytes", hit.source.use { it.source().readUtf8() })
            }

            assertThrows(IOException::class.java) {
                runTest {
                    AuroraPosterRequestFetcher(
                        data = AuroraPosterRequest(primaryUrl = "https://fixture.invalid/missing.jpg"),
                        options = options,
                        callFactoryLazy = lazy { failingFactory },
                        imageLoader = imageLoader,
                    ).fetch()
                }
            }
            assertEquals(0, networkCalls.get())
        } finally {
            diskCache.shutdown()
        }
    }

    @Test
    fun `loadAuroraPosterSource retries single-url transient disconnect once and succeeds`() = runTest {
        primaryServer.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
        )
        primaryServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody("recovered-after-disconnect"),
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(CoverRecoveryInterceptor())
            .build()

        val result = loadAuroraPosterSource(
            callFactory = client,
            fileSystem = FileSystem.SYSTEM,
            request = AuroraPosterRequest(
                primaryUrl = primaryServer.url("/single-poster.png").toString(),
            ),
            retryDelayMs = 0L,
        ) as SourceFetchResult

        assertEquals(2, primaryServer.requestCount)
        assertEquals("recovered-after-disconnect", result.source.use { it.source().readUtf8() })
        assertFalse(CoverRequestPolicy.isBlacklisted(primaryServer.hostName))
    }
}
