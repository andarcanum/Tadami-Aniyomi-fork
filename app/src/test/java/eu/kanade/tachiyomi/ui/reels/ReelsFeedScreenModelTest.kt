package eu.kanade.tachiyomi.ui.reels

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.FeedPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
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
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.domain.reels.anime.repository.ReelsFavoriteRepository
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ReelsFeedScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class MapPreferenceStore : PreferenceStore {
        private val map = mutableMapOf<String, Any>()

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            createPref(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            createPref(key, defaultValue)

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            createPref(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            createPref(key, defaultValue)

        override fun getString(key: String, defaultValue: String): Preference<String> =
            createPref(key, defaultValue)

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            createPref(key, defaultValue)

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = createPref(key, defaultValue)

        override fun getAll(): Map<String, *> = map

        @Suppress("UNCHECKED_CAST")
        private fun <T> createPref(key: String, defaultValue: T): Preference<T> {
            return object : Preference<T> {
                override fun key(): String = key
                override fun get(): T = (map[key] as? T) ?: defaultValue
                override fun set(value: T) {
                    if (value == null) map.remove(key) else map[key] = value as Any
                }
                override fun isSet(): Boolean = map.containsKey(key)
                override fun delete() {
                    map.remove(key)
                }
                override fun defaultValue(): T = defaultValue
                override fun changes(): Flow<T> = MutableStateFlow(get())
                override fun stateIn(scope: kotlinx.coroutines.CoroutineScope): StateFlow<T> =
                    MutableStateFlow(get()).asStateFlow()
            }
        }
    }

    private class FakeReelsFavoriteRepository : ReelsFavoriteRepository {
        val favorites = mutableMapOf<Pair<String, Long>, ReelsFavorite>()

        // One-shot gate for getIdsBySource: parks the first call until released and returns
        // the snapshot, simulating a favorites DB read racing UI actions.
        private var idGate: CompletableDeferred<Unit>? = null
        private var idSnapshot: List<String>? = null

        fun parkNextIds(snapshot: List<String>): CompletableDeferred<Unit> =
            CompletableDeferred<Unit>().also {
                idGate = it
                idSnapshot = snapshot
            }

        override fun subscribeAll(): Flow<List<ReelsFavorite>> = MutableStateFlow(favorites.values.toList())

        override suspend fun getAll(): List<ReelsFavorite> = favorites.values.toList()

        override suspend fun getBySource(sourceId: Long): List<ReelsFavorite> =
            favorites.values.filter { it.sourceId == sourceId }

        override suspend fun getIdsBySource(sourceId: Long): List<String> {
            val gate = idGate
            if (gate != null) {
                idGate = null
                gate.await()
                return idSnapshot.orEmpty()
            }
            return favorites.values.filter { it.sourceId == sourceId }.map { it.videoId }
        }

        override suspend fun insert(favorite: ReelsFavorite) {
            favorites[favorite.videoId to favorite.sourceId] = favorite
        }

        override suspend fun insertAll(favorites: List<ReelsFavorite>) {
            favorites.forEach { insert(it) }
        }

        override suspend fun delete(videoId: String, sourceId: Long) {
            favorites.remove(videoId to sourceId)
        }
    }

    @Test
    fun `loads feed, toggles controls, persists queries and switches sources`() = runTest(testDispatcher) {
        val sampleItem1 = ShortVideoItem(
            id = "vid-1",
            title = "Test Reel 1",
            author = "Alice",
            videoUrl = "https://example.com/sd1.mp4",
            videoUrlHd = "https://example.com/hd1.mp4",
            posterUrl = "https://example.com/poster1.jpg",
            durationSec = 10f,
            hasAudio = true,
        )

        val sampleItem2 = ShortVideoItem(
            id = "vid-2",
            title = "Test Reel 2",
            author = "Bob",
            videoUrl = "https://example.com/sd2.mp4",
            videoUrlHd = "https://example.com/hd2.mp4",
            posterUrl = "https://example.com/poster2.jpg",
            durationSec = 12f,
            hasAudio = true,
        )

        class SortFilter : AnimeFilter.Select<String>("Sort", arrayOf("Trending", "Recent"), 0)

        val feedSource1 = object : AnimeFeedSource {
            override val id: Long = 101L
            override val name: String = "RedGIFs (Reels)"
            override val lang: String = "all"

            override fun getFilterList(): AnimeFilterList = AnimeFilterList(SortFilter())

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
                return FeedPage(videos = listOf(sampleItem1), hasNextPage = true)
            }

            override suspend fun getSearchFeed(page: Int, cursor: String?, query: String, filters: AnimeFilterList): FeedPage {
                return FeedPage(videos = listOf(sampleItem1), hasNextPage = false)
            }
        }

        val feedSource2 = object : AnimeFeedSource {
            override val id: Long = 102L
            override val name: String = "TikTok (Reels)"
            override val lang: String = "all"

            override fun getFilterList(): AnimeFilterList = AnimeFilterList()

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
                return FeedPage(videos = listOf(sampleItem2), hasNextPage = true)
            }

            override suspend fun getSearchFeed(page: Int, cursor: String?, query: String, filters: AnimeFilterList): FeedPage {
                return FeedPage(videos = listOf(sampleItem2), hasNextPage = false)
            }
        }

        val fakeSourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(feedSource1, feedSource2))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = when (sourceKey) {
                101L -> feedSource1
                102L -> feedSource2
                else -> null
            }
            override fun getOrStub(sourceKey: Long): AnimeSource = get(sourceKey) ?: feedSource1
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val sourcePreferences = SourcePreferences(MapPreferenceStore())
        val fakeFavorites = FakeReelsFavoriteRepository()

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 101L,
            sourceManager = fakeSourceManager,
            sourcePreferences = sourcePreferences,
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = fakeFavorites,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldHaveSize(1)
        screenModel.state.value.items.first().id shouldBe "vid-1"
        screenModel.state.value.currentSourceId shouldBe 101L
        screenModel.state.value.sourceName shouldBe "RedGIFs (Reels)"
        sourcePreferences.lastUsedReelsSource().get() shouldBe 101L

        // Test Like toggle + persistence
        screenModel.toggleLike(sampleItem1)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.likedIds.contains("vid-1") shouldBe true
        fakeFavorites.favorites.keys shouldBe setOf("vid-1" to 101L)

        screenModel.toggleLike(sampleItem1)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.likedIds.contains("vid-1") shouldBe false
        fakeFavorites.favorites.isEmpty() shouldBe true

        // Test Mute toggle
        val initialMuted = screenModel.state.value.isMuted
        screenModel.toggleMute()
        screenModel.state.value.isMuted shouldBe !initialMuted

        // Test Auto-advance toggle
        screenModel.state.value.isAutoAdvance shouldBe true
        sourcePreferences.autoAdvanceReels().get() shouldBe true
        screenModel.toggleAutoAdvance()
        screenModel.state.value.isAutoAdvance shouldBe false
        sourcePreferences.autoAdvanceReels().get() shouldBe false

        // Test Crop Mode toggle
        screenModel.state.value.isCropMode shouldBe false
        screenModel.toggleCropMode()
        screenModel.state.value.isCropMode shouldBe true
        sourcePreferences.reelsCropMode().get() shouldBe true

        // Test Filter selection and persistence
        val filters = screenModel.state.value.filters
        val sortFilter = filters.filterIsInstance<SortFilter>().first()
        sortFilter.state = 1 // Change from Trending (0) to Recent (1)
        screenModel.applyFilters()
        testDispatcher.scheduler.advanceUntilIdle()

        sourcePreferences.lastReelsFilter(101L).get() shouldBe "0=1"

        // Test Search & Query Persistence
        screenModel.search("cosplay")
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.searchQuery shouldBe "cosplay"
        sourcePreferences.lastReelsQuery(101L).get() shouldBe "cosplay"

        // Switch to Source 2 (TikTok)
        screenModel.switchSource(102L)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.currentSourceId shouldBe 102L
        screenModel.state.value.sourceName shouldBe "TikTok (Reels)"
        screenModel.state.value.items.shouldHaveSize(1)
        screenModel.state.value.items.first().id shouldBe "vid-2"
        sourcePreferences.lastUsedReelsSource().get() shouldBe 102L

        // Switch back to Source 1 (RedGIFs) and verify filter is restored
        screenModel.switchSource(101L)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.currentSourceId shouldBe 101L
        val restoredSortFilter = screenModel.state.value.filters.filterIsInstance<SortFilter>().first()
        restoredSortFilter.state shouldBe 1
    }

    @Test
    fun `does not persist reels history while incognito`() = runTest(testDispatcher) {
        val incognitoItem = ShortVideoItem(
            id = "vid-x",
            videoUrl = "https://example.com/x.mp4",
            posterUrl = "https://example.com/x.jpg",
        )
        val feedSource = object : AnimeFeedSource {
            override val id: Long = 201L
            override val name: String = "Incognito Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
                FeedPage(videos = listOf(incognitoItem), hasNextPage = false)

            override suspend fun getSearchFeed(page: Int, cursor: String?, query: String, filters: AnimeFilterList): FeedPage =
                getFeed(page, cursor, filters)
        }

        val fakeSourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(feedSource))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = if (sourceKey == 201L) feedSource else null
            override fun getOrStub(sourceKey: Long): AnimeSource = feedSource
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val sourcePreferences = SourcePreferences(MapPreferenceStore())
        val fakeFavorites = FakeReelsFavoriteRepository()

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 201L,
            sourceManager = fakeSourceManager,
            sourcePreferences = sourcePreferences,
            ioDispatcher = testDispatcher,
            isIncognito = { true },
            sourceIconProvider = { null },
            reelsFavoriteRepository = fakeFavorites,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.items.shouldHaveSize(1)

        screenModel.search("cosplay")
        testDispatcher.scheduler.advanceUntilIdle()

        // Liking still works in-session but must not be persisted while incognito.
        screenModel.toggleLike(incognitoItem)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.likedIds.contains("vid-x") shouldBe true
        fakeFavorites.favorites.isEmpty() shouldBe true

        // The in-session feed keeps working normally...
        screenModel.state.value.searchQuery shouldBe "cosplay"
        // ...but nothing leaks into persisted history while incognito.
        sourcePreferences.lastUsedReelsSource().get() shouldBe -1L
        sourcePreferences.lastReelsQuery(201L).get() shouldBe ""
        sourcePreferences.lastReelsFilter(201L).get() shouldBe ""
    }

    @Test
    fun `rapid double loadNextPageIfNeeded fetches the next page only once`() = runTest(testDispatcher) {
        val requestedPages = mutableListOf<Int>()
        val feedSource = object : AnimeFeedSource {
            override val id: Long = 401L
            override val name: String = "Pager Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
                requestedPages += page
                val videos = (0 until 5).map { idx ->
                    ShortVideoItem(
                        id = "vid-p$page-$idx",
                        videoUrl = "https://example.com/p${page}v$idx.mp4",
                        posterUrl = "https://example.com/p${page}v$idx.jpg",
                    )
                }
                return FeedPage(videos = videos, hasNextPage = true)
            }

            override suspend fun getSearchFeed(page: Int, cursor: String?, query: String, filters: AnimeFilterList): FeedPage =
                getFeed(page, cursor, filters)
        }

        val fakeSourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(feedSource))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = if (sourceKey == 401L) feedSource else null
            override fun getOrStub(sourceKey: Long): AnimeSource = feedSource
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 401L,
            sourceManager = fakeSourceManager,
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = FakeReelsFavoriteRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        requestedPages shouldBe listOf(1)

        // The pager fires two page-changed events before the first load job's coroutine is
        // dispatched; only one append fetch may be launched.
        screenModel.onPageChanged(3)
        screenModel.onPageChanged(3)
        testDispatcher.scheduler.advanceUntilIdle()

        requestedPages shouldBe listOf(1, 2)
    }

    @Test
    fun `like persists even when the item was already dropped from the feed`() = runTest(testDispatcher) {
        val feedSource = object : AnimeFeedSource {
            override val id: Long = 701L
            override val name: String = "Displaced Item Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
                FeedPage(emptyList(), false)

            override suspend fun getSearchFeed(page: Int, cursor: String?, query: String, filters: AnimeFilterList): FeedPage =
                getFeed(page, cursor, filters)
        }

        val fakeSourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(feedSource))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = if (sourceKey == 701L) feedSource else null
            override fun getOrStub(sourceKey: Long): AnimeSource = feedSource
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val fakeFavorites = FakeReelsFavoriteRepository()
        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 701L,
            sourceManager = fakeSourceManager,
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = fakeFavorites,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // The video page passes the item it renders; the model must not re-find it in
        // state, where a concurrent refresh may have already displaced it.
        screenModel.toggleLike(
            ShortVideoItem(
                id = "ghost",
                videoUrl = "https://example.com/ghost.mp4",
                posterUrl = "https://example.com/ghost.jpg",
            ),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.likedIds.contains("ghost") shouldBe true
        fakeFavorites.favorites.keys shouldBe setOf("ghost" to 701L)
    }

    @Test
    fun `unlike during favorites load is not resurrected by the stale snapshot`() = runTest(testDispatcher) {
        val gateItem = ShortVideoItem(
            id = "vid-1",
            videoUrl = "https://example.com/v1.mp4",
            posterUrl = "https://example.com/v1.jpg",
        )
        val feedSource = object : AnimeFeedSource {
            override val id: Long = 501L
            override val name: String = "Gate Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
                FeedPage(videos = listOf(gateItem), hasNextPage = false)

            override suspend fun getSearchFeed(page: Int, cursor: String?, query: String, filters: AnimeFilterList): FeedPage =
                getFeed(page, cursor, filters)
        }

        val fakeSourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(feedSource))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = if (sourceKey == 501L) feedSource else null
            override fun getOrStub(sourceKey: Long): AnimeSource = feedSource
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val fakeFavorites = FakeReelsFavoriteRepository()
        // Favorites read races the UI: it parks and later reports vid-1 as persisted.
        val gate = fakeFavorites.parkNextIds(listOf("vid-1"))

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 501L,
            sourceManager = fakeSourceManager,
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = fakeFavorites,
        )
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(1)

        // Like, then unlike while the favorites read is still parked: the DB snapshot is
        // stale the moment it is captured.
        screenModel.toggleLike(gateItem)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.toggleLike(gateItem)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.likedIds.contains("vid-1") shouldBe false
        fakeFavorites.favorites.isEmpty() shouldBe true

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // The stale persisted snapshot must not resurrect the unliked video.
        screenModel.state.value.likedIds.contains("vid-1") shouldBe false
    }

    @Test
    fun `late favorites result from the previous source does not leak into the new source`() = runTest(testDispatcher) {
        fun feedSource(id: Long, name: String) = object : AnimeFeedSource {
            override val id: Long = id
            override val name: String = name
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage = FeedPage(emptyList(), false)

            override suspend fun getSearchFeed(page: Int, cursor: String?, query: String, filters: AnimeFilterList): FeedPage =
                getFeed(page, cursor, filters)
        }

        val sourceA = feedSource(601L, "Feed A")
        val sourceB = feedSource(602L, "Feed B")

        val fakeSourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(sourceA, sourceB))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = when (sourceKey) {
                601L -> sourceA
                602L -> sourceB
                else -> null
            }
            override fun getOrStub(sourceKey: Long): AnimeSource = sourceA
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val fakeFavorites = FakeReelsFavoriteRepository()
        val gate = fakeFavorites.parkNextIds(listOf("vid-a1"))

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 601L,
            sourceManager = fakeSourceManager,
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = fakeFavorites,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Switch away before source A's favorites read resolves.
        screenModel.switchSource(602L)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.currentSourceId shouldBe 602L

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.likedIds.contains("vid-a1") shouldBe false
    }

    @Test
    fun `append failure with non-empty feed sets pageError and next success clears it`() = runTest(testDispatcher) {
        val requestedPages = mutableListOf<Int>()
        // A failed append does not consume the page cursor, so the next trigger retries the
        // same page; only the first attempt of page 2 fails (transient CDN error).
        val failedAttempts = mutableSetOf<Int>()
        val feedSource = object : AnimeFeedSource {
            override val id: Long = 801L
            override val name: String = "Failing Append Feed"
            override val lang: String = "all"

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
                requestedPages += page
                if (page == 2 && failedAttempts.add(page)) throw RuntimeException("CDN exploded")
                return FeedPage(
                    videos = listOf(
                        ShortVideoItem(
                            id = "vid-p$page",
                            videoUrl = "https://example.com/p$page.mp4",
                            posterUrl = "https://example.com/p$page.jpg",
                        ),
                    ),
                    hasNextPage = true,
                )
            }

            override suspend fun getSearchFeed(page: Int, cursor: String?, query: String, filters: AnimeFilterList): FeedPage =
                getFeed(page, cursor, filters)
        }

        val fakeSourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(listOf(feedSource))
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = if (sourceKey == 801L) feedSource else null
            override fun getOrStub(sourceKey: Long): AnimeSource = feedSource
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 801L,
            sourceManager = fakeSourceManager,
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = FakeReelsFavoriteRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(1)

        // Page 2 fails while the feed is non-empty: the error must be transient (pageError),
        // not the full-screen error, and the feed must stay usable.
        screenModel.onPageChanged(0)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.pageError shouldBe "CDN exploded"
        screenModel.state.value.error shouldBe null
        screenModel.state.value.isLoading shouldBe false
        screenModel.state.value.items.shouldHaveSize(1)

        // The user swipes again; the retried append succeeds and clears the stale page error.
        screenModel.onPageChanged(0)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.pageError shouldBe null
        screenModel.state.value.items.shouldHaveSize(2)
        // The failed page was retried, not skipped.
        requestedPages shouldBe listOf(1, 2, 2)
    }

    @Test
    fun `pagination stops when hasNextPage is false`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(901L) { page ->
            if (page == 1) {
                FeedPage((0 until 5).map { videoItem("stop-p1-$it") }, hasNextPage = true)
            } else {
                FeedPage((0 until 2).map { videoItem("stop-p2-$it") }, hasNextPage = false)
            }
        }
        val screenModel = buildModel(sourceId = 901L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(5)

        screenModel.onPageChanged(3)
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(7)

        // Further page-changed events must not fetch again once the source reported the end.
        screenModel.onPageChanged(6)
        testDispatcher.scheduler.advanceUntilIdle()

        source.requestedPages shouldBe listOf(1, 2)
        screenModel.state.value.canLoadMore shouldBe false
    }

    @Test
    fun `duplicate ids across pages are deduped`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(902L) { page ->
            if (page == 1) {
                FeedPage(listOf(videoItem("dup")), hasNextPage = true)
            } else {
                FeedPage(listOf(videoItem("dup"), videoItem("fresh")), hasNextPage = false)
            }
        }
        val screenModel = buildModel(sourceId = 902L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.onPageChanged(0)
        testDispatcher.scheduler.advanceUntilIdle()

        source.requestedPages shouldBe listOf(1, 2)
        screenModel.state.value.items.map { it.id } shouldBe listOf("dup", "fresh")
    }

    @Test
    fun `clearSearch restores the base feed and scroll target`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(
            id = 903L,
            feedProvider = {
                FeedPage(listOf(videoItem("base-a"), videoItem("base-b")), hasNextPage = false)
            },
            searchProvider = {
                FeedPage(listOf(videoItem("search-result")), hasNextPage = false)
            },
        )
        val screenModel = buildModel(sourceId = 903L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(2)

        // The user was on the second video before searching.
        screenModel.onPageChanged(1)
        screenModel.search("tag")
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(1)
        screenModel.state.value.searchQuery shouldBe "tag"

        screenModel.clearSearch()
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.searchQuery shouldBe ""
        screenModel.state.value.items.map { it.id } shouldBe listOf("base-a", "base-b")
        screenModel.state.value.targetPageIndex shouldBe 1
    }

    @Test
    fun `non-feed source id sets an error and stays non-critical`() = runTest(testDispatcher) {
        val notAFeed = object : AnimeSource {
            override val id: Long = 999L
            override val name: String = "Catalogue Source"
            override val lang: String = "all"

            override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime
            override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = emptyList()
            override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()
            override suspend fun getVideoList(episode: SEpisode): List<Video> = emptyList()
        }

        val screenModel = buildModel(sourceId = 999L, manager = sourceManagerOf(notAFeed))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.error shouldBe "Source is not a video feed source"
        screenModel.state.value.isLoading shouldBe false
        screenModel.state.value.items.shouldHaveSize(0)
    }

    @Test
    fun `malformed persisted filters do not crash source switch`() = runTest(testDispatcher) {
        class SortFilter : AnimeFilter.Select<String>("Sort", arrayOf("Trending", "Recent"), 0)
        class NsfwFilter : AnimeFilter.CheckBox("Nsfw", false)

        val source = object : AnimeFeedSource {
            override val id: Long = 904L
            override val name: String = "Filter Feed"
            override val lang: String = "all"

            override fun getFilterList(): AnimeFilterList = AnimeFilterList(SortFilter(), NsfwFilter())

            override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage =
                FeedPage(listOf(videoItem("filtered")), hasNextPage = false)

            override suspend fun getSearchFeed(page: Int, cursor: String?, query: String, filters: AnimeFilterList): FeedPage =
                getFeed(page, cursor, filters)
        }

        val preferences = SourcePreferences(MapPreferenceStore())
        preferences.lastReelsFilter(904L).set(";;garbage=x;0=zzz;1=9:true")

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 904L,
            sourceManager = sourceManagerOf(source),
            sourcePreferences = preferences,
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = FakeReelsFavoriteRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // The malformed values are skipped; the feed still loads.
        screenModel.state.value.items.shouldHaveSize(1)
        screenModel.state.value.filters.filterIsInstance<SortFilter>().first().state shouldBe 0
        screenModel.state.value.filters.filterIsInstance<NsfwFilter>().first().state shouldBe false
    }

    @Test
    fun `incognito unlike still reaches the database`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(905L) { FeedPage(listOf(videoItem("vid-x")), hasNextPage = false) }
        val fakeFavorites = FakeReelsFavoriteRepository()
        fakeFavorites.favorites["vid-x" to 905L] = ReelsFavorite(
            videoId = "vid-x",
            sourceId = 905L,
            title = null,
            author = null,
            videoUrl = "https://example.com/vid-x.mp4",
            videoUrlHd = null,
            posterUrl = "https://example.com/vid-x.jpg",
            posterUrlVertical = null,
            webUrl = null,
            durationSec = null,
            hasAudio = true,
            addedAt = Date(1_000L),
        )

        val screenModel =
            buildModel(sourceId = 905L, manager = sourceManagerOf(source), repository = fakeFavorites, incognito = true)
        testDispatcher.scheduler.advanceUntilIdle()

        // The persisted like is loaded, then unliked: the removal must persist even though
        // new likes are blocked in incognito.
        screenModel.state.value.likedIds.contains("vid-x") shouldBe true
        screenModel.toggleLike(videoItem("vid-x"))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.likedIds.contains("vid-x") shouldBe false
        fakeFavorites.favorites.isEmpty() shouldBe true
    }

    @Test
    fun `offline playlist targets the initial page`() = runTest(testDispatcher) {
        val favorites = listOf(
            offlineFavorite("off-a"),
            offlineFavorite("off-b"),
        )

        val screenModel = buildModel(
            sourceId = 301L,
            manager = sourceManagerOf(),
            initialFavorites = favorites,
            initialPage = 1,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.isOffline shouldBe true
        screenModel.state.value.items.map { it.id } shouldBe listOf("off-a", "off-b")
        screenModel.state.value.targetPageIndex shouldBe 1
    }

    @Test
    fun `onPageChanged updates activeIndex and triggers pagination`() = runTest(testDispatcher) {
        val source = RecordingFeedSource(906L) { page ->
            FeedPage((0 until 5).map { videoItem("adv-p$page-$it") }, hasNextPage = true)
        }
        val screenModel = buildModel(sourceId = 906L, manager = sourceManagerOf(source))
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.onPageChanged(4)
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.activeIndex shouldBe 4
        screenModel.state.value.isPlaying shouldBe true
        source.requestedPages shouldBe listOf(1, 2)
    }

    private fun videoItem(id: String) = ShortVideoItem(
        id = id,
        videoUrl = "https://example.com/$id.mp4",
        posterUrl = "https://example.com/$id.jpg",
    )

    private fun offlineFavorite(videoId: String) = ReelsFavorite(
        videoId = videoId,
        sourceId = 301L,
        title = "Saved $videoId",
        author = "Alice",
        videoUrl = "https://example.com/$videoId.mp4",
        videoUrlHd = null,
        posterUrl = "https://example.com/$videoId.jpg",
        posterUrlVertical = null,
        webUrl = null,
        durationSec = 9.0,
        hasAudio = true,
        addedAt = Date(0),
    )

    private class RecordingFeedSource(
        override val id: Long,
        override val name: String = "Feed $id",
        private val searchProvider: ((Int) -> FeedPage)? = null,
        private val feedProvider: (Int) -> FeedPage,
    ) : AnimeFeedSource {
        val requestedPages = mutableListOf<Int>()

        override val lang: String = "all"

        override suspend fun getFeed(page: Int, cursor: String?, filters: AnimeFilterList): FeedPage {
            requestedPages += page
            return feedProvider(page)
        }

        override suspend fun getSearchFeed(page: Int, cursor: String?, query: String, filters: AnimeFilterList): FeedPage {
            requestedPages += page
            return (searchProvider ?: feedProvider)(page)
        }
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
        sourceId: Long,
        manager: AnimeSourceManager,
        repository: ReelsFavoriteRepository = FakeReelsFavoriteRepository(),
        incognito: Boolean = false,
        initialFavorites: List<ReelsFavorite> = emptyList(),
        initialPage: Int = 0,
    ) = ReelsFeedScreenModel(
        initialSourceId = sourceId,
        initialFavorites = initialFavorites,
        initialPage = initialPage,
        sourceManager = manager,
        sourcePreferences = SourcePreferences(MapPreferenceStore()),
        ioDispatcher = testDispatcher,
        isIncognito = { incognito },
        sourceIconProvider = { null },
        reelsFavoriteRepository = repository,
    )

    @Test
    fun `offline favorites playlist opens liked videos without a live source`() = runTest(testDispatcher) {
        val favorite = ReelsFavorite(
            videoId = "vid-f",
            sourceId = 301L,
            title = "Saved reel",
            author = "Alice",
            videoUrl = "https://example.com/f.mp4",
            videoUrlHd = null,
            posterUrl = "https://example.com/f.jpg",
            posterUrlVertical = null,
            webUrl = "https://www.redgifs.com/watch/vid-f",
            durationSec = 9.0,
            hasAudio = true,
            addedAt = Date(0),
        )

        // No installed feed source for 301: offline mode must not depend on it.
        val emptySourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val sources: Flow<List<AnimeSource>> = MutableStateFlow(emptyList())
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = MutableStateFlow(emptyList())
            override fun get(sourceKey: Long): AnimeSource? = null
            override fun getOrStub(sourceKey: Long): AnimeSource =
                StubAnimeSource(id = sourceKey, lang = "", name = "")
            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val screenModel = ReelsFeedScreenModel(
            initialSourceId = 301L,
            initialFavorites = listOf(favorite),
            initialPage = 0,
            sourceManager = emptySourceManager,
            sourcePreferences = SourcePreferences(MapPreferenceStore()),
            ioDispatcher = testDispatcher,
            isIncognito = { false },
            sourceIconProvider = { null },
            reelsFavoriteRepository = FakeReelsFavoriteRepository(),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        screenModel.state.value.isOffline shouldBe true
        screenModel.state.value.items.shouldHaveSize(1)
        screenModel.state.value.items.first().id shouldBe "vid-f"
        screenModel.state.value.items.first().videoUrl shouldBe "https://example.com/f.mp4"
        screenModel.state.value.likedIds shouldBe setOf("vid-f")

        // Network entry points are no-ops in offline mode.
        screenModel.loadFeed(reset = true)
        screenModel.search("cosplay")
        testDispatcher.scheduler.advanceUntilIdle()
        screenModel.state.value.items.shouldHaveSize(1)
        screenModel.state.value.searchQuery shouldBe ""
    }
}
