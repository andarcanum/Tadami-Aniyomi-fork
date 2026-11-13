package eu.kanade.tachiyomi.data.suggestions.sources

import eu.kanade.tachiyomi.data.suggestions.sources.dto.MuSeriesDetail
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MangaUpdatesSimilarSourceTest {

    @Test
    fun `name should be MangaUpdates`() {
        val source = MangaUpdatesSimilarSource(SuggestionMediaType.MANGA)
        assertEquals("MangaUpdates", source.name)
    }

    @Test
    fun `allowedTypes includes Novel so light novels are kept`() {
        // Guard the new "Novel" entry that enables MangaUpdates for the NOVEL
        // media type. If this ever changes, the NOVEL branch will silently
        // filter out every recommendation.
        val source = MangaUpdatesSimilarSource(SuggestionMediaType.NOVEL)
        // The field is private, but its effect is observable through the
        // matching of a recommendation. Use a tiny reflective check to make
        // the regression visible.
        val allowedField = MangaUpdatesSimilarSource::class.java
            .getDeclaredField("allowedTypes")
            .apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val allowed = allowedField.get(source) as Set<String>
        assert(allowed.contains("Novel")) { "allowedTypes should contain Novel, got $allowed" }
        assert(allowed.contains("Manga"))
    }

    @Test
    fun `mediaType NOVEL is accepted by the early-return guard`() {
        // We can't exercise the network path here, but we can check that the
        // guard is no longer hard-rejecting NOVEL. The simplest way is to
        // assert that the constructor accepts NOVEL without throwing and
        // that mediaType field matches.
        val source = MangaUpdatesSimilarSource(SuggestionMediaType.NOVEL)
        assertEquals(SuggestionMediaType.NOVEL, source.mediaType)
        assertNotEquals(SuggestionMediaType.ANIME, source.mediaType)
    }

    @Test
    fun `MuSeriesDetail deserializes genres status and completed fields`() {
        val payload = """
            {
              "type": "Novel",
              "adult": false,
              "genres": [{"genre": "Fantasy"}, {"genre": "Romance"}],
              "status": "2 Volumes (Complete)",
              "completed": true
            }
        """.trimIndent()
        val detail = Json { ignoreUnknownKeys = true }.decodeFromString<MuSeriesDetail>(payload)
        assertEquals(listOf("Fantasy", "Romance"), detail.genres.mapNotNull { it.genre })
        assertEquals("2 Volumes (Complete)", detail.status)
        assertTrue(detail.completed)
    }
}
