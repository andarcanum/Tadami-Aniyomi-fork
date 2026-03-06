package eu.kanade.presentation.entries.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.NovelCover

class ItemCoverTest {

    @Test
    fun `novel cover model resolves to novel cover`() {
        val cover = NovelCover(
            novelId = 1L,
            sourceId = 2L,
            isNovelFavorite = false,
            url = "https://example.org/cover.jpg",
            lastModified = 0L,
        )

        val model = resolveCoverModel(cover)

        model shouldBe cover
    }

    @Test
    fun `novel cover model with blank url resolves to null`() {
        val model = resolveCoverModel(
            NovelCover(
                novelId = 1L,
                sourceId = 2L,
                isNovelFavorite = false,
                url = "   ",
                lastModified = 0L,
            ),
        )

        model shouldBe null
    }

    @Test
    fun `anime and manga cover models with blank urls resolve to null`() {
        resolveCoverModel(
            AnimeCover(
                animeId = 1L,
                sourceId = 2L,
                isAnimeFavorite = false,
                url = " ",
                lastModified = 0L,
            ),
        ) shouldBe null

        resolveCoverModel(
            MangaCover(
                mangaId = 1L,
                sourceId = 2L,
                isMangaFavorite = false,
                url = "",
                lastModified = 0L,
            ),
        ) shouldBe null
    }

    @Test
    fun `blank string cover model resolves to null`() {
        resolveCoverModel("   ") shouldBe null
    }

    @Test
    fun `resolved blank model is not loadable`() {
        val model = resolveCoverModel(
            NovelCover(
                novelId = 1L,
                sourceId = 2L,
                isNovelFavorite = false,
                url = null,
                lastModified = 0L,
            ),
        )

        isLoadableCoverData(model) shouldBe false
    }
}
