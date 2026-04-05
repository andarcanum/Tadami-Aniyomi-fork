package eu.kanade.domain.anime.interactor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MetadataSearchUtilsTest {

    @Test
    fun `normalizes season suffixes from search query`() {
        normalizeMetadataSearchQuery("  My Anime Season 2  ") shouldBe "My Anime"
        normalizeMetadataSearchQuery("Аниме Сезон 3") shouldBe "Аниме"
    }

    @Test
    fun `chooses fallback poster when primary is a placeholder`() {
        chooseBestPosterUrl("missing_poster.png", "https://example.org/poster.jpg") shouldBe
            "https://example.org/poster.jpg"
    }

    @Test
    fun `builds medium fallback from large poster url`() {
        buildMediumPosterFallback("https://example.org/images/large/poster.jpg") shouldBe
            "https://example.org/images/medium/poster.jpg"
    }
}
