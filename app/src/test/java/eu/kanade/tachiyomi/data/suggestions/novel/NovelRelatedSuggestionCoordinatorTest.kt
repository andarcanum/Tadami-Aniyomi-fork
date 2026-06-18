package eu.kanade.tachiyomi.data.suggestions.novel

import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType
import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel

class NovelRelatedSuggestionCoordinatorTest {

    @Test
    fun `fetchRelatedSuggestions returns NO_RELATED_SUPPORT when source does not support related`() = runTest {
        val novel = Novel.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-no-support")
        val source = FakeNovelCatalogueSource(supportsRelatedNovels = false)
        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val coordinator = NovelRelatedSuggestionCoordinator()
        val outcome = coordinator.fetchRelatedSuggestions(novel, source, seed)

        assertTrue(outcome is NovelFallbackOutcome.Empty)
        assertEquals(NovelFallbackReason.NO_RELATED_SUPPORT, (outcome as NovelFallbackOutcome.Empty).reason)
    }

    @Test
    fun `fetchRelatedSuggestions returns RELATED_EMPTY when source related list is empty`() = runTest {
        val novel = Novel.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-empty")
        val source = FakeNovelCatalogueSource(supportsRelatedNovels = true)
        source.relatedNovelsToReturn = emptyList()
        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val coordinator = NovelRelatedSuggestionCoordinator()
        val outcome = coordinator.fetchRelatedSuggestions(novel, source, seed)

        assertTrue(outcome is NovelFallbackOutcome.Empty)
        assertEquals(NovelFallbackReason.RELATED_EMPTY, (outcome as NovelFallbackOutcome.Empty).reason)
        assertTrue(source.getRelatedNovelsCalled)
    }

    @Test
    fun `fetchRelatedSuggestions returns Success and maps, dedupes, and caps related titles`() = runTest {
        val novel = Novel.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-success")
        val source = FakeNovelCatalogueSource(supportsRelatedNovels = true)

        val fakeRelated = List(25) { i ->
            SNovel.create().apply {
                title = "Related Novel $i"
                url = "/related-$i"
                thumbnail_url = "http://thumb/$i"
            }
        }
        // Duplicate one
        val duplicate = SNovel.create().apply {
            title = "Related Novel 0"
            url = "/related-0"
        }
        source.relatedNovelsToReturn = fakeRelated + duplicate

        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val coordinator = NovelRelatedSuggestionCoordinator()
        val outcome = coordinator.fetchRelatedSuggestions(novel, source, seed)

        assertTrue(outcome is NovelFallbackOutcome.Success)
        val success = outcome as NovelFallbackOutcome.Success

        // Assert capped at 20
        assertEquals(20, success.items.size)
        // Assert first is mapped correctly
        val first = success.items.first()
        assertEquals("Related Novel 0", first.title)
        assertEquals("/related-0", first.providerUrl)
        assertEquals("http://thumb/0", first.thumbnailUrl)
        assertEquals(source.name, first.providerName)
        assertEquals("1:/related-0", first.providerId)
        assertEquals(SuggestionMediaType.NOVEL, first.mediaType)
    }

    @Test
    fun `fetchRelatedSuggestions fills missing thumbnail from novel details`() = runTest {
        val novel = Novel.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-related-thumb")
        val source = FakeNovelCatalogueSource(supportsRelatedNovels = true)
        val related = SNovel.create().apply {
            title = "Related Novel"
            url = "/related-thumb"
            thumbnail_url = null
        }
        val details = related.copy().apply {
            thumbnail_url = "https://img.example/related.jpg"
        }
        source.relatedNovelsToReturn = listOf(related)
        source.detailsByUrl = mapOf(related.url to details)
        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val outcome = NovelRelatedSuggestionCoordinator().fetchRelatedSuggestions(novel, source, seed)

        val success = outcome as NovelFallbackOutcome.Success
        assertEquals("https://img.example/related.jpg", success.items.single().thumbnailUrl)
        assertEquals(1, source.getNovelDetailsCallCount)
    }

    @Test
    fun `fetchRelatedSuggestions can return more than carousel cap when requested`() = runTest {
        val novel = Novel.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-full")
        val source = FakeNovelCatalogueSource(supportsRelatedNovels = true)
        source.relatedNovelsToReturn = List(25) { i ->
            SNovel.create().apply {
                title = "Related Novel $i"
                url = "/related-full-$i"
            }
        }
        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val coordinator = NovelRelatedSuggestionCoordinator()
        val outcome = coordinator.fetchRelatedSuggestions(novel, source, seed, maxResults = 25)

        assertTrue(outcome is NovelFallbackOutcome.Success)
        assertEquals(25, (outcome as NovelFallbackOutcome.Success).items.size)
    }

    @Test
    fun `fetchRelatedSuggestions does not reuse smaller cached result for larger request`() = runTest {
        val novel = Novel.create().copy(id = 123L, title = "Solo Leveling", url = "/solo-cache-limit")
        val source = FakeNovelCatalogueSource(supportsRelatedNovels = true)
        source.relatedNovelsToReturn = List(25) { i ->
            SNovel.create().apply {
                title = "Related Cache Novel $i"
                url = "/related-cache-$i"
            }
        }
        val seed = SuggestionSeed(
            mediaType = SuggestionMediaType.NOVEL,
            primaryTitle = "Solo Leveling",
            candidateTitles = listOf("Solo Leveling"),
            description = "",
        )

        val coordinator = NovelRelatedSuggestionCoordinator()
        val carouselOutcome = coordinator.fetchRelatedSuggestions(novel, source, seed, maxResults = 20)
        val fullOutcome = coordinator.fetchRelatedSuggestions(novel, source, seed, maxResults = 25)

        assertEquals(20, (carouselOutcome as NovelFallbackOutcome.Success).items.size)
        assertEquals(25, (fullOutcome as NovelFallbackOutcome.Success).items.size)
    }
}
