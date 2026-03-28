package eu.kanade.tachiyomi.data.coil

import coil3.Extras
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.CachePolicy
import coil3.request.Options
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Request
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.novel.model.NovelCover
import java.nio.file.Path

class NovelCoverFetcherTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `fetch prefers dedicated library cover cache for favorite novels before network`() {
        runTest {
            val context = mockk<android.content.Context>(relaxed = true)
            val imageLoader = mockk<ImageLoader>(relaxed = true)
            val coverCache = NovelCoverCache(tempDir.resolve("novelcovers").toFile(), createDir = true)
            val coverFile = coverCache.getCoverFile("https://example.org/cover.jpg")!!
            coverFile.parentFile?.mkdirs()
            coverFile.writeText("cached-cover")
            val data = NovelCover(
                novelId = 9L,
                sourceId = 77L,
                isNovelFavorite = true,
                url = "https://example.org/cover.jpg",
                lastModified = 1234L,
            )
            val options = Options(
                context = context,
                size = Size.ORIGINAL,
                scale = Scale.FIT,
                precision = Precision.EXACT,
                diskCacheKey = "novel-cover-test",
                fileSystem = FileSystem.SYSTEM,
                memoryCachePolicy = CachePolicy.ENABLED,
                diskCachePolicy = CachePolicy.ENABLED,
                networkCachePolicy = CachePolicy.ENABLED,
                extras = Extras.EMPTY,
            )

            val result = NovelCoverFetcher(
                data = data,
                options = options,
                sourceSiteUrlLazy = lazy { "https://example.org" },
                coverFileLazy = lazy { coverCache.getCoverFile(data.url) },
                diskCacheKeyLazy = lazy { "novel-cover-test" },
                pluginHeadersProvider = { emptyMap() },
                callFactoryLazy = lazy {
                    object : Call.Factory {
                        override fun newCall(request: Request): Call {
                            error("network should not be called when library cache exists")
                        }
                    }
                },
                imageLoader = imageLoader,
            ).fetch()

            assertTrue(result is SourceFetchResult)
            result as SourceFetchResult
            assertEquals(DataSource.DISK, result.dataSource)
            assertEquals(coverFile.toOkioPath(), result.source.file())
        }
    }

    @Test
    fun `buildNovelCoverRequest adds plugin image headers and keeps site origin fallback`() {
        val request = buildNovelCoverRequest(
            url = "https://novel.tl/images/cover.jpg",
            siteUrl = "https://novel.tl/",
            pluginHeaders = mapOf(
                "Accept" to "image/webp,image/*",
                "Referer" to "https://cdn.example/plugin/",
            ),
            readFromNetwork = true,
        )

        assertEquals("image/webp,image/*", request.header("Accept"))
        assertEquals("https://cdn.example/plugin/", request.header("Referer"))
        assertEquals("https://novel.tl", request.header("Origin"))
    }

    @Test
    fun `buildNovelCoverRequest adds referer and origin from site url`() {
        val request = buildNovelCoverRequest(
            url = "https://novel.tl/images/cover.jpg",
            siteUrl = "https://novel.tl/",
            readFromNetwork = true,
        )

        assertEquals("https://novel.tl/", request.header("Referer"))
        assertEquals("https://novel.tl", request.header("Origin"))
    }

    @Test
    fun `buildNovelCoverRequest skips referer and origin when site is missing`() {
        val request = buildNovelCoverRequest(
            url = "https://novel.tl/images/cover.jpg",
            siteUrl = "   ",
            readFromNetwork = true,
        )

        assertNull(request.header("Referer"))
        assertNull(request.header("Origin"))
    }

    @Test
    fun `buildNovelCoverRequest sets cache control based on policy`() {
        val networkRequest = buildNovelCoverRequest(
            url = "https://novel.tl/images/cover.jpg",
            siteUrl = "https://novel.tl",
            readFromNetwork = true,
        )
        val offlineRequest = buildNovelCoverRequest(
            url = "https://novel.tl/images/cover.jpg",
            siteUrl = "https://novel.tl",
            readFromNetwork = false,
        )

        assertEquals("no-store", networkRequest.header("Cache-Control"))
        assertEquals("no-cache, only-if-cached", offlineRequest.header("Cache-Control"))
    }
}
