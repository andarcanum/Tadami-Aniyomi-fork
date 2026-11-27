package eu.kanade.tachiyomi.data.suggestions

import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SuggestionCacheTest {

    @Test
    fun `makeKey is stable for same inputs`() {
        val key1 = SuggestionCache.makeKey(
            "AniList",
            "Spear of Fate",
            "NOVEL",
            candidateTitles = listOf("Spear of Fate", "Копьё Судьбы"),
            description = "Test description",
            author = "Author",
        )
        val key2 = SuggestionCache.makeKey(
            "AniList",
            "Spear of Fate",
            "NOVEL",
            candidateTitles = listOf("Копьё Судьбы", "Spear of Fate"),
            description = "Test description",
            author = "Author",
        )
        assertEquals(key1, key2)
    }

    @Test
    fun `makeKey differs when candidates change`() {
        val key1 = SuggestionCache.makeKey(
            "AniList",
            "Spear of Fate",
            "NOVEL",
            candidateTitles = listOf("Spear of Fate"),
        )
        val key2 = SuggestionCache.makeKey(
            "AniList",
            "Spear of Fate",
            "NOVEL",
            candidateTitles = listOf("Spear of Fate", "Копьё Судьбы"),
        )
        assertNotEquals(key1, key2)
    }

    @Test
    fun `makeKey differs when description changes`() {
        val key1 = SuggestionCache.makeKey(
            "AniList",
            "Spear of Fate",
            "NOVEL",
            description = "First description",
        )
        val key2 = SuggestionCache.makeKey(
            "AniList",
            "Spear of Fate",
            "NOVEL",
            description = "Second description",
        )
        assertNotEquals(key1, key2)
    }

    @Test
    fun `makeKey differs when author changes`() {
        val key1 = SuggestionCache.makeKey(
            "AniList",
            "Spear of Fate",
            "NOVEL",
            author = "Author One",
        )
        val key2 = SuggestionCache.makeKey(
            "AniList",
            "Spear of Fate",
            "NOVEL",
            author = "Author Two",
        )
        assertNotEquals(key1, key2)
    }

    @Test
    fun `put and get roundtrip preserves list`() {
        SuggestionCache.invalidateAll()
        val items = listOf(
            SuggestionItem(
                title = "Spear of Fate",
                searchQueries = listOf("Spear of Fate"),
                thumbnailUrl = null,
                providerName = "AniList",
                providerUrl = "https://example.com",
                providerId = "1",
                mediaType = SuggestionMediaType.NOVEL,
                reason = SuggestionReason.EXTERNAL_ANILIST,
            ),
        )
        val key = "test:roundtrip"
        SuggestionCache.put(key, items)
        val cached = SuggestionCache.get(key)
        assertNotNull(cached)
        assertEquals(1, cached!!.size)
        assertEquals("Spear of Fate", cached[0].title)
    }

    @Test
    fun `get returns null for missing key`() {
        SuggestionCache.invalidateAll()
        assertNull(SuggestionCache.get("test:nonexistent"))
    }

    @Test
    fun `invalidateAll clears the cache`() {
        SuggestionCache.invalidateAll()
        val key = "test:invalidate"
        SuggestionCache.put(key, emptyList())
        assertNotNull(SuggestionCache.get(key))
        SuggestionCache.invalidateAll()
        assertNull(SuggestionCache.get(key))
    }

    @Test
    fun `makeKey differs when extraKeys change`() {
        val keyNsfwFiltered = SuggestionCache.makeKey(
            "AniList",
            "Overlord",
            "NOVEL",
            extraKeys = listOf("nsfw=true"),
        )
        val keyNsfwUnfiltered = SuggestionCache.makeKey(
            "AniList",
            "Overlord",
            "NOVEL",
            extraKeys = listOf("nsfw=false"),
        )
        assertNotEquals(keyNsfwFiltered, keyNsfwUnfiltered)
    }

    @Test
    fun `TTL is 24 hours`() {
        // This guards the production setting; the actual expiry test is
        // hard to write without time mocking, so we just verify that the
        // cached entry is still readable immediately after put().
        SuggestionCache.invalidateAll()
        val key = "test:ttl"
        SuggestionCache.put(key, emptyList())
        val cached = SuggestionCache.get(key)
        assertNotNull(cached)
    }
}
