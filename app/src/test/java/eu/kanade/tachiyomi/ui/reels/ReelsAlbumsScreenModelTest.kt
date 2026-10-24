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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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

@OptIn(ExperimentalCoroutinesApi::class)
class ReelsAlbumsScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class CatalogSource(
        override val id: Long = 6001L,
        private val provider: suspend (page: Int, cursor: String?) -> FeedCategoryPage,
    ) : AnimeFeedSource, AnimeFeedBrowseSource {
        override val name: String = "Catalog Source"
        override val lang: String = "all"
        val requests = mutableListOf<Pair<Int, String?>>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage {
            requests += page to cursor
            return provider(page, cursor)
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

    private fun buildModel(source: AnimeSource) = ReelsAlbumsScreenModel(
        sourceId = source.id,
        albumRepository = FakeAlbumRepository(),
        sourceManager = sourceManagerOf(source),
        ioDispatcher = testDispatcher,
    )

    private fun category(id: String) = FeedCategory(id = id, name = "Category $id")

    @Test
    fun `the add-sheet catalog paginates and dedupes ids across pages`() = runTest(testDispatcher) {
        val source = CatalogSource { page, _ ->
            if (page == 1) {
                FeedCategoryPage(listOf(category("c1"), category("c2")), hasNextPage = true)
            } else {
                FeedCategoryPage(listOf(category("c2"), category("c3")), hasNextPage = false)
            }
        }
        val model = buildModel(source)
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.catalog.map { it.id } shouldBe listOf("c1", "c2")
        model.state.value.catalogCanLoadMore shouldBe true

        model.loadCatalogMore()
        testDispatcher.scheduler.advanceUntilIdle()

        source.requests shouldBe listOf(1 to null, 2 to null)
        model.state.value.catalog.map { it.id } shouldBe listOf("c1", "c2", "c3")
        model.state.value.catalogCanLoadMore shouldBe false
    }

    @Test
    fun `a dropped catalog cursor stops pagination instead of replaying the head`() = runTest(testDispatcher) {
        val source = CatalogSource { page, _ ->
            if (page == 1) {
                FeedCategoryPage(listOf(category("c1")), hasNextPage = true, nextCursor = "T")
            } else {
                FeedCategoryPage(listOf(category("c2")), hasNextPage = true, nextCursor = null)
            }
        }
        val model = buildModel(source)
        testDispatcher.scheduler.advanceUntilIdle()

        model.loadCatalogMore()
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.catalog.map { it.id } shouldBe listOf("c1", "c2")
        model.state.value.catalogCanLoadMore shouldBe false

        model.loadCatalogMore()
        testDispatcher.scheduler.advanceUntilIdle()
        source.requests shouldBe listOf(1 to null, 2 to "T")
    }

    @Test
    fun `catalog append failure keeps the rows and retry repeats the same page and cursor`() =
        runTest(testDispatcher) {
            var failed = false
            val source = CatalogSource { page, _ ->
                if (page == 1) {
                    FeedCategoryPage(listOf(category("c1"), category("c2")), hasNextPage = true, nextCursor = "T")
                } else {
                    if (!failed) {
                        failed = true
                        throw IllegalStateException("offline")
                    }
                    FeedCategoryPage(listOf(category("c3")), hasNextPage = false)
                }
            }
            val model = buildModel(source)
            testDispatcher.scheduler.advanceUntilIdle()

            model.loadCatalogMore()
            testDispatcher.scheduler.advanceUntilIdle()

            model.state.value.catalog.map { it.id } shouldBe listOf("c1", "c2")
            model.state.value.catalogPageError shouldNotBe null
            model.state.value.catalogCanLoadMore shouldBe true
            model.state.value.catalogNextPage shouldBe 2
            model.state.value.catalogNextCursor shouldBe "T"

            model.loadCatalogMore()
            testDispatcher.scheduler.advanceUntilIdle()

            source.requests shouldBe listOf(1 to null, 2 to "T", 2 to "T")
            model.state.value.catalogPageError shouldBe null
            model.state.value.catalog.map { it.id } shouldBe listOf("c1", "c2", "c3")
            model.state.value.catalogCanLoadMore shouldBe false
        }

    @Test
    fun `reloading the catalog resets the stream and its cursor`() = runTest(testDispatcher) {
        val source = CatalogSource { page, _ ->
            if (page == 1) {
                FeedCategoryPage(listOf(category("c1")), hasNextPage = true, nextCursor = "T")
            } else {
                FeedCategoryPage(listOf(category("c2")), hasNextPage = false)
            }
        }
        val model = buildModel(source)
        testDispatcher.scheduler.advanceUntilIdle()

        model.loadCatalogMore()
        testDispatcher.scheduler.advanceUntilIdle()
        model.state.value.catalog.map { it.id } shouldBe listOf("c1", "c2")

        model.loadCatalog()
        testDispatcher.scheduler.advanceUntilIdle()

        source.requests shouldBe listOf(1 to null, 2 to "T", 1 to null)
        model.state.value.catalog.map { it.id } shouldBe listOf("c1")
        model.state.value.catalogCanLoadMore shouldBe true
        model.state.value.catalogCursorMode shouldBe true
    }

    private class FakeAlbumRepository : ReelsAlbumRepository {
        private val albums = mutableMapOf<Pair<String, Long>, ReelsAlbum>()
        private val bySource = mutableMapOf<Long, MutableStateFlow<List<ReelsAlbum>>>()

        override fun subscribeBySource(sourceId: Long): Flow<List<ReelsAlbum>> =
            bySource.getOrPut(sourceId) { MutableStateFlow(emptyList()) }

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
