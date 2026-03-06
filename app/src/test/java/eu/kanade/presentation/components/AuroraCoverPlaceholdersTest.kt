package eu.kanade.presentation.components

import eu.kanade.tachiyomi.R
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.NovelCover

class AuroraCoverPlaceholdersTest {

    @Test
    fun `aurora cover model resolves blank values to null`() {
        resolveAuroraCoverModel(null) shouldBe null
        resolveAuroraCoverModel("   ") shouldBe null
        resolveAuroraCoverModel(
            AnimeCover(
                animeId = 3L,
                sourceId = 4L,
                isAnimeFavorite = false,
                url = "",
                lastModified = 0L,
            ),
        ) shouldBe null
        resolveAuroraCoverModel(
            MangaCover(
                mangaId = 5L,
                sourceId = 6L,
                isMangaFavorite = false,
                url = " ",
                lastModified = 0L,
            ),
        ) shouldBe null
        resolveAuroraCoverModel(
            NovelCover(
                novelId = 1L,
                sourceId = 2L,
                isNovelFavorite = false,
                url = " ",
                lastModified = 0L,
            ),
        ) shouldBe null
    }

    @Test
    fun `aurora cover model keeps loadable data`() {
        val animeCover = AnimeCover(
            animeId = 3L,
            sourceId = 4L,
            isAnimeFavorite = false,
            url = "https://example.org/anime.jpg",
            lastModified = 0L,
        )
        val mangaCover = MangaCover(
            mangaId = 5L,
            sourceId = 6L,
            isMangaFavorite = false,
            url = "https://example.org/manga.jpg",
            lastModified = 0L,
        )
        val cover = NovelCover(
            novelId = 1L,
            sourceId = 2L,
            isNovelFavorite = false,
            url = "https://example.org/cover.jpg",
            lastModified = 0L,
        )

        resolveAuroraCoverModel("https://example.org/image.jpg") shouldBe "https://example.org/image.jpg"
        resolveAuroraCoverModel(animeCover) shouldBe animeCover
        resolveAuroraCoverModel(mangaCover) shouldBe mangaCover
        resolveAuroraCoverModel(cover) shouldBe cover
    }

    @Test
    fun `aurora placeholder variant maps to expected resource`() {
        auroraCoverPlaceholderResId(AuroraCoverPlaceholderVariant.Portrait) shouldBe
            R.drawable.aurora_cover_placeholder_portrait
        auroraCoverPlaceholderResId(AuroraCoverPlaceholderVariant.Wide) shouldBe
            R.drawable.aurora_cover_placeholder_wide
    }
}
