package eu.kanade.tachiyomi.data.suggestions.novel

import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel

class NovelSearchFallbackEngineTest {

    @Test
    fun `fetchSearchFallback returns SEARCH_EMPTY when no results match similarity threshold`() = runTest {
        val novel = Novel.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-search-empty")
        val source = FakeNovelCatalogueSource()
        source.searchNovelsToReturn = listOf(
            SNovel.create().apply {
                title = "Completely Unrelated Book"
                url = "/unrelated"
            },
        )

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val engine = NovelSearchFallbackEngine()
        val outcome = engine.fetchSearchFallback(novel, source, seed)

        assertTrue(outcome is NovelFallbackOutcome.Empty)
        assertEquals(NovelFallbackReason.SEARCH_EMPTY, (outcome as NovelFallbackOutcome.Empty).reason)
    }

    @Test
    fun `fetchSearchFallback returns Success and ranks matching titles above threshold`() = runTest {
        val novel = Novel.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-search-success")
        val source = FakeNovelCatalogueSource()
        source.searchNovelsToReturn = listOf(
            SNovel.create().apply {
                title = "Solo Leveling Side Stories"
                url = "/solo-1"
            },
            SNovel.create().apply {
                title = "Solo Leveling Official"
                url = "/solo-official"
            },
            SNovel.create().apply {
                title = "Completely Unrelated Book"
                url = "/unrelated"
            },
        )

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val engine = NovelSearchFallbackEngine()
        val outcome = engine.fetchSearchFallback(novel, source, seed)

        assertTrue(outcome is NovelFallbackOutcome.Success)
        val success = outcome as NovelFallbackOutcome.Success
        assertEquals(2, success.items.size)
        assertTrue(success.items.any { it.title == "Solo Leveling Side Stories" })
        assertTrue(success.items.any { it.title == "Solo Leveling Official" })
    }

    @Test
    fun `fetchSearchFallback handles Cyrillic titles correctly`() = runTest {
        val novel = Novel.create().copy(id = 123L, title = "Атака Титанов", url = "/titan-search")
        val source = FakeNovelCatalogueSource()
        source.searchNovelsToReturn = listOf(
            SNovel.create().apply {
                title = "Атака Титанов Побочная история"
                url = "/titan-ru"
            },
        )

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = "Атака Титанов",
            candidateTitles = listOf("Атака Титанов", "Attack on Titan"),
            description = "",
        )

        val engine = NovelSearchFallbackEngine()
        val outcome = engine.fetchSearchFallback(novel, source, seed)

        assertTrue(outcome is NovelFallbackOutcome.Success)
        val success = outcome as NovelFallbackOutcome.Success
        assertEquals(1, success.items.size)
        assertEquals("Атака Титанов Побочная история", success.items.first().title)
    }

    @Test
    fun `fetchSearchFallback fills missing thumbnail from novel details`() = runTest {
        val novel = Novel.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-thumb-source-novel")
        val source = FakeNovelCatalogueSource()
        val searchResult = SNovel.create().apply {
            title = "Solo Leveling Side Stories"
            url = "/solo-thumb-result-novel"
            thumbnail_url = null
        }
        val detailResult = searchResult.copy().apply {
            thumbnail_url = "https://img.example/solo-novel.jpg"
        }
        source.searchNovelsToReturn = listOf(searchResult)
        source.detailsByUrl = mapOf(searchResult.url to detailResult)

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val outcome = NovelSearchFallbackEngine().fetchSearchFallback(novel, source, seed)

        val success = outcome as NovelFallbackOutcome.Success
        assertEquals("https://img.example/solo-novel.jpg", success.items.single().thumbnailUrl)
        assertEquals(1, source.getNovelDetailsCallCount)
    }

    @Test
    fun `fetchSearchFallback backfills from popular novels when search results are empty`() = runTest {
        val novel = Novel.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-backfill")
        val source = FakeNovelCatalogueSource()
        source.popularNovelsToReturn = List(5) { i ->
            SNovel.create().apply {
                title = "Catalog Novel $i"
                url = "/catalog-$i"
                thumbnail_url = "http://thumb/catalog-$i"
            }
        }
        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val engine = NovelSearchFallbackEngine()
        val outcome = engine.fetchSearchFallback(novel, source, seed, maxResults = 4)

        assertTrue(outcome is NovelFallbackOutcome.Success)
        val success = outcome as NovelFallbackOutcome.Success
        assertEquals(4, success.items.size)
        assertEquals("Catalog Novel 0", success.items.first().title)
        assertTrue(source.getPopularNovelsCalled)
    }

    @Test
    fun `fetchSearchFallback does not reuse smaller cached result for larger request`() = runTest {
        val novel = Novel.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-search-cache-limit")
        val source = FakeNovelCatalogueSource()
        source.popularNovelsToReturn = List(5) { i ->
            SNovel.create().apply {
                title = "Catalog Cache Novel $i"
                url = "/catalog-cache-$i"
            }
        }
        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val engine = NovelSearchFallbackEngine()
        val carouselOutcome = engine.fetchSearchFallback(novel, source, seed, maxResults = 2)
        val fullOutcome = engine.fetchSearchFallback(novel, source, seed, maxResults = 4)

        assertEquals(2, (carouselOutcome as NovelFallbackOutcome.Success).items.size)
        assertEquals(4, (fullOutcome as NovelFallbackOutcome.Success).items.size)
    }

    @Test
    fun `genre backfill is used before popular and skips popular if target is reached`() = runTest {
        val novel = Novel.create().copy(
            id = 123L,
            title = "Solo Leveling",
            url = "/solo-genre-first",
            genre = listOf("Action", "Fantasy", "Adventure"),
        )
        val source = FakeNovelCatalogueSource()
        source.searchNovelsToReturn = List(20) { i ->
            SNovel.create().apply {
                title = "Action Novel $i"
                url = "/genre-action-$i"
            }
        }
        source.popularNovelsToReturn = List(20) { i ->
            SNovel.create().apply {
                title = "Popular Novel $i"
                url = "/popular-$i"
            }
        }
        source.popularNovelsWithFiltersToReturn = source.searchNovelsToReturn
        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val engine = NovelSearchFallbackEngine()
        val outcome = engine.fetchSearchFallback(novel, source, seed, maxResults = 20)

        assertTrue(outcome is NovelFallbackOutcome.Success)
        val success = outcome as NovelFallbackOutcome.Success
        assertEquals(20, success.items.size)
        assertTrue(success.items.all { it.title.startsWith("Action Novel") })
        assertFalse(source.getPopularNovelsCalled)
    }

    @Test
    fun `popular backfill is capped for carousel when relevance layers are empty`() = runTest {
        val novel = Novel.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-popular-cap")
        val source = FakeNovelCatalogueSource()
        source.popularNovelsToReturn = List(30) { i ->
            SNovel.create().apply {
                title = "Popular Fill $i"
                url = "/popular-fill-$i"
            }
        }
        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val engine = NovelSearchFallbackEngine()
        val outcome = engine.fetchSearchFallback(novel, source, seed, maxResults = 20)

        assertTrue(outcome is NovelFallbackOutcome.Success)
        val success = outcome as NovelFallbackOutcome.Success
        assertEquals(8, success.items.size)
        assertTrue(source.getPopularNovelsCalled)
        assertEquals(1, source.getPopularNovelsCallCount)
    }
}
