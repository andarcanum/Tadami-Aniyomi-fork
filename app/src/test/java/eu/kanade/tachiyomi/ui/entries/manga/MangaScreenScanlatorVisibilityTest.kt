package eu.kanade.tachiyomi.ui.entries.manga

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaScreenScanlatorVisibilityTest {

    @Test
    fun `selector is shown when preference is enabled`() {
        shouldShowMangaScanlatorSelector(
            isPreferenceEnabled = true,
            sourceBaseUrl = "https://example.org",
        ) shouldBe true
    }

    @Test
    fun `selector is hidden when preference is disabled and source is not inkstory`() {
        shouldShowMangaScanlatorSelector(
            isPreferenceEnabled = false,
            sourceBaseUrl = "https://example.org",
        ) shouldBe false
    }

    @Test
    fun `selector stays visible for inkstory sources even when preference is disabled`() {
        shouldShowMangaScanlatorSelector(
            isPreferenceEnabled = false,
            sourceBaseUrl = "https://inkstory.net",
        ) shouldBe true

        shouldShowMangaScanlatorSelector(
            isPreferenceEnabled = false,
            sourceBaseUrl = "https://api.inkstory.net/v2",
        ) shouldBe true
    }

    @Test
    fun `inkstory host detector ignores invalid or empty urls`() {
        isInkStoryBaseUrl(null) shouldBe false
        isInkStoryBaseUrl("") shouldBe false
        isInkStoryBaseUrl("not a url") shouldBe false
    }
}
