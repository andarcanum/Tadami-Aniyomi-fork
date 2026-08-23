package eu.kanade.tachiyomi.data.coil

import coil3.Extras
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.CachePolicy
import coil3.request.Options
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import eu.kanade.tachiyomi.network.interceptor.CoverRecoveryInterceptor
import eu.kanade.tachiyomi.network.interceptor.CoverRequestPolicy
import eu.kanade.tachiyomi.network.withCoverTimeouts
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.FileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.MangaCover
import java.io.File
import java.util.concurrent.TimeUnit

class CoverNetworkOptimizationTest {

    private lateinit var server: MockWebServer
    private val imageLoader = mockk<ImageLoader>(relaxed = true) {
        every { diskCache } returns null
    }

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        CoverRequestPolicy.resetForTests()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        CoverRequestPolicy.resetForTests()
    }

    private fun coverOptions(): Options = Options(
        context = mockk(relaxed = true),
        size = Size.ORIGINAL,
        scale = Scale.FIT,
        precision = Precision.EXACT,
        diskCacheKey = "cover-opt-test",
        fileSystem = FileSystem.SYSTEM,
        memoryCachePolicy = CachePolicy.ENABLED,
        diskCachePolicy = CachePolicy.ENABLED,
        networkCachePolicy = CachePolicy.ENABLED,
        extras = Extras.EMPTY,
    )

    private fun mangaCoverFetcher(
        options: Options,
        callFactory: Call.Factory,
        url: String,
    ): MangaCoverFetcher = MangaCoverFetcher(
        url = url,
        isLibraryManga = false,
        options = options,
        coverFileProvider = { null },
        customCoverFileLazy = lazy { java.io.File("nonexistent-custom-cover") },
        diskCacheKeyProvider = { effectiveUrl, _ -> "manga;1;$effectiveUrl;0" },
        sourceLazy = lazy { null },
        callFactoryLazy = lazy { callFactory },
        imageLoader = imageLoader,
    )

    private suspend fun fetchMangaCover(callFactory: Call.Factory): SourceFetchResult {
        val result = mangaCoverFetcher(
            options = coverOptions(),
            callFactory = callFactory,
            url = server.url("/cover.png").toString(),
        ).fetch()
        return result as SourceFetchResult
    }

    @Test
    fun `manga cover request keeps okhttp cache eligible for conditional gets`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setHeader("Cache-Control", "max-age=60")
                .setBody("cover-bytes"),
        )
        val client = OkHttpClient()

        val result = fetchMangaCover(client)

        assertEquals(DataSource.NETWORK, result.dataSource)
        val recorded = server.takeRequest()
        assertFalse(
            recorded.headers.names().contains("Cache-Control"),
            "online cover requests must stay eligible for OkHttp conditional gets",
        )
    }

    @Test
    fun `manga cover fetch does not retry when host is blacklisted`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor(CoverRecoveryInterceptor())
            .build()
        // Two consecutive recoverable failures put the host on the blacklist.
        repeat(2) {
            server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))
            runCatching { fetchMangaCover(client) }
            server.takeRequest() // consume: keeps per-iteration counts aligned
        }
        assertTrue(CoverRequestPolicy.isBlacklisted(server.hostName))
        server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))

        runCatching { fetchMangaCover(client) }

        assertEquals(
            0,
            server.requestCount - 2,
            "blacklisted hosts must be skipped without a network attempt",
        )
    }

    @Test
    fun `anime cover fetch retries transient network failures once`() = runTest {
        server.enqueue(
            MockResponse()
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START)
                .setBody("dropped"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody("anime-cover-bytes"),
        )

        val animeCover = tachiyomi.domain.entries.anime.model.AnimeCover(
            animeId = 7L,
            sourceId = 42L,
            isAnimeFavorite = false,
            url = server.url("/anime-cover.png").toString(),
            lastModified = 0L,
        )
        val result = AnimeImageFetcher(
            url = animeCover.url,
            isLibraryAnime = false,
            options = coverOptions(),
            coverFileProvider = { null },
            customCoverFileLazy = lazy { File("nonexistent-anime-custom-cover") },
            diskCacheKeyProvider = { effectiveUrl, _ -> "anime;7;$effectiveUrl;0" },
            sourceLazy = lazy { null },
            callFactoryLazy = lazy { OkHttpClient() },
            imageLoader = imageLoader,
        ).fetch()

        assertTrue(result is SourceFetchResult)
        assertEquals(2, server.requestCount, "transient IO failure must be retried exactly once")
    }

    @Test
    fun `cover client applies short timeouts instead of the default two minute call timeout`() {
        val client = OkHttpClient.Builder()
            .callTimeout(120, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
            .withCoverTimeouts()
        assertEquals(25_000, client.callTimeoutMillis)
        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(20_000, client.readTimeoutMillis)
    }
}
