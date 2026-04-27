package eu.kanade.presentation.components

import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.model.MangaCover

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
