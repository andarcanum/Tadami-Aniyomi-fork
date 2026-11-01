package eu.kanade.tachiyomi.ui.reels

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.animesource.model.FeedVideoQualityVariant
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReelsQualityDownloadTest {

    private lateinit var app: Application
    private lateinit var server: MockWebServer
    private lateinit var root: File

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        root = File(app.filesDir, "reels_offline")
        root.deleteRecursively()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        root.deleteRecursively()
    }

    private fun store() = ReelsOfflineStoreImpl(
        app = app,
        client = OkHttpClient(),
        ioDispatcher = Dispatchers.IO,
        maxBytes = 1_000_000L,
        minFreeBytes = 0L,
    )

    private fun variant() = FeedVideoQualityVariant(
        id = "alt:7",
        width = 720,
        height = 1280,
        codec = "h264",
        sizeBytes = 6L,
        isSource = false,
    )

    private fun snapshot(item: ShortVideoItem) = ReelsQualityDownloadSnapshot(
        owner = ReelsQualityOwner("token", 7L, "42"),
        item = item,
        variant = variant(),
        cacheIdentity = "unique-7",
        headers = emptyMap(),
    )

    private fun item() = ShortVideoItem(
        id = "tdlib:-100123/100",
        videoUrl = server.url("/video.mp4").toString(),
        posterUrl = "https://example.com/poster.jpg",
    )

    @Test
    fun `selected download stores sidecar and clearAll removes it`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("ABCDEF"))
        val item = item()
        val store = store()

        store.downloadSelected(snapshot(item)) shouldBe ReelsOfflineStore.DownloadResult.SAVED

        val quality = store.storedQuality(7L, item.id)
        quality shouldNotBe null
        quality!!.variant shouldBe variant()
        val sidecarBytes = qualityFile(7L, item.id).length()
        store.usedBytes() shouldBe 6L + sidecarBytes

        store.clearAll() shouldBe 6L + sidecarBytes
        store.storedQuality(7L, item.id) shouldBe null
        store.storedPairs().isEmpty() shouldBe true
    }

    @Test
    fun `deleteBySource removes the metadata subtree and counts media copies`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("ABCDEF"))
        val item = item()
        val store = store()
        store.downloadSelected(snapshot(item))

        store.deleteBySource(7L) shouldBe 1
        store.storedQuality(7L, item.id) shouldBe null
        store.storedPairs().isEmpty() shouldBe true
    }

    @Test
    fun `existing legacy copy is not relabeled by a later quality download`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("ABCDEF"))
        val item = item()
        val store = store()
        store.download(item, 7L) shouldBe ReelsOfflineStore.DownloadResult.SAVED

        store.downloadSelected(snapshot(item)) shouldBe ReelsOfflineStore.DownloadResult.SAVED
        store.storedQuality(7L, item.id) shouldBe null
    }

    @Test
    fun `changed media invalidates the stored quality`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("ABCDEF"))
        val item = item()
        val store = store()
        store.downloadSelected(snapshot(item))

        val media = root.listFiles().orEmpty().first { it.isFile && it.name.endsWith(".mp4") }
        media.appendText("X")

        store.storedQuality(7L, item.id) shouldBe null
    }

    @Test
    fun `corrupt sidecar is unknown rather than a guessed quality`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("ABCDEF"))
        val item = item()
        val store = store()
        store.downloadSelected(snapshot(item))

        qualityFile(7L, item.id).writeText("{not-json")
        store.storedQuality(7L, item.id) shouldBe null
    }

    private fun qualityFile(sourceId: Long, itemId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(itemId.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(root, "quality/$sourceId/$digest.json")
    }
}
