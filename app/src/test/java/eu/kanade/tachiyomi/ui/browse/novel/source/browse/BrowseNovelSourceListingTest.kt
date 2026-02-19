package eu.kanade.tachiyomi.ui.browse.novel.source.browse

import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class BrowseNovelSourceListingTest {

    @Test
    fun `valueOf uses popular listing for null query`() {
        BrowseNovelSourceScreenModel.Listing.valueOf(null)
            .shouldBeInstanceOf<BrowseNovelSourceScreenModel.Listing.Popular>()
    }

    @Test
    fun `visible filters hide sort in latest listing`() {
        val sort = object : NovelFilter.Sort(
            name = "Sort",
            values = arrayOf("Popular", "Latest"),
            state = NovelFilter.Sort.Selection(index = 0, ascending = true),
        ) {}
        val text = object : NovelFilter.Text("Keyword", "") {}
        val filters = NovelFilterList(sort, text)

        val visible = visibleNovelFiltersForListing(
            listing = BrowseNovelSourceScreenModel.Listing.Latest,
            filters = filters,
        )

        visible.any { it is NovelFilter.Sort } shouldBe false
        visible.any { it is NovelFilter.Text } shouldBe true
    }

    @Test
    fun `visible filters keep sort outside latest listing`() {
        val sort = object : NovelFilter.Sort(
            name = "Sort",
            values = arrayOf("Popular", "Latest"),
            state = NovelFilter.Sort.Selection(index = 0, ascending = true),
        ) {}
        val filters = NovelFilterList(sort)

        val visible = visibleNovelFiltersForListing(
            listing = BrowseNovelSourceScreenModel.Listing.Popular,
            filters = filters,
        )

        visible.any { it is NovelFilter.Sort } shouldBe true
    }

    @Test
    fun `visible filters hide sort-like select in latest listing`() {
        val sortLikeSelect = object : NovelFilter.Select<String>(
            name = "Sort by",
            values = arrayOf("Popular", "Latest"),
            state = 0,
        ) {}
        val text = object : NovelFilter.Text("Keyword", "") {}
        val filters = NovelFilterList(sortLikeSelect, text)

        val visible = visibleNovelFiltersForListing(
            listing = BrowseNovelSourceScreenModel.Listing.Latest,
            filters = filters,
        )

        visible.any { it is NovelFilter.Select<*> } shouldBe false
        visible.any { it is NovelFilter.Text } shouldBe true
    }

    @Test
    fun `visible filters hide nested sort in latest listing`() {
        val nestedSort = object : NovelFilter.Sort(
            name = "Sort",
            values = arrayOf("Popular", "Latest"),
            state = NovelFilter.Sort.Selection(index = 0, ascending = true),
        ) {}
        val nestedText = object : NovelFilter.Text("Keyword", "") {}
        val group = object : NovelFilter.Group<NovelFilter<*>>(
            name = "Advanced",
            state = listOf(nestedSort, nestedText),
        ) {}
        val filters = NovelFilterList(group)

        val visible = visibleNovelFiltersForListing(
            listing = BrowseNovelSourceScreenModel.Listing.Latest,
            filters = filters,
        )

        val visibleGroup = visible.first() as NovelFilter.Group<*>
        visibleGroup.state.any { it is NovelFilter.Sort } shouldBe false
        visibleGroup.state.any { it is NovelFilter.Text } shouldBe true
    }
}
