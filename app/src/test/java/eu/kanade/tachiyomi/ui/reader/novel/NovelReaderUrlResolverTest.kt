package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelReaderUrlResolverTest {

    @Test
    fun `prefers novel url base over plugin site when chapter path is relative`() {
        val resolved = resolveNovelChapterWebUrl(
            chapterUrl = "chapter-1",
            pluginSite = "https://example.org",
            novelUrl = "https://example.org/books/slug",
        )

        resolved shouldBe "https://example.org/books/chapter-1"
    }

    @Test
    fun `uses plugin site fallback when novel url is not absolute`() {
        val resolved = resolveNovelChapterWebUrl(
            chapterUrl = "/chapter-1",
            pluginSite = "example.org",
            novelUrl = "/books/slug",
        )

        resolved shouldBe "https://example.org/chapter-1"
    }

    @Test
    fun `uses plugin site first for root-relative chapter paths`() {
        val resolved = resolveNovelChapterWebUrl(
            chapterUrl = "/chapter-1",
            pluginSite = "https://example.org",
            novelUrl = "https://books.example.org/book/slug",
        )

        resolved shouldBe "https://example.org/chapter-1"
    }
}
