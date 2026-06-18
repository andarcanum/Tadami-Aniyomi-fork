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
    fun `non favorite novel cover model with blank url is not loadable`() {
        val cover = NovelCover(
            novelId = 1L,
            sourceId = 2L,
            isNovelFavorite = false,
            url = "   ",
            lastModified = 0L,
        )
        val model = resolveCoverModel(cover)

        model shouldBe cover
        isLoadableCoverData(model) shouldBe false
    }

    @Test
    fun `favorite novel cover model with blank url remains loadable for custom cover`() {
        val cover = NovelCover(
            novelId = 1L,
            sourceId = 2L,
            isNovelFavorite = true,
            url = null,
            lastModified = 0L,
        )
        val model = resolveCoverModel(cover)

        model shouldBe cover
        isLoadableCoverData(model) shouldBe true
    }

    @Test
    fun `non favorite anime and manga cover models with blank urls are not loadable`() {
        val animeCover = AnimeCover(
            animeId = 1L,
            sourceId = 2L,
            isAnimeFavorite = false,
            url = " ",
            lastModified = 0L,
        )
        isLoadableCoverData(resolveCoverModel(animeCover)) shouldBe false

        val mangaCover = MangaCover(
            mangaId = 1L,
            sourceId = 2L,
            isMangaFavorite = false,
            url = "",
            lastModified = 0L,
        )
        isLoadableCoverData(resolveCoverModel(mangaCover)) shouldBe false
    }

    @Test
    fun `favorite anime and manga cover models with blank urls remain loadable for custom covers`() {
        val animeCover = AnimeCover(
            animeId = 1L,
            sourceId = 2L,
            isAnimeFavorite = true,
            url = " ",
            lastModified = 0L,
        )
        isLoadableCoverData(resolveCoverModel(animeCover)) shouldBe true

        val mangaCover = MangaCover(
            mangaId = 1L,
            sourceId = 2L,
            isMangaFavorite = true,
            url = "",
            lastModified = 0L,
        )
        isLoadableCoverData(resolveCoverModel(mangaCover)) shouldBe true
    }

    @Test
    fun `blank string cover model resolves to null`() {
        resolveCoverModel("   ") shouldBe null
    }
}
