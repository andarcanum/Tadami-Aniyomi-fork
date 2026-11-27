package eu.kanade.tachiyomi.ui.reels

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.ui.reels.player.isReelImageUrl
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReelsOfflineStoreImplTest {

    private lateinit var app: Application
    private lateinit var server: MockWebServer
    private lateinit var dir: File

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        dir = File(app.filesDir, "reels_offline")
        dir.deleteRecursively()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    private fun store(maxBytes: Long = 1_000_000L) = ReelsOfflineStoreImpl(
        app = app,
        client = OkHttpClient(),
        ioDispatcher = Dispatchers.IO,
        maxBytes = maxBytes,
        minFreeBytes = 0L,
    )

    private fun item(id: String, url: String) = ShortVideoItem(
        id = id,
        videoUrl = url,
        posterUrl = "https://example.com/poster.jpg",
    )

    private fun files(): List<File> = dir.listFiles().orEmpty().toList()

    @Test
    fun `parallel downloads cannot both pass the quota snapshot`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("AAAAAA"))
        server.enqueue(MockResponse().setBody("BBBBBB"))
        val store = store(maxBytes = 10L)

        // Two 6-byte copies under a 10-byte quota: the snapshot race used to let both pass.
        val results = listOf(
            async(Dispatchers.IO) {
                store.download(item("a", server.url("/a.mp4").toString()), 7L)
            },
            async(Dispatchers.IO) {
                store.download(item("b", server.url("/b.mp4").toString()), 7L)
            },
        ).map { it.await() }

        results.count { it == ReelsOfflineStore.DownloadResult.SAVED } shouldBe 1
        results.count { it == ReelsOfflineStore.DownloadResult.QUOTA } shouldBe 1
        store.usedBytes() shouldBe 6L
        files().filter { it.name.endsWith(".part") }.shouldBeEmpty()
    }

    @Test
    fun `chunked body stops at the quota instead of after the whole stream`(): Unit = runBlocking {
        server.enqueue(MockResponse().setChunkedBody("x".repeat(100), 7))

        val result = store(maxBytes = 10L)
            .download(item("big", server.url("/stream").toString()), 1L)

        result shouldBe ReelsOfflineStore.DownloadResult.QUOTA
        files().shouldBeEmpty()
    }

    @Test
    fun `a transport failure leaves neither a part nor a final file`(): Unit = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody("x".repeat(4096))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        val result = store().download(item("broken", server.url("/broken.mp4").toString()), 1L)

        result shouldBe ReelsOfflineStore.DownloadResult.NETWORK
        files().shouldBeEmpty()
    }

    @Test
    fun `a successful copy is never replaced by a second writer`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("first"))
        val store = store()

        store.download(item("keep", server.url("/keep.mp4").toString()), 1L) shouldBe
            ReelsOfflineStore.DownloadResult.SAVED
        store.download(item("keep", server.url("/keep.mp4").toString()), 1L) shouldBe
            ReelsOfflineStore.DownloadResult.SAVED

        server.requestCount shouldBe 1
        store.localUrl(1L, "keep")!! shouldEndWith ".mp4"
    }

    @Test
    fun `opaque still URL is stored with its response image type`(): Unit = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "image/jpeg")
                .setBody("jpeg-bytes"),
        )
        val store = store()

        store.download(item("opaque", server.url("/media/12345").toString()), 9L) shouldBe
            ReelsOfflineStore.DownloadResult.SAVED

        val local = store.localUrl(9L, "opaque")!!
        local shouldEndWith ".jpg"
        // Robolectric on Windows encodes the path into the file URI authority, so a real
        // lastPathSegment is unavailable there; classify the stored name as on Android.
        isReelImageUrl("file:///data/user/0/eu.kanade/files/reels_offline/${files().single().name}") shouldBe true
        store.storedPairs() shouldBe setOf(9L to "opaque")
    }

    @Test
    fun `extension from a query-bearing URL is preserved`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("video"))
        val store = store()

        store.download(item("signed", server.url("/clip.mp4?token=abc").toString()), 1L) shouldBe
            ReelsOfflineStore.DownloadResult.SAVED

        store.localUrl(1L, "signed")!! shouldEndWith ".mp4"
    }

    @Test
    fun `adaptive DASH manifests are rejected before any request`(): Unit = runBlocking {
        val store = store()

        store.download(item("dash", server.url("/manifest.mpd").toString()), 1L) shouldBe
            ReelsOfflineStore.DownloadResult.UNSUPPORTED

        server.requestCount shouldBe 0
        store.usedBytes() shouldBe 0L
    }

    @Test
    fun `clear HLS m3u8 playlist stitches segments into offline store`(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                #EXTM3U
                #EXTINF:2.0,
                seg1.ts
                #EXTINF:2.0,
                seg2.ts
                """.trimIndent(),
            ),
        )
        server.enqueue(MockResponse().setBody("partA-"))
        server.enqueue(MockResponse().setBody("partB"))
        val store = store()

        store.download(item("hls", server.url("/playlist.m3u8").toString()), 1L) shouldBe
            ReelsOfflineStore.DownloadResult.SAVED

        store.isStored(1L, "hls") shouldBe true
        store.usedBytes() shouldBe 11L
    }

    @Test
    fun `an extensionless adaptive response is rejected after its content type`(): Unit = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/vnd.apple.mpegurl")
                .setBody("#EXTM3U"),
        )

        val result = store().download(item("hls", server.url("/stream").toString()), 1L)

        result shouldBe ReelsOfflineStore.DownloadResult.UNSUPPORTED
        files().shouldBeEmpty()
    }

    @Test
    fun `custom headers are propagated and fallback referer applies only when referer missing`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("first"))
        server.enqueue(MockResponse().setBody("second"))
        val store = store()

        val headersWithExplicitReferer = mapOf(
            "Cookie" to "session=xyz",
            "User-Agent" to "CustomUA/1.0",
            "Referer" to "https://custom.example.com/stream",
        )
        val item1 = ShortVideoItem(
            id = "with-ref",
            videoUrl = server.url("/stream1.mp4").toString(),
            webUrl = "https://origin.example.com/watch",
            posterUrl = "https://example.com/poster.jpg",
        )
        store.download(item1, 1L, headersWithExplicitReferer) shouldBe ReelsOfflineStore.DownloadResult.SAVED
        val recorded1 = server.takeRequest()
        recorded1.getHeader("Cookie") shouldBe "session=xyz"
        recorded1.getHeader("User-Agent") shouldBe "CustomUA/1.0"
        recorded1.getHeader("Referer") shouldBe "https://custom.example.com/stream"

        val headersWithoutReferer = mapOf(
            "Authorization" to "Bearer 123",
        )
        val item2 = ShortVideoItem(
            id = "fallback-ref",
            videoUrl = server.url("/stream2.mp4").toString(),
            webUrl = "https://origin.example.com/watch",
            posterUrl = "https://example.com/poster.jpg",
        )
        store.download(item2, 1L, headersWithoutReferer) shouldBe ReelsOfflineStore.DownloadResult.SAVED
        val recorded2 = server.takeRequest()
        recorded2.getHeader("Authorization") shouldBe "Bearer 123"
        recorded2.getHeader("Referer") shouldBe "https://origin.example.com/"
    }
}
