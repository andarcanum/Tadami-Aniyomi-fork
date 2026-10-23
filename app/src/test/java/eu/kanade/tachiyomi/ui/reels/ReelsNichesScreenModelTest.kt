package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import eu.kanade.tachiyomi.animesource.model.FeedCategoryPage
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.domain.reels.anime.repository.ReelsAlbumRepository
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ReelsNichesScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeBrowseSource(
        override val id: Long = 4001L,
    ) : AnimeFeedSource, AnimeFeedBrowseSource {
        override val name: String = "Browse Source"
        override val lang: String = "all"

        val categoryRequests = mutableListOf<Pair<Int, String?>>()
        var pages: Int = 2

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage {
            categoryRequests += page to cursor
            return FeedCategoryPage(
                categories = listOf(FeedCategory(id = "c$page", name = "niche$page")),
                hasNextPage = page < pages,
            )
        }

        override suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage =
            FeedPage(emptyList(), false)
    }

    private class PlainFeedSource(override val id: Long = 4002L) : AnimeFeedSource {
        override val name: String = "Plain Feed"
        override val lang: String = "all"
        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)
    }

    /**
     * Its [gates] suspend every category request until the test releases them, so the order
     * of stale-vs-fresh completions is fully deterministic.
     */
    private class GateBrowseSource(override val id: Long = 4003L) : AnimeFeedSource, AnimeFeedBrowseSource {
        override val name: String = "Gate Browse"
        override val lang: String = "all"
        val gates = mutableListOf<CompletableDeferred<Unit>>()
        val categoryRequests = mutableListOf<Pair<Int, String?>>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage {
            categoryRequests += page to cursor
            val gate = CompletableDeferred<Unit>()
            gates += gate
            gate.await()
            return FeedCategoryPage(
                categories = listOf(FeedCategory(id = "c$page", name = "niche$page")),
                hasNextPage = true,
            )
        }

        override suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage =
            FeedPage(emptyList(), false)
    }

    private class FailingBrowseSource(
        override val id: Long = 4004L,
        private val failOnPage: Int = 2,
    ) : AnimeFeedSource, AnimeFeedBrowseSource {
        override val name: String = "Failing Browse"
        override val lang: String = "all"

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage {
            if (page == failOnPage) throw IllegalStateException("boom")
            return FeedCategoryPage(
                categories = listOf(FeedCategory(id = "c$page", name = "niche$page")),
                hasNextPage = true,
            )
        }

        override suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage =
            FeedPage(emptyList(), false)
    }

    /** Whole directory in one page, the way a channel catalog is served. */
    private class CatalogBrowseSource(override val id: Long = 4005L) : AnimeFeedSource, AnimeFeedBrowseSource {
        override val name: String = "Catalog Browse"
        override val lang: String = "all"
        val requests = AtomicInteger(0)

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage {
            requests.incrementAndGet()
            return FeedCategoryPage(
                categories = listOf(
                    FeedCategory(id = "channel:durov", name = "Pavel Durov"),
                    FeedCategory(id = "channel:anime_clips", name = "Anime Clips"),
                    FeedCategory(id = "channel:films", name = "Films · без видео"),
                    FeedCategory(id = "niche:mix", name = "All channels"),
                ),
                hasNextPage = false,
            )
        }

        override suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage =
            FeedPage(emptyList(), false)
    }

    private fun sourceManagerOf(vararg sources: AnimeSource): AnimeSourceManager = object : AnimeSourceManager {
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
        override val sources: Flow<List<AnimeSource>> = MutableStateFlow(sources.toList())
        override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
        override fun get(sourceKey: Long): AnimeSource? = sources.firstOrNull { it.id == sourceKey }
        override fun getOrStub(sourceKey: Long): AnimeSource = get(sourceKey) ?: sources.first()
        override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
        override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
        override fun getStubSources(): List<StubAnimeSource> = emptyList()
    }

    @Test
    fun `paginates the category directory with the sticky protocol`() = runTest(testDispatcher) {
        val source = FakeBrowseSource()
        val model = ReelsNichesScreenModel(
            sourceId = 4001L,
            sourceManager = sourceManagerOf(source),
            ioDispatcher = testDispatcher,
            albumRepository = FakeAlbumRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.categories.shouldHaveSize(1)
        model.state.value.canLoadMore shouldBe true
        model.state.value.isLoading shouldBe false

        model.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.categories.shouldHaveSize(2)
        model.state.value.canLoadMore shouldBe false
        source.categoryRequests shouldBe listOf(1 to null, 2 to null)
    }

    @Test
    fun `source without the browse capability yields the empty state`() = runTest(testDispatcher) {
        val model = ReelsNichesScreenModel(
            sourceId = 4002L,
            sourceManager = sourceManagerOf(PlainFeedSource()),
            ioDispatcher = testDispatcher,
            albumRepository = FakeAlbumRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.categories.shouldHaveSize(0)
        model.state.value.canLoadMore shouldBe false
        model.state.value.error shouldBe null
    }

    @Test
    fun `a loadMore racing a retry cannot write stale pages into the fresh state`() = runTest(testDispatcher) {
        val source = GateBrowseSource()
        val model = ReelsNichesScreenModel(
            sourceId = 4003L,
            sourceManager = sourceManagerOf(source),
            ioDispatcher = testDispatcher,
            albumRepository = FakeAlbumRepository(),
        )
        testDispatcher.scheduler.runCurrent()
        source.categoryRequests shouldBe listOf(1 to null)

        // The in-flight attempt must not be duplicated while it is still loading.
        model.loadMore()
        source.categoryRequests shouldBe listOf(1 to null)

        model.retry()
        testDispatcher.scheduler.runCurrent()
        source.gates.shouldHaveSize(2)

        // The retry's attempt completes first...
        source.gates[1].complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        model.state.value.categories.shouldHaveSize(1)
        model.state.value.canLoadMore shouldBe true

        // ...then the superseded attempt lands: its page must be dropped entirely.
        source.gates[0].complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        model.state.value.categories.shouldHaveSize(1)
        model.state.value.nextPage shouldBe 2
        model.state.value.pageError shouldBe null
    }

    @Test
    fun `a mid-feed failure keeps the grid usable and surfaces a transient error`() = runTest(testDispatcher) {
        val source = FailingBrowseSource()
        val model = ReelsNichesScreenModel(
            sourceId = 4004L,
            sourceManager = sourceManagerOf(source),
            ioDispatcher = testDispatcher,
            albumRepository = FakeAlbumRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.categories.shouldHaveSize(1)
        model.state.value.canLoadMore shouldBe true
        model.state.value.isLoading shouldBe false

        model.loadMore() // page 2 fails
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.categories.shouldHaveSize(1)
        model.state.value.error shouldBe null
        model.state.value.pageError shouldNotBe null
        // Pagination stays armed: scrolling away and back to the bottom retries.
        model.state.value.canLoadMore shouldBe true

        model.onPageErrorShown()
        model.state.value.pageError shouldBe null
    }

    @Test
    fun `search narrows the whole catalog locally without extra requests`() = runTest(testDispatcher) {
        val source = CatalogBrowseSource()
        val model = ReelsNichesScreenModel(
            sourceId = 4005L,
            sourceManager = sourceManagerOf(source),
            ioDispatcher = testDispatcher,
            albumRepository = FakeAlbumRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.visibleCategories.shouldHaveSize(4)
        source.requests.get() shouldBe 1

        // @username, any register: matched against the category id, not the visible title.
        model.onSearchQueryChange("@ANIME")
        model.state.value.visibleCategories.map { it.id } shouldBe listOf("channel:anime_clips")

        // Title match, without the leading "@".
        model.onSearchQueryChange("durov")
        model.state.value.visibleCategories.map { it.name } shouldBe listOf("Pavel Durov")

        // The full directory is kept: filtering never drops pages from the state.
        model.state.value.categories.shouldHaveSize(4)

        // A blank query restores everything; a miss yields an empty (but non-error) grid.
        model.onSearchQueryChange("   ")
        model.state.value.visibleCategories.shouldHaveSize(4)
        model.onSearchQueryChange("nothing-matches")
        model.state.value.visibleCategories.shouldHaveSize(0)

        // No extra directory request (and no feed search) was issued while typing.
        source.requests.get() shouldBe 1
        model.state.value.error shouldBe null
    }

    @Test
    fun `retry keeps the search query`() = runTest(testDispatcher) {
        val source = CatalogBrowseSource()
        val model = ReelsNichesScreenModel(
            sourceId = 4005L,
            sourceManager = sourceManagerOf(source),
            ioDispatcher = testDispatcher,
            albumRepository = FakeAlbumRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        model.onSearchQueryChange("durov")
        model.retry()
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.searchQuery shouldBe "durov"
        model.state.value.visibleCategories.map { it.name } shouldBe listOf("Pavel Durov")
    }

    private class FakeAlbumRepository : ReelsAlbumRepository {
        val albums = mutableMapOf<Pair<String, Long>, ReelsAlbum>()

        override fun subscribeBySource(sourceId: Long): Flow<List<ReelsAlbum>> =
            MutableStateFlow(albums.values.filter { it.sourceId == sourceId })

        override suspend fun getBySource(sourceId: Long): List<ReelsAlbum> =
            albums.values.filter { it.sourceId == sourceId }

        override suspend fun insert(album: ReelsAlbum) {
            albums[album.albumId to album.sourceId] = album
        }

        override suspend fun delete(sourceId: Long, albumId: String) {
            albums.remove(albumId to sourceId)
        }

        override suspend fun deleteBySource(sourceId: Long) {
            albums.keys.filter { it.second == sourceId }.forEach { albums.remove(it) }
        }

        override fun subscribeWatched(sourceId: Long): Flow<Set<String>> = MutableStateFlow(emptySet())

        override suspend fun markWatched(sourceId: Long, videoId: String) = Unit

        override suspend fun unmarkWatched(sourceId: Long, videoId: String) = Unit

        override suspend fun deleteWatchedBySource(sourceId: Long) = Unit
    }
}
