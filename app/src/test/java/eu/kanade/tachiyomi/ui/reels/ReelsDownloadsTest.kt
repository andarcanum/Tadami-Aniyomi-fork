package eu.kanade.tachiyomi.ui.reels

import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class ReelsDownloadsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = MockWebServer().apply { start() }
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        server.shutdown()
    }

    @Test
    fun `success writes the complete body to the supplied destination`(): Unit = runBlocking {
        val bytes = ByteArray(150_000) { (it % 251).toByte() }
        server.enqueue(mediaResponse(bytes))
        val file = temporaryFolder.newFile("export.mp4")
        val destination = Uri.fromFile(file)
        val progress = mutableListOf<Pair<Long, Long?>>()

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()),
            7L,
            destination = destination,
            onProgress = { copied, total -> progress += copied to total },
        )

        assertEquals(ReelsDownloadResult.Saved(destination, 150_000L), result)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(150_000L to 150_000L, progress.last())
        assertTrue(progress.zipWithNext().all { (first, second) -> first.first < second.first })
        assertTrue(progress.size <= 4)
    }

    @Test
    fun `chunked media saves real bytes with an unknown total`(): Unit = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "video/webm")
                .setChunkedBody("webm-bytes", 3),
        )
        val file = temporaryFolder.newFile("export.webm")
        val progress = mutableListOf<Pair<Long, Long?>>()

        val result = downloads().download(
            item(server.url("/stream").toString()),
            7L,
            destination = Uri.fromFile(file),
            onProgress = { copied, total -> progress += copied to total },
        )

        assertEquals(ReelsDownloadResult.Saved(Uri.fromFile(file), 10L), result)
        assertArrayEquals("webm-bytes".toByteArray(), file.readBytes())
        assertEquals(10L to null, progress.last())
    }

    @Test
    fun `source headers and the supplied clients cookies reach the request`(): Unit = runBlocking {
        client = client.newBuilder().cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit

            override fun loadForRequest(url: HttpUrl): List<Cookie> = listOf(
                Cookie.Builder().name("session").value("kept").domain(url.host).build(),
            )
        }).build()
        server.enqueue(mediaResponse("video".toByteArray()))
        val file = temporaryFolder.newFile("export.mp4")

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()).copy(webUrl = "https://watch.example/post/123"),
            7L,
            headers = mapOf(
                "X-Source" to "extension-value",
                "User-Agent" to "Extension Agent",
                "referer" to "https://explicit.example/watch/",
            ),
            destination = Uri.fromFile(file),
        )

        assertTrue(result is ReelsDownloadResult.Saved)
        val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("extension-value", request.getHeader("X-Source"))
        assertEquals("Extension Agent", request.getHeader("User-Agent"))
        assertEquals("https://explicit.example/watch/", request.getHeader("Referer"))
        assertEquals("session=kept", request.getHeader("Cookie"))
    }

    @Test
    fun `missing Referer falls back to the watch page origin without its query`(): Unit = runBlocking {
        server.enqueue(mediaResponse("video".toByteArray()))
        val file = temporaryFolder.newFile("export.mp4")

        val result = downloads().download(
            item(server.url("/clip.mp4").toString())
                .copy(webUrl = "https://watch.example:8443/post/123?secret=not-a-header"),
            7L,
            headers = mapOf("X-Source" to "kept"),
            destination = Uri.fromFile(file),
        )

        assertTrue(result is ReelsDownloadResult.Saved)
        val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("https://watch.example:8443/", request.getHeader("Referer"))
        assertEquals("kept", request.getHeader("X-Source"))
    }

    @Test
    @Config(sdk = [28])
    fun `older Android requires a caller chosen document before requesting media`(): Unit = runBlocking {
        val result = downloads().download(item(server.url("/clip.mp4").toString()), 7L)

        assertEquals(ReelsDownloadResult.Failed, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    @Config(sdk = [28])
    fun `a local favorite exports its bytes without touching the source`(): Unit = runBlocking {
        val source = temporaryFolder.newFile("local.webm").apply { writeBytes("webm-bytes".toByteArray()) }
        val file = temporaryFolder.newFile("export.webm")
        val localItem = item(source.toURI().toString())

        val result = downloads().download(localItem, 7L, destination = Uri.fromFile(file))

        assertEquals(ReelsDownloadResult.Saved(Uri.fromFile(file), 10L), result)
        assertArrayEquals("webm-bytes".toByteArray(), file.readBytes())
        assertArrayEquals("webm-bytes".toByteArray(), source.readBytes())
        assertTrue(downloads().fileName(localItem, 7L).endsWith(".webm"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `filename removes traversal and unsafe characters and bounds long titles`() {
        val title = "../\\bad:name*?<>|\n" + '"' + "界".repeat(300)
        val video = item("https://cdn.example/clip.WEBM?token=../unsafe").copy(title = title, id = "../../item")

        val name = downloads().fileName(video, Long.MIN_VALUE)

        assertTrue(name.endsWith(".webm"))
        assertFalse(name.contains(".."))
        assertTrue(name.none { it in "/\\:*?<>|" || it == '"' || it.isISOControl() })
        assertTrue(name.toByteArray().size <= 255)
        assertEquals(name, downloads().fileName(video, Long.MIN_VALUE))
    }

    @Test
    fun `filename has a safe fallback for missing titles and separates sources and colliding item ids`() {
        val first = item("https://cdn.example/clip.mp4").copy(id = "Aa")
        val second = first.copy(id = "BB") // Same Java String hash; must remain distinct exports.
        val name = downloads().fileName(first, 7L)

        assertTrue(name.substringBeforeLast('.').isNotBlank())
        assertTrue(name.endsWith(".mp4"))
        assertNotEquals(name, downloads().fileName(second, 7L))
        assertNotEquals(name, downloads().fileName(first, 8L))
        assertTrue(downloads().fileName(first.copy(title = "  "), 7L).endsWith(".mp4"))
    }

    @Test
    fun `native Downloads publishes only the complete file with its image MIME and extension`(): Unit = runBlocking {
        val provider = downloadsProvider()
        val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 3, 4)
        server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody(Buffer().write(bytes)))

        val result = downloads().download(item(server.url("/opaque").toString()), 7L)

        assertTrue(result is ReelsDownloadResult.Saved)
        val saved = result as ReelsDownloadResult.Saved
        assertEquals(6L, saved.bytes)
        assertArrayEquals(bytes, provider.fileFor(saved.uri).readBytes())
        val row = requireNotNull(provider.rows[saved.uri])
        assertEquals("image/jpeg", row.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertTrue(row.getAsString(MediaStore.MediaColumns.DISPLAY_NAME).endsWith(".jpg"))
        assertEquals(Environment.DIRECTORY_DOWNLOADS, row.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals(0, row.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        assertEquals(listOf(1), provider.pendingWhenOpened)
        assertEquals(listOf(saved.uri), provider.published)
        assertArrayEquals(bytes, provider.bytesAtPublish.single())
        assertTrue(provider.deleted.isEmpty())
    }

    @Test
    fun `a native publishing failure removes only the entry created by this attempt`(): Unit = runBlocking {
        val provider = downloadsProvider()
        val existing = provider.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues())
        provider.fileFor(existing).writeBytes("keep".toByteArray())
        provider.failPublishing = true
        server.enqueue(mediaResponse("complete video".toByteArray()))

        val result = downloads().download(item(server.url("/clip.mp4").toString()), 7L)

        assertEquals(ReelsDownloadResult.Failed, result)
        assertEquals(setOf(existing), provider.rows.keys)
        assertArrayEquals("keep".toByteArray(), provider.fileFor(existing).readBytes())
        assertEquals(1, provider.deleted.size)
        assertNotEquals(existing, provider.deleted.single())
        assertTrue(provider.published.isEmpty())
    }

    @Test
    @Config(sdk = [28])
    fun `a caller chosen document works on older Android without native publication`(): Unit = runBlocking {
        val provider = downloadsProvider()
        val document = provider.newDocument()
        server.enqueue(mediaResponse("document bytes".toByteArray()))

        val result = downloads().download(item(server.url("/clip.mp4").toString()), 7L, destination = document)

        assertEquals(ReelsDownloadResult.Saved(document, 14L), result)
        assertArrayEquals("document bytes".toByteArray(), provider.fileFor(document).readBytes())
        assertTrue(provider.published.isEmpty())
        assertTrue(provider.deleted.isEmpty())
    }

    @Test
    fun `a failed caller chosen document is deleted through DocumentsContract`(): Unit = runBlocking {
        val provider = downloadsProvider()
        val document = provider.newDocument()
        server.enqueue(MockResponse().setResponseCode(403))

        val result = downloads().download(item(server.url("/clip.mp4").toString()), 7L, destination = document)

        assertEquals(ReelsDownloadResult.Failed, result)
        assertEquals(listOf(document), provider.deletedDocuments)
        assertFalse(provider.fileFor(document).exists())
        assertTrue(provider.rows.isEmpty())
    }

    @Test
    fun `adaptive URLs are rejected before requesting and the new destination is removed`(): Unit = runBlocking {
        for ((index, path) in listOf("/master.M3U8?token=signed", "/manifest.mpd").withIndex()) {
            val file = temporaryFolder.newFile("adaptive-" + index + ".mp4")

            val result = downloads().download(item(server.url(path).toString()), 7L, destination = Uri.fromFile(file))

            assertEquals(ReelsDownloadResult.Unsupported, result)
            assertFalse(file.exists())
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `extensionless manifests are rejected by MIME without leaving a file`(): Unit = runBlocking {
        val types = listOf("application/vnd.apple.mpegurl", "application/dash+xml", "audio/x-mpegurl")
        for ((index, type) in types.withIndex()) {
            server.enqueue(MockResponse().setHeader("Content-Type", type).setBody("manifest"))
            val file = temporaryFolder.newFile("manifest-" + index + ".mp4")

            val result = downloads().download(
                item(server.url("/opaque").toString()),
                7L,
                destination = Uri.fromFile(file),
            )

            assertEquals(ReelsDownloadResult.Unsupported, result)
            assertFalse(file.exists())
        }
    }

    @Test
    fun `nonmedia MIME is rejected even when the URL has a media extension`(): Unit = runBlocking {
        for ((index, type) in listOf("text/html", "application/json", "text/plain").withIndex()) {
            server.enqueue(MockResponse().setHeader("Content-Type", type).setBody("error page"))
            val file = temporaryFolder.newFile("error-" + index + ".mp4")

            val result = downloads().download(
                item(server.url("/clip.mp4").toString()),
                7L,
                destination = Uri.fromFile(file),
            )

            assertEquals(ReelsDownloadResult.Failed, result)
            assertFalse(file.exists())
        }
    }

    @Test
    fun `HTML and JSON disguised as video are failed payloads`(): Unit = runBlocking {
        val pages = listOf(" \n<html>login</html>", "\uFEFF" + """{"error":true}""", "[1,2]")
        for ((index, page) in pages.withIndex()) {
            server.enqueue(mediaResponse(page.toByteArray()))
            val file = temporaryFolder.newFile("disguised-" + index + ".mp4")

            val result = downloads().download(
                item(server.url("/clip.mp4").toString()),
                7L,
                destination = Uri.fromFile(file),
            )

            assertEquals(ReelsDownloadResult.Failed, result)
            assertFalse(file.exists())
        }
    }

    @Test
    fun `an empty successful response is failure and removes the destination`(): Unit = runBlocking {
        server.enqueue(mediaResponse(byteArrayOf()))
        val file = temporaryFolder.newFile("empty.mp4")

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()),
            7L,
            destination = Uri.fromFile(file),
        )

        assertEquals(ReelsDownloadResult.Failed, result)
        assertFalse(file.exists())
    }

    @Test
    fun `a truncated transfer cannot return Saved and removes its partial file`(): Unit = runBlocking {
        server.enqueue(
            mediaResponse(ByteArray(128 * 1024))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        val file = temporaryFolder.newFile("truncated.mp4")

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()),
            7L,
            destination = Uri.fromFile(file),
        )

        assertEquals(ReelsDownloadResult.Failed, result)
        assertFalse(file.exists())
    }

    @Test
    fun `a partial HTTP range is not mistaken for a complete download`(): Unit = runBlocking {
        server.enqueue(
            mediaResponse("partial".toByteArray())
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-6/100"),
        )
        val file = temporaryFolder.newFile("range.mp4")

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()),
            7L,
            destination = Uri.fromFile(file),
        )

        assertEquals(ReelsDownloadResult.Failed, result)
        assertFalse(file.exists())
    }

    @Test
    fun `a failed native transfer removes only its pending entry`(): Unit = runBlocking {
        val provider = downloadsProvider()
        val existing = provider.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues())
        provider.fileFor(existing).writeBytes("keep".toByteArray())
        server.enqueue(
            mediaResponse(ByteArray(128 * 1024))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        val result = downloads().download(item(server.url("/clip.mp4").toString()), 7L)

        assertEquals(ReelsDownloadResult.Failed, result)
        assertEquals(setOf(existing), provider.rows.keys)
        assertArrayEquals("keep".toByteArray(), provider.fileFor(existing).readBytes())
        assertEquals(1, provider.deleted.size)
        assertNotEquals(existing, provider.deleted.single())
        assertTrue(provider.published.isEmpty())
    }

    @Test
    fun `cancellation while a stalled response body closes the call and drops partial bytes`(): Unit = runBlocking {
        server.enqueue(mediaResponse(ByteArray(128 * 1024)).throttleBody(64L * 1024, 3, TimeUnit.SECONDS))
        val file = temporaryFolder.newFile("cancelled.mp4")
        val started = CompletableDeferred<Unit>()
        val download = async(Dispatchers.IO) {
            downloads().download(
                item(server.url("/clip.mp4").toString()),
                7L,
                destination = Uri.fromFile(file),
                onProgress = { _, _ -> started.complete(Unit) },
            )
        }

        try {
            withTimeout(5_000) { started.await() }
            withTimeout(2_000) { download.cancelAndJoin() }
            assertTrue(download.isCancelled)
            assertFalse(file.exists())
        } finally {
            download.cancelAndJoin()
        }
    }

    @Test
    fun `cancellation before headers does not wait for the client read timeout`(): Unit = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val file = temporaryFolder.newFile("no-response.mp4")
        val download = async(Dispatchers.IO) {
            downloads().download(item(server.url("/clip.mp4").toString()), 7L, destination = Uri.fromFile(file))
        }

        try {
            requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            withTimeout(2_000) { download.cancelAndJoin() }
            assertTrue(download.isCancelled)
            assertFalse(file.exists())
        } finally {
            download.cancelAndJoin()
        }
    }

    @Test
    fun `cancellation from progress is propagated without publishing the native entry`(): Unit = runBlocking {
        val provider = downloadsProvider()
        server.enqueue(mediaResponse(ByteArray(128 * 1024)))

        try {
            downloads().download(
                item(server.url("/clip.mp4").toString()),
                7L,
                onProgress = { _, _ -> throw CancellationException("caller cancelled") },
            )
            fail("Cancellation must not become Failed or Saved")
        } catch (_: CancellationException) {
            assertTrue(provider.rows.isEmpty())
            assertEquals(1, provider.deleted.size)
            assertTrue(provider.published.isEmpty())
        }
    }

    @Test
    fun `native local export keeps its media extension and MIME`(): Unit = runBlocking {
        val provider = downloadsProvider()
        val source = temporaryFolder.newFile("favorite.webm").apply { writeBytes("webm-bytes".toByteArray()) }

        val result = downloads().download(item(source.toURI().toString()), 7L)

        assertTrue(result is ReelsDownloadResult.Saved)
        val saved = result as ReelsDownloadResult.Saved
        val row = requireNotNull(provider.rows[saved.uri])
        assertTrue(row.getAsString(MediaStore.MediaColumns.DISPLAY_NAME).endsWith(".webm"))
        assertEquals("video/webm", row.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertArrayEquals("webm-bytes".toByteArray(), provider.fileFor(saved.uri).readBytes())
        assertArrayEquals("webm-bytes".toByteArray(), source.readBytes())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `generic binary response preserves the signed URLs media extension`(): Unit = runBlocking {
        val provider = downloadsProvider()
        server.enqueue(MockResponse().setHeader("Content-Type", "application/octet-stream").setBody("webm-bytes"))

        val result = downloads().download(item(server.url("/clip.webm?token=signed").toString()), 7L)

        assertTrue(result is ReelsDownloadResult.Saved)
        val saved = result as ReelsDownloadResult.Saved
        val row = requireNotNull(provider.rows[saved.uri])
        assertTrue(row.getAsString(MediaStore.MediaColumns.DISPLAY_NAME).endsWith(".webm"))
        assertEquals("video/webm", row.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertArrayEquals("webm-bytes".toByteArray(), provider.fileFor(saved.uri).readBytes())
    }

    @Test
    fun `a known response MIME corrects a misleading URL extension`(): Unit = runBlocking {
        val provider = downloadsProvider()
        val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 3)
        server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody(Buffer().write(bytes)))

        val result = downloads().download(item(server.url("/misleading.mp4").toString()), 7L)

        assertTrue(result is ReelsDownloadResult.Saved)
        val saved = result as ReelsDownloadResult.Saved
        val row = requireNotNull(provider.rows[saved.uri])
        assertTrue(row.getAsString(MediaStore.MediaColumns.DISPLAY_NAME).endsWith(".jpg"))
        assertEquals("image/jpeg", row.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertArrayEquals(bytes, provider.fileFor(saved.uri).readBytes())
    }

    @Test
    fun `a caller already cancelled before IO dispatch still cleans its new destination`(): Unit = runBlocking {
        val file = temporaryFolder.newFile("cancelled-before-dispatch.mp4")
        val download = async(Dispatchers.IO) {
            currentCoroutineContext().cancel()
            downloads().download(item(server.url("/clip.mp4").toString()), 7L, destination = Uri.fromFile(file))
        }

        withTimeout(2_000) { download.join() }

        assertTrue(download.isCancelled)
        assertFalse(file.exists())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `real manifests disguised as video remain Unsupported`(): Unit = runBlocking {
        val manifests = listOf(
            "#EXTM3U\nsegment.ts",
            """<?xml version="1.0"?><MPD xmlns="urn:mpeg:dash:schema:mpd:2011"/>""",
        )
        for ((index, manifest) in manifests.withIndex()) {
            server.enqueue(mediaResponse(manifest.toByteArray()))
            val file = temporaryFolder.newFile("manifest-body-" + index + ".mp4")

            val result = downloads().download(
                item(server.url("/clip.mp4").toString()),
                7L,
                destination = Uri.fromFile(file),
            )

            assertEquals(ReelsDownloadResult.Unsupported, result)
            assertFalse(file.exists())
        }
    }

    @Test
    fun `opaque media with absent or generic MIME is recognized by a small container signature`(): Unit = runBlocking {
        val provider = downloadsProvider()
        val samples = listOf(
            Triple("000000186674797069736f6d0000020069736f6d6d703432", "mp4", "video/mp4"),
            Triple("1a45dfa39f4286810142f7810142f2810442f381084282847765626d4287810242858102", "webm", "video/webm"),
            Triple("ffd8ff010203ffd9", "jpg", "image/jpeg"),
            Triple("89504e470d0a1a0a01020304", "png", "image/png"),
        )
        for (contentType in listOf(null, "application/octet-stream")) {
            for ((hex, extension, mime) in samples) {
                val bytes = hex.decodeHex().toByteArray()
                server.enqueue(
                    MockResponse().setBody(Buffer().write(bytes)).apply {
                        contentType?.let { setHeader("Content-Type", it) }
                    },
                )

                val result = downloads().download(item(server.url("/opaque").toString()), 7L)

                assertTrue(result is ReelsDownloadResult.Saved)
                val saved = result as ReelsDownloadResult.Saved
                val row = requireNotNull(provider.rows[saved.uri])
                assertTrue(row.getAsString(MediaStore.MediaColumns.DISPLAY_NAME).endsWith("." + extension))
                assertEquals(mime, row.getAsString(MediaStore.MediaColumns.MIME_TYPE))
                assertEquals(bytes.size.toLong(), saved.bytes)
                assertArrayEquals(bytes, provider.fileFor(saved.uri).readBytes())
            }
        }
    }

    @Test
    fun `opaque binary without a media contract or supported signature remains Unsupported`(): Unit = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/octet-stream")
                .setBody(Buffer().write(ByteArray(1024))),
        )
        val file = temporaryFolder.newFile("unclassified.mp4")

        val result = downloads().download(item(server.url("/opaque").toString()), 7L, destination = Uri.fromFile(file))

        assertEquals(ReelsDownloadResult.Unsupported, result)
        assertFalse(file.exists())
    }

    @Test
    fun `opaque generic MIME HTML is a failed payload not an unsupported contract`(): Unit = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/octet-stream")
                .setBody("<html>login</html>"),
        )
        val file = temporaryFolder.newFile("opaque-error.mp4")

        val result = downloads().download(item(server.url("/opaque").toString()), 7L, destination = Uri.fromFile(file))

        assertEquals(ReelsDownloadResult.Failed, result)
        assertFalse(file.exists())
    }

    @Test
    fun `fast transfers report first and final progress rather than every buffer`(): Unit = runBlocking {
        val source = temporaryFolder.newFile("large.mp4").apply { writeBytes(ByteArray(1024 * 1024)) }
        val file = temporaryFolder.newFile("export-large.mp4")
        val progress = mutableListOf<Long>()

        val result = downloads().download(
            item(source.toURI().toString()),
            7L,
            destination = Uri.fromFile(file),
            onProgress = { bytes, _ -> progress += bytes },
        )

        assertEquals(ReelsDownloadResult.Saved(Uri.fromFile(file), 1_048_576L), result)
        assertTrue(progress.first() > 0)
        assertEquals(1_048_576L, progress.last())
        assertTrue(progress.size <= 3)
        assertTrue(progress.zipWithNext().all { (first, second) -> first < second })
    }

    @Test
    fun `a local Matroska file retains its extension instead of becoming WebM`(): Unit = runBlocking {
        val provider = downloadsProvider()
        val header = "1a45dfa3a34286810142f7810142f2810442f381084282886d6174726f736b61" +
            "4287810242858102"
        val bytes = header.decodeHex().toByteArray()
        val source = temporaryFolder.newFile("favorite.mkv").apply { writeBytes(bytes) }

        val result = downloads().download(item(source.toURI().toString()), 7L)

        assertTrue(result is ReelsDownloadResult.Saved)
        val saved = result as ReelsDownloadResult.Saved
        val row = requireNotNull(provider.rows[saved.uri])
        assertTrue(row.getAsString(MediaStore.MediaColumns.DISPLAY_NAME).endsWith(".mkv"))
        assertEquals("video/x-matroska", row.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertArrayEquals(bytes, provider.fileFor(saved.uri).readBytes())
        assertArrayEquals(bytes, source.readBytes())
    }

    @Test
    fun `a throttled 503 response is retried and the next attempt saves the body`(): Unit = runBlocking {
        val bytes = ByteArray(150_000) { (it % 251).toByte() }
        server.enqueue(throttledResponse(503))
        server.enqueue(mediaResponse(bytes))
        val file = temporaryFolder.newFile("throttled.mp4")

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()),
            7L,
            destination = Uri.fromFile(file),
        )

        assertTrue(result is ReelsDownloadResult.Saved)
        assertEquals(2, server.requestCount)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun `a throttled 429 response is retried too`(): Unit = runBlocking {
        val bytes = ByteArray(150_000) { (it % 251).toByte() }
        server.enqueue(throttledResponse(429))
        server.enqueue(mediaResponse(bytes))
        val file = temporaryFolder.newFile("rate-limited.mp4")

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()),
            7L,
            destination = Uri.fromFile(file),
        )

        assertTrue(result is ReelsDownloadResult.Saved)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `persistent throttling stops at the attempt budget without opening the destination`(): Unit = runBlocking {
        repeat(3) { server.enqueue(throttledResponse(503)) }
        val file = temporaryFolder.newFile("starved.mp4")

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()),
            7L,
            destination = Uri.fromFile(file),
        )

        assertEquals(ReelsDownloadResult.Failed, result)
        assertEquals(3, server.requestCount)
        assertEquals(0L, file.length())
    }

    @Test
    fun `a non-throttled error answer is not retried`(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("gone"))
        val file = temporaryFolder.newFile("missing.mp4")

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()),
            7L,
            destination = Uri.fromFile(file),
        )

        assertEquals(ReelsDownloadResult.Failed, result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `downloads drop the shared whole-call timeout but keep the socket timeouts`() {
        val base = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .build()

        val download = reelsDownloadClient(base)

        assertEquals(0, download.callTimeoutMillis)
        assertEquals(30_000, download.connectTimeoutMillis)
        assertEquals(30_000, download.readTimeoutMillis)
    }

    @Test
    fun `an interrupted native transfer keeps a resumable partial instead of deleting it`(): Unit = runBlocking {
        val provider = downloadsProvider()
        server.enqueue(
            mediaResponse(ByteArray(128 * 1024))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()),
            7L,
            keepPartial = true,
        )

        assertTrue(result is ReelsDownloadResult.Partial)
        val partial = result as ReelsDownloadResult.Partial
        assertTrue(partial.bytes > 0L)
        assertEquals(emptyList<Uri>(), provider.deleted)
        assertEquals(setOf(partial.uri), provider.rows.keys)
    }

    @Test
    fun `an interrupted transfer without the flag still removes its destination`(): Unit = runBlocking {
        val provider = downloadsProvider()
        server.enqueue(
            mediaResponse(ByteArray(128 * 1024))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        val result = downloads().download(item(server.url("/clip.mp4").toString()), 7L)

        assertEquals(ReelsDownloadResult.Failed, result)
        assertEquals(1, provider.deleted.size)
        assertEquals(emptySet<Uri>(), provider.rows.keys)
    }

    @Test
    fun `cancelling removes the pending entry even when partials are allowed`(): Unit = runBlocking {
        val provider = downloadsProvider()
        server.enqueue(mediaResponse(ByteArray(128 * 1024)).throttleBody(64L * 1024, 3, TimeUnit.SECONDS))
        val started = CompletableDeferred<Unit>()
        val download = async(Dispatchers.IO) {
            downloads().download(
                item(server.url("/clip.mp4").toString()),
                7L,
                keepPartial = true,
                onProgress = { _, _ -> started.complete(Unit) },
            )
        }

        try {
            withTimeout(5_000) { started.await() }
            withTimeout(2_000) { download.cancelAndJoin() }
            assertTrue(download.isCancelled)
            assertEquals(1, provider.deleted.size)
        } finally {
            download.cancelAndJoin()
        }
    }

    @Test
    fun `a resumed download asks for the missing range and appends to the entry`(): Unit = runBlocking {
        val provider = downloadsProvider()
        val prefix = ByteArray(4096) { 1 }
        val rest = ByteArray(2048) { 2 }
        val uri = provider.newDocument()
        provider.fileFor(uri).writeBytes(prefix)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes 4096-6143/6144")
                .setBody(Buffer().write(rest)),
        )

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()),
            7L,
            resumeFrom = 4096L,
            resumeUri = uri,
            keepPartial = true,
        )

        assertTrue(result is ReelsDownloadResult.Saved)
        val saved = result as ReelsDownloadResult.Saved
        assertEquals(6144L, saved.bytes)
        assertArrayEquals(prefix + rest, provider.fileFor(uri).readBytes())
        assertEquals("bytes=4096-", requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)).getHeader("Range"))
        assertTrue(uri in provider.published)
    }

    @Test
    fun `a server that ignores the range rewrites the file from zero`(): Unit = runBlocking {
        val provider = downloadsProvider()
        val full = ByteArray(6144) { (it % 251).toByte() }
        val uri = provider.newDocument()
        provider.fileFor(uri).writeBytes(full.copyOfRange(0, 4096))
        server.enqueue(mediaResponse(full))

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()),
            7L,
            resumeFrom = 4096L,
            resumeUri = uri,
            keepPartial = true,
        )

        assertTrue(result is ReelsDownloadResult.Saved)
        assertEquals(full.size.toLong(), (result as ReelsDownloadResult.Saved).bytes)
        assertArrayEquals(full, provider.fileFor(uri).readBytes())
    }

    @Test
    fun `a resume offset the server rejects drops the stale partial`(): Unit = runBlocking {
        val provider = downloadsProvider()
        val uri = provider.newDocument()
        provider.fileFor(uri).writeBytes(ByteArray(4096))
        server.enqueue(
            MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */6144")
                .setBody(Buffer()),
        )

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()),
            7L,
            resumeFrom = 9000L,
            resumeUri = uri,
            keepPartial = true,
        )

        assertEquals(ReelsDownloadResult.Failed, result)
        assertEquals(listOf(uri), provider.deleted)
    }

    @Test
    fun `a caller supplied document never keeps a partial`(): Unit = runBlocking {
        val file = temporaryFolder.newFile("legacy.mp4")
        server.enqueue(
            mediaResponse(ByteArray(128 * 1024))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()),
            7L,
            destination = Uri.fromFile(file),
            keepPartial = true,
        )

        assertEquals(ReelsDownloadResult.Failed, result)
        assertEquals(0L, file.length())
    }

    @Test
    fun `a throttled resume keeps the bytes already on disk`(): Unit = runBlocking {
        val provider = downloadsProvider()
        val uri = provider.newDocument()
        provider.fileFor(uri).writeBytes(ByteArray(4096))
        repeat(3) { server.enqueue(throttledResponse(503)) }

        val result = downloads().download(
            item(server.url("/clip.mp4").toString()),
            7L,
            resumeFrom = 4096L,
            resumeUri = uri,
            keepPartial = true,
        )

        assertEquals(ReelsDownloadResult.Partial(uri, 4096L), result)
        assertEquals(emptyList<Uri>(), provider.deleted)
    }

    private fun throttledResponse(code: Int) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "text/html")
        .setBody("<html>cdn busy</html>")

    private fun downloads() = ReelsDownloads(
        context = app,
        client = client,
        ioDispatcher = Dispatchers.IO,
        retryBaseDelayMs = 1L,
    )

    private fun item(url: String) = ShortVideoItem(
        id = "clip",
        videoUrl = url,
        posterUrl = "https://example.com/poster.jpg",
    )

    private fun mediaResponse(bytes: ByteArray) = MockResponse()
        .setHeader("Content-Type", "video/mp4")
        .setBody(Buffer().write(bytes))

    private fun downloadsProvider(): DownloadsProvider =
        Robolectric.setupContentProvider(DownloadsProvider::class.java, "media")

    class DownloadsProvider : ContentProvider() {
        val rows = linkedMapOf<Uri, ContentValues>()
        val pendingWhenOpened = mutableListOf<Int?>()
        val published = mutableListOf<Uri>()
        val bytesAtPublish = mutableListOf<ByteArray>()
        val deleted = mutableListOf<Uri>()
        val deletedDocuments = mutableListOf<Uri>()
        var failPublishing = false
        private var nextId = 1L
        private lateinit var dir: File

        override fun onCreate(): Boolean {
            dir = File(requireNotNull(context).cacheDir, "downloads-provider-" + System.nanoTime()).apply { mkdirs() }
            return true
        }

        fun fileFor(uri: Uri): File = File(dir, uri.lastPathSegment + ".bin")

        fun newDocument(): Uri {
            val uri = Uri.parse("content://media/document/" + nextId++)
            rows[uri] = ContentValues()
            fileFor(uri).writeBytes(byteArrayOf())
            return uri
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val target = ContentUris.withAppendedId(uri, nextId++)
            rows[target] = ContentValues(values ?: ContentValues())
            return target
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            pendingWhenOpened += rows[uri]?.getAsInteger(MediaStore.MediaColumns.IS_PENDING)
            // Real MediaProvider honours "wt"/"wa"; the fake must too or a resume test
            // would silently pass on a truncated file.
            val append = mode.contains('a')
            return ParcelFileDescriptor.open(
                fileFor(uri),
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE or
                    if (append) ParcelFileDescriptor.MODE_APPEND else ParcelFileDescriptor.MODE_TRUNCATE,
            )
        }

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            if (failPublishing) return 0
            val row = rows[uri] ?: return 0
            row.putAll(values ?: return 0)
            if (values.getAsInteger(MediaStore.MediaColumns.IS_PENDING) == 0) {
                published += uri
                bytesAtPublish += fileFor(uri).readBytes()
            }
            return 1
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deleted += uri
            fileFor(uri).delete()
            return if (rows.remove(uri) != null) 1 else 0
        }

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            if (method == "android:deleteDocument") {
                val document = requireNotNull(extras?.getParcelable<Uri>("uri"))
                deletedDocuments += document
                delete(document, null, null)
                return Bundle().apply { putBoolean("result", true) }
            }
            return super.call(method, arg, extras)
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String? = rows[uri]?.getAsString(MediaStore.MediaColumns.MIME_TYPE)
    }
}
