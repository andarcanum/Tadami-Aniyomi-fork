package eu.kanade.presentation.browse.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel

class BrowseNovelSourceScreenTest {

    @Test
    fun `novelBrowseItemKey is unique for same url with different indices`() {
        val first = novelBrowseItemKey(url = "/novel/infinity-is-my-affinity", index = 4)
        val second = novelBrowseItemKey(url = "/novel/infinity-is-my-affinity", index = 9)

        (first == second) shouldBe false
    }

    @Test
    fun `novelBrowseItemKey keeps key deterministic`() {
        val key = novelBrowseItemKey(url = "/novel/a", index = 2)
        key shouldBe "novel//novel/a#2"
    }

    @Test
    fun `novelBrowseItemKey handles null url`() {
        val key = novelBrowseItemKey(url = null, index = 3)
        key shouldBe "novel/#3"
    }

    @Test
    fun `asBrowseNovelCover keeps favorite flag for in-library state`() {
        val novel = Novel.create().copy(
            id = 12L,
            source = 99L,
            favorite = true,
            thumbnailUrl = "https://example.org/thumb.jpg",
            coverLastModified = 1234L,
        )

        val cover = novel.asBrowseNovelCover()

        cover.novelId shouldBe 12L
        cover.sourceId shouldBe 99L
        cover.isNovelFavorite shouldBe true
        cover.url shouldBe "https://example.org/thumb.jpg"
        cover.lastModified shouldBe 1234L
    }
}
