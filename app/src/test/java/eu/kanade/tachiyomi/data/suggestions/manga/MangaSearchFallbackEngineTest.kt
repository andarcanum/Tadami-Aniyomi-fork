package eu.kanade.tachiyomi.data.suggestions.manga

import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga

class MangaSearchFallbackEngineTest {

    @Test
    fun `fetchSearchFallback returns Success and ranks matching titles above threshold`() = runTest {
        val manga = Manga.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-search-success")
        val source = FakeMangaCatalogueSource()
        source.searchMangasToReturn = listOf(
            SManga.create().apply {
                title = "Solo Leveling Side Stories"
                url = "/solo-1"
            },
            SManga.create().apply {
                title = "Solo Leveling Official"
                url = "/solo-official"
            },
            SManga.create().apply {
                title = "Completely Unrelated Book"
                url = "/unrelated"
            },
        )

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.MANGA,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val engine = MangaSearchFallbackEngine()
        val outcome = engine.fetchSearchFallback(manga, source, seed)

        assertTrue(outcome is MangaFallbackOutcome.Success)
        val success = outcome as MangaFallbackOutcome.Success
        assertEquals(2, success.items.size)
        assertTrue(success.items.any { it.title == "Solo Leveling Side Stories" })
        assertTrue(success.items.any { it.title == "Solo Leveling Official" })
    }

    @Test
    fun `fetchSearchFallback fills missing thumbnail from manga details`() = runTest {
        val manga = Manga.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-thumb-source")
        val source = FakeMangaCatalogueSource()
        val searchResult = SManga.create().apply {
            title = "Solo Leveling Side Stories"
            url = "/solo-thumb-result"
            thumbnail_url = null
        }
        val detailResult = searchResult.copy().apply {
            thumbnail_url = "https://img.example/solo.jpg"
        }
        source.searchMangasToReturn = listOf(searchResult)
        source.detailsByUrl = mapOf(searchResult.url to detailResult)

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.MANGA,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val outcome = MangaSearchFallbackEngine().fetchSearchFallback(manga, source, seed)

        val success = outcome as MangaFallbackOutcome.Success
        assertEquals("https://img.example/solo.jpg", success.items.single().thumbnailUrl)
        assertEquals(1, source.getMangaDetailsCallCount)
    }

    @Test
    fun `fetchSearchFallback filters out franchise duplicates`() = runTest {
        val manga = Manga.create().copy(id = 123L, title = "Solo Leveling Vol 1", url = "/solo-1")
        val source = FakeMangaCatalogueSource()
        source.searchMangasToReturn = listOf(
            SManga.create().apply {
                title = "Solo Leveling Vol 2"
                url = "/solo-2"
            },
            SManga.create().apply {
                title = "Solo Leveling Season 2"
                url = "/solo-season-2"
            },
        )

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.MANGA,
            primaryTitle = "Solo Leveling Vol 1",
            candidateTitles = listOf("Solo Leveling Vol 1"),
            description = "",
        )

        val engine = MangaSearchFallbackEngine()
        val outcome = engine.fetchSearchFallback(manga, source, seed)

        // Volume 2 and Season 2 should be filtered out by franchise duplicates filter
        assertTrue(outcome is MangaFallbackOutcome.Empty)
    }

    @Test
    fun `fetchSearchFallback handles Author search and filters correctly`() = runTest {
        val manga = Manga.create().copy(
            id = 123L,
            title = "Solo Leveling",
            author = "Chugong",
            url = "/solo-search-author",
        )
        val source = FakeMangaCatalogueSource()
        source.searchMangasToReturn = listOf(
            SManga.create().apply {
                title = "Overgeared"
                url = "/overgeared"
            },
        )

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.MANGA,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val engine = MangaSearchFallbackEngine()
        val outcome = engine.fetchSearchFallback(manga, source, seed)

        // Searching by author "Chugong" returned "Overgeared", which should be accepted with author baseline score
        assertTrue(outcome is MangaFallbackOutcome.Success)
        val success = outcome as MangaFallbackOutcome.Success
        assertEquals(1, success.items.size)
        assertEquals("Overgeared", success.items.first().title)
    }

    @Test
    fun `fetchSearchFallback handles Cyrillic author and genre search correctly`() = runTest {
        val manga = Manga.create().copy(
            id = 123L,
            title = "Магическая битва",
            author = "Гэгэ Акутами",
            genre = listOf("Сёнен", "Фэнтези"),
            url = "/jujutsu-ru",
        )
        val source = FakeMangaCatalogueSource()
        source.searchMangasToReturn = listOf(
            SManga.create().apply {
                title = "Волейбол"
                url = "/haikyu-ru"
            },
        )

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.MANGA,
            primaryTitle = "Магическая битва",
            candidateTitles = listOf("Магическая битва", "Jujutsu Kaisen"),
            description = "",
        )

        val engine = MangaSearchFallbackEngine()
        val outcome = engine.fetchSearchFallback(manga, source, seed)

        assertTrue(outcome is MangaFallbackOutcome.Success)
        val success = outcome as MangaFallbackOutcome.Success
        assertEquals(1, success.items.size)
        assertEquals("Волейбол", success.items.first().title)
    }
}
