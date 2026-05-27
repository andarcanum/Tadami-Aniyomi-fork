package eu.kanade.tachiyomi.ui.browse.anime.feed

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

        val options = buildAnimeFeedSelectionOptions(
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
