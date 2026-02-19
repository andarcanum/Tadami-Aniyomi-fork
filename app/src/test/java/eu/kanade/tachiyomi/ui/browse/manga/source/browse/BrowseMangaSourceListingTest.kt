package eu.kanade.tachiyomi.ui.browse.manga.source.browse

import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class BrowseMangaSourceListingTest {

    @Test
    fun `valueOf uses popular listing for null query`() {
        BrowseMangaSourceScreenModel.Listing.valueOf(null)
            .shouldBeInstanceOf<BrowseMangaSourceScreenModel.Listing.Popular>()
    }
}
