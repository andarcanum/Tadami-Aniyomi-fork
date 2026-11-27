package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.model.Chapter

class HttpPageLoaderTest {

    @Test
    fun `getPages keeps duplicate urls from source`() = runTest {
        val source = mockk<HttpSource>()
        every { source.baseUrl } returns "https://example.org"
        coEvery { source.getPageList(any()) } returns listOf(
            Page(index = 0, url = "https://example.org/page", imageUrl = "https://example.org/image-1"),
            Page(index = 1, url = "https://example.org/page", imageUrl = "https://example.org/image-2"),
            Page(index = 2, url = "https://example.org/page", imageUrl = "https://example.org/image-3"),
        )

        val chapterCache = mockk<ChapterCache>()
        every { chapterCache.getPageListFromCache(any(), any()) } returns emptyList()
        justRun { chapterCache.putPageListToCache(any(), any()) }
        val sourcePreferences = mockk<SourcePreferences>()
        every { sourcePreferences.dataSaver() } returns mockk {
            every { get() } returns SourcePreferences.DataSaver.NONE
        }
        val readerPreferences = mockk<ReaderPreferences>()

        val loader = HttpPageLoader(
            chapter = ReaderChapter(
                Chapter.create().copy(
                    id = 1L,
                    mangaId = 42L,
                    url = "https://example.org/chapter-1",
                ),
            ),
            source = source,
            chapterCache = chapterCache,
            sourcePreferences = sourcePreferences,
            readerPreferences = readerPreferences,
        )

        try {
            val pages = loader.getPages()

            pages.size shouldBe 3
            pages[0].imageUrl shouldBe "https://example.org/image-1"
            pages[1].imageUrl shouldBe "https://example.org/image-2"
            pages[2].imageUrl shouldBe "https://example.org/image-3"
            verify(exactly = 1) { chapterCache.putPageListToCache(any(), any()) }
        } finally {
            loader.recycle()
        }
    }

    @Test
    fun `getPages refetches when cached urls contain signed query tokens`() = runTest {
        val source = mockk<HttpSource>()
        every { source.baseUrl } returns "https://example.org"
        coEvery { source.getPageList(any()) } returns listOf(
            Page(index = 0, url = "https://example.org/p1", imageUrl = "https://cdn.example.org/1.jpg?Expires=999999"),
        )

        val chapterCache = mockk<ChapterCache>()
        every { chapterCache.getPageListFromCache(any(), any()) } returns listOf(
            Page(
                index = 0,
                url = "https://example.org/p1",
                imageUrl = "https://cdn.example.org/1.jpg?X-Amz-Signature=old",
            ),
        )
        justRun { chapterCache.putPageListToCache(any(), any()) }
        val sourcePreferences = mockk<SourcePreferences>()
        every { sourcePreferences.dataSaver() } returns mockk {
            every { get() } returns SourcePreferences.DataSaver.NONE
        }

        val loader = HttpPageLoader(
            chapter = ReaderChapter(
                Chapter.create().copy(id = 2L, mangaId = 42L, url = "https://example.org/chapter-2"),
            ),
            source = source,
            chapterCache = chapterCache,
            sourcePreferences = sourcePreferences,
            readerPreferences = mockk(),
        )

        try {
            val pages = loader.getPages()
            pages[0].imageUrl shouldBe "https://cdn.example.org/1.jpg?Expires=999999"
            coVerify(exactly = 1) { source.getPageList(any()) }
        } finally {
            loader.recycle()
        }
    }

    @Test
    fun `hasSignedQueryToken matches query params only`() {
        hasSignedQueryToken("https://cdn.example.org/token/page.jpg") shouldBe false
        hasSignedQueryToken("https://cdn.example.org/page.jpg?width=1080") shouldBe false
        hasSignedQueryToken("https://cdn.example.org/page.jpg?t=123") shouldBe true
        hasSignedQueryToken("https://cdn.example.org/page.jpg?v=1&Expires=123") shouldBe true
        hasSignedQueryToken("https://cdn.example.org/page.jpg?X-Amz-Signature=abc") shouldBe true
        hasSignedQueryToken("https://cdn.example.org/page.jpg?hdnea=st=123") shouldBe true
    }
}
