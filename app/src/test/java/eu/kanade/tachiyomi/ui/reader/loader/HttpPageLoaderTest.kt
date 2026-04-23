package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
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
        every { chapterCache.getPageListFromCache(any()) } returns emptyList()
        val sourcePreferences = mockk<SourcePreferences>()
        every { sourcePreferences.dataSaver() } returns mockk {
            every { get() } returns SourcePreferences.DataSaver.NONE
        }

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
        )

        try {
            val pages = loader.getPages()

            pages.size shouldBe 3
            pages[0].imageUrl shouldBe "https://example.org/image-1"
            pages[1].imageUrl shouldBe "https://example.org/image-2"
            pages[2].imageUrl shouldBe "https://example.org/image-3"
        } finally {
            loader.recycle()
        }
    }
}
