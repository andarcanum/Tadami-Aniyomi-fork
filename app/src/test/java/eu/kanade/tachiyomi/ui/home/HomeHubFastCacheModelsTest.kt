package eu.kanade.tachiyomi.ui.home

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeHubFastCacheModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `legacy cached items decode with safe cover identity defaults`() {
        val hero = json.decodeFromString(
            CachedHeroItem.serializer(),
            """{"entryId":7,"title":"T","episodeNumber":3.0,"coverUrl":"u","coverLastModified":9,"subId":11}""",
        )
        assertEquals(-1L, hero.sourceId)
        assertEquals(false, hero.favorite)

        val history = json.decodeFromString(
            CachedHistoryItem.serializer(),
            """{"mangaId":7,"title":"T","chapterNumber":3.0,"coverUrl":"u","coverLastModified":9}""",
        )
        assertEquals(-1L, history.sourceId)
        assertEquals(false, history.favorite)
    }

    @Test
    fun `cached recommendation roundtrips cover identity`() {
        val original = CachedRecommendationItem(
            entryId = 1L,
            title = "T",
            coverUrl = "u",
            coverLastModified = 2L,
            totalCount = 10L,
            progressCount = 3L,
            sourceId = 42L,
            favorite = true,
        )
        val decoded = json.decodeFromString(
            CachedRecommendationItem.serializer(),
            json.encodeToString(CachedRecommendationItem.serializer(), original),
        )
        assertEquals(original, decoded)
    }
}
