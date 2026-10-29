package eu.kanade.tachiyomi.data.suggestions.anime

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime

class AnimeSearchFallbackEngineTest {

    @Test
    fun `fetchSearchFallback returns Success and ranks matching titles above threshold`() = runTest {
        val anime = Anime.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-search-success")
        val source = FakeAnimeCatalogueSource()
        source.searchAnimesToReturn = listOf(
            SAnime.create().apply {
                title = "Solo Leveling Side Stories"
                url = "/solo-1"
            },
            SAnime.create().apply {
                title = "Solo Leveling Official"
                url = "/solo-official"
            },
            SAnime.create().apply {
                title = "Completely Unrelated Book"
                url = "/unrelated"
            },
        )

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.ANIME,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val engine = AnimeSearchFallbackEngine()
        val outcome = engine.fetchSearchFallback(anime, source, seed)

        assertTrue(outcome is AnimeFallbackOutcome.Success)
        val success = outcome as AnimeFallbackOutcome.Success
        assertEquals(2, success.items.size)
        assertTrue(success.items.any { it.title == "Solo Leveling Side Stories" })
        assertTrue(success.items.any { it.title == "Solo Leveling Official" })
    }

    @Test
    fun `fetchSearchFallback fills missing thumbnail from anime details`() = runTest {
        val anime = Anime.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-thumb-source-anime")
        val source = FakeAnimeCatalogueSource()
        val searchResult = SAnime.create().apply {
            title = "Solo Leveling Side Stories"
            url = "/solo-thumb-result-anime"
            thumbnail_url = null
        }
        val detailResult = searchResult.copy().apply {
            thumbnail_url = "https://img.example/solo-anime.jpg"
        }
        source.searchAnimesToReturn = listOf(searchResult)
        source.detailsByUrl = mapOf(searchResult.url to detailResult)

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.ANIME,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val outcome = AnimeSearchFallbackEngine().fetchSearchFallback(anime, source, seed)

        val success = outcome as AnimeFallbackOutcome.Success
        assertEquals("https://img.example/solo-anime.jpg", success.items.single().thumbnailUrl)
        assertEquals(1, source.getAnimeDetailsCallCount)
    }

    @Test
    fun `fetchSearchFallback filters out franchise duplicates`() = runTest {
        val anime = Anime.create().copy(id = 123L, title = "Solo Leveling Vol 1", url = "/solo-1")
        val source = FakeAnimeCatalogueSource()
        source.searchAnimesToReturn = listOf(
            SAnime.create().apply {
                title = "Solo Leveling Vol 2"
                url = "/solo-2"
            },
            SAnime.create().apply {
                title = "Solo Leveling Season 2"
                url = "/solo-season-2"
            },
        )

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.ANIME,
            primaryTitle = "Solo Leveling Vol 1",
            candidateTitles = listOf("Solo Leveling Vol 1"),
            description = "",
        )

        val engine = AnimeSearchFallbackEngine()
        val outcome = engine.fetchSearchFallback(anime, source, seed)

        // Volume 2 and Season 2 should be filtered out by franchise duplicates filter
        assertTrue(outcome is AnimeFallbackOutcome.Empty)
    }

    @Test
    fun `fetchSearchFallback handles Author search and filters correctly`() = runTest {
        val anime = Anime.create().copy(
            id = 123L,
            title = "Solo Leveling",
            author = "Chugong",
            url = "/solo-search-author",
        )
        val source = FakeAnimeCatalogueSource()
        source.searchAnimesToReturn = listOf(
            SAnime.create().apply {
                title = "Overgeared"
                url = "/overgeared"
            },
        )

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.ANIME,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val engine = AnimeSearchFallbackEngine()
        val outcome = engine.fetchSearchFallback(anime, source, seed)

        // Searching by author "Chugong" returned "Overgeared", which should be accepted with author baseline score
        assertTrue(outcome is AnimeFallbackOutcome.Success)
        val success = outcome as AnimeFallbackOutcome.Success
        assertEquals(1, success.items.size)
        assertEquals("Overgeared", success.items.first().title)
    }

    @Test
    fun `fetchSearchFallback handles Cyrillic author and genre search correctly`() = runTest {
        val anime = Anime.create().copy(
            id = 123L,
            title = "Магическая битва",
            author = "Гэгэ Акутами",
            genre = listOf("Сёнен", "Фэнтези"),
            url = "/jujutsu-ru",
        )
        val source = FakeAnimeCatalogueSource()
        source.searchAnimesToReturn = listOf(
            SAnime.create().apply {
                title = "Волейбол"
                url = "/haikyu-ru"
            },
        )

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.ANIME,
            primaryTitle = "Магическая битва",
            candidateTitles = listOf("Магическая битва", "Jujutsu Kaisen"),
            description = "",
        )

        val engine = AnimeSearchFallbackEngine()
        val outcome = engine.fetchSearchFallback(anime, source, seed)

        assertTrue(outcome is AnimeFallbackOutcome.Success)
        val success = outcome as AnimeFallbackOutcome.Success
        assertEquals(1, success.items.size)
        assertEquals("Волейбол", success.items.first().title)
    }

    @Test
    fun `fetchSearchFallback survives getFilterList LinkageError and still searches`() = runTest {
        val anime = Anime.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-link-error")
        val source = object : FakeAnimeCatalogueSource() {
            override fun getFilterList(): AnimeFilterList {
                throw NoSuchMethodError(
                    "No static method runBlockingK in class kotlinx.coroutines.BuildersKt",
                )
            }
        }
        source.searchAnimesToReturn = listOf(
            SAnime.create().apply {
                title = "Solo Leveling Side Stories"
                url = "/solo-1"
            },
        )

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.ANIME,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val outcome = AnimeSearchFallbackEngine().fetchSearchFallback(anime, source, seed)

        assertTrue(outcome is AnimeFallbackOutcome.Success)
        assertEquals(1, (outcome as AnimeFallbackOutcome.Success).items.size)
    }

    @Test
    fun `fetchSearchFallback contains extension OutOfMemoryError and stops remaining tiers`() = runTest {
        val anime = Anime.create().copy(
            id = 123L,
            title = "Solo Leveling",
            author = "Chugong",
            genre = listOf("Fantasy"),
            url = "/solo-oom-abort-anime",
        )
        var searchCalls = 0
        val source = object : FakeAnimeCatalogueSource() {
            override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
                searchCalls++
                throw OutOfMemoryError("Failed to allocate a 8388624 byte allocation")
            }
        }

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.ANIME,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val outcome = AnimeSearchFallbackEngine().fetchSearchFallback(anime, source, seed)

        assertTrue(outcome is AnimeFallbackOutcome.Empty)
        // Tier 1 is the only tier that may run: the heap is already exhausted, so the
        // author/genre tiers must not issue more requests against the same source.
        assertEquals(1, searchCalls)
    }

    @Test
    fun `fetchSearchFallback keeps already collected items when a later query runs out of memory`() = runTest {
        val anime = Anime.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-oom-retain-anime")
        val source = object : FakeAnimeCatalogueSource() {
            override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
                if (query == "Solo Leveling") {
                    return AnimesPage(
                        listOf(
                            SAnime.create().apply {
                                title = "Solo Leveling Side Stories"
                                url = "/solo-oom-result-anime"
                            },
                        ),
                        false,
                    )
                }
                throw OutOfMemoryError("Failed to allocate a 8388624 byte allocation")
            }
        }

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.ANIME,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val outcome = AnimeSearchFallbackEngine().fetchSearchFallback(anime, source, seed)

        val success = outcome as AnimeFallbackOutcome.Success
        assertEquals(1, success.items.size)
        assertEquals("Solo Leveling Side Stories", success.items.single().title)
    }
}
