package eu.kanade.presentation.entries.components.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

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
}
