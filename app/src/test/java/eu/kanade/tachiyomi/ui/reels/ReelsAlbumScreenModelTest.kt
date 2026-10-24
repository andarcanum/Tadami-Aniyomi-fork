package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeFeedBrowseSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedCategoryPage
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.kotest.matchers.collections.shouldBeEmpty
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
import tachiyomi.domain.reels.anime.model.ReelsWatchEntry
import tachiyomi.domain.reels.anime.repository.ReelsAlbumRepository
import tachiyomi.domain.reels.anime.repository.ReelsWatchRepository
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager

@OptIn(ExperimentalCoroutinesApi::class)
class ReelsAlbumScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class RecordingAlbumSource(
        override val id: Long = 5001L,
        private val provider: suspend (page: Int, cursor: String?) -> FeedPage,
    ) : AnimeFeedSource, AnimeFeedBrowseSource {
        override val name: String = "Album Source"
        override val lang: String = "all"
        val requests = mutableListOf<Pair<Int, String?>>()

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
            FeedPage(emptyList(), false)

        override suspend fun getBrowseCategories(page: Int, cursor: String?): FeedCategoryPage =
            FeedCategoryPage(emptyList(), hasNextPage = false)

        override suspend fun getCategoryFeed(categoryId: String, page: Int, cursor: String?): FeedPage {
            requests += page to cursor
            return provider(page, cursor)
        }
    }

    private class PlainFeedSource(override val id: Long = 5002L) : AnimeFeedSource {
        override val name: String = "Plain Feed"
        override val lang: String = "all"
        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
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

    private fun buildModel(
        source: AnimeSource,
        albumRepository: ReelsAlbumRepository = FakeAlbumRepository(),
    ) = ReelsAlbumScreenModel(
        sourceId = source.id,
        albumId = "album-1",
        albumName = "Album One",
        albumRepository = albumRepository,
        watchRepository = FakeReelsWatchRepository(),
        sourceManager = sourceManagerOf(source),
        ioDispatcher = testDispatcher,
    )

    private fun videoItem(id: String) = ShortVideoItem(
        id = id,
        videoUrl = "https://example.com/$id.mp4",
        posterUrl = "https://example.com/$id.jpg",
    )

    @Test
    fun `page-int album keeps page pagination and terminates on hasNextPage false`() = runTest(testDispatcher) {
        val source = RecordingAlbumSource { page, _ ->
            when (page) {
                1 -> FeedPage(listOf(videoItem("a1"), videoItem("a2")), hasNextPage = true)
                else -> FeedPage(listOf(videoItem("a3")), hasNextPage = false)
            }
        }
        val model = buildModel(source)
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.items.map { it.id } shouldBe listOf("a1", "a2")
        model.state.value.canLoadMore shouldBe true

        model.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        source.requests shouldBe listOf(1 to null, 2 to null)
        model.state.value.items.map { it.id } shouldBe listOf("a1", "a2", "a3")
        model.state.value.canLoadMore shouldBe false
    }

    @Test
    fun `the first token locks the cursor and later pages pass it verbatim`() = runTest(testDispatcher) {
        val source = RecordingAlbumSource { page, _ ->
            if (page == 1) {
                FeedPage(listOf(videoItem("a1")), hasNextPage = true, nextCursor = "T")
            } else {
                FeedPage(listOf(videoItem("a2")), hasNextPage = false)
            }
        }
        val model = buildModel(source)
        testDispatcher.scheduler.advanceUntilIdle()

        model.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        source.requests shouldBe listOf(1 to null, 2 to "T")
        model.state.value.items.map { it.id } shouldBe listOf("a1", "a2")
        model.state.value.canLoadMore shouldBe false
    }

    @Test
    fun `a null continuation after the lock stops pagination instead of replaying the head`() =
        runTest(testDispatcher) {
            val source = RecordingAlbumSource { page, _ ->
                if (page == 1) {
                    FeedPage(listOf(videoItem("a1")), hasNextPage = true, nextCursor = "T")
                } else {
                    // Contract violation: cursor mode is locked, more pages are claimed,
                    // but the token disappeared.
                    FeedPage(listOf(videoItem("a2")), hasNextPage = true, nextCursor = null)
                }
            }
            val model = buildModel(source)
            testDispatcher.scheduler.advanceUntilIdle()

            model.loadMore()
            testDispatcher.scheduler.advanceUntilIdle()

            model.state.value.items.map { it.id } shouldBe listOf("a1", "a2")
            model.state.value.canLoadMore shouldBe false

            // Further scrolling must not restart the head or repeat page 2.
            model.loadMore()
            testDispatcher.scheduler.advanceUntilIdle()
            source.requests shouldBe listOf(1 to null, 2 to "T")
        }

    @Test
    fun `duplicate video ids across pages do not duplicate lazy keys`() = runTest(testDispatcher) {
        val source = RecordingAlbumSource { page, _ ->
            if (page == 1) {
                FeedPage(listOf(videoItem("a1"), videoItem("a2")), hasNextPage = true)
            } else {
                FeedPage(listOf(videoItem("a2"), videoItem("a3")), hasNextPage = false)
            }
        }
        val model = buildModel(source)
        testDispatcher.scheduler.advanceUntilIdle()

        model.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.items.map { it.id } shouldBe listOf("a1", "a2", "a3")
    }

    @Test
    fun `first-page failure surfaces the error and retry repeats the same page`() = runTest(testDispatcher) {
        var attempts = 0
        val source = RecordingAlbumSource { page, _ ->
            if (page == 1 && attempts++ == 0) throw IllegalStateException("offline")
            FeedPage(listOf(videoItem("a1")), hasNextPage = false)
        }
        val model = buildModel(source)
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.items.shouldBeEmpty()
        model.state.value.error shouldBe "offline"
        model.state.value.isLoading shouldBe false

        model.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        source.requests shouldBe listOf(1 to null, 1 to null)
        model.state.value.error shouldBe null
        model.state.value.items.map { it.id } shouldBe listOf("a1")
        model.state.value.canLoadMore shouldBe false
    }

    @Test
    fun `append failure keeps the grid and retry repeats the same page and cursor`() = runTest(testDispatcher) {
        var failed = false
        val source = RecordingAlbumSource { page, _ ->
            if (page == 1) {
                FeedPage(listOf(videoItem("a1")), hasNextPage = true, nextCursor = "T")
            } else {
                if (!failed) {
                    failed = true
                    throw IllegalStateException("timeout")
                }
                FeedPage(listOf(videoItem("a2")), hasNextPage = false)
            }
        }
        val model = buildModel(source)
        testDispatcher.scheduler.advanceUntilIdle()

        model.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        model.state.value.items.map { it.id } shouldBe listOf("a1")
        model.state.value.error shouldBe null
        model.state.value.pageError shouldNotBe null
        model.state.value.canLoadMore shouldBe true
        model.state.value.nextPage shouldBe 2
        model.state.value.nextCursor shouldBe "T"

        model.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        source.requests shouldBe listOf(1 to null, 2 to "T", 2 to "T")
        model.state.value.pageError shouldBe null
        model.state.value.items.map { it.id } shouldBe listOf("a1", "a2")
        model.state.value.canLoadMore shouldBe false

        model.onPageErrorShown()
        model.state.value.pageError shouldBe null
    }

    @Test
    fun `a source without the browse capability is unsupported, not an empty album`() =
        runTest(testDispatcher) {
            val model = buildModel(PlainFeedSource())
            testDispatcher.scheduler.advanceUntilIdle()

            model.state.value.unsupported shouldBe true
            model.state.value.error shouldBe null
            model.state.value.items.shouldBeEmpty()
            model.state.value.canLoadMore shouldBe false
        }

    private class FakeAlbumRepository : ReelsAlbumRepository {
        private val albums = mutableMapOf<Pair<String, Long>, ReelsAlbum>()
        private val bySource = mutableMapOf<Long, MutableStateFlow<List<ReelsAlbum>>>()

        private fun flowOf(sourceId: Long): MutableStateFlow<List<ReelsAlbum>> =
            bySource.getOrPut(sourceId) { MutableStateFlow(albums.values.filter { it.sourceId == sourceId }) }

        private fun publish(sourceId: Long) {
            bySource[sourceId]?.value = albums.values.filter { it.sourceId == sourceId }
        }

        override fun subscribeBySource(sourceId: Long): Flow<List<ReelsAlbum>> = flowOf(sourceId)

        override suspend fun getBySource(sourceId: Long): List<ReelsAlbum> =
            albums.values.filter { it.sourceId == sourceId }

        override suspend fun insert(album: ReelsAlbum) {
            albums[album.albumId to album.sourceId] = album
            publish(album.sourceId)
        }

        override suspend fun delete(sourceId: Long, albumId: String) {
            albums.remove(albumId to sourceId)
            publish(sourceId)
        }

        override suspend fun deleteBySource(sourceId: Long) {
            albums.keys.filter { it.second == sourceId }.forEach { albums.remove(it) }
            publish(sourceId)
        }

        override fun subscribeWatched(sourceId: Long): Flow<Set<String>> = MutableStateFlow(emptySet())

        override suspend fun markWatched(sourceId: Long, videoId: String) = Unit

        override suspend fun unmarkWatched(sourceId: Long, videoId: String) = Unit

        override suspend fun deleteWatchedBySource(sourceId: Long) = Unit
    }

    private class FakeReelsWatchRepository : ReelsWatchRepository {
        private val entries = mutableMapOf<Pair<String, Long>, ReelsWatchEntry>()

        override fun subscribeAll(): Flow<List<ReelsWatchEntry>> = MutableStateFlow(entries.values.toList())

        override suspend fun getByVideo(videoId: String, sourceId: Long): ReelsWatchEntry? =
            entries[videoId to sourceId]

        override suspend fun upsert(entry: ReelsWatchEntry) {
            entries[entry.videoId to entry.sourceId] = entry
        }

        override suspend fun delete(videoId: String, sourceId: Long) {
            entries.remove(videoId to sourceId)
        }

        override suspend fun deleteAll() = entries.clear()
    }
}
