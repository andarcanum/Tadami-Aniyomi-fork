package eu.kanade.presentation.components

import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.NovelCover

class AuroraCoverPlaceholdersTest {

    @Test
    fun `resolveAuroraCoverPlaceholderMemoryCacheKey includes last modified for cover models`() {
        val key = resolveAuroraCoverPlaceholderMemoryCacheKey(
            AnimeCover(
                animeId = 1L,
                sourceId = 2L,
                isAnimeFavorite = true,
                url = "https://example.org/anime.jpg",
                lastModified = 1234L,
            ),
        )

        key shouldBe "anime;1;https://example.org/anime.jpg;1234"
    }

    @Test
    fun `non favorite blank cover models resolve to null so they do not poison image loading`() {
        resolveAuroraCoverModel(
            AnimeCover(
                animeId = 1L,
                sourceId = 2L,
                isAnimeFavorite = false,
                url = " ",
                lastModified = 0L,
            ),
        ) shouldBe null
        resolveAuroraCoverModel(
            MangaCover(
                mangaId = 3L,
                sourceId = 4L,
                isMangaFavorite = false,
                url = "",
                lastModified = 0L,
            ),
        ) shouldBe null
        resolveAuroraCoverModel(
            NovelCover(
                novelId = 5L,
                sourceId = 6L,
                isNovelFavorite = false,
                url = null,
                lastModified = 0L,
            ),
        ) shouldBe null
    }

    @Test
    fun `favorite blank cover models stay loadable to allow custom covers`() {
        val cover = NovelCover(
            novelId = 5L,
            sourceId = 6L,
            isNovelFavorite = true,
            url = null,
            lastModified = 0L,
        )

        resolveAuroraCoverModel(cover) shouldBe cover
        resolveAuroraCoverPlaceholderMemoryCacheKey(cover) shouldBe "novel;5;null;0"
    }

    @Test
    fun `buildAuroraCoverImageRequest applies placeholder memory cache key`() {
        val request = buildAuroraCoverImageRequest(
            context = mockk<Context>(relaxed = true),
            data = MangaCover(
                mangaId = 3L,
                sourceId = 4L,
                isMangaFavorite = false,
                url = "https://example.org/manga.jpg",
                lastModified = 5678L,
            ),
        )

        request.placeholderMemoryCacheKey?.key shouldBe "manga;3;https://example.org/manga.jpg;5678"
    }
}
