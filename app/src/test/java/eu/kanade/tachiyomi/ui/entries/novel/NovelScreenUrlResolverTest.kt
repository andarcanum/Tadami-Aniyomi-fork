package eu.kanade.tachiyomi.ui.entries.novel

import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.source.novel.NovelWebUrlSource
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel as DomainNovel

class NovelScreenUrlResolverTest {

    @Test
    fun `returns absolute novel url as is`() {
        runBlocking {
            val resolved = resolveNovelEntryWebUrl(
                novelUrl = "https://ranobelib.me/ru/book-slug",
                source = null,
            )

            resolved shouldBe "https://ranobelib.me/ru/book-slug"
        }
    }

    @Test
    fun `resolves relative novel url via source web resolver`() {
        runBlocking {
            val source = object : NovelSource, NovelWebUrlSource {
                override val id: Long = 1L
                override val name: String = "Test"
                override suspend fun getNovelWebUrl(novelPath: String): String? {
                    return "https://ranobelib.me$novelPath"
                }
                override suspend fun getChapterWebUrl(chapterPath: String, novelPath: String?): String? = null
            }

            val resolved = resolveNovelEntryWebUrl(
                novelUrl = "/ru/book-slug",
                source = source,
            )

            resolved shouldBe "https://ranobelib.me/ru/book-slug"
        }
    }

    @Test
    fun `falls back to site url when source web resolver returns null`() {
        runBlocking {
            val source = object : NovelSource, NovelWebUrlSource, NovelSiteSource {
                override val id: Long = 1L
                override val name: String = "Test"
                override val siteUrl: String? = "https://ranobelib.me"
                override suspend fun getNovelWebUrl(novelPath: String): String? = null
                override suspend fun getChapterWebUrl(chapterPath: String, novelPath: String?): String? = null
            }

            val resolved = resolveNovelEntryWebUrl(
                novelUrl = "/ru/book-slug",
                source = source,
            )

            resolved shouldBe "https://ranobelib.me/ru/book-slug"
        }
    }

    @Test
    fun `returns null for blank novel url`() {
        runBlocking {
            val resolved = resolveNovelEntryWebUrl(
                novelUrl = "   ",
                source = null,
            )

            resolved shouldBe null
        }
    }

    @Test
    fun `shows WebView login hint when novel has no chapters and no metadata`() {
        val source = object : NovelSource, NovelSiteSource {
            override val id: Long = 1L
            override val name: String = "Test source"
            override val siteUrl: String? = "https://ranobelib.me"
        }
        val novel = DomainNovel.create().copy(
            id = 10L,
            url = "/title/slug",
            description = null,
            author = null,
            genre = null,
        )

        resolveNovelNeedsWebViewLoginHint(
            novel = novel,
            source = source,
            chaptersCount = 0,
            canOpenWebView = true,
            isRefreshing = false,
        ) shouldBe true
    }

    @Test
    fun `hides WebView login hint when chapters already available`() {
        val source = object : NovelSource, NovelSiteSource {
            override val id: Long = 1L
            override val name: String = "Test source"
            override val siteUrl: String? = "https://ranobelib.me"
        }
        val novel = DomainNovel.create().copy(
            id = 10L,
            url = "/title/slug",
            description = null,
        )

        resolveNovelNeedsWebViewLoginHint(
            novel = novel,
            source = source,
            chaptersCount = 2,
            canOpenWebView = true,
            isRefreshing = false,
        ) shouldBe false
    }

    @Test
    fun `shows WebView login hint when metadata is present but url is relative`() {
        val source = object : NovelSource, NovelSiteSource {
            override val id: Long = 1L
            override val name: String = "Test source"
            override val siteUrl: String? = "https://ranobelib.me"
        }
        val novel = DomainNovel.create().copy(
            id = 10L,
            url = "/title/slug",
            description = "Has description",
        )

        resolveNovelNeedsWebViewLoginHint(
            novel = novel,
            source = source,
            chaptersCount = 0,
            canOpenWebView = true,
            isRefreshing = false,
        ) shouldBe true
    }

    @Test
    fun `hides WebView login hint when metadata is present and url is absolute`() {
        val source = object : NovelSource, NovelSiteSource {
            override val id: Long = 1L
            override val name: String = "Test source"
            override val siteUrl: String? = "https://ranobelib.me"
        }
        val novel = DomainNovel.create().copy(
            id = 10L,
            initialized = true,
            url = "https://ranobelib.me/title/slug",
            description = "Has description",
        )

        resolveNovelNeedsWebViewLoginHint(
            novel = novel,
            source = source,
            chaptersCount = 0,
            canOpenWebView = true,
            isRefreshing = false,
        ) shouldBe false
    }

    @Test
    fun `shows WebView login hint when novel is not initialized even with metadata`() {
        val source = object : NovelSource, NovelSiteSource {
            override val id: Long = 1L
            override val name: String = "Test source"
            override val siteUrl: String? = "https://ranobelib.me"
        }
        val novel = DomainNovel.create().copy(
            id = 10L,
            url = "/title/slug",
            initialized = false,
            description = "Some short description",
        )

        resolveNovelNeedsWebViewLoginHint(
            novel = novel,
            source = source,
            chaptersCount = 0,
            canOpenWebView = true,
            isRefreshing = false,
        ) shouldBe true
    }

    @Test
    fun `builds stable login hint key only when hint should be shown`() {
        resolveNovelWebViewLoginHintKey(
            novelId = 1L,
            chaptersCount = 0,
            description = null,
            needsLoginHint = true,
        ) shouldBe "1:0:0"

        resolveNovelWebViewLoginHintKey(
            novelId = 1L,
            chaptersCount = 0,
            description = null,
            needsLoginHint = false,
        ) shouldBe null
    }

    @Test
    fun `resolves login url to source site when available`() {
        runBlocking {
            val source = object : NovelSource, NovelSiteSource {
                override val id: Long = 1L
                override val name: String = "Test source"
                override val siteUrl: String? = "https://hexnovels.me"
            }

            val resolved = resolveNovelLoginWebUrl(
                novelUrl = "/content/one-idiot-three-wishes-unlimited-chaos",
                source = source,
            )

            resolved shouldBe "https://hexnovels.me/"
        }
    }

    @Test
    fun `falls back to novel url for login when source site is unavailable`() {
        runBlocking {
            val source = object : NovelSource {
                override val id: Long = 1L
                override val name: String = "Test source"
            }

            val resolved = resolveNovelLoginWebUrl(
                novelUrl = "https://example.org/title",
                source = source,
            )

            resolved shouldBe "https://example.org/title"
        }
    }
}
