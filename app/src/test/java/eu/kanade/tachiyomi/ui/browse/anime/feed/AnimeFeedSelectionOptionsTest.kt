package eu.kanade.tachiyomi.ui.browse.anime.feed

import eu.kanade.presentation.browse.buildFeedSelectionOptions
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.model.FeedListingType
import tachiyomi.domain.source.model.SavedSearch

class AnimeFeedSelectionOptionsTest {

    @Test
    fun `selection options use provided labels and include saved searches`() {
        val search = SavedSearch(
            id = 7L,
            source = 2L,
            name = "My Search",
            query = "isekai",
            filtersJson = null,
        )

        val options = buildFeedSelectionOptions(
            sourceSupportsLatest = true,
            savedSearches = listOf(search),
            latestLabel = "Latest",
            popularLabel = "Popular",
        )

        options.map { it.label } shouldBe listOf("Latest", "Popular", "My Search")
        options.map { it.listingType } shouldBe listOf(
            FeedListingType.LATEST,
            FeedListingType.POPULAR,
            FeedListingType.SAVED_SEARCH,
        )
        options.last().savedSearch shouldBe search
    }

    @Test
    fun `selection options omit latest when source does not support it`() {
        val options = buildFeedSelectionOptions(
            sourceSupportsLatest = false,
            savedSearches = emptyList(),
            latestLabel = "Latest",
            popularLabel = "Popular",
        )

        options.map { it.listingType } shouldContainExactly listOf(FeedListingType.POPULAR)
    }

    @Test
    fun `subtitle uses localized labels and saved search name`() {
        buildAnimeFeedSubtitle(
            language = "English",
            listingType = FeedListingType.LATEST,
            savedSearchName = null,
            latestLabel = "Latest",
            popularLabel = "Popular",
        ) shouldBe "English · Latest"

        buildAnimeFeedSubtitle(
            language = "English",
            listingType = FeedListingType.POPULAR,
            savedSearchName = null,
            latestLabel = "Latest",
            popularLabel = "Popular",
        ) shouldBe "English · Popular"

        buildAnimeFeedSubtitle(
            language = "English",
            listingType = FeedListingType.SAVED_SEARCH,
            savedSearchName = "My Search",
            latestLabel = "Latest",
            popularLabel = "Popular",
        ) shouldBe "English · My Search"
    }
}
