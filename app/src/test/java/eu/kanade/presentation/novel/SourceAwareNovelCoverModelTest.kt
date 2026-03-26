package eu.kanade.presentation.novel

import io.mockk.mockk
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover

class SourceAwareNovelCoverModelTest {

    @Test
    fun `sourceAwareNovelCoverModel keeps source aware novel cover data`() {
        val novel = Novel.create().copy(
            id = 42L,
            source = 99L,
            favorite = true,
            thumbnailUrl = "https://example.org/cover.jpg",
            coverLastModified = 5678L,
        )

        val model = sourceAwareNovelCoverModel(novel)

        model shouldBe NovelCover(
            novelId = 42L,
            sourceId = 99L,
            isNovelFavorite = true,
            url = "https://example.org/cover.jpg",
            lastModified = 5678L,
        )
    }

    @Test
    fun `novel cover image request keeps novel cover data and placeholder cache key`() {
        val novel = Novel.create().copy(
            id = 42L,
            source = 99L,
            favorite = true,
            thumbnailUrl = "https://example.org/cover.jpg",
            coverLastModified = 5678L,
        )

        val request = buildNovelCoverImageRequest(
            context = mockk(relaxed = true),
            novel = novel,
        )

        request.data shouldBe NovelCover(
            novelId = 42L,
            sourceId = 99L,
            isNovelFavorite = true,
            url = "https://example.org/cover.jpg",
            lastModified = 5678L,
        )
        request.placeholderMemoryCacheKey?.key shouldBe "https://example.org/cover.jpg"
    }
}
