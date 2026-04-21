package eu.kanade.presentation.entries.components.aurora

import android.content.Context
import coil3.size.Size
import eu.kanade.presentation.components.resolveAuroraPosterModelPair
import eu.kanade.presentation.components.shouldApplyAuroraPosterTrim
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.MangaCover

class AuroraPosterBackgroundRequestTest {

    @Test
    fun `aurora poster background spec keys by cache and size only`() {
        val spec = auroraPosterBackgroundSpec(
            baseCacheKey = "anime-bg;42;1234;request",
            containerWidthPx = 1080,
            containerHeightPx = 1920,
        )

        spec.memoryCacheKey shouldBe "anime-bg;42;1234;request;1080x1920"
    }

    @Test
    fun `buildAuroraPosterBackgroundRequest keeps configure block and requested size`() = runTest {
        val spec = auroraPosterBackgroundSpec(
            baseCacheKey = "anime-bg;42;1234;request",
            containerWidthPx = 1080,
            containerHeightPx = 1920,
        )

        val request = buildAuroraPosterBackgroundRequest(
            context = mockk<Context>(relaxed = true),
            data = "https://example.org/cover.jpg",
            spec = spec,
            containerWidthPx = 1080,
            containerHeightPx = 1920,
        ) {
            placeholderMemoryCacheKey("https://example.org/placeholder.jpg")
        }

        request.memoryCacheKey shouldBe spec.memoryCacheKey
        request.placeholderMemoryCacheKey?.key shouldBe "https://example.org/placeholder.jpg"
        request.sizeResolver.size() shouldBe Size(1080, 1920)
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
