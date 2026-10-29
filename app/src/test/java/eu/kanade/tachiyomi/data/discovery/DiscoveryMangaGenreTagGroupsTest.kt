package eu.kanade.tachiyomi.data.discovery

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Fixtures preserve the distinct query fields and option IDs used by LibGroup. */
class DiscoveryMangaGenreTagGroupsTest {
    private class IdOption(name: String, val id: String) : Filter.TriState(name)
    private class Genres(options: List<IdOption>) : Filter.Group<IdOption>("Жанры", options)
    private class Tags(options: List<IdOption>) : Filter.Group<IdOption>("Теги", options)
    private class Statuses(options: List<IdOption>) : Filter.Group<IdOption>("Статус", options)

    private fun queryFields(filters: FilterList): List<Pair<String, String>> = buildList {
        for (filter in filters) {
            val field = when (filter) {
                is Genres -> "genres[]"
                is Tags -> "tags[]"
                else -> continue
            }
            val group = filter as Filter.Group<*>
            for (option in group.state.filterIsInstance<IdOption>()) {
                if (option.state == Filter.TriState.STATE_INCLUDE) add(field to option.id)
            }
        }
    }

    @Test
    fun `required system in the second tag group produces a tags parameter not a genre parameter`(): Unit = runTest {
        val genre = IdOption("Фэнтези", "10")
        val tag = IdOption("Система", "42")
        val filters = FilterList(Genres(listOf(genre)), Tags(listOf(tag)))
        val originalTags = filters[1]
        AppDiscoverySourceCatalog().withMangaGenreFilters(filters, listOf("system")) shouldBe filters
        (filters[1] === originalTags) shouldBe true
        genre.state shouldBe Filter.TriState.STATE_IGNORE
        tag.state shouldBe Filter.TriState.STATE_INCLUDE
        queryFields(filters) shouldBe listOf("tags[]" to "42")
    }

    @Test
    fun `required genre and required tag preserve separate fields and original ids`(): Unit = runTest {
        val filters = FilterList(
            Genres(listOf(IdOption("Фэнтези", "10"))),
            Tags(listOf(IdOption("Система", "42"))),
        )
        AppDiscoverySourceCatalog().withMangaGenreFilters(filters, listOf("Fantasy", "System")) shouldBe filters
        queryFields(filters) shouldBe listOf("genres[]" to "10", "tags[]" to "42")
    }

    @Test
    fun `the same label in genre and tag groups is not sent twice as an extra AND constraint`(): Unit = runTest {
        val genre = IdOption("Фэнтези", "10")
        val tag = IdOption("Фэнтези", "99")
        val filters = FilterList(Genres(listOf(genre)), Tags(listOf(tag)))
        AppDiscoverySourceCatalog().withMangaGenreFilters(filters, listOf("Fantasy")) shouldBe filters
        queryFields(filters) shouldBe listOf("genres[]" to "10")
        tag.state shouldBe Filter.TriState.STATE_IGNORE
    }

    @Test
    fun `unrelated groups are never selected just because an option has the same name`(): Unit = runTest {
        val irrelevant = IdOption("Система", "999")
        val filters = FilterList(
            Statuses(listOf(irrelevant)),
            Genres(listOf(IdOption("Фэнтези", "10"))),
            Tags(listOf(IdOption("Система", "42"))),
        )
        AppDiscoverySourceCatalog().withMangaGenreFilters(filters, listOf("System")) shouldBe filters
        irrelevant.state shouldBe Filter.TriState.STATE_IGNORE
        queryFields(filters) shouldBe listOf("tags[]" to "42")
    }
}
