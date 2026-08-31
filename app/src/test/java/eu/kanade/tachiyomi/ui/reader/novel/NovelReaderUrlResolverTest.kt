package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.source.local.entries.novel.LocalNovelSource

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

    @Test
    fun `offline local novel source never fabricates a chapter web url`() {
        runBlocking {
            // LocalNovelSource stores the plain file name in novel.url. The host-guessing
            // heuristic used to turn "book.txt" into "https://book.txt/..." and every WebView
            // navigation then failed with net::ERR_NAME_NOT_RESOLVED on the invented host.
            val resolved = resolveNovelChapterWebUrlForSource(
                source = localNovelSource(),
                chapterUrl = "book.txt/Chapter 1.txt",
                novelUrl = "book.txt",
                pluginSite = null,
            )

            resolved shouldBe null
        }
    }

    @Test
    fun `web url source answer wins over the string heuristic`() {
        runBlocking {
            val source = object : NovelSource, NovelWebUrlSource {
                override val id: Long = 42L
                override val name: String = "Web source"
                override suspend fun getNovelWebUrl(novelPath: String): String? = null
                override suspend fun getChapterWebUrl(chapterPath: String, novelPath: String?): String? =
                    "https://ranobelib.me/ch/$chapterPath"
            }

            val resolved = resolveNovelChapterWebUrlForSource(
                source = source,
                chapterUrl = "ch-1",
                novelUrl = "https://ranobelib.me/novel/slug",
                pluginSite = null,
            )

            resolved shouldBe "https://ranobelib.me/ch/ch-1"
        }
    }

    @Test
    fun `online source without web url interfaces keeps the string heuristic`() {
        runBlocking {
            val source = object : NovelSource {
                override val id: Long = 7L
                override val name: String = "Plain online source"
            }

            val resolved = resolveNovelChapterWebUrlForSource(
                source = source,
                chapterUrl = "novel/ch1",
                novelUrl = "site.org/novel",
                pluginSite = null,
            )

            resolved shouldBe "https://site.org/novel/ch1"
        }
    }

    private fun localNovelSource(): NovelSource = object : NovelSource {
        override val id: Long = LocalNovelSource.ID
        override val name: String = "Local novels"
    }
}
