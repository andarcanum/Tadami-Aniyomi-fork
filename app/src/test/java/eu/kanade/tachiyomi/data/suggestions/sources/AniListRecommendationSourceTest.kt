package eu.kanade.tachiyomi.data.suggestions.sources

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AniListRecommendationSourceTest {

    @Test
    fun `name should be AniList`() {
        val source = AniListRecommendationSource(SuggestionMediaType.ANIME)
        assertEquals("AniList", source.name)
    }

    @Test
    fun `isValidRecommendationType isolates NOVEL from regular MANGA`() {
        val method = AniListRecommendationSource::class.java
            .getDeclaredMethod("isValidRecommendationType", String::class.java, String::class.java)
            .apply { isAccessible = true }
        val novelSource = AniListRecommendationSource(SuggestionMediaType.NOVEL)
        val mangaSource = AniListRecommendationSource(SuggestionMediaType.MANGA)

        assertEquals(true, method.invoke(novelSource, "MANGA", "NOVEL"))
        assertEquals(false, method.invoke(novelSource, "MANGA", "MANGA"))
        assertEquals(false, method.invoke(novelSource, "MANGA", "ONE_SHOT"))
        assertEquals(false, method.invoke(mangaSource, "MANGA", "NOVEL"))
        assertEquals(true, method.invoke(mangaSource, "MANGA", "MANGA"))
    }
}
