package eu.kanade.tachiyomi.data.suggestions

import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SuggestionItemTest {

    @Test
    fun `searchQuery accessor returns first non-blank entry`() {
        val item = SuggestionItem(
            title = "Spear of Fate",
            searchQueries = listOf("", "  ", "Копьё Судьбы", "spear of fate"),
            thumbnailUrl = null,
            providerName = "AniList",
            providerUrl = "https://example.com",
            providerId = "1",
            mediaType = SuggestionMediaType.NOVEL,
            reason = SuggestionReason.EXTERNAL_ANILIST,
        )
        assertEquals("Копьё Судьбы", item.searchQuery)
    }

    @Test
    fun `searchQuery accessor falls back to title when all queries are blank`() {
        val item = SuggestionItem(
            title = "Spear of Fate",
            searchQueries = listOf("", "  "),
            thumbnailUrl = null,
            providerName = "AniList",
            providerUrl = "https://example.com",
            providerId = "1",
            mediaType = SuggestionMediaType.NOVEL,
        )
        assertEquals("Spear of Fate", item.searchQuery)
    }

    @Test
    fun `searchQuery default to title when searchQueries not provided`() {
        val item = SuggestionItem(
            title = "Spear of Fate",
            thumbnailUrl = null,
            providerName = "MangaUpdates",
            providerUrl = "https://example.com",
            providerId = "2",
            mediaType = SuggestionMediaType.NOVEL,
            reason = SuggestionReason.EXTERNAL_MU,
        )
        assertEquals(listOf("Spear of Fate"), item.searchQueries)
        assertEquals("Spear of Fate", item.searchQuery)
    }

    @Test
    fun `default reason is SEARCH_TITLE`() {
        val item = SuggestionItem(
            title = "Spear of Fate",
            thumbnailUrl = null,
            providerName = "MangaUpdates",
            providerUrl = "https://example.com",
            providerId = "1",
            mediaType = SuggestionMediaType.NOVEL,
        )
        assertEquals(SuggestionReason.SEARCH_TITLE, item.reason)
    }

    @Test
    fun `searchQueries preserves all provided variants`() {
        val queries = listOf("Копьё Судьбы", "Spear of Fate", "spear of fate", "운명의 창")
        val item = SuggestionItem(
            title = "Spear of Fate",
            searchQueries = queries,
            thumbnailUrl = null,
            providerName = "MangaUpdates",
            providerUrl = "https://example.com",
            providerId = "1",
            mediaType = SuggestionMediaType.NOVEL,
            reason = SuggestionReason.SEARCH_TITLE,
        )
        assertEquals(queries, item.searchQueries)
        assertTrue(item.searchQueries.contains("Копьё Судьбы"))
        assertTrue(item.searchQueries.contains("운명의 창"))
    }

    @Test
    fun `all SuggestionReason values are covered`() {
        // Guard against silent removal of a reason enum value.
        val expected = setOf(
            "RELATED",
            "EXTERNAL_ANILIST",
            "EXTERNAL_MAL",
            "EXTERNAL_MU",
            "EXTERNAL_NU",
            "EXTERNAL_SHIKIMORI",
            "SEARCH_TITLE",
            "SEARCH_AUTHOR",
            "SEARCH_GENRE",
            "POPULAR_BACKFILL",
        )
        val actual = SuggestionReason.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }
}
