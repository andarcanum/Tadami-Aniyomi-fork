package eu.kanade.tachiyomi.extension.novel.kotlin

import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class KotlinNovelFilterAdapterTest {

    private val adapter: KotlinNovelFilterAdapter = KotlinNovelFilterAdapterImpl()

    @Test
    fun `toNovelFilterList maps basic header and separator filters`() {
        val mangaFilters = FilterList(
            Filter.Header("My Header"),
            Filter.Separator("My Separator"),
        )

        val novelFilters = adapter.toNovelFilterList(mangaFilters)

        novelFilters.size shouldBe 2

        val header = novelFilters[0]
        header.shouldBeInstanceOf<NovelFilter.Header>()
        header.name shouldBe "My Header"

        val separator = novelFilters[1]
        separator.shouldBeInstanceOf<NovelFilter.Separator>()
        separator.name shouldBe "My Separator"
    }

    @Test
    fun `toNovelFilterList maps stateful filters`() {
        val mangaFilters = FilterList(
            object : Filter.CheckBox("Checkbox 1", true) {},
            object : Filter.TriState("Tristate 1", Filter.TriState.STATE_EXCLUDE) {},
            object : Filter.Text("Text 1", "hello") {},
            object : Filter.Select<String>("Select 1", arrayOf("A", "B", "C"), 1) {},
            object : Filter.Sort("Sort 1", arrayOf("Name", "Date"), Filter.Sort.Selection(1, false)) {},
        )

        val novelFilters = adapter.toNovelFilterList(mangaFilters)

        novelFilters.size shouldBe 5

        (novelFilters[0] as NovelFilter.CheckBox).state shouldBe true
        (novelFilters[1] as NovelFilter.TriState).state shouldBe NovelFilter.TriState.STATE_EXCLUDE
        (novelFilters[2] as NovelFilter.Text).state shouldBe "hello"

        val select = novelFilters[3] as NovelFilter.Select<*>
        select.values shouldBe arrayOf("A", "B", "C")
        select.state shouldBe 1

        val sort = novelFilters[4] as NovelFilter.Sort
        sort.values shouldBe arrayOf("Name", "Date")
        sort.state shouldBe NovelFilter.Sort.Selection(1, false)
    }

    @Test
    fun `toNovelFilterList maps group filters recursively`() {
        val mangaFilters = FilterList(
            object : Filter.Group<Filter<*>>(
                "Group 1",
                listOf(
                    object : Filter.CheckBox("Child Checkbox", true) {},
                ),
            ) {},
        )

        val novelFilters = adapter.toNovelFilterList(mangaFilters)

        novelFilters.size shouldBe 1
        val group = novelFilters[0] as NovelFilter.Group<*>
        group.name shouldBe "Group 1"
        group.state.size shouldBe 1

        val child = group.state[0] as NovelFilter.CheckBox
        child.name shouldBe "Child Checkbox"
        child.state shouldBe true
    }

    @Test
    fun `toMangaFilterList copies modified state back to original filters`() {
        val catalogueSource = object : DummyCatalogueSource() {
            override fun getFilterList(): FilterList {
                return FilterList(
                    CustomCheckbox("Checkbox 1", false),
                    CustomTristate("Tristate 1", Filter.TriState.STATE_IGNORE),
                    CustomText("Text 1", ""),
                    CustomSelect("Select 1", arrayOf("A", "B", "C"), 0),
                    CustomSort("Sort 1", arrayOf("Name", "Date"), null),
                )
            }
        }

        val novelFilters = NovelFilterList(
            object : NovelFilter.CheckBox("Checkbox 1", true) {},
            object : NovelFilter.TriState("Tristate 1", NovelFilter.TriState.STATE_INCLUDE) {},
            object : NovelFilter.Text("Text 1", "world") {},
            object : NovelFilter.Select<Any?>("Select 1", arrayOf("A", "B", "C"), 2) {},
            object : NovelFilter.Sort("Sort 1", arrayOf("Name", "Date"), NovelFilter.Sort.Selection(1, true)) {},
        )

        val mappedMangaFilters = adapter.toMangaFilterList(novelFilters, catalogueSource)

        mappedMangaFilters.size shouldBe 5

        val cb = mappedMangaFilters[0] as CustomCheckbox
        cb.shouldBeInstanceOf<CustomCheckbox>()
        cb.state shouldBe true

        val ts = mappedMangaFilters[1] as CustomTristate
        ts.shouldBeInstanceOf<CustomTristate>()
        ts.state shouldBe Filter.TriState.STATE_INCLUDE

        val txt = mappedMangaFilters[2] as CustomText
        txt.shouldBeInstanceOf<CustomText>()
        txt.state shouldBe "world"

        val sel = mappedMangaFilters[3] as CustomSelect
        sel.shouldBeInstanceOf<CustomSelect>()
        sel.state shouldBe 2

        val sort = mappedMangaFilters[4] as CustomSort
        sort.shouldBeInstanceOf<CustomSort>()
        sort.state shouldBe Filter.Sort.Selection(1, true)
    }

    @Test
    fun `toMangaFilterList copies nested group states recursively`() {
        val catalogueSource = object : DummyCatalogueSource() {
            override fun getFilterList(): FilterList {
                return FilterList(
                    CustomGroup(
                        "Group 1",
                        listOf(CustomCheckbox("Child Checkbox", false)),
                    ),
                )
            }
        }

        val novelFilters = NovelFilterList(
            object : NovelFilter.Group<NovelFilter<*>>(
                "Group 1",
                listOf(object : NovelFilter.CheckBox("Child Checkbox", true) {}),
            ) {},
        )

        val mappedMangaFilters = adapter.toMangaFilterList(novelFilters, catalogueSource)

        mappedMangaFilters.size shouldBe 1
        val group = mappedMangaFilters[0] as CustomGroup
        group.state.size shouldBe 1

        val child = group.state[0] as CustomCheckbox
        child.state shouldBe true
    }

    @Test
    fun `toMangaFilterList copies state correctly even if filters are reordered`() {
        val catalogueSource = object : DummyCatalogueSource() {
            override fun getFilterList(): FilterList {
                return FilterList(
                    CustomCheckbox("Checkbox A", false),
                    CustomCheckbox("Checkbox B", false),
                )
            }
        }

        val novelFilters = NovelFilterList(
            object : NovelFilter.CheckBox("Checkbox B", true) {},
            object : NovelFilter.CheckBox("Checkbox A", true) {},
        )

        val mappedMangaFilters = adapter.toMangaFilterList(novelFilters, catalogueSource)

        mappedMangaFilters.size shouldBe 2

        val cbA = mappedMangaFilters[0] as CustomCheckbox
        cbA.name shouldBe "Checkbox A"
        cbA.state shouldBe true

        val cbB = mappedMangaFilters[1] as CustomCheckbox
        cbB.name shouldBe "Checkbox B"
        cbB.state shouldBe true
    }

    @Test
    fun `toMangaFilterList copies state correctly for filters with duplicate names at different positions`() {
        val catalogueSource = object : DummyCatalogueSource() {
            override fun getFilterList(): FilterList {
                return FilterList(
                    CustomCheckbox("Include", false),
                    CustomCheckbox("Include", false),
                )
            }
        }

        val novelFilters = NovelFilterList(
            object : NovelFilter.CheckBox("Include", true) {},
            object : NovelFilter.CheckBox("Include", false) {},
        )

        val mappedMangaFilters = adapter.toMangaFilterList(novelFilters, catalogueSource)

        mappedMangaFilters.size shouldBe 2
        (mappedMangaFilters[0] as CustomCheckbox).state shouldBe true
        (mappedMangaFilters[1] as CustomCheckbox).state shouldBe false
    }

    // Helper custom filter classes to test identity preservation
    private class CustomCheckbox(name: String, state: Boolean) : Filter.CheckBox(name, state)
    private class CustomTristate(name: String, state: Int) : Filter.TriState(name, state)
    private class CustomText(name: String, state: String) : Filter.Text(name, state)
    private class CustomSelect(
        name: String,
        values: Array<String>,
        state: Int,
    ) : Filter.Select<String>(name, values, state)
    private class CustomSort(name: String, values: Array<String>, state: Selection?) : Filter.Sort(name, values, state)
    private class CustomGroup(name: String, state: List<Filter<*>>) : Filter.Group<Filter<*>>(name, state)

    private open class DummyCatalogueSource : CatalogueSource {
        override val id: Long = 1L
        override val name: String = "Dummy"
        override val lang: String = "en"
        override val supportsLatest: Boolean = false
        override fun getFilterList() = FilterList()
        override suspend fun getPopularManga(page: Int) = throw NotImplementedError()
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) = throw NotImplementedError()
        override suspend fun getLatestUpdates(page: Int) = throw NotImplementedError()
    }
}
