package eu.kanade.tachiyomi.data.cache

import android.content.Context
import eu.kanade.tachiyomi.source.model.Page
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.items.chapter.model.Chapter
import java.nio.file.Path

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

    private fun createCache(): ChapterCache {
        val context = mockk<Context>()
        every { context.cacheDir } returns tempDir.toFile()
        return ChapterCache(
            context = context,
            json = Json { encodeDefaults = true },
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
