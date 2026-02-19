package eu.kanade.tachiyomi.ui.browse.novel.source.browse

import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BrowseNovelSourceWebUrlResolverTest {

    @Test
    fun `returns absolute site url`() {
        val source = object : NovelSource, NovelSiteSource {
            override val id: Long = 1L
            override val name: String = "Test"
            override val siteUrl: String? = "https://ranobelib.me"
        }

        resolveNovelSourceWebUrl(source) shouldBe "https://ranobelib.me/"
    }

    @Test
    fun `adds https scheme for host without scheme`() {
        val source = object : NovelSource, NovelSiteSource {
            override val id: Long = 1L
            override val name: String = "Test"
            override val siteUrl: String? = "ranobelib.me"
        }

        resolveNovelSourceWebUrl(source) shouldBe "https://ranobelib.me/"
    }

    @Test
    fun `returns null when source has no site url`() {
        val source = object : NovelSource {
            override val id: Long = 1L
            override val name: String = "Test"
        }

        resolveNovelSourceWebUrl(source) shouldBe null
    }

    @Test
    fun `returns null for invalid url`() {
        val source = object : NovelSource, NovelSiteSource {
            override val id: Long = 1L
            override val name: String = "Test"
            override val siteUrl: String? = "not a valid url"
        }

        resolveNovelSourceWebUrl(source) shouldBe null
    }
}
