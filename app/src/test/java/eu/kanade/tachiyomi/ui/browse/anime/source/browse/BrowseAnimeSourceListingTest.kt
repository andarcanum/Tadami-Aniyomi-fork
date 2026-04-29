package eu.kanade.tachiyomi.ui.browse.anime.source.browse

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.ui.browse.search.SavedSearchFilterSerializer
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.model.SourceType

class BrowseAnimeSourceListingTest {

    @Test
    fun `valueOf uses popular listing for null query`() {
        BrowseAnimeSourceScreenModel.Listing.valueOf(null)
            .shouldBeInstanceOf<BrowseAnimeSourceScreenModel.Listing.Popular>()
    }

    @Test
    fun `BrowseAnimeSourceScreenModel SavedSearch filterable is false when there are no saved searches`() {
        val state = BrowseAnimeSourceScreenModel.State(
            listing = BrowseAnimeSourceScreenModel.Listing.Popular,
        )

        state.filterable shouldBe false
    }

    @Test
    fun `BrowseAnimeSourceScreenModel SavedSearch filterable is true when saved searches exist`() {
        val state = BrowseAnimeSourceScreenModel.State(
            listing = BrowseAnimeSourceScreenModel.Listing.Popular,
            savedSearches = persistentListOf(
                SavedSearch(
                    id = 1L,
                    source = 42L,
                    sourceType = SourceType.ANIME,
                    name = "saved",
                    query = "q",
                    filtersJson = null,
                ) to false,
            ),
        )

        state.filterable shouldBe true
    }

    @Test
    fun `anime saved search filters roundtrip`() {
        val original = AnimeFilterList(
            TestText("Query", "wolf"),
            TestSelect("Sort", 1),
            TestCheckBox("Only Free", true),
            TestTriState("Dub", AnimeFilter.TriState.STATE_INCLUDE),
            TestGroup("Group", listOf(TestCheckBox("Nested", true))),
            TestSort("Order", AnimeFilter.Sort.Selection(1, true)),
        )

        val json = SavedSearchFilterSerializer.serialize(original)
        val restored = AnimeFilterList(
            TestText("Query"),
            TestSelect("Sort"),
            TestCheckBox("Only Free"),
            TestTriState("Dub"),
            TestGroup("Group", listOf(TestCheckBox("Nested"))),
            TestSort("Order"),
        )

        SavedSearchFilterSerializer.deserialize(json, restored)

        restored[0].state shouldBe "wolf"
        restored[1].state shouldBe 1
        restored[2].state shouldBe true
        restored[3].state shouldBe AnimeFilter.TriState.STATE_INCLUDE
        (restored[4] as TestGroup).state.first().state shouldBe true
        (restored[5] as TestSort).state shouldBe AnimeFilter.Sort.Selection(1, true)
    }

    private class TestSelect(
        name: String,
        state: Int = 0,
    ) : AnimeFilter.Select<String>(name, arrayOf("A", "B"), state)

    private class TestText(
        name: String,
        state: String = "",
    ) : AnimeFilter.Text(name, state)

    private class TestCheckBox(
        name: String,
        state: Boolean = false,
    ) : AnimeFilter.CheckBox(name, state)

    private class TestTriState(
        name: String,
        state: Int = AnimeFilter.TriState.STATE_IGNORE,
    ) : AnimeFilter.TriState(name, state)

    private class TestGroup(
        name: String,
        state: List<AnimeFilter<*>>,
    ) : AnimeFilter.Group<AnimeFilter<*>>(name, state)

    private class TestSort(
        name: String,
        state: AnimeFilter.Sort.Selection? = null,
    ) : AnimeFilter.Sort(name, arrayOf("A", "B"), state)
}
