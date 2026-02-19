package eu.kanade.tachiyomi.ui.browse.anime.source.browse

import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class BrowseAnimeSourceListingTest {

    @Test
    fun `valueOf uses popular listing for null query`() {
        BrowseAnimeSourceScreenModel.Listing.valueOf(null)
            .shouldBeInstanceOf<BrowseAnimeSourceScreenModel.Listing.Popular>()
    }
}
