package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentSetOf
import org.junit.jupiter.api.Test

class ReelsLocalFilterTest {

    private fun item(
        id: String,
        title: String? = id,
        author: String? = null,
        durationSec: Float? = null,
        tags: List<String> = emptyList(),
        createdAtEpochSec: Long? = null,
        likesCount: Long? = null,
        viewsCount: Long? = null,
    ) = ShortVideoItem(
        id = id,
        title = title,
        author = author,
        videoUrl = "https://example.com/$id.mp4",
        posterUrl = "https://example.com/$id.jpg",
        durationSec = durationSec,
        tags = tags,
        createdAtEpochSec = createdAtEpochSec,
        likesCount = likesCount,
        viewsCount = viewsCount,
    )

    @Test
    fun `duration filter buckets short medium long and drops unknown durations`() {
        val items = listOf(
            item("s", durationSec = 45f),
            item("m1", durationSec = 60f),
            item("m2", durationSec = 300f),
            item("l", durationSec = 301f),
            item("u", durationSec = null),
            item("z", durationSec = 0f),
        )

        applyReelsLocalFilter(items, ReelsLocalFilterState(duration = ReelsDurationFilter.All))
            .map { it.id } shouldContainExactly listOf("s", "m1", "m2", "l", "u", "z")
        applyReelsLocalFilter(items, ReelsLocalFilterState(duration = ReelsDurationFilter.Short))
            .map { it.id } shouldContainExactly listOf("s")
        applyReelsLocalFilter(items, ReelsLocalFilterState(duration = ReelsDurationFilter.Medium))
            .map { it.id } shouldContainExactly listOf("m1", "m2")
        applyReelsLocalFilter(items, ReelsLocalFilterState(duration = ReelsDurationFilter.Long))
            .map { it.id } shouldContainExactly listOf("l")
    }

    @Test
    fun `sorting orders by newest longest shortest popular with missing metadata last`() {
        val items = listOf(
            item("a", durationSec = 120f, createdAtEpochSec = 100L, likesCount = 50L),
            item("b", durationSec = null, createdAtEpochSec = null, likesCount = null, viewsCount = null),
            item("c", durationSec = 600f, createdAtEpochSec = 300L, likesCount = null, viewsCount = 500L),
            item("d", durationSec = 25f, createdAtEpochSec = 200L, likesCount = 1000L),
        )

        applyReelsLocalFilter(items, ReelsLocalFilterState(sort = ReelsVideoSort.Newest))
            .map { it.id } shouldContainExactly listOf("c", "d", "a", "b")
        applyReelsLocalFilter(items, ReelsLocalFilterState(sort = ReelsVideoSort.Longest))
            .map { it.id } shouldContainExactly listOf("c", "a", "d", "b")
        applyReelsLocalFilter(items, ReelsLocalFilterState(sort = ReelsVideoSort.Shortest))
            .map { it.id } shouldContainExactly listOf("d", "a", "c", "b")
        applyReelsLocalFilter(items, ReelsLocalFilterState(sort = ReelsVideoSort.Popular))
            .map { it.id } shouldContainExactly listOf("d", "c", "a", "b")
    }

    @Test
    fun `extractAvailableTags deduplicates case-insensitively and orders by frequency`() {
        val items = listOf(
            item("1", tags = listOf("#Action", "comedy", "action")),
            item("2", tags = listOf("ACTION", "Drama")),
            item("3", tags = listOf("drama", "  ", "")),
        )

        extractAvailableTags(items) shouldContainExactly listOf("Action", "Drama", "comedy")
    }

    @Test
    fun `tag filter matches any selected tag case-insensitively`() {
        val items = listOf(
            item("1", tags = listOf("Action", "SciFi")),
            item("2", tags = listOf("Comedy")),
            item("3", tags = listOf("#drama")),
        )

        applyReelsLocalFilter(
            items,
            ReelsLocalFilterState(selectedTags = persistentSetOf("action", "Drama")),
        ).map { it.id } shouldContainExactly listOf("1", "3")
    }

    @Test
    fun `watch filter respects manual watched marks and 90 percent progress`() {
        val items = listOf(
            item("manual"),
            item("progressed"),
            item("partial"),
            item("fresh"),
        )
        val watched = setOf("manual")
        val progress = mapOf("progressed" to 0.92f, "partial" to 0.45f)

        applyReelsLocalFilter(
            items = items,
            filter = ReelsLocalFilterState(watchFilter = ReelsWatchFilter.Unwatched),
            watchedIds = watched,
            progressMap = progress,
        ).map { it.id } shouldContainExactly listOf("partial", "fresh")

        applyReelsLocalFilter(
            items = items,
            filter = ReelsLocalFilterState(watchFilter = ReelsWatchFilter.Watched),
            watchedIds = watched,
            progressMap = progress,
        ).map { it.id } shouldContainExactly listOf("manual", "progressed")
    }

    @Test
    fun `niches directory filter supports savedOnly search and sorting`() {
        val categories = listOf(
            FeedCategory(id = "ch_b", name = "Beta", itemCount = 10),
            FeedCategory(id = "ch_a", name = "Alpha", itemCount = 50),
            FeedCategory(id = "ch_c", name = "Gamma", itemCount = null),
        )

        applyNichesFilterAndSort(
            categories = categories,
            searchQuery = "",
            sort = ReelsNichesSort.Name,
            savedOnly = false,
            savedAlbumIds = emptySet(),
        ).map { it.id } shouldContainExactly listOf("ch_a", "ch_b", "ch_c")

        applyNichesFilterAndSort(
            categories = categories,
            searchQuery = "",
            sort = ReelsNichesSort.MostVideos,
            savedOnly = false,
            savedAlbumIds = emptySet(),
        ).map { it.id } shouldContainExactly listOf("ch_a", "ch_b", "ch_c")

        applyNichesFilterAndSort(
            categories = categories,
            searchQuery = "@ch_",
            sort = ReelsNichesSort.Default,
            savedOnly = true,
            savedAlbumIds = setOf("ch_b", "ch_c"),
        ).map { it.id } shouldContainExactly listOf("ch_b", "ch_c")
    }

    @Test
    fun `filter state active count tracks all enabled constraints`() {
        ReelsLocalFilterState().isActive shouldBe false
        ReelsLocalFilterState().activeCount shouldBe 0

        val active = ReelsLocalFilterState(
            duration = ReelsDurationFilter.Long,
            sort = ReelsVideoSort.Newest,
            selectedTags = persistentSetOf("a", "b"),
            watchFilter = ReelsWatchFilter.Unwatched,
        )
        active.isActive shouldBe true
        active.activeCount shouldBe 5
    }

    @Test
    fun `persistent filter serialization round-trips duration sort and watch while omitting tags`() {
        val original = ReelsLocalFilterState(
            duration = ReelsDurationFilter.Long,
            sort = ReelsVideoSort.Newest,
            selectedTags = persistentSetOf("ephemeral-tag"),
            watchFilter = ReelsWatchFilter.Unwatched,
        )
        val encoded = original.serializePersistent()
        val decoded = ReelsLocalFilterState.deserializePersistent(encoded)

        decoded.duration shouldBe ReelsDurationFilter.Long
        decoded.sort shouldBe ReelsVideoSort.Newest
        decoded.watchFilter shouldBe ReelsWatchFilter.Unwatched
        decoded.selectedTags shouldBe persistentSetOf()

        ReelsLocalFilterState().serializePersistent() shouldBe ""
        ReelsLocalFilterState.deserializePersistent("garbage|duration=invalid") shouldBe ReelsLocalFilterState()
        ReelsNichesSort.fromStored("MostVideos") shouldBe ReelsNichesSort.MostVideos
        ReelsNichesSort.fromStored("unknown") shouldBe ReelsNichesSort.Default
    }

    private class TestSelectFilter(
        name: String,
        values: Array<String>,
        defaultIndex: Int = 0,
    ) : AnimeFilter.Select<String>(name, values, defaultIndex)

    @Test
    fun `syncCategoryFiltersWithLocalFilter maps duration and sort into plugin category filters`() {
        val orderFilter = TestSelectFilter(
            "Сортировка",
            arrayOf("Свежие (как в Telegram)", "Популярное (Топ просмотров)", "В разнобой (Shuffle)"),
            defaultIndex = 0,
        )
        val durationFilter = TestSelectFilter(
            "Длительность",
            arrayOf(
                "До 1 мин (Shorts)",
                "До 3 мин (Стандарт)",
                "До 5 мин",
                "От 1 до 5 мин",
                "От 5 мин",
                "Без ограничений",
            ),
            defaultIndex = 5,
        )
        val filters = AnimeFilterList(orderFilter, durationFilter)

        val changedToLongPopular = syncCategoryFiltersWithLocalFilter(
            filters,
            ReelsLocalFilterState(
                duration = ReelsDurationFilter.Long,
                sort = ReelsVideoSort.Popular,
            ),
        )
        changedToLongPopular shouldBe true
        durationFilter.state shouldBe 4 // "От 5 мин"
        orderFilter.state shouldBe 1 // "Популярное (Топ просмотров)"

        // Calling again with the same state is a no-op.
        syncCategoryFiltersWithLocalFilter(
            filters,
            ReelsLocalFilterState(
                duration = ReelsDurationFilter.Long,
                sort = ReelsVideoSort.Popular,
            ),
        ) shouldBe false

        // Medium maps to "От 1 до 5 мин", Short to "До 1 мин", All to "Без ограничений".
        syncCategoryFiltersWithLocalFilter(
            filters,
            ReelsLocalFilterState(duration = ReelsDurationFilter.Medium, sort = ReelsVideoSort.Newest),
        ) shouldBe true
        durationFilter.state shouldBe 3
        orderFilter.state shouldBe 0

        syncCategoryFiltersWithLocalFilter(
            filters,
            ReelsLocalFilterState(duration = ReelsDurationFilter.Short),
        ) shouldBe true
        durationFilter.state shouldBe 0

        syncCategoryFiltersWithLocalFilter(
            filters,
            ReelsLocalFilterState(),
        ) shouldBe true
        durationFilter.state shouldBe 5
        orderFilter.state shouldBe 0
    }

    @Test
    fun `normalizeReelsFilterScopeKey unifies channel niche album and creator handle while separating other niches`() {
        val fromNiche = normalizeReelsFilterScopeKey("channel:c_-1003708764014")
        val fromCreator = normalizeReelsFilterScopeKey("@c_-1003708764014")
        val fromRaw = normalizeReelsFilterScopeKey("C_-1003708764014")
        val otherNiche = normalizeReelsFilterScopeKey("channel:other_channel")

        fromNiche shouldBe "c_-1003708764014"
        fromCreator shouldBe fromNiche
        fromRaw shouldBe fromNiche
        (fromNiche == otherNiche) shouldBe false
    }
}
