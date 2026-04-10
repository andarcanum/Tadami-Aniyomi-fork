package eu.kanade.presentation.entries.components.aurora

import eu.kanade.presentation.components.resolveAuroraPosterModelPair
import eu.kanade.presentation.components.shouldApplyAuroraPosterTrim
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.MangaCover

class AuroraPosterBackgroundRequestTest {

    @Test
    fun `aurora poster background uses distinct sharp and blur cache keys`() {
        val spec = auroraPosterBackgroundSpec(
            baseCacheKey = "novel-bg;42;1234",
            containerWidthPx = 1080,
            containerHeightPx = 2400,
            blurRadiusPx = 60,
        )

        spec.sharpMemoryCacheKey shouldBe "novel-bg;42;1234;sharp"
        spec.blurMemoryCacheKey shouldBe "novel-bg;42;1234;blur;360x800;r60"
    }

    @Test
    fun `aurora poster background downsamples blur layer conservatively`() {
        val spec = auroraPosterBackgroundSpec(
            baseCacheKey = "manga-bg;7;999",
            containerWidthPx = 1440,
            containerHeightPx = 3120,
            blurRadiusPx = 48,
        )

        spec.blurWidthPx shouldBe 480
        spec.blurHeightPx shouldBe 1040
    }

    @Test
    fun `aurora poster background clamps tiny blur targets to one pixel`() {
        val spec = auroraPosterBackgroundSpec(
            baseCacheKey = "anime-bg;1;2",
            containerWidthPx = 2,
            containerHeightPx = 1,
            blurRadiusPx = 12,
        )

        spec.blurWidthPx shouldBe 1
        spec.blurHeightPx shouldBe 1
    }

    @Test
    fun `aurora poster model pair keeps primary and fallback candidates`() {
        val primary = MangaCover(
            mangaId = 7L,
            sourceId = 9L,
            isMangaFavorite = true,
            url = "https://example.org/primary.jpg",
            lastModified = 1234L,
        )
        val fallback = "https://example.org/fallback.jpg"

        val pair = resolveAuroraPosterModelPair(primary, fallback)

        pair.primary shouldBe primary
        pair.fallback shouldBe fallback
    }

    @Test
    fun `aurora poster trim policy skips animated images`() {
        shouldApplyAuroraPosterTrim("https://example.org/static.jpg") shouldBe true
        shouldApplyAuroraPosterTrim("https://example.org/animated.webp") shouldBe false
    }
}
