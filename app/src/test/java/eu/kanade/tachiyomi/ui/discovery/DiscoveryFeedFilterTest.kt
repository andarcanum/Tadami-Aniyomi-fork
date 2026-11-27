package eu.kanade.tachiyomi.ui.discovery

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import tachiyomi.domain.discovery.model.DiscoverySuggestion

class DiscoveryFeedFilterTest {

    private fun item(
        title: String,
        genres: List<String> = emptyList(),
        rowType: DiscoveryRowType = DiscoveryRowType.TREND,
        reason: String? = null,
    ) = DiscoverySuggestion(
        id = 0L,
        mediaType = DiscoveryMediaType.NOVEL,
        rowType = rowType,
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        reason = reason,
        seedTitle = null,
        provider = "test",
        score = 1.0,
        position = 0L,
        createdAt = 0L,
        genres = genres,
    )

    @Test
    fun `extractAvailableGenres extracts top genres ordered by frequency`() {
        val items = listOf(
            item("1", genres = listOf("Fantasy", "Action")),
            item("2", genres = listOf("Fantasy", "Romance")),
            item("3", genres = listOf("Fantasy", "Action")),
            item("4", rowType = DiscoveryRowType.TASTE, reason = "Action, Drama"),
        )
        val genres = extractAvailableGenres(items, maxGenres = 3)
        // Fantasy: 3, Action: 3, Romance: 1, Drama: 1 -> top 3 should include Fantasy, Action, and one of Romance/Drama
        genres.take(2) shouldContainExactly listOf("Fantasy", "Action")
        genres.size shouldBe 3
    }

    @Test
    fun `filterDiscoveryFeed with null genre returns all items`() {
        val items = listOf(
            item("1", genres = listOf("Fantasy")),
            item("2", genres = listOf("Action")),
        )
        filterDiscoveryFeed(items, selectedGenre = null) shouldBe items
    }

    @Test
    fun `filterDiscoveryFeed filters items by genre matching multilingual variants`() {
        val items = listOf(
            item("1", genres = listOf("Fantasy", "Action")),
            item("2", genres = listOf("Romance")),
            item("3", rowType = DiscoveryRowType.TASTE, reason = "Фэнтези"),
        )
        val fantasyItems = filterDiscoveryFeed(items, selectedGenre = "Fantasy")
        fantasyItems.map { it.title } shouldContainExactly listOf("1", "3")

        val romanceItems = filterDiscoveryFeed(items, selectedGenre = "Romance")
        romanceItems.map { it.title } shouldContainExactly listOf("2")
    }

    @Test
    fun `filterDiscoveryFeed with non-matching genre returns empty list`() {
        val items = listOf(
            item("1", genres = listOf("Fantasy")),
        )
        filterDiscoveryFeed(items, selectedGenre = "Horror") shouldBe emptyList()
    }
}
