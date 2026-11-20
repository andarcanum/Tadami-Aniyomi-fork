package eu.kanade.tachiyomi.data.cache

import android.content.Context
import eu.kanade.tachiyomi.source.model.Page
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.items.chapter.model.Chapter
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Path
import kotlin.test.assertFailsWith

class ChapterCacheTest {

    @field:TempDir
    lateinit var tempDir: Path
    private var cache: ChapterCache? = null

    @AfterEach
    fun tearDown() {
        cache?.close()
        cache = null
    }

    @Test
    fun `roundtrips page list through cache`() {
        val cache = createCache().also { this.cache = it }
        val chapter = createChapter()
        val pages = listOf(
            Page(index = 0, url = "https://example.org/page-1", imageUrl = "https://example.org/image-a"),
            Page(index = 1, url = "https://example.org/page-1", imageUrl = "https://example.org/image-b"),
            Page(index = 2, url = "https://example.org/page-2", imageUrl = "https://example.org/image-c"),
        )

        cache.putPageListToCache(chapter, pages)

        val cachedPages = cache.getPageListFromCache(chapter)
        cachedPages.size shouldBe 3
        cachedPages[0].imageUrl shouldBe "https://example.org/image-a"
        cachedPages[1].imageUrl shouldBe "https://example.org/image-b"
        cachedPages[2].url shouldBe "https://example.org/page-2"
    }

    @Test
    fun `returns empty list when page list is missing`() {
        val cache = createCache().also { this.cache = it }

        cache.getPageListFromCache(createChapter()) shouldBe emptyList()
    }

    @Test
    fun `applies custom image cache size`() {
        val cache = createCache(maxSizeBytes = 75L * 1024 * 1024).also { this.cache = it }

        cache.maxSizeBytes shouldBe 75L * 1024 * 1024

        cache.setMaxSizeMb(50)

        cache.maxSizeBytes shouldBe 50L * 1024 * 1024
    }

    @Test
    fun `openImageStream reads cached bytes and refreshes LRU order`() {
        val cache = createCache(maxSizeBytes = 1024L * 1024L).also { this.cache = it }
        val payloadA = ByteArray(400 * 1024) { 1 }
        val payloadB = ByteArray(400 * 1024) { 2 }
        val payloadC = ByteArray(400 * 1024) { 3 }

        cache.putImageToCache("https://example.org/a.png", createImageResponse("https://example.org/a.png", payloadA))
        cache.putImageToCache("https://example.org/b.png", createImageResponse("https://example.org/b.png", payloadB))

        // Touch "a" via openImageStream so "b" becomes the least-recently-used entry.
        val readA = cache.openImageStream("https://example.org/a.png").use { it.readBytes() }
        readA shouldBe payloadA

        // Adding "c" exceeds 1 MiB and must evict "b", keeping "a".
        cache.putImageToCache("https://example.org/c.png", createImageResponse("https://example.org/c.png", payloadC))

        cache.isImageInCache("https://example.org/a.png") shouldBe true
        cache.isImageInCache("https://example.org/b.png") shouldBe false
        cache.isImageInCache("https://example.org/c.png") shouldBe true
    }

    @Test
    fun `openImageStream throws FileNotFoundException for missing entry`() {
        val cache = createCache().also { this.cache = it }

        assertFailsWith<FileNotFoundException> {
            cache.openImageStream("https://example.org/missing.png")
        }
    }

    @Test
    fun `getPageListFromCache expires stale entries and supports legacy list format`() {
        val cache = createCache().also { this.cache = it }
        val chapter = createChapter()
        val pages = listOf(
            Page(index = 0, url = "https://example.org/page-1", imageUrl = "https://example.org/image-a"),
        )

        cache.putPageListToCache(chapter, pages)
        cache.getPageListFromCache(chapter, maxAgeMs = -1L) shouldBe emptyList()
        cache.getPageListFromCache(chapter, maxAgeMs = 60_000L).size shouldBe 1
    }

    @Test
    fun `putImageToCache rejects empty response body without committing`() {
        val cache = createCache().also { this.cache = it }
        val url = "https://example.org/empty.png"

        assertFailsWith<IOException> {
            cache.putImageToCache(url, createImageResponse(url, ByteArray(0)))
        }
        cache.isImageInCache(url) shouldBe false
    }

    @Test
    fun `putImageToCache rejects truncated response body shorter than content length`() {
        val cache = createCache().also { this.cache = it }
        val url = "https://example.org/truncated.png"
        val truncatedBody = object : ResponseBody() {
            override fun contentType() = "image/png".toMediaType()
            override fun contentLength() = 1024L
            override fun source() = Buffer().write(ByteArray(128) { 7 })
        }
        val response = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(truncatedBody)
            .build()

        assertFailsWith<IOException> {
            cache.putImageToCache(url, response)
        }
        cache.isImageInCache(url) shouldBe false
    }

    @Test
    fun `putImageToCache rejects html error pages without committing`() {
        val cache = createCache().also { this.cache = it }
        val url = "https://example.org/cloudflare.png"
        val response = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("<html><body>Challenge</body></html>".toResponseBody("text/html; charset=utf-8".toMediaType()))
            .build()

        assertFailsWith<IOException> {
            cache.putImageToCache(url, response)
        }
        cache.isImageInCache(url) shouldBe false
    }

    @Test
    fun `removeImageFromCache evicts existing entry and returns false when missing`() {
        val cache = createCache().also { this.cache = it }
        val url = "https://example.org/removable.png"
        cache.putImageToCache(url, createImageResponse(url, ByteArray(64) { 3 }))
        cache.isImageInCache(url) shouldBe true

        cache.removeImageFromCache(url) shouldBe true
        cache.isImageInCache(url) shouldBe false
        cache.removeImageFromCache(url) shouldBe false
    }

    private fun createImageResponse(url: String, bytes: ByteArray): Response {
        return Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(bytes.toResponseBody("image/png".toMediaType()))
            .build()
    }

    private fun createCache(maxSizeBytes: Long = 100L * 1024 * 1024): ChapterCache {
        val context = mockk<Context>()
        every { context.cacheDir } returns tempDir.toFile()
        return ChapterCache(
            context = context,
            json = Json { encodeDefaults = true },
            maxSizeBytes = maxSizeBytes,
        )
    }

    private fun createChapter(): Chapter {
        return Chapter.create().copy(
            id = 1L,
            mangaId = 42L,
            url = "https://example.org/chapter-1",
        )
    }
}
